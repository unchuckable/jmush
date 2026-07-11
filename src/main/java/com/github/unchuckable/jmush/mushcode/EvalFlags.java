package com.github.unchuckable.jmush.mushcode;

/**
 * The subset of {@code eval.c}'s {@code EV_*} evaluation flags that are structural (decided
 * once, at parse time) rather than dependent on a specific evaluation's runtime values --
 * matching jmush's parse-once/evaluate-many split. Immutable; {@code with*} methods return a
 * new instance.
 *
 * Only the flags with an observable effect in jmush today are modeled:
 * <ul>
 *   <li>{@code functionCheck} (EV_FCHECK) -- whether {@code name(args)} is parsed as a
 *       function call at all, vs. left as literal text.</li>
 *   <li>{@code strip} (EV_STRIP) -- whether a matched {@code {...}} group has its braces
 *       removed from the output.</li>
 *   <li>{@code compressSpaces} (absence of EV_NO_COMPRESS) -- whether runs of spaces
 *       collapse to one.</li>
 * </ul>
 * Not modeled (no observable effect yet, since the corresponding feature doesn't exist):
 * EV_FMAND (needs the Value error-message system), EV_NO_LOCATION (needs %l), EV_EVAL/EV_TOP/
 * EV_NOTRACE/EV_STRIP_TS/EV_STRIP_LS/EV_STRIP_ESC/EV_STRIP_AROUND (all narrower call-site
 * concerns, not general parse-time semantics).
 */
public final class EvalFlags {

  /** Matches typical top-level command evaluation (e.g. {@code think}): EV_FCHECK | EV_EVAL | EV_TOP. */
  public static final EvalFlags DEFAULT = new EvalFlags(true, false, true);

  private final boolean functionCheck;
  private final boolean strip;
  private final boolean compressSpaces;

  private EvalFlags(boolean functionCheck, boolean strip, boolean compressSpaces) {
    this.functionCheck = functionCheck;
    this.strip = strip;
    this.compressSpaces = compressSpaces;
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

  public EvalFlags withFunctionCheck(boolean enabled) {
    return new EvalFlags(enabled, strip, compressSpaces);
  }

  public EvalFlags withStrip(boolean enabled) {
    return new EvalFlags(functionCheck, enabled, compressSpaces);
  }

  public EvalFlags withSpaceCompression(boolean enabled) {
    return new EvalFlags(functionCheck, strip, enabled);
  }
}
