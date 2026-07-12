package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushErrors;
import com.github.unchuckable.jmush.mushcode.MushValueException;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import com.github.unchuckable.jmush.util.WildcardMatcher;
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
   * Shared {@code trim}/{@code ljust}/{@code rjust}/{@code iter} fill/trim/separator-character
   * validation: empty means {@code defaultChar}, exactly one char is used as-is, more than one is
   * {@link MushErrors#SEPARATOR_MUST_BE_ONE_CHARACTER} -- not "use the first character". Package-
   * visible so {@code ControlFunctions.iterFunction} can reuse it for {@code iter()}'s {@code
   * sep}/{@code osep} args (same underlying C {@code delim_check} validation).
   */
  static char parseFillChar(Value value, char defaultChar) {
    String s = value.asString();
    if (s.isEmpty()) {
      return defaultChar;
    }
    if (s.length() > 1) {
      throw new MushValueException(MushErrors.SEPARATOR_MUST_BE_ONE_CHARACTER);
    }
    return s.charAt(0);
  }

  /**
   * {@code words(list[,sep])}. Element count after splitting {@code list} on {@code sep} (default
   * space, see {@link #parseFillChar}) -- reuses {@link ControlFunctions#splitCompressed}/{@link
   * ControlFunctions#splitLiteral}, since functions.c's {@code countwords} repeatedly calls {@code
   * next_token} (same collapse-adjacent-run behavior as {@code split_token}), which ends up fully
   * collapsing all runs for the default separator, same as {@code iter()}'s list-splitting. Zero
   * args (not to be confused with an empty {@code list} arg) returns {@code "0"}, oracle-verified.
   */
  @MushFunction(name = "words")
  public static Value words(ExecutionContext ctx, List<Value> args) {
    checkListSepArity("WORDS", args);
    if (args.isEmpty()) {
      return Value.of("0");
    }
    String list = args.get(0).asString();
    char sep = args.size() > 1 ? parseFillChar(args.get(1), ' ') : ' ';
    String[] elements =
        sep == ' '
            ? ControlFunctions.splitCompressed(list)
            : ControlFunctions.splitLiteral(list, sep);
    return Value.ofInt(elements.length);
  }

  /**
   * {@code first(list[,sep])}. First element after splitting {@code list} on {@code sep} (default
   * space, see {@link #parseFillChar}) -- since only the first element is kept, this is equivalent
   * to {@link ControlFunctions#splitCompressed}/{@link ControlFunctions#splitLiteral} element
   * {@code [0]}. Zero args, or an empty (post-trim, for the default separator) {@code list}, both
   * return {@code ""}, oracle-verified.
   */
  @MushFunction(name = "first")
  public static Value first(ExecutionContext ctx, List<Value> args) {
    checkListSepArity("FIRST", args);
    if (args.isEmpty()) {
      return Value.of("");
    }
    String list = args.get(0).asString();
    char sep = args.size() > 1 ? parseFillChar(args.get(1), ' ') : ' ';
    String[] elements =
        sep == ' '
            ? ControlFunctions.splitCompressed(list)
            : ControlFunctions.splitLiteral(list, sep);
    return Value.of(elements.length == 0 ? "" : elements[0]);
  }

  /**
   * {@code rest(list[,sep])}. Everything after the first element of {@code list}, split on {@code
   * sep} (default space, see {@link #parseFillChar}). Zero args returns {@code ""}.
   *
   * <p><b>Not</b> the same as joining {@code splitCompressed(list)[1..]} back together --
   * functions.c's {@code fun_rest} does a single {@code trim_space_sep} + {@code split_token} call,
   * not a full list split. {@code trim_space_sep} trims the leading/trailing run once, and {@code
   * split_token} skips the first token plus *only* the run of separators immediately following it
   * -- every other interior separator run in the remainder is returned raw, uncollapsed. Ordinary
   * typed-in multiple spaces never surface this (the general argument evaluator's own space
   * compression collapses them before {@code rest()} even runs), but backslash-escaped spaces
   * bypass that and reveal it: {@code rest(a\ \ b\ \ \ c)} -- a two-space run, then a three-space
   * run -- keeps that trailing three-space run intact in the remainder, unlike the fully-collapsed
   * single-space result you'd get from ordinary unescaped input (oracle-verified; NOTE: don't
   * "simplify" the literal multi-space example strings in this comment -- javadoc reflow/spotless
   * silently collapses embedded whitespace runs in prose, which previously turned this into a
   * self-contradictory "is X, not X" sentence). For a non-space {@code sep} there's no
   * pre-compression step at all, so leftover consecutive {@code sep} chars past the first one stay
   * in the remainder verbatim (e.g. {@code rest(a--b,-)} is {@code "-b"}, not {@code "b"}). See
   * {@link #splitFirst}.
   */
  @MushFunction(name = "rest")
  public static Value rest(ExecutionContext ctx, List<Value> args) {
    checkListSepArity("REST", args);
    if (args.isEmpty()) {
      return Value.of("");
    }
    String list = args.get(0).asString();
    char sep = args.size() > 1 ? parseFillChar(args.get(1), ' ') : ' ';
    return Value.of(splitFirst(list, sep)[1]);
  }

  /**
   * {@code words}/{@code first}/{@code rest} share an arity shape {@link
   * com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry}'s generic {@code
   * minArgs}/{@code maxArgs} check can't express: zero args is a valid sentinel case (handled by
   * each caller, not here), one or two args is normal, and anything higher is {@code "1 OR 2
   * ARGUMENTS"} -- not {@code FunctionRegistry}'s {@code "BETWEEN 0 AND 2"}, which is what a {@code
   * minArgs = 0} annotation would generate (oracle-verified via {@code words(a,b,c)}). So these
   * three methods take the registry's default (unchecked) arity and validate it here instead.
   */
  private static void checkListSepArity(String name, List<Value> args) {
    if (args.size() > 2) {
      throw new MushValueException("#-1 FUNCTION (" + name + ") EXPECTS 1 OR 2 ARGUMENTS");
    }
  }

  /**
   * Mirrors functions.c's {@code trim_space_sep} + {@code split_token} pair exactly: trims
   * leading/trailing separator runs (space separator only -- a no-op for any other {@code sep}),
   * then splits off just the first element and the separator run immediately following it, leaving
   * every later interior run in the remainder untouched. Returns a 2-element array: {@code
   * [firstElement, remainder]}, both {@code ""} for an empty (post-trim) {@code list}.
   */
  private static String[] splitFirst(String list, char sep) {
    String trimmed = sep == ' ' ? list.trim() : list;
    if (trimmed.isEmpty()) {
      return new String[] {"", ""};
    }
    int cut = trimmed.indexOf(sep);
    if (cut < 0) {
      return new String[] {trimmed, ""};
    }
    String first = trimmed.substring(0, cut);
    int remainderStart = cut + 1;
    if (sep == ' ') {
      while (remainderStart < trimmed.length() && trimmed.charAt(remainderStart) == ' ') {
        remainderStart++;
      }
    }
    return new String[] {first, trimmed.substring(remainderStart)};
  }

  /**
   * {@code match(list, pattern[, sep])}. Splits {@code list} on {@code sep} (default space, see
   * {@link #parseFillChar}) the same way {@link #words}/{@link #first} do (reusing {@link
   * ControlFunctions#splitCompressed}/{@link ControlFunctions#splitLiteral} -- functions.c's {@code
   * fun_match} calls {@code split_token} per element, which, unlike {@code next_token}, does
   * collapse each token cleanly), then returns the 1-based index of the first element
   * wildcard-matching {@code pattern} (see {@link WildcardMatcher#matches}, same matcher {@code
   * switch()} uses), or {@code "0"} if none match.
   *
   * <p>Unlike {@code words}/{@code first}/{@code rest}, an empty (post-trim) {@code list} is
   * <b>not</b> zero elements to check -- functions.c's matching loop is a {@code do}/{@code while}
   * that always runs at least once, so an empty list still gets checked as a single empty-string
   * token (oracle-verified: {@code match(,*)} is {@code "1"}, not {@code "0"}, since {@code *}
   * matches the empty string).
   */
  @MushFunction(name = "match", minArgs = 2, maxArgs = 3)
  public static Value match(ExecutionContext ctx, List<Value> args) {
    String list = args.get(0).asString();
    String pattern = args.get(1).asString();
    char sep = args.size() > 2 ? parseFillChar(args.get(2), ' ') : ' ';
    String[] elements =
        sep == ' '
            ? ControlFunctions.splitCompressed(list)
            : ControlFunctions.splitLiteral(list, sep);
    if (elements.length == 0) {
      elements = new String[] {""};
    }
    for (int i = 0; i < elements.length; i++) {
      if (WildcardMatcher.matches(pattern, elements[i])) {
        return Value.ofInt(i + 1);
      }
    }
    return Value.of("0");
  }

  /**
   * {@code extract(list, first, len[, sep])}. {@code first}/{@code len} are {@code
   * atoi()}-truncated (lenient, like {@link #left}/{@link #right}/{@link #mid}); {@code first < 1}
   * or {@code len < 1} silently returns {@code ""} (not an error). Otherwise returns the {@code
   * len} elements of {@code list} (split on {@code sep}, default space) starting at the {@code
   * first} one (1-based) -- clamped, not an error, if fewer than {@code len} elements remain
   * (oracle-verified: {@code extract(a b c,2,5)} is {@code "b c"}).
   *
   * <p><b>Not</b> a slice of {@link ControlFunctions#splitCompressed}/{@link
   * ControlFunctions#splitLiteral} rejoined -- functions.c's {@code fun_extract} only ever calls
   * {@code next_token} (never {@code split_token}) while walking to the start/end of the requested
   * range, and {@code next_token} never mutates the buffer it walks; it's used purely to *locate*
   * offsets. So the result is a raw substring of the (leading/trailing-trimmed) list from the start
   * of element {@code first} through just before element {@code first + len}, with everything in
   * between -- including interior multi-separator runs -- preserved verbatim, regardless of {@code
   * sep}. Oracle-verified the same way {@link #rest}'s equivalent quirk was: ordinary typed-in
   * multiple spaces never surface this (general-argument-evaluator pre-compression collapses them
   * first), but backslash-escaped spaces do -- {@code extract(a\ \ b\ \ \ c,1,3)} returns both
   * interior runs untouched, not collapsed. Don't "simplify" this back into a split-and-rejoin
   * implementation. See {@link #advanceToken}.
   */
  @MushFunction(name = "extract", minArgs = 3, maxArgs = 4)
  public static Value extract(ExecutionContext ctx, List<Value> args) {
    String list = args.get(0).asString();
    long first = args.get(1).atoi();
    long len = args.get(2).atoi();
    char sep = args.size() > 3 ? parseFillChar(args.get(3), ' ') : ' ';
    if (first < 1 || len < 1) {
      return Value.of("");
    }
    String trimmed = sep == ' ' ? list.trim() : list;
    if (trimmed.isEmpty()) {
      return Value.of("");
    }
    int offset = 0;
    for (long i = 1; i < first; i++) {
      offset = advanceToken(trimmed, offset, sep);
      if (offset < 0) {
        return Value.of("");
      }
    }
    if (offset >= trimmed.length()) {
      return Value.of("");
    }
    int startOffset = offset;
    int cur = offset;
    long remaining = len - 1;
    while (remaining > 0) {
      int next = advanceToken(trimmed, cur, sep);
      if (next < 0) {
        break;
      }
      cur = next;
      remaining--;
    }
    int endOffset;
    if (remaining == 0) {
      int sepIndex = trimmed.indexOf(sep, cur);
      endOffset = sepIndex < 0 ? trimmed.length() : sepIndex;
    } else {
      endOffset = trimmed.length();
    }
    return Value.of(trimmed.substring(startOffset, endOffset));
  }

  /**
   * Mirrors functions.c's {@code next_token} exactly, but as an index into {@code s} rather than a
   * pointer: finds {@code sep} at or after {@code pos}, and returns one past it -- skipping any
   * further consecutive {@code sep} chars from there too, but only for the space separator (like
   * {@code next_token}, and unlike {@code split_token}/{@link #splitFirst}, never writes into
   * {@code s} -- it only locates a boundary). Returns {@code -1} if {@code sep} doesn't occur at or
   * after {@code pos} (no more tokens).
   */
  private static int advanceToken(String s, int pos, char sep) {
    int idx = s.indexOf(sep, pos);
    if (idx < 0) {
      return -1;
    }
    int next = idx + 1;
    if (sep == ' ') {
      while (next < s.length() && s.charAt(next) == ' ') {
        next++;
      }
    }
    return next;
  }
}
