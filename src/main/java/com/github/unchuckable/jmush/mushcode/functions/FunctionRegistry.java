package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MushValueException;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.builtin.ControlFunctions;
import com.github.unchuckable.jmush.mushcode.functions.builtin.MathFunctions;
import com.github.unchuckable.jmush.mushcode.functions.builtin.RegisterFunctions;
import com.github.unchuckable.jmush.mushcode.functions.builtin.StringFunctions;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflects over an explicit, hand-maintained list of provider classes and builds a lookup of {@code
 * name -> MushFunctionHandler}, one entry per {@code @MushFunction}-annotated method. {@link
 * MushcodeParser} holds a reference to a {@code FunctionRegistry} instance (via {@link #build()})
 * rather than the raw {@code Map} directly -- separates "how function names resolve" from "how
 * mushcode gets scanned," and lets a future caller that only needs name resolution (not parsing)
 * depend on this type instead of the whole parser. Adapters are built once here via {@code
 * MethodHandle}/{@code LambdaMetafactory} (not per-call reflective {@code Method.invoke}), so the
 * natural-parameter style costs nothing at runtime once JIT-warmed -- the same mechanism the JVM
 * uses for ordinary lambdas/method references. Generic arg-count validation and {@link
 * MushValueException} -> {@code #-1 ...} conversion are applied here too, so individual function
 * bodies stay free of that boilerplate.
 */
public class FunctionRegistry {

  private static final List<Class<?>> PROVIDER_CLASSES =
      Arrays.asList(
          MathFunctions.class,
          StringFunctions.class,
          RegisterFunctions.class,
          ControlFunctions.class);

  /**
   * The erased signature shared by the two {@code List}-taking method shapes: lazy methods take
   * {@code (ExecutionContext, List<Expression>)}, variadic ones {@code (ExecutionContext,
   * List<Value>)} -- indistinguishable after erasure, so one {@link #adapt} signature serves both.
   */
  private static final MethodType LIST_SHAPE =
      MethodType.methodType(Value.class, ExecutionContext.class, List.class);

  /**
   * The shape of a variadic, non-{@code lazy} {@code @MushFunction} method -- pure over
   * already-evaluated {@link Value}s. The variadic branch of {@link #buildHandler} bridges from the
   * {@code Expression}-based {@link MushFunctionHandler} contract by evaluating every argument into
   * a fresh list first. Fixed-arity methods skip the intermediate list entirely ({@link
   * #adaptFixedArity}'s wrapper evaluates each argument straight into the {@link
   * MushFunction1}/{@link MushFunction2} call), and {@code lazy} methods are adapted straight to
   * {@link MushFunctionHandler} with no evaluation at all.
   */
  private interface EagerFunctionHandler {
    Value execute(ExecutionContext context, List<Value> parameters);
  }

  private final Map<String, MushFunctionHandler> handlers;

  private FunctionRegistry(Map<String, MushFunctionHandler> handlers) {
    this.handlers = handlers;
  }

  /**
   * Wraps an arbitrary, already-built {@code name -> MushFunctionHandler} map directly, bypassing
   * the {@code @MushFunction} reflection pipeline entirely -- for tests that want to inject a stub
   * handler without a real provider class.
   */
  public static FunctionRegistry of(Map<String, MushFunctionHandler> handlers) {
    return new FunctionRegistry(new HashMap<>(handlers));
  }

  public static FunctionRegistry build() {
    Map<String, MushFunctionHandler> registry = new HashMap<>();
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    for (Class<?> providerClass : PROVIDER_CLASSES) {
      for (Method method : providerClass.getDeclaredMethods()) {
        MushFunction annotation = method.getAnnotation(MushFunction.class);
        if (annotation == null) {
          continue;
        }
        registry.put(annotation.name().toLowerCase(), buildHandler(lookup, method, annotation));
      }
    }
    return new FunctionRegistry(registry);
  }

  public MushFunctionHandler getFunction(String name) {
    return handlers.get(name.toLowerCase());
  }

  private static MushFunctionHandler buildHandler(
      MethodHandles.Lookup lookup, Method method, MushFunction annotation) {
    if (!Modifier.isStatic(method.getModifiers())) {
      throw new IllegalArgumentException("@MushFunction method must be static: " + method);
    }
    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length < 1 || paramTypes[0] != ExecutionContext.class) {
      throw new IllegalArgumentException(
          "@MushFunction method's first parameter must be ExecutionContext: " + method);
    }

    // "core" always operates on unevaluated Expressions -- each shape below decides how (and
    // whether) to evaluate them, so arg-count validation and MushValueException conversion can
    // be applied uniformly regardless of laziness.
    MushFunctionHandler core;
    int minArgs;
    int maxArgs;
    if (annotation.lazy()) {
      if (paramTypes.length != 2 || paramTypes[1] != List.class) {
        throw new IllegalArgumentException(
            "lazy @MushFunction method must take (ExecutionContext, List<Expression>): " + method);
      }
      core = adapt(lookup, method, MushFunctionHandler.class, "execute", LIST_SHAPE);
      minArgs = annotation.minArgs();
      maxArgs = annotation.maxArgs();
    } else if (paramTypes.length == 2 && paramTypes[1] == List.class) {
      // variadic: arity isn't derivable from the signature, so the annotation must say
      EagerFunctionHandler eagerHandler =
          adapt(lookup, method, EagerFunctionHandler.class, "execute", LIST_SHAPE);
      core = (ctx, args) -> eagerHandler.execute(ctx, evaluateAll(args, ctx));
      minArgs = annotation.minArgs();
      maxArgs = annotation.maxArgs();
    } else if (allTrailingParamsAreValue(paramTypes)) {
      // fixed-arity: the Value parameter count *is* the arity, so derive it rather than
      // trust the annotation to restate it correctly
      int arity = paramTypes.length - 1;
      core = adaptFixedArity(lookup, method, arity);
      minArgs = arity;
      maxArgs = arity;
    } else {
      throw new IllegalArgumentException(
          "@MushFunction method must take (ExecutionContext, Value...), "
              + "(ExecutionContext, List<Value>), or (with lazy=true) "
              + "(ExecutionContext, List<Expression>): "
              + method);
    }

    String name = annotation.name();
    return (ctx, argExpressions) -> {
      List<Expression> args =
          argExpressions == null ? Collections.<Expression>emptyList() : argExpressions;
      if (args.size() < minArgs || args.size() > maxArgs) {
        return Value.of(
            "#-1 FUNCTION ("
                + name.toUpperCase()
                + ") EXPECTS "
                + expectedArgsDescription(minArgs, maxArgs)
                + " ARGUMENTS");
      }
      try {
        return core.execute(ctx, args);
      } catch (MushValueException e) {
        return Value.of(e.getMessage());
      }
    };
  }

  private static List<Value> evaluateAll(List<Expression> parameters, ExecutionContext context) {
    List<Value> values = new ArrayList<>(parameters.size());
    for (Expression parameter : parameters) {
      values.add(parameter.evaluateExpression(context));
    }
    return values;
  }

  private static boolean allTrailingParamsAreValue(Class<?>[] paramTypes) {
    if (paramTypes.length < 2) {
      return false;
    }
    for (int i = 1; i < paramTypes.length; i++) {
      if (paramTypes[i] != Value.class) {
        return false;
      }
    }
    return true;
  }

  private static String expectedArgsDescription(int minArgs, int maxArgs) {
    if (minArgs == maxArgs) {
      return String.valueOf(minArgs);
    }
    if (maxArgs == Integer.MAX_VALUE) {
      return "AT LEAST " + minArgs;
    }
    // Oracle-verified: a two-value range (e.g. ljust/rjust's 2-3) reads "N OR M ARGUMENTS", not
    // "BETWEEN N AND M ARGUMENTS" -- that phrasing only kicks in for a wider range (e.g. trim's
    // 1-3, confirmed "BETWEEN 1 AND 3 ARGUMENTS").
    if (maxArgs - minArgs == 1) {
      return minArgs + " OR " + maxArgs;
    }
    return "BETWEEN " + minArgs + " AND " + maxArgs;
  }

  /**
   * Wraps a fixed-arity method in a handler that evaluates each argument expression directly into
   * the {@code MushFunctionN} call -- the arity was already validated by the wrapper in {@link
   * #buildHandler}, so the positional gets are safe, and no intermediate {@code List<Value>} (as
   * the variadic shape needs) is built.
   */
  private static MushFunctionHandler adaptFixedArity(
      MethodHandles.Lookup lookup, Method method, int arity) {
    switch (arity) {
      case 1:
        {
          MushFunction1 fn =
              adapt(
                  lookup,
                  method,
                  MushFunction1.class,
                  "apply",
                  MethodType.methodType(Value.class, ExecutionContext.class, Value.class));
          return (ctx, args) -> fn.apply(ctx, args.get(0).evaluateExpression(ctx));
        }
      case 2:
        {
          MushFunction2 fn =
              adapt(
                  lookup,
                  method,
                  MushFunction2.class,
                  "apply",
                  MethodType.methodType(
                      Value.class, ExecutionContext.class, Value.class, Value.class));
          return (ctx, args) ->
              fn.apply(
                  ctx, args.get(0).evaluateExpression(ctx), args.get(1).evaluateExpression(ctx));
        }
      case 3:
        {
          MushFunction3 fn =
              adapt(
                  lookup,
                  method,
                  MushFunction3.class,
                  "apply",
                  MethodType.methodType(
                      Value.class, ExecutionContext.class, Value.class, Value.class, Value.class));
          return (ctx, args) ->
              fn.apply(
                  ctx,
                  args.get(0).evaluateExpression(ctx),
                  args.get(1).evaluateExpression(ctx),
                  args.get(2).evaluateExpression(ctx));
        }
      default:
        throw new IllegalArgumentException(
            "No arity-specific MushFunctionN interface for "
                + arity
                + " Value arguments: "
                + method
                + " -- add one, or use the List<Value> variadic form.");
    }
  }

  /**
   * Builds a {@code LambdaMetafactory} adapter from the annotated static method onto the given
   * single-abstract-method interface -- the same class-spinning mechanism {@code javac} uses for
   * method references, so post-JIT the call costs the same as an ordinary lambda invocation.
   */
  private static <T> T adapt(
      MethodHandles.Lookup lookup,
      Method method,
      Class<T> samInterface,
      String samMethodName,
      MethodType samSignature) {
    try {
      MethodHandle target = lookup.unreflect(method);
      CallSite site =
          LambdaMetafactory.metafactory(
              lookup,
              samMethodName,
              MethodType.methodType(samInterface),
              samSignature,
              target,
              target.type());
      return samInterface.cast(site.getTarget().invoke());
    } catch (Throwable t) {
      throw new IllegalStateException("Failed to adapt " + method, t);
    }
  }
}
