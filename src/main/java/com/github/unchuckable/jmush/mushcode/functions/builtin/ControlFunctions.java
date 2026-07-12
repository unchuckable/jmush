package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import com.github.unchuckable.jmush.util.WildcardMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Functions that need raw, unevaluated access to their arguments (functions.c's {@code FN_NO_EVAL})
 * -- e.g. {@code switch()} evaluates its subject once, then only the winning branch, never the
 * losing ones (oracle-verified: a losing branch's side effect, e.g. a {@code setq()}, never fires).
 * Marked {@code lazy = true} so {@link
 * com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry} registers the method as-is,
 * with no eager-evaluation wrapper -- see {@code @MushFunction}'s javadoc.
 *
 * <p>Unlike an ordinary eager function's arguments, these bodies' branches never see EV_FMAND at
 * all -- {@code MushcodeParser.handleFunctionCall} and {@code DynamicFunctionExpression} both
 * suppress it (via {@code EvalFlags.withFmand(false)}) before parsing/evaluating a {@code lazy}
 * function's arguments, since real TinyMUSH's {@code switch}/{@code ifelse}/{@code iter} don't
 * propagate it into their own branches either -- oracle-verified: {@code
 * [switch(1,1,badname(1),2)]} literal-falls-back to {@code "badname(1)"} even directly inside
 * {@code [...]}, it never hard-errors. See {@code FunctionEntry}'s javadoc.
 */
public class ControlFunctions {

  /**
   * {@code switch(subject, pattern1, branch1[, pattern2, branch2, ...][, default])}. Evaluates
   * {@code subject} once, then each {@code pattern} in turn (patterns can contain code) until one
   * wildcard-matches, at which point only that pattern's branch is evaluated and returned -- later
   * patterns/branches are never touched. Falls through to a trailing unpaired argument (the
   * default) if present. Oracle-verified: fewer than 2 arguments (including zero) returns "", not
   * an error.
   */
  @MushFunction(name = "switch", lazy = true)
  public static Value switchFunction(ExecutionContext context, List<Expression> args) {
    if (args == null || args.isEmpty()) {
      return Value.of("");
    }
    String subject = args.get(0).evaluateExpression(context).asString();
    int i = 1;
    for (; i + 1 < args.size(); i += 2) {
      String pattern = args.get(i).evaluateExpression(context).asString();
      if (WildcardMatcher.matches(pattern, subject)) {
        return args.get(i + 1).evaluateExpression(context);
      }
    }
    if (i < args.size()) {
      return args.get(i).evaluateExpression(context);
    }
    return Value.of("");
  }

  /**
   * {@code ifelse(condition, iftrue, iffalse)}. Evaluates {@code condition} once, then only the
   * winning branch (oracle-verified via {@code setr()} side effects: the losing branch never
   * fires). Truthiness is {@link Value#isTruthy()} (functions.c's {@code xlate()}), not the {@code
   * atoi}-truncating check {@code not()}/{@code and()}/{@code or()} use -- e.g. {@code
   * ifelse(abc,yes,no)} is {@code "yes"}, unlike {@code not(abc)} treating {@code "abc"} as falsy.
   */
  @MushFunction(name = "ifelse", lazy = true, minArgs = 3, maxArgs = 3)
  public static Value ifelseFunction(ExecutionContext context, List<Expression> args) {
    boolean condition = args.get(0).evaluateExpression(context).isTruthy();
    return args.get(condition ? 1 : 2).evaluateExpression(context);
  }

  /**
   * {@code iter(list, body[, sep[, osep]])}. Evaluates {@code list} once, splits it on {@code sep}
   * (default space), then evaluates {@code body} once per element with {@code ##}/{@code #@}/{@code
   * #!} (see {@link com.github.unchuckable.jmush.mushcode.expressions.LoopTokenExpression} and
   * friends) resolving to that element/its 1-based index/the enclosing nesting depth -- joining
   * results with {@code osep} (default space). {@code sep}/{@code osep} are themselves evaluated
   * and must be exactly one character (see {@link StringFunctions#parseFillChar}).
   *
   * <p>The join is oracle-verified to follow a simple "no separator until something's been written"
   * rule -- {@code osep} is prepended before every result *after* the first one actually appended,
   * even if that result is itself empty (so a trailing empty result still gets a trailing
   * separator, but a leading one doesn't get a leading separator): {@code iter(a b
   * c,switch(##,b,,##))} evaluates to {@code "a"}, then {@code osep}, then the empty middle result,
   * then {@code osep} again, then {@code "c"} -- i.e. {@code osep} appears twice in a row around
   * the empty result, not once.
   *
   * <p>List-splitting is oracle-verified to differ by separator: the default space separator
   * collapses runs and trims leading/trailing occurrences (matching eval.c's general {@code
   * at_space} space-compression), but a custom single-char separator does a plain, non-collapsing
   * split -- consecutive/leading/trailing occurrences produce empty elements (oracle-verified:
   * {@code iter(a--b,##,-)} splits into three elements {@code "a"}/{@code ""}/{@code "b"}, not
   * two). An empty (post-trim, for the default separator) list produces zero iterations, not one
   * empty-string element (oracle-verified: {@code iter(,X##)} is {@code ""}, not {@code "X"}).
   */
  @MushFunction(name = "iter", lazy = true, minArgs = 2, maxArgs = 4)
  public static Value iterFunction(ExecutionContext context, List<Expression> args) {
    String list = args.get(0).evaluateExpression(context).asString();
    char sep =
        args.size() > 2
            ? StringFunctions.parseFillChar(args.get(2).evaluateExpression(context), ' ')
            : ' ';
    char osep =
        args.size() > 3
            ? StringFunctions.parseFillChar(args.get(3).evaluateExpression(context), ' ')
            : ' ';

    String[] elements = sep == ' ' ? splitCompressed(list) : splitLiteral(list, sep);

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < elements.length; i++) {
      context.pushLoop(Value.of(elements[i]), i + 1);
      try {
        String elementResult = args.get(1).evaluateExpression(context).asString();
        if (result.length() > 0) {
          result.append(osep);
        }
        result.append(elementResult);
      } finally {
        context.popLoop();
      }
    }
    return Value.of(result.toString());
  }

  /**
   * Splits on runs of {@code ' '}, trimming leading/trailing spaces first (no regex). Package-
   * visible so {@code StringFunctions}'s {@code words()}/{@code first()} can reuse it (same
   * underlying C {@code trim_space_sep}/{@code split_token}/{@code next_token} semantics).
   */
  static String[] splitCompressed(String list) {
    String trimmed = list.trim();
    int n = trimmed.length();
    if (n == 0) {
      return new String[0];
    }
    List<String> parts = new ArrayList<>();
    int i = 0;
    while (i < n) {
      int start = i;
      while (i < n && trimmed.charAt(i) != ' ') {
        i++;
      }
      parts.add(trimmed.substring(start, i));
      while (i < n && trimmed.charAt(i) == ' ') {
        i++;
      }
    }
    return parts.toArray(new String[0]);
  }

  /**
   * Plain, non-collapsing split on a single literal char (no regex). Package-visible so {@code
   * StringFunctions}'s {@code words()}/{@code first()} can reuse it.
   */
  static String[] splitLiteral(String list, char sep) {
    if (list.isEmpty()) {
      return new String[0];
    }
    List<String> parts = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < list.length(); i++) {
      if (list.charAt(i) == sep) {
        parts.add(list.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(list.substring(start));
    return parts.toArray(new String[0]);
  }

  private ControlFunctions() {
    // static provider class, do not instantiate
  }
}
