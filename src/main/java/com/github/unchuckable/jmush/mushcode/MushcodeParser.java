package com.github.unchuckable.jmush.mushcode;

import com.github.unchuckable.jmush.mushcode.expressions.AttributeExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ContextExpressions;
import com.github.unchuckable.jmush.mushcode.expressions.DynamicFunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopDepthExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopIndexExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopTokenExpression;
import com.github.unchuckable.jmush.mushcode.expressions.MandatoryMatchExpression;
import com.github.unchuckable.jmush.mushcode.expressions.RegisterExpression;
import com.github.unchuckable.jmush.mushcode.expressions.UppercaseFirstExpression;
import com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry;
import com.github.unchuckable.jmush.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MushcodeParser {

  private final FunctionRegistry functionRegistry;

  public MushcodeParser(FunctionRegistry functionRegistry) {
    this.functionRegistry = functionRegistry;
  }

  public Expression parse(String string) {
    return parse(string, EvalFlags.DEFAULT);
  }

  public Expression parse(String string, EvalFlags flags) {
    return new ParseRun(string, flags).run();
  }

  public List<Expression> getParameters(
      String string, int startIndex, int endIndex, EvalFlags flags) {
    List<Expression> parameters = new ArrayList<>();
    int currentIndex = startIndex;
    int nextIndex = StringUtils.findIndexOf(',', string, currentIndex, endIndex);
    while (nextIndex != -1) {
      parameters.add(parseArgument(string, currentIndex, nextIndex, flags));
      currentIndex = nextIndex + 1;
      nextIndex = StringUtils.findIndexOf(',', string, currentIndex, endIndex);
    }
    parameters.add(parseArgument(string, currentIndex, endIndex, flags));
    return parameters;
  }

  /**
   * Parses one function-call argument (or the catenated single-argument span), localizing a {@link
   * MandatoryMatchException} (an EV_FMAND name-resolution failure inside {@code [...]}) to just
   * this argument's {@link Value} instead of letting it unwind past sibling arguments and the whole
   * containing {@code [...]} block -- oracle-verified: {@code [cat(add(1,2),badname(3))]} evaluates
   * to {@code "3 #-1 FUNCTION (BADNAME) NOT FOUND"} (add(1,2)'s result and cat()'s join survive),
   * not a whole-block failure. This is the parse-time half of that scoping; {@link
   * com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry}'s argument evaluation is the
   * eval-time half (for dynamically-resolved names, which can only fail at evaluation time).
   */
  private Expression parseArgument(String string, int startIndex, int endIndex, EvalFlags flags) {
    try {
      return parse(string.substring(startIndex, endIndex), flags);
    } catch (MandatoryMatchException e) {
      return Value.of(e.getMessage());
    }
  }

  /**
   * The scanner state for one {@link #parse(String, EvalFlags)} invocation -- the equivalent of one
   * {@code exec()} stack frame in {@code eval.c}. Nested constructs ({@code [...]}/{@code {...}}
   * groups, function arguments) each get their own {@code ParseRun} via a recursive {@code parse()}
   * call, which is what scopes the per-scan flags below ({@code nameBoundary}, {@code
   * functionCheckAvailable}) exactly like C locals in a recursive {@code exec()}.
   */
  private class ParseRun {

    private final String string;
    private final EvalFlags flags;

    /** Completed sibling expressions of the tree level being built. */
    private final List<Expression> finishedExpressions = new ArrayList<>();

    /** The pending run of literal characters, flushed into {@link #finishedExpressions}. */
    private final StringBuilder builder = new StringBuilder();

    /**
     * Marks where the current function-name candidate starts within {@link #finishedExpressions} --
     * mirrors eval.c's "oldp": it only advances once a function call actually dispatches, so
     * literal text, substitutions, and forced-eval/{}-group output accumulated since the last
     * dispatched call are all still eligible to be re-read as the *next* function name (see {@link
     * #handleFunctionCall()}).
     */
    private int nameBoundary = 0;

    /**
     * EV_FCHECK is a one-shot flag per eval.c invocation: the *first* unescaped '(' encountered
     * (whose matching ')' is found) consumes it unconditionally -- whether that '(' turns out to
     * name a real function or not -- and every subsequent '(' at this scan level is permanently
     * literal until a fresh nested scan (a [...] block, a {...} block, or a function argument, each
     * of which is its own ParseRun) re-enables it. Oracle-verified: "add(1,2)add(3,4)" ->
     * "3add(3,4)", not "37" -- see eval.c:908 (no match found), :1012/:1086 (dispatch completed,
     * success or error), all of which do `eval &= ~EV_FCHECK` before falling through to plain
     * literal-character handling.
     */
    private boolean functionCheckAvailable;

    /**
     * eval.c initializes at_space = 1, so a leading space is treated as an immediate repeat and
     * fully suppressed (not just compressed to one) -- see eval.c:444.
     */
    private boolean lastWasSpace = true;

    /**
     * Tracks specifically "the last char appended was a backslash-escaped space" -- separate from
     * {@code lastWasSpace}, which must stay {@code false} for an escaped space so interior/leading
     * compression keeps treating it as non-compressible (oracle-verified, e.g. {@code rest(a\ \ b\
     * \ \ c)}). Needed only so {@link #finish} can replicate eval.c's {@code parse_to_cleanup}
     * trailing-deletion quirk precisely: that check runs on raw, not-yet-%- substituted source
     * text, so it sees a literal space for a resolved {@code \ } escape but *not* for a {@code %b}
     * substitution (also a literal, non-compressible space, but one that doesn't exist in the
     * source text at that point) -- oracle-verified {@code trim(%b%bhello%b%b,l)} keeps both
     * trailing {@code %b} spaces, while {@code cat(a\ ,b)} loses its one escaped space. Reset to
     * {@code false} by every branch except {@link #handleEscape}, which sets it based on whether
     * the escaped char was itself a space.
     */
    private boolean lastWasEscapedSpace = false;

    private int index = 0;

    private ParseRun(String string, EvalFlags flags) {
      this.string = string;
      this.flags = flags;
      this.functionCheckAvailable = flags.isFunctionCheckEnabled();
    }

    private Expression run() {
      while (index < string.length()) {
        char thisChar = string.charAt(index);

        if (thisChar == ' ') {
          lastWasEscapedSpace = false;
          if (flags.isSpaceCompressionEnabled() && lastWasSpace) {
            index++;
            continue;
          }
          builder.append(' ');
          lastWasSpace = true;
          index++;
          continue;
        }
        lastWasSpace = false;
        lastWasEscapedSpace = false;

        switch (thisChar) {
          case '\\':
            handleEscape();
            break;
          case '{':
            handleLiteralGroup();
            break;
          case '[':
            handleForcedEval();
            break;
          case '(':
            handleFunctionCall();
            break;
          case '%':
            handleSubstitution();
            break;
          case '#':
            handleLoopToken();
            break;
          default:
            builder.append(thisChar);
        }
        index++;
      }
      return finish();
    }

    /** The backslash is dropped; the char after it is copied verbatim. */
    private void handleEscape() {
      index++;
      if (index < string.length()) {
        char escaped = string.charAt(index);
        builder.append(escaped);
        lastWasEscapedSpace = escaped == ' ';
      }
    }

    /**
     * Literal grouping: braces are kept in the output (unless EV_STRIP), contents still have
     * %-substitutions evaluated but not function calls (eval.c:552-582).
     */
    private void handleLiteralGroup() {
      int endIndex = StringUtils.findIndexOf('}', string, index + 1);
      if (endIndex == -1) {
        builder.append('{');
        return;
      }
      if (!flags.isStripEnabled()) {
        builder.append('{');
      }
      flushBuilder();
      EvalFlags innerFlags = flags.withFunctionCheck(false).withStrip(false);
      finishedExpressions.add(parse(string.substring(index + 1, endIndex), innerFlags));
      if (!flags.isStripEnabled()) {
        builder.append('}');
      }
      index = endIndex;
    }

    /**
     * Forced evaluation: function checking is always forced on inside [...], regardless of the
     * ambient flags (eval.c:527-551, "eval | EV_FCHECK | EV_FMAND").
     */
    private void handleForcedEval() {
      int endIndex = StringUtils.findIndexOf(']', string, index + 1);
      if (endIndex == -1) {
        builder.append('[');
        return;
      }
      flushBuilder();
      EvalFlags innerFlags = flags.withFunctionCheck(true).withFmand(true);
      Expression inner;
      try {
        inner = parse(string.substring(index + 1, endIndex), innerFlags);
      } catch (MandatoryMatchException e) {
        // A statically-named call failed to resolve -- resolved eagerly here, at parse time,
        // discarding the rest of this [...] block's text (matches eval.c's alldone = 1).
        inner = Value.of(e.getMessage());
      }
      // Guards against a *dynamically*-named call inside failing later, at evaluation time --
      // see MandatoryMatchExpression's javadoc.
      finishedExpressions.add(new MandatoryMatchExpression(inner));
      index = endIndex;
    }

    private void handleFunctionCall() {
      if (!functionCheckAvailable) {
        builder.append('(');
        return;
      }
      int endIndex = StringUtils.findIndexOf(')', string, index + 1);
      if (endIndex == -1) {
        builder.append('(');
        return;
      }

      // The function-name candidate is everything accumulated since the last dispatched
      // call: pending finishedExpressions entries plus the current literal run.
      List<Expression> pendingName =
          new ArrayList<>(finishedExpressions.subList(nameBoundary, finishedExpressions.size()));
      if (builder.length() > 0) {
        pendingName.add(Value.of(builder.toString()));
      }
      boolean nameIsConstant = true;
      for (Expression piece : pendingName) {
        if (!piece.isConstant()) {
          nameIsConstant = false;
          break;
        }
      }

      if (nameIsConstant) {
        // Common/fast path: fold the (always context-independent, per isConstant()'s
        // contract) pieces to a literal string and resolve the function right now.
        StringBuilder nameBuilder = new StringBuilder();
        for (Expression piece : pendingName) {
          nameBuilder.append(piece.evaluateExpression(null).asString());
        }
        String name = nameBuilder.toString();
        FunctionRegistry.FunctionEntry entry = functionRegistry.lookup(name);
        if (entry == null) {
          // EV_FMAND ([...] forced-eval): a failed match is a hard error, not a literal
          // fallback -- thrown here since static names resolve at parse time. Caught either by
          // parseArgument (localizing to just the enclosing argument, if this call is inside
          // one -- oracle-verified [cat(add(1,2),badname(3))] keeps "3 " despite badname(3)
          // failing) or, if this is the [...] block's own top-level scan, by handleForcedEval.
          if (flags.isFmandEnabled()) {
            throw MandatoryMatchException.functionNotFound(name);
          }
          builder.append('(');
          functionCheckAvailable = false;
          return;
        }
        // FN_NO_EVAL (lazy, e.g. switch()/ifelse()/iter()) functions don't inherit EV_FMAND into
        // their own arguments -- oracle-verified [switch(1,1,badname(1),2)] literal-falls-back to
        // "badname(1)" even directly inside [...], it never hard-errors -- unlike an ordinary eager
        // function's arguments (see MandatoryMatchException's javadoc). These functions decide for
        // themselves, per call, whether/when to evaluate each argument, so there's no single point
        // here to *localize* a failure the way parseArgument does for eager functions -- fmand must
        // just be off for them entirely.
        EvalFlags argumentFlags = entry.lazy() ? flags.withFmand(false) : flags;
        // catenateArgs functions (functions.c's negative-nargs, e.g. lcstr()/strlen()) don't
        // comma-split their sole argument at all -- the whole span is one expression, commas
        // included as literal text. See @MushFunction#catenateArgs's javadoc.
        List<Expression> parameters =
            entry.catenateArgs()
                ? Collections.singletonList(
                    parseArgument(string, index + 1, endIndex, argumentFlags))
                : getParameters(string, index + 1, endIndex, argumentFlags);
        builder.setLength(0);
        finishedExpressions.add(new FunctionExpression(entry.handler(), parameters));
      } else {
        // The name depends on a substitution/function result -- defer name resolution (and the
        // invoke-vs-literal-fallback decision) to evaluation time (see DynamicFunctionExpression).
        // Arguments are eagerly comma-split here too (the common case -- most resolved names
        // won't catenate), but the raw span/flags are also retained so DynamicFunctionExpression
        // can re-parse as one un-split expression in the rare case the resolved name does.
        while (finishedExpressions.size() > nameBoundary) {
          finishedExpressions.remove(finishedExpressions.size() - 1);
        }
        builder.setLength(0);
        Expression nameExpression =
            pendingName.size() == 1 ? pendingName.get(0) : new ConcatExpression(pendingName);
        List<Expression> arguments = getParameters(string, index + 1, endIndex, flags);
        String rawArguments = string.substring(index + 1, endIndex);
        finishedExpressions.add(
            new DynamicFunctionExpression(
                functionRegistry,
                nameExpression,
                arguments,
                rawArguments,
                flags,
                MushcodeParser.this));
      }
      index = endIndex;
      nameBoundary = finishedExpressions.size();
      functionCheckAvailable = false;
    }

    private void handleSubstitution() {
      index++;
      if (index == string.length()) {
        return;
      }
      char nextChar = string.charAt(index);
      boolean upper = Character.isUpperCase(nextChar);
      Expression substitution = null;
      switch (Character.toLowerCase(nextChar)) {
        case '%':
          builder.append('%');
          break;
        case 'r':
          builder.append("\r\n");
          break;
        case 'b':
          builder.append(' ');
          break;
        case 't':
          builder.append('\t');
          break;
        case '#':
          substitution = ContextExpressions.CALLER_REF;
          break;
        case 'n':
          substitution = ContextExpressions.CALLER_NAME;
          break;
        case 'q':
          // the char after 'q'/'v' is always consumed if present, even when it isn't a
          // valid digit/letter -- a documented eval.c misfeature (oracle-verified: e.g.
          // "a%qzb" -> "ab", not "azb")
          if (index + 1 < string.length()) {
            char registerChar = string.charAt(index + 1);
            index++;
            if (Character.isDigit(registerChar)) {
              substitution = new RegisterExpression(registerChar - '0');
            }
          }
          break;
        case 'v':
          if (index + 1 < string.length()) {
            char attributeLetter = Character.toUpperCase(string.charAt(index + 1));
            index++;
            if (attributeLetter >= 'A' && attributeLetter <= 'Z') {
              substitution = new AttributeExpression("V" + attributeLetter);
            }
          }
          break;
        default:
          // unimplemented substitution -- copy the character verbatim, matching
          // eval.c's default case, until the corresponding gap is closed
          builder.append(nextChar);
      }
      if (substitution != null) {
        if (upper) {
          substitution = new UppercaseFirstExpression(substitution);
        }
        flushBuilder();
        finishedExpressions.add(substitution);
      }
    }

    /**
     * {@code ##}/{@code #@}/{@code #!} -- {@code iter()}'s loop-element/index/depth tokens
     * (eval.c:1088-1117). These aren't {@code %}-prefixed, so they need their own tokenizer case; a
     * bare {@code #} not followed by one of the three recognized chars is just a literal {@code #}
     * -- the next char is left untouched for the main loop to process normally next, matching
     * {@code %}'s existing peek-and-fall-through style.
     */
    private void handleLoopToken() {
      if (index + 1 < string.length()) {
        Expression substitution;
        switch (string.charAt(index + 1)) {
          case '#':
            substitution = new LoopTokenExpression();
            break;
          case '@':
            substitution = new LoopIndexExpression();
            break;
          case '!':
            substitution = new LoopDepthExpression();
            break;
          default:
            substitution = null;
        }
        if (substitution != null) {
          index++;
          flushBuilder();
          finishedExpressions.add(substitution);
          return;
        }
      }
      builder.append('#');
    }

    private Expression finish() {
      // eval.c:1123-1131 -- if the string ended on a space eligible for compression, that
      // trailing space is deleted entirely (not merely compressed to one). Also fires for a
      // trailing backslash-escaped space (lastWasEscapedSpace), matching eval.c's parse_to_cleanup
      // -- oracle-verified cat(a\ \ \ ,b) loses exactly one of the three escaped spaces -- but NOT
      // for a %b-produced trailing space, which stays escape-protected the same way an escaped
      // space is protected from interior compression (oracle-verified trim(%b%bhello%b%b,l) keeps
      // both trailing %b spaces). See lastWasEscapedSpace's javadoc for why these differ.
      if (flags.isSpaceCompressionEnabled()
          && (lastWasSpace || lastWasEscapedSpace)
          && builder.length() > 0) {
        builder.setLength(builder.length() - 1);
      }

      if (finishedExpressions.isEmpty()) {
        return Value.of(builder.toString());
      }
      if (builder.length() > 0) {
        finishedExpressions.add(Value.of(builder.toString()));
      }
      return finishedExpressions.size() == 1
          ? finishedExpressions.get(0)
          : new ConcatExpression(finishedExpressions);
    }

    private void flushBuilder() {
      if (builder.length() > 0) {
        finishedExpressions.add(Value.of(builder.toString()));
        builder.setLength(0);
      }
    }
  }
}
