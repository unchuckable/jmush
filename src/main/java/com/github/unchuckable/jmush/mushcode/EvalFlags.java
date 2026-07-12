package com.github.unchuckable.jmush.mushcode;

/**
 * The subset of {@code eval.c}'s {@code EV_*} evaluation flags that are structural (decided once,
 * at parse time) rather than dependent on a specific evaluation's runtime values -- matching
 * jmush's parse-once/evaluate-many split. Immutable; {@code with*} methods return a new instance.
 *
 * <p>Only the flags with an observable effect in jmush today are modeled:
 *
 * <ul>
 *   <li>{@code functionCheck} (EV_FCHECK) -- whether {@code name(args)} is parsed as a function
 *       call at all, vs. left as literal text.
 *   <li>{@code strip} (EV_STRIP) -- whether a matched {@code {...}} group has its braces removed
 *       from the output.
 *   <li>{@code compressSpaces} (absence of EV_NO_COMPRESS) -- whether runs of spaces collapse to
 *       one.
 *   <li>{@code fmand} (EV_FMAND) -- whether a function-name candidate that fails to resolve is a
 *       hard {@code #-1 FUNCTION (name) NOT FOUND} error (aborting the rest of that scan -- {@code
 *       eval.c}'s {@code alldone = 1}) instead of the ordinary literal-text fallback. Only {@code
 *       [...]} forced-eval sets this -- see {@code MushcodeParser.handleForcedEval} and {@code
 *       expressions.MandatoryMatchExpression}.
 * </ul>
 *
 * Not modeled (no observable effect yet, since the corresponding feature doesn't exist):
 * EV_NO_LOCATION (needs %l), EV_EVAL/EV_TOP/EV_NOTRACE/EV_STRIP_TS/EV_STRIP_LS/EV_STRIP_ESC/
 * EV_STRIP_AROUND (all narrower call-site concerns, not general parse-time semantics).
 */
public final class EvalFlags {

  /**
   * Matches typical top-level command evaluation (e.g. {@code think}): EV_FCHECK | EV_EVAL |
   * EV_TOP.
   */
  public static final EvalFlags DEFAULT = new EvalFlags(true, false, true, false);

  private final boolean functionCheck;
  private final boolean strip;
  private final boolean compressSpaces;
  private final boolean fmand;

  private EvalFlags(boolean functionCheck, boolean strip, boolean compressSpaces, boolean fmand) {
    this.functionCheck = functionCheck;
    this.strip = strip;
    this.compressSpaces = compressSpaces;
    this.fmand = fmand;
  }

  public boolean isFunctionCheckEnabled() {
    return functionCheck;
  }

  public boolean isStripEnabled() {
    return strip;
  }

  public boolean isSpaceCompressionEnabled() {
    return compressSpaces;
  }

  public boolean isFmandEnabled() {
    return fmand;
  }

  public EvalFlags withFunctionCheck(boolean enabled) {
    return new EvalFlags(enabled, strip, compressSpaces, fmand);
  }

  public EvalFlags withStrip(boolean enabled) {
    return new EvalFlags(functionCheck, enabled, compressSpaces, fmand);
  }

  public EvalFlags withSpaceCompression(boolean enabled) {
    return new EvalFlags(functionCheck, strip, enabled, fmand);
  }

  public EvalFlags withFmand(boolean enabled) {
    return new EvalFlags(functionCheck, strip, compressSpaces, enabled);
  }
}
