package com.github.unchuckable.jmush.mushcode;

import com.github.unchuckable.jmush.mushcode.expressions.AttributeExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ContextExpressions;
import com.github.unchuckable.jmush.mushcode.expressions.DynamicFunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopDepthExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopIndexExpression;
import com.github.unchuckable.jmush.mushcode.expressions.LoopTokenExpression;
import com.github.unchuckable.jmush.mushcode.expressions.RegisterExpression;
import com.github.unchuckable.jmush.mushcode.expressions.UppercaseFirstExpression;
import com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry;
import com.github.unchuckable.jmush.mushcode.functions.MushFunctionHandler;
import com.github.unchuckable.jmush.util.StringUtils;
import java.util.ArrayList;
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
      parameters.add(parse(string.substring(currentIndex, nextIndex), flags));
      currentIndex = nextIndex + 1;
      nextIndex = StringUtils.findIndexOf(',', string, currentIndex, endIndex);
    }
    parameters.add(parse(string.substring(currentIndex, endIndex), flags));
    return parameters;
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
        builder.append(string.charAt(index));
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
      EvalFlags innerFlags = flags.withFunctionCheck(true);
      finishedExpressions.add(parse(string.substring(index + 1, endIndex), innerFlags));
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
        MushFunctionHandler function = functionRegistry.getFunction(nameBuilder.toString());
        if (function == null) {
          builder.append('(');
          functionCheckAvailable = false;
          return;
        }
        List<Expression> parameters = getParameters(string, index + 1, endIndex, flags);
        builder.setLength(0);
        finishedExpressions.add(new FunctionExpression(function, parameters));
      } else {
        // The name depends on a substitution/function result -- defer name resolution (and the
        // invoke-vs-literal-fallback decision) to evaluation time (see DynamicFunctionExpression).
        while (finishedExpressions.size() > nameBoundary) {
          finishedExpressions.remove(finishedExpressions.size() - 1);
        }
        builder.setLength(0);
        Expression nameExpression =
            pendingName.size() == 1 ? pendingName.get(0) : new ConcatExpression(pendingName);
        List<Expression> arguments = getParameters(string, index + 1, endIndex, flags);
        finishedExpressions.add(
            new DynamicFunctionExpression(functionRegistry, nameExpression, arguments));
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
      // trailing space is deleted entirely (not merely compressed to one).
      if (flags.isSpaceCompressionEnabled() && lastWasSpace && builder.length() > 0) {
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
