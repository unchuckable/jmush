package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.EvalFlags;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry;
import java.util.Collections;
import java.util.List;

/**
 * A function call whose name isn't known until evaluation -- e.g. {@code %q0(1,2)} where {@code
 * %q0} holds {@code "add"}. Real TinyMUSH resolves function names from whatever text ends up in the
 * interpreter's *output* buffer (eval.c's {@code oldp}/{@code hashfind} mechanism), so a
 * substitution can supply a function name; {@code MushcodeParser} normally resolves names
 * statically at parse time, and only builds this node when the accumulated name-candidate contains
 * a non-constant piece. Arguments are eagerly comma-split by the caller ({@code
 * MushcodeParser.handleFunctionCall}, the same {@code getParameters} call the static path uses) and
 * handed over pre-parsed as {@link #arguments} -- correct for the common case, since most resolved
 * names won't be {@code catenateArgs} functions (see {@code @MushFunction#catenateArgs}'s javadoc).
 * For the rare case where the resolved name *does* catenate (oracle-verified: {@code
 * [setq(0,lcstr)]%q0(HELLO,add(1,2))} resolves identically to calling {@code lcstr(...)} directly
 * -- there's no static/dynamic split in the real mechanism, since C resolves the name from
 * accumulated text regardless of where that text came from), the raw, not-yet-comma-split argument
 * span ({@link #rawArguments}) is also retained and re-parsed as a single expression on demand via
 * {@link #parser}. This can't be memoized across evaluations of the same parsed tree, since the
 * resolved name -- and thus the split decision -- can legitimately differ between separate
 * evaluations (e.g. inside a loop where the register holds a different function name each
 * iteration); it's cheap enough to just re-parse each time it's actually needed, and the common
 * (non-catenating) path pays nothing extra either way.
 *
 * <p>Known divergence from the oracle: when the resolved name doesn't match a function, real
 * TinyMUSH continues scanning the parenthesized text char-by-char with a *contaminated* accumulator
 * (carrying the failed name's text forward), so a nested, otherwise-valid function call inside
 * those "arguments" still won't fire (e.g. {@code unknownfunc(add(1,2),3)} stays fully literal on a
 * real server). This class instead evaluates the arguments independently and rejoins them with
 * literal commas, so a nested valid call inside a failed dynamic name's arguments *will* fire here.
 */
public class DynamicFunctionExpression implements Expression {

  private final FunctionRegistry functionRegistry;
  private final Expression nameExpression;
  private final List<Expression> arguments;
  private final String rawArguments;
  private final EvalFlags flags;
  private final MushcodeParser parser;

  public DynamicFunctionExpression(
      FunctionRegistry functionRegistry,
      Expression nameExpression,
      List<Expression> arguments,
      String rawArguments,
      EvalFlags flags,
      MushcodeParser parser) {
    this.functionRegistry = functionRegistry;
    this.nameExpression = nameExpression;
    this.arguments = arguments;
    this.rawArguments = rawArguments;
    this.flags = flags;
    this.parser = parser;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    String name = nameExpression.evaluateExpression(context).asString();
    FunctionRegistry.FunctionEntry entry = functionRegistry.lookup(name);
    if (entry != null) {
      List<Expression> resolvedArguments =
          entry.catenateArgs()
              ? Collections.singletonList(parser.parse(rawArguments, flags))
              : arguments;
      return entry.handler().execute(context, resolvedArguments);
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
