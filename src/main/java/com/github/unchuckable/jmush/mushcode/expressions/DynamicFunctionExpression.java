package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.EvalFlags;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunctionHandler;
import java.util.List;

/**
 * A function call whose name isn't known until evaluation -- e.g. {@code %q0(1,2)} where {@code
 * %q0} holds {@code "add"}. Real TinyMUSH resolves function names from whatever text ends up in the
 * interpreter's *output* buffer (eval.c's {@code oldp}/{@code hashfind} mechanism), so a
 * substitution can supply a function name; {@code MushcodeParser} normally resolves names
 * statically at parse time, and only builds this node when the accumulated name-candidate contains
 * a non-constant piece. The argument text is comma-split and parsed once, at construction (that
 * split is the same whichever name resolves); only name resolution -- and with it the
 * invoke-vs-literal-fallback decision -- is deferred to evaluation time.
 *
 * <p>Known divergence from the oracle: when the resolved name doesn't match a function, real
 * TinyMUSH continues scanning the parenthesized text char-by-char with a *contaminated* accumulator
 * (carrying the failed name's text forward), so a nested, otherwise-valid function call inside
 * those "arguments" still won't fire (e.g. {@code unknownfunc(add(1,2),3)} stays fully literal on a
 * real server). This class instead evaluates the arguments independently and rejoins them with
 * literal commas, so a nested valid call inside a failed dynamic name's arguments *will* fire here.
 */
public class DynamicFunctionExpression implements Expression {

  private final MushcodeParser parser;
  private final Expression nameExpression;
  private final List<Expression> arguments;

  public DynamicFunctionExpression(
      MushcodeParser parser, Expression nameExpression, String rawArguments, EvalFlags flags) {
    this.parser = parser;
    this.nameExpression = nameExpression;
    this.arguments = parser.getParameters(rawArguments, 0, rawArguments.length(), flags);
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    String name = nameExpression.evaluateExpression(context).asString();
    MushFunctionHandler function = parser.getFunction(name);
    if (function != null) {
      return function.execute(context, arguments);
    }

    StringBuilder fallback = new StringBuilder(name).append('(');
    for (int i = 0; i < arguments.size(); i++) {
      if (i > 0) {
        fallback.append(',');
      }
      fallback.append(arguments.get(i).evaluateExpression(context).asString());
    }
    return Value.of(fallback.append(')').toString());
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
