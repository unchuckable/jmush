package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushValueException;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.builtin.MathFunctions;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflects over an explicit, hand-maintained list of provider classes and builds the {@code
 * Map<String, MushFunctionHandler>} the parser expects, one entry per
 * {@code @MushFunction}-annotated method. Adapters are built once here via {@code
 * MethodHandle}/{@code LambdaMetafactory} (not per-call reflective {@code Method.invoke}), so the
 * natural-parameter style costs nothing at runtime once JIT-warmed -- the same mechanism the JVM
 * uses for ordinary lambdas/method references. Generic arg-count validation and {@link
 * MushValueException} -> {@code #-1 ...} conversion are applied here too, so individual function
 * bodies stay free of that boilerplate.
 */
public class FunctionRegistry {

  private static final List<Class<?>> PROVIDER_CLASSES = Arrays.asList(MathFunctions.class);

  public static Map<String, MushFunctionHandler> build() {
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
    return registry;
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

    MushFunctionHandler unvalidated;
    int minArgs;
    int maxArgs;
    if (paramTypes.length == 2 && paramTypes[1] == List.class) {
      // variadic: arity isn't derivable from the signature, so the annotation must say
      unvalidated = adaptVariadic(lookup, method);
      minArgs = annotation.minArgs();
      maxArgs = annotation.maxArgs();
    } else if (allTrailingParamsAreValue(paramTypes)) {
      // fixed-arity: the Value parameter count *is* the arity, so derive it rather than
      // trust the annotation to restate it correctly
      int arity = paramTypes.length - 1;
      unvalidated = adaptFixedArity(lookup, method, arity);
      minArgs = arity;
      maxArgs = arity;
    } else {
      throw new IllegalArgumentException(
          "@MushFunction method must take (ExecutionContext, Value...) or "
              + "(ExecutionContext, List<Value>): "
              + method);
    }

    String name = annotation.name();
    return (ctx, args) -> {
      int argCount = args == null ? 0 : args.size();
      if (argCount < minArgs || argCount > maxArgs) {
        return Value.of(
            "#-1 FUNCTION ("
                + name.toUpperCase()
                + ") EXPECTS "
                + expectedArgsDescription(minArgs, maxArgs)
                + " ARGUMENTS");
      }
      try {
        return unvalidated.execute(ctx, args);
      } catch (MushValueException e) {
        return Value.of(e.getMessage());
      }
    };
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
    return "BETWEEN " + minArgs + " AND " + maxArgs;
  }

  private static MushFunctionHandler adaptVariadic(MethodHandles.Lookup lookup, Method method) {
    try {
      MethodHandle target = lookup.unreflect(method);
      CallSite site =
          LambdaMetafactory.metafactory(
              lookup,
              "execute",
              MethodType.methodType(MushFunctionHandler.class),
              MethodType.methodType(Value.class, ExecutionContext.class, List.class),
              target,
              target.type());
      return (MushFunctionHandler) site.getTarget().invoke();
    } catch (Throwable t) {
      throw new IllegalStateException("Failed to adapt " + method, t);
    }
  }

  private static MushFunctionHandler adaptFixedArity(
      MethodHandles.Lookup lookup, Method method, int arity) {
    try {
      MethodHandle target = lookup.unreflect(method);
      switch (arity) {
        case 1:
          {
            MushFunction1 fn =
                (MushFunction1)
                    LambdaMetafactory.metafactory(
                            lookup,
                            "apply",
                            MethodType.methodType(MushFunction1.class),
                            MethodType.methodType(Value.class, ExecutionContext.class, Value.class),
                            target,
                            target.type())
                        .getTarget()
                        .invoke();
            return (ctx, args) -> fn.apply(ctx, args.get(0));
          }
        case 2:
          {
            MushFunction2 fn =
                (MushFunction2)
                    LambdaMetafactory.metafactory(
                            lookup,
                            "apply",
                            MethodType.methodType(MushFunction2.class),
                            MethodType.methodType(
                                Value.class, ExecutionContext.class, Value.class, Value.class),
                            target,
                            target.type())
                        .getTarget()
                        .invoke();
            return (ctx, args) -> fn.apply(ctx, args.get(0), args.get(1));
          }
        default:
          throw new IllegalArgumentException(
              "No arity-specific MushFunctionN interface for "
                  + arity
                  + " Value arguments: "
                  + method
                  + " -- add one, or use the List<Value> variadic form.");
      }
    } catch (Throwable t) {
      throw new IllegalStateException("Failed to adapt " + method, t);
    }
  }

  private FunctionRegistry() {
    // static factory, do not instantiate
  }
}
