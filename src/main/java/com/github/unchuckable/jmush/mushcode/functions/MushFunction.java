package com.github.unchuckable.jmush.mushcode.functions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method in a provider class (e.g. {@code MathFunctions}) as a built-in mushcode
 * function, mirroring {@code functions.c}'s {@code FUNCTION()} macro table. {@link
 * FunctionRegistry} reflects over an explicit list of provider classes and adapts each annotated
 * method into the uniform {@link MushFunctionHandler} the parser expects.
 *
 * <p>The annotated method's first parameter must be {@code ExecutionContext}. The remaining
 * parameters are either a fixed number of {@code Value} parameters (for readability -- e.g. {@code
 * sub(ExecutionContext ctx, Value a, Value b)}), or a single trailing {@code List<Value>} for
 * genuinely variadic functions (e.g. {@code add()}) -- {@link FunctionRegistry} evaluates every
 * argument eagerly before calling either shape, matching pure functions over already-evaluated
 * values ({@code functions.c}'s ordinary, non-{@code FN_NO_EVAL} functions).
 *
 * <p>Set {@link #lazy} to {@code true} for a function that needs raw, unevaluated access to its
 * arguments instead (mirroring {@code functions.c}'s {@code FN_NO_EVAL} flag -- e.g. {@code
 * switch()}, which must only evaluate its matching branch). A lazy method's signature is fixed:
 * {@code (ExecutionContext, List<Expression>)}; it is registered as-is with no evaluation wrapper,
 * so it's responsible for calling {@code Expression.evaluateExpression} itself, selectively.
 *
 * <p>{@link #minArgs} and {@link #maxArgs} are only consulted for the {@code List<Value>}
 * (variadic) and {@code lazy} shapes, where arity isn't derivable from the signature. For the
 * fixed-arity {@code Value...} shape, {@link FunctionRegistry} derives the required argument count
 * directly from the parameter count -- these are ignored there, so there's no way for the declared
 * arity and the annotation to drift out of sync.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MushFunction {

  String name();

  int minArgs() default 0;

  int maxArgs() default Integer.MAX_VALUE;

  boolean lazy() default false;
}
