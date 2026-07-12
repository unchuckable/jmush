package com.github.unchuckable.jmush.mushcode;

import java.util.Locale;

/**
 * Signals eval.c's {@code EV_FMAND} failure: a function-name candidate inside {@code [...]}
 * forced-eval didn't resolve to a real function. Thrown from two places -- {@code
 * MushcodeParser.handleFunctionCall} (statically-named calls, at parse time) and {@code
 * expressions.DynamicFunctionExpression} (dynamically-resolved names, at evaluation time) -- and
 * caught at every argument boundary the failure can propagate through (each function-call argument,
 * the catenated single-argument span, and the {@code [...]} block itself), converting it to the
 * error {@link Value} in place -- oracle-verified: {@code [cat(add(1,2),badname(3))]} gives {@code
 * "3 #-1 FUNCTION (BADNAME) NOT FOUND"} (the sibling argument's output survives), not a whole-block
 * failure. Like {@link MushValueException}, this is an expected mushcode-level condition, not a
 * Java-level bug, so stack-trace capture is disabled.
 */
public class MandatoryMatchException extends RuntimeException {

  public MandatoryMatchException(String message) {
    super(message, null, false, false);
  }

  /**
   * Builds the oracle-verified {@code "#-1 FUNCTION (name) NOT FOUND"} message (name lowercased,
   * surrounding text uppercase regardless of source casing) shared by both throw sites documented
   * above.
   */
  public static MandatoryMatchException functionNotFound(String name) {
    return new MandatoryMatchException(
        "#-1 FUNCTION (" + name.toLowerCase(Locale.ROOT) + ") NOT FOUND");
  }
}
