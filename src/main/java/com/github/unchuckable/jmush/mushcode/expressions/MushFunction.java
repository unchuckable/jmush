package com.github.unchuckable.jmush.mushcode.expressions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method in a provider class (e.g. {@code MathFunctions}) as a built-in
 * mushcode function, mirroring {@code functions.c}'s {@code FUNCTION()} macro table.
 * {@link FunctionRegistry} reflects over an explicit list of provider classes and adapts
 * each annotated method into the uniform {@link MushFunctionHandler} the parser expects.
 *
 * <p>The annotated method's first parameter must be {@code ExecutionContext}. The
 * remaining parameters are either a fixed number of {@code Value} parameters (for
 * readability -- e.g. {@code sub(ExecutionContext ctx, Value a, Value b)}), or a single
 * trailing {@code List<Value>} for genuinely variadic functions (e.g. {@code add()}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MushFunction {

  String name();

  int minArgs() default 0;

  int maxArgs() default Integer.MAX_VALUE;
}
