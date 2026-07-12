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
  // String.length() is equivalent for now. catenateArgs = true: STRLEN has nargs = -1 in
  // functions.c's table, so its argument is never comma-split (oracle-verified strlen(a,b) is 3,
  // the length of literal "a,b", not an arg-count error) -- see @MushFunction#catenateArgs.
  @MushFunction(name = "strlen", catenateArgs = true)
  public static Value strlen(ExecutionContext ctx, Value a) {
    return Value.ofInt(a.asString().length());
  }

  /**
   * {@code lcstr(str)}. Lowercases the whole string. {@code catenateArgs = true} (see {@link
   * #strlen}'s note and {@code @MushFunction#catenateArgs}): {@code str} is never comma-split, so
   * {@code lcstr(a,add(1,2))} is {@code "a,add(1,2)"} (the literal, unevaluated text -- {@code
   * add(1,2)}'s own {@code (} never gets a function-check chance, since the one-shot check was
   * already burned on the failed {@code "a,add"} name candidate), not {@code "a,3"} -- while {@code
   * lcstr(add(1,2))} (no comma) evaluates normally to {@code "3"}.
   */
  @MushFunction(name = "lcstr", catenateArgs = true)
  public static Value lcstr(ExecutionContext ctx, Value str) {
    return Value.of(str.asString().toLowerCase());
  }

  /** {@code ucstr(str)} -- like {@link #lcstr}, but uppercases. */
  @MushFunction(name = "ucstr", catenateArgs = true)
  public static Value ucstr(ExecutionContext ctx, Value str) {
    return Value.of(str.asString().toUpperCase());
  }

  /**
   * {@code capstr(str)} -- like {@link #lcstr}/{@link #ucstr} (same {@code catenateArgs}
   * semantics), but leaves {@code str} otherwise unchanged and uppercases only its first character
   * (a no-op via {@link Character#toUpperCase} if that character isn't a cased letter). Empty
   * {@code str} (including zero args, since {@code catenateArgs} parses an empty span to {@code
   * Value.of("")}) returns {@code ""}.
   */
  @MushFunction(name = "capstr", catenateArgs = true)
  public static Value capstr(ExecutionContext ctx, Value str) {
    String s = str.asString();
    if (s.isEmpty()) {
      return Value.of("");
    }
    return Value.of(Character.toUpperCase(s.charAt(0)) + s.substring(1));
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

  /**
   * {@code before(str[, sep])} / {@code after(str[, sep])}. {@code sep} is an arbitrary-length
   * literal substring (not a single char, not a wildcard), defaulting to {@code " "} if omitted or
   * empty. Zero args returns {@code ""} (oracle-verified, same {@code nfargs == 0} pattern as
   * {@link #words}/{@link #first}/{@link #rest}); see {@link #checkSubstringArity} for the 1-2 arg
   * range and its exact error wording. <b>Only</b> if {@code sep} ends up exactly a single space
   * does {@code str} get leading/trailing-trimmed first ({@code trim_space_sep(str,' ')}, i.e.
   * plain {@link String#trim}) -- oracle-verified {@code before(hello world,wor)} is {@code "hello
   * "} (untrimmed, {@code sep} isn't a space) vs {@code before( a b c )} is {@code "a"} (trimmed
   * first, default {@code sep} is a space). The search itself is a plain literal substring find --
   * {@code String.indexOf(String)} is exactly equivalent to functions.c's char-by-char {@code
   * index()} + {@code strncmp} scan, no need to hand-roll it. {@code before()} returns everything
   * up to (not including) the match, or the whole (possibly-trimmed) string if there's no match;
   * {@code after()} returns everything after the match, or {@code ""} if there's no match.
   */
  @MushFunction(name = "before")
  public static Value before(ExecutionContext ctx, List<Value> args) {
    checkSubstringArity("BEFORE", args);
    if (args.isEmpty()) {
      return Value.of("");
    }
    String bp = args.get(0).asString();
    String mp = args.size() > 1 ? args.get(1).asString() : "";
    if (mp.isEmpty()) {
      mp = " ";
    }
    if (mp.equals(" ")) {
      bp = bp.trim();
    }
    int idx = bp.indexOf(mp);
    return Value.of(idx < 0 ? bp : bp.substring(0, idx));
  }

  /** See {@link #before} -- same rules, but returns everything after the match. */
  @MushFunction(name = "after")
  public static Value after(ExecutionContext ctx, List<Value> args) {
    checkSubstringArity("AFTER", args);
    if (args.isEmpty()) {
      return Value.of("");
    }
    String bp = args.get(0).asString();
    String mp = args.size() > 1 ? args.get(1).asString() : "";
    if (mp.isEmpty()) {
      mp = " ";
    }
    if (mp.equals(" ")) {
      bp = bp.trim();
    }
    int idx = bp.indexOf(mp);
    return Value.of(idx < 0 ? "" : bp.substring(idx + mp.length()));
  }

  /**
   * {@code before}/{@code after} share the same {@code words}/{@code first}/{@code rest}-style
   * arity shape {@link com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry}'s generic
   * {@code minArgs}/{@code maxArgs} check can't express (zero args is a valid sentinel, one or two
   * is normal, anything higher is {@code "1 OR 2 ARGUMENTS"}, oracle-verified via {@code
   * before(a,b,c)}) -- see {@link #checkListSepArity}'s javadoc for the full explanation, which
   * applies equally here.
   */
  private static void checkSubstringArity(String name, List<Value> args) {
    if (args.size() > 2) {
      throw new MushValueException("#-1 FUNCTION (" + name + ") EXPECTS 1 OR 2 ARGUMENTS");
    }
  }

  /**
   * {@code index(list, sep, first, len)}. Always exactly 4 args (oracle-verified {@code "#-1
   * FUNCTION (INDEX) EXPECTS 4 ARGUMENTS"} for both too few and too many -- a clean {@code minArgs
   * = maxArgs = 4} fit, unlike {@code before}/{@code after}). {@code sep}'s <b>first character</b>
   * is used as-is with no length validation -- unlike every other separator-taking function here, a
   * multi-char {@code sep} is <b>not</b> an error, it silently uses just the first character
   * (oracle-verified {@code index(a|b|c,xy,1,1)}); an empty {@code sep} silently defaults to space.
   * {@code first}/{@code len} are {@code atoi()}-lenient, like {@link #extract}; {@code first < 1},
   * {@code len < 1}, or an empty {@code list} all return {@code ""}.
   *
   * <p>Positions into the <b>original, untrimmed</b> {@code list} -- there's no {@code
   * trim_space_sep} step at all here, unlike every other list-splitting function so far. Walking to
   * the start of element {@code first} uses <b>raw</b> {@code indexOf} occurrences of the separator
   * char -- no compression/skip-ahead over consecutive occurrences, even when the separator is a
   * space (a different, simpler walk than {@link #advanceToken}'s). From that offset, though,
   * literal space characters are <b>unconditionally</b> skipped, regardless of what {@code sep} is
   * -- oracle-verified {@code index(a|\ \ \ b,|,2,1)} is {@code "b"}, not the three raw spaces plus
   * {@code "b"}. Walking forward {@code len} more separator occurrences to find the end boundary is
   * the same raw walk; if all {@code len} are found, trailing spaces immediately before that cut
   * point are also stripped (oracle-verified {@code index(a\ \ \ |b,|,1,1)} is {@code "a"}) --
   * <b>but</b> if the walk runs off the end before finding {@code len} occurrences, the result is
   * the raw, untrimmed remainder to the end of the string (asymmetric with the success case --
   * confirmed by the C source's control flow, which only reaches the trailing-strip code inside the
   * "found it" branch; don't "fix" this asymmetry, it's real).
   */
  @MushFunction(name = "index", minArgs = 4, maxArgs = 4)
  public static Value index(ExecutionContext ctx, List<Value> args) {
    String list = args.get(0).asString();
    String sepArg = args.get(1).asString();
    char sep = sepArg.isEmpty() ? ' ' : sepArg.charAt(0);
    long first = args.get(2).atoi();
    long len = args.get(3).atoi();
    if (first < 1 || len < 1 || list.isEmpty()) {
      return Value.of("");
    }

    int pos = 0;
    long skip = first - 1;
    while (skip > 0) {
      int idx = list.indexOf(sep, pos);
      if (idx < 0) {
        return Value.of("");
      }
      pos = idx + 1;
      skip--;
    }
    while (pos < list.length() && list.charAt(pos) == ' ') {
      pos++;
    }
    if (pos >= list.length()) {
      return Value.of("");
    }

    int startOffset = pos;
    int p = startOffset;
    long remaining = len;
    int cutIndex = -1;
    while (remaining > 0) {
      int idx = list.indexOf(sep, p);
      if (idx < 0) {
        break;
      }
      remaining--;
      if (remaining == 0) {
        cutIndex = idx;
      } else {
        p = idx + 1;
      }
    }
    if (remaining == 0) {
      int end = cutIndex;
      while (end > startOffset && list.charAt(end - 1) == ' ') {
        end--;
      }
      return Value.of(list.substring(startOffset, end));
    }
    return Value.of(list.substring(startOffset));
  }

  /**
   * {@code revwords(list[,sep])}. Reverses the element order of {@code list} (split on {@code sep},
   * default space, see {@link #parseFillChar}), keeping each element's own characters intact --
   * functions.c reverses the whole string then re-reverses each {@code split_token}-cut word in
   * place, which nets out to exactly that. Same arity shape as {@link #words}/{@link #first}/{@link
   * #rest}/{@link #before}/{@link #after} (see {@link #checkListSepArity}): zero args is {@code
   * ""}, 1-2 is normal, 3+ errors {@code "1 OR 2 ARGUMENTS"}. Plain join with {@code sep} -- no
   * {@code iter()}-style "separator only after something's been written" nuance -- oracle-verified
   * {@code revwords(a--b,-)} is {@code "b--a"}, i.e. {@code ["a","","b"]} reversed to {@code
   * ["b","","a"]} rejoined with {@code -}.
   */
  @MushFunction(name = "revwords")
  public static Value revwords(ExecutionContext ctx, List<Value> args) {
    checkListSepArity("REVWORDS", args);
    if (args.isEmpty()) {
      return Value.of("");
    }
    String list = args.get(0).asString();
    char sep = args.size() > 1 ? parseFillChar(args.get(1), ' ') : ' ';
    String[] elements =
        sep == ' '
            ? ControlFunctions.splitCompressed(list)
            : ControlFunctions.splitLiteral(list, sep);
    StringBuilder result = new StringBuilder();
    for (int i = elements.length - 1; i >= 0; i--) {
      if (result.length() > 0) {
        result.append(sep);
      }
      result.append(elements[i]);
    }
    return Value.of(result.toString());
  }

  /**
   * {@code repeat(str, n)}. {@code n} is {@code atoi()}-truncated. {@code n < 1}, or an empty
   * {@code str}, silently returns {@code ""}; {@code n == 1} returns {@code str} unchanged.
   * Otherwise concatenates {@code n} copies of {@code str} -- but functions.c bounds-checks {@code
   * str}'s length, {@code n} itself, and their product against {@code LBUF_SIZE - 1} (the same
   * {@link #LBUF_SIZE_MINUS_ONE} {@link #mid} uses) first, erroring {@link
   * MushErrors#STRING_TOO_LONG} if any is exceeded (oracle-verified {@code repeat(a,999999)}) --
   * checked in that order so the multiplication only runs once both operands are individually
   * known-small, no overflow handling needed.
   */
  @MushFunction(name = "repeat")
  public static Value repeat(ExecutionContext ctx, Value str, Value n) {
    long times = n.atoi();
    String s = str.asString();
    if (times < 1 || s.isEmpty()) {
      return Value.of("");
    }
    if (times == 1) {
      return Value.of(s);
    }
    long len = s.length();
    if (len > LBUF_SIZE_MINUS_ONE
        || times > LBUF_SIZE_MINUS_ONE
        || len * times > LBUF_SIZE_MINUS_ONE) {
      return Value.of(MushErrors.STRING_TOO_LONG);
    }
    StringBuilder result = new StringBuilder();
    for (long i = 0; i < times; i++) {
      result.append(s);
    }
    return Value.of(result.toString());
  }

  /**
   * {@code space(n)}. Empty/missing {@code n} returns a single space. Otherwise {@code n} is {@code
   * atoi()}-truncated, except for one asymmetric wrinkle matching functions.c's {@code fun_space}
   * exactly: if the lenient parse gives {@code < 1}, the result defaults to a single space
   * <b>unless</b> {@code n}'s raw text is a strict, cleanly-formatted integer literal (see {@link
   * #isStrictInteger}) <b>and</b> that value is exactly {@code 0} -- in which case the result is
   * {@code ""} (zero spaces), not one. So {@code space(0)} is {@code ""}, but {@code
   * space(-5)}/{@code space(abc)}/{@code space(0.0)} (not strictly-formatted, or non-zero) are all
   * a single space -- all oracle-verified.
   */
  @MushFunction(name = "space")
  public static Value space(ExecutionContext ctx, Value n) {
    String s = n.asString();
    long num = s.isEmpty() ? 1 : n.atoi();
    if (num < 1 && !(isStrictInteger(s) && num == 0)) {
      num = 1;
    }
    StringBuilder result = new StringBuilder();
    for (long i = 0; i < num; i++) {
      result.append(' ');
    }
    return Value.of(result.toString());
  }

  /**
   * Mirrors functions.c's {@code is_integer()} exactly: optional leading whitespace, an optional
   * single {@code +}/{@code -} (must be followed by at least one more char), at least one digit,
   * only digits after that, optional trailing whitespace, and nothing else. Deliberately distinct
   * from {@link Value#atoi} (lenient truncation) and {@link Value#asInt} (strict but throwing) --
   * this is a yes/no check on the raw text's format, needed only by {@link #space}.
   */
  private static boolean isStrictInteger(String s) {
    int i = 0;
    int n = s.length();
    while (i < n && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
      i++;
      if (i >= n) {
        return false;
      }
    }
    if (i >= n || !Character.isDigit(s.charAt(i))) {
      return false;
    }
    while (i < n && Character.isDigit(s.charAt(i))) {
      i++;
    }
    while (i < n && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i == n;
  }

  /**
   * {@code center(str, width[, sep])}. {@code sep} defaults to space (see {@link #parseFillChar},
   * strict one-char validation -- oracle-verified {@code center(hi,6,xy)} errors {@link
   * MushErrors#SEPARATOR_MUST_BE_ONE_CHARACTER}, unlike {@code index()}'s laxer handling). {@code
   * width} is {@code atoi()}-truncated. If {@code str}'s length is already {@code >= width} (covers
   * {@code width <= 0} automatically), returns {@code str} unchanged -- never an error, never
   * truncates (oracle-verified {@code center(hi,0)}/{@code center(hi,-3)} both {@code "hi"}).
   * Otherwise pads both sides with {@code sep}: {@code leadChars = width/2 - len/2} (plain integer
   * division both terms -- functions.c's literal {@code + .5} float promotion is a no-op once
   * truncated back to an int, so it's dropped here), {@code trailChars = width - leadChars - len}.
   */
  @MushFunction(name = "center", minArgs = 2, maxArgs = 3)
  public static Value center(ExecutionContext ctx, List<Value> args) {
    String s = args.get(0).asString();
    long width = args.get(1).atoi();
    char sep = args.size() > 2 ? parseFillChar(args.get(2), ' ') : ' ';
    long len = s.length();
    if (len >= width) {
      return Value.of(s);
    }
    long leadChars = width / 2 - len / 2;
    long trailChars = width - leadChars - len;
    StringBuilder result = new StringBuilder();
    for (long i = 0; i < leadChars; i++) {
      result.append(sep);
    }
    result.append(s);
    for (long i = 0; i < trailChars; i++) {
      result.append(sep);
    }
    return Value.of(result.toString());
  }
}
