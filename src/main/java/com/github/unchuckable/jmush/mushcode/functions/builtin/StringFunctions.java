package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushErrors;
import com.github.unchuckable.jmush.mushcode.MushValueException;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import java.util.List;

public class StringFunctions {

  @MushFunction(name = "cat")
  public static Value cat(ExecutionContext ctx, List<Value> args) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(args.get(i).asString());
    }
    return Value.of(builder.toString());
  }

  // functions.c's strlen() runs the argument through strip_ansi() first -- jmush never produces
  // ANSI escape codes yet (see DESIGN.md's deferred "ANSI-aware string handling" item), so plain
  // String.length() is equivalent for now.
  @MushFunction(name = "strlen")
  public static Value strlen(ExecutionContext ctx, Value a) {
    return Value.ofInt(a.asString().length());
  }

  /**
   * {@code left(str, n)}. {@code n} is {@code atoi()}-truncated (lenient). Returns "" if the string
   * is empty or {@code n < 1}; otherwise the first {@code n} chars, silently clamped to the
   * string's length if {@code n} overshoots -- never an error either direction. Not ANSI-aware (see
   * {@link #strlen} note).
   */
  @MushFunction(name = "left")
  public static Value left(ExecutionContext ctx, Value str, Value n) {
    String s = str.asString();
    long count = n.atoi();
    if (s.isEmpty() || count < 1) {
      return Value.of("");
    }
    return Value.of(s.substring(0, (int) Math.min(count, s.length())));
  }

  /** {@code right(str, n)} -- same rules as {@link #left}, but returns the last {@code n} chars. */
  @MushFunction(name = "right")
  public static Value right(ExecutionContext ctx, Value str, Value n) {
    String s = str.asString();
    long count = n.atoi();
    if (s.isEmpty() || count < 1) {
      return Value.of("");
    }
    int start = (int) Math.max(0, s.length() - count);
    return Value.of(s.substring(start));
  }

  // functions.c's LBUF_SIZE (alloc.h) -- mid() rejects a start/len past this, oracle-verified
  // (mid(hello,8000,2) errors, mid(hello,7999,2) doesn't). Known, accepted gap: a start/len
  // literal that overflows a 64-bit long (e.g. a 20-digit number) won't trip this check the same
  // way the C reference's own (implementation-defined) overflow behavior does -- Value.atoi()
  // collapses any unparseable-as-long input to 0 rather than replicating C's overflow value,
  // and reworking that shared, widely-used contract isn't worth it for this vanishingly-unlikely
  // input shape.
  private static final long LBUF_SIZE_MINUS_ONE = 7999;

  /**
   * {@code mid(str, start, len)}. Both args {@code atoi()}-truncated. Unlike {@link #left}/{@link
   * #right}, negative or too-large (see {@link #LBUF_SIZE_MINUS_ONE}) input is an error ({@link
   * MushErrors#OUT_OF_RANGE}), not silently clamped. Returns "" (not an error) if {@code start} is
   * past the end of the string or {@code len == 0}; otherwise clamps {@code start + len} down to
   * the string's length.
   */
  @MushFunction(name = "mid")
  public static Value mid(ExecutionContext ctx, Value str, Value startArg, Value lenArg) {
    long start = startArg.atoi();
    long len = lenArg.atoi();
    if (start < 0 || len < 0 || start > LBUF_SIZE_MINUS_ONE || len > LBUF_SIZE_MINUS_ONE) {
      throw new MushValueException(MushErrors.OUT_OF_RANGE);
    }
    String s = str.asString();
    if (start >= s.length() || len == 0) {
      return Value.of("");
    }
    int end = (int) Math.min(start + len, s.length());
    return Value.of(s.substring((int) start, end));
  }

  /**
   * {@code trim(str[, side[, char]])}. {@code side} is {@code "l"}/{@code "r"} (case-insensitive)
   * to trim only that end, anything else (including omitted) trims both. {@code char} is a single
   * trim character (see {@link #parseFillChar}), defaulting to space.
   */
  @MushFunction(name = "trim", minArgs = 1, maxArgs = 3)
  public static Value trim(ExecutionContext ctx, List<Value> args) {
    String s = args.get(0).asString();
    String side = args.size() > 1 ? args.get(1).asString() : "";
    char trimChar = args.size() > 2 ? parseFillChar(args.get(2), ' ') : ' ';
    boolean trimLeft = !"r".equalsIgnoreCase(side);
    boolean trimRight = !"l".equalsIgnoreCase(side);

    int start = 0;
    int end = s.length();
    if (trimLeft) {
      while (start < end && s.charAt(start) == trimChar) {
        start++;
      }
    }
    if (trimRight) {
      while (end > start && s.charAt(end - 1) == trimChar) {
        end--;
      }
    }
    return Value.of(s.substring(start, end));
  }

  /**
   * {@code ljust(str, width[, fillchar])}. {@code width} is {@code atoi()}-truncated. Pads on the
   * right with {@code fillchar} (see {@link #parseFillChar}, defaulting to space); if {@code width}
   * doesn't exceed the string's length (including a negative width), the string is returned
   * unchanged -- this never truncates.
   */
  @MushFunction(name = "ljust", minArgs = 2, maxArgs = 3)
  public static Value ljust(ExecutionContext ctx, List<Value> args) {
    String s = args.get(0).asString();
    long width = args.get(1).atoi();
    char fill = args.size() > 2 ? parseFillChar(args.get(2), ' ') : ' ';
    long spaces = width - s.length();
    if (spaces <= 0) {
      return Value.of(s);
    }
    StringBuilder result = new StringBuilder(s);
    for (long i = 0; i < spaces; i++) {
      result.append(fill);
    }
    return Value.of(result.toString());
  }

  /** {@code rjust(str, width[, fillchar])} -- like {@link #ljust}, but pads on the left. */
  @MushFunction(name = "rjust", minArgs = 2, maxArgs = 3)
  public static Value rjust(ExecutionContext ctx, List<Value> args) {
    String s = args.get(0).asString();
    long width = args.get(1).atoi();
    char fill = args.size() > 2 ? parseFillChar(args.get(2), ' ') : ' ';
    long spaces = width - s.length();
    if (spaces <= 0) {
      return Value.of(s);
    }
    StringBuilder result = new StringBuilder();
    for (long i = 0; i < spaces; i++) {
      result.append(fill);
    }
    result.append(s);
    return Value.of(result.toString());
  }

  /**
   * Shared {@code trim}/{@code ljust}/{@code rjust} fill/trim-character validation: empty means
   * {@code defaultChar}, exactly one char is used as-is, more than one is {@link
   * MushErrors#SEPARATOR_MUST_BE_ONE_CHARACTER} -- not "use the first character".
   */
  private static char parseFillChar(Value value, char defaultChar) {
    String s = value.asString();
    if (s.isEmpty()) {
      return defaultChar;
    }
    if (s.length() > 1) {
      throw new MushValueException(MushErrors.SEPARATOR_MUST_BE_ONE_CHARACTER);
    }
    return s.charAt(0);
  }
}
