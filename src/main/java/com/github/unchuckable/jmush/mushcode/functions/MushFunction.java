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
 *
 * <p>Set {@link #catenateArgs} to {@code true} for a function matching one of the 13 entries in
 * {@code functions.c}'s table with a negative {@code nargs} ({@code CAPSTR}, {@code ESCAPE}, {@code
 * HTML_ESCAPE}, {@code HTML_UNESCAPE}, {@code LCSTR}, {@code REVERSE}, {@code S}, {@code SEARCH},
 * {@code SECURE}, {@code STRLEN}, {@code UCSTR}, {@code URL_ESCAPE}, {@code URL_UNESCAPE}) --
 * {@code -1} is the only negative value used anywhere in that table, and it means {@code
 * parse_arglist} does not comma-split the argument at all: the whole parenthesized text is
 * evaluated as a single expression (commas and all, literal except where they still happen to parse
 * as nested constructs) rather than split at top-level commas first. In practice always paired with
 * the single-{@code Value} {@code MushFunction1} shape, since every real {@code nargs = -1}
 * function takes exactly one (uncomma-split) argument -- {@link
 * com.github.unchuckable.jmush.mushcode.MushcodeParser} is what actually skips comma-splitting for
 * a {@code catenateArgs} function, both for statically- and dynamically-resolved names (see {@link
 * com.github.unchuckable.jmush.mushcode.expressions.DynamicFunctionExpression}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MushFunction {

  String name();

  int minArgs() default 0;

  int maxArgs() default Integer.MAX_VALUE;

  boolean lazy() default false;

  boolean catenateArgs() default false;
}
