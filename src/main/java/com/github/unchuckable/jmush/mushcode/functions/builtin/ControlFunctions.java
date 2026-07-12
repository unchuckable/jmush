package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import com.github.unchuckable.jmush.util.WildcardMatcher;
import java.util.List;

/**
 * Functions that need raw, unevaluated access to their arguments (functions.c's {@code FN_NO_EVAL})
 * -- e.g. {@code switch()} evaluates its subject once, then only the winning branch, never the
 * losing ones (oracle-verified: a losing branch's side effect, e.g. a {@code setq()}, never fires).
 * Marked {@code lazy = true} so {@link
 * com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry} registers the method as-is,
 * with no eager-evaluation wrapper -- see {@code @MushFunction}'s javadoc.
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

  private ControlFunctions() {
    // static provider class, do not instantiate
  }
}
