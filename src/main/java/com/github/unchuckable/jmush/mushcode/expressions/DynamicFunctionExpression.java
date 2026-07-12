package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.EvalFlags;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MandatoryMatchException;
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
      // FN_NO_EVAL (lazy) functions don't inherit EV_FMAND into their own arguments -- see
      // FunctionEntry's javadoc -- but that's only knowable once the name resolves, here, at
      // evaluation time; MushcodeParser.handleFunctionCall applies the same suppression at parse
      // time, but only for a *statically*-named call, since it knows the resolved entry already.
      EvalFlags argumentFlags = entry.lazy() ? flags.withFmand(false) : flags;
      List<Expression> resolvedArguments;
      if (entry.catenateArgs()) {
        // Localizes a MandatoryMatchException from this re-parse to just this (sole) argument,
        // matching MushcodeParser.parseArgument's parse-time equivalent -- e.g. once %q0 resolves
        // to "lcstr", a failure inside its catenated argument becomes that argument's error Value,
        // and lcstr() runs normally on it (which is why the result ends up lowercased).
        Expression catenated;
        try {
          catenated = parser.parse(rawArguments, argumentFlags);
        } catch (MandatoryMatchException e) {
          catenated = Value.of(e.getMessage());
        }
        resolvedArguments = Collections.singletonList(catenated);
      } else if (entry.lazy()) {
        // The pre-split arguments field was parsed at parse time under the *ambient* flags, before
        // this name was known to resolve to a lazy function -- e.g. a static name-resolution
        // failure inside one of switch()'s own branches would already have been converted to a
        // hard-error Value under fmand=true, which real TinyMUSH never does here. Re-parse from
        // the raw span with fmand off to get this right (oracle-verified:
        // [setq(0,switch)][%q0(1,1,badname(1),2)] literal-falls-back to "badname(1)").
        resolvedArguments =
            parser.getParameters(rawArguments, 0, rawArguments.length(), argumentFlags);
      } else {
        resolvedArguments = arguments;
      }
      return entry.handler().execute(context, resolvedArguments);
    }

    // EV_FMAND ([...] forced-eval): a failed match is a hard error, not a literal fallback --
    // thrown here since dynamic names only resolve at evaluation time. Caught either by
    // FunctionRegistry's argument evaluation (localizing to just the enclosing argument, if this
    // call is itself one -- oracle-verified [setq(0,badname)][cat(%q0(1),2)] keeps " 2" despite
    // %q0(1) failing) or, if this is the [...] block's own top-level scan, by
    // MandatoryMatchExpression.
    if (flags.isFmandEnabled()) {
      throw MandatoryMatchException.functionNotFound(name);
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
