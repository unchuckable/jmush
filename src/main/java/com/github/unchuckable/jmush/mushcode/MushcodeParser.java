package com.github.unchuckable.jmush.mushcode;

import com.github.unchuckable.jmush.mushcode.expressions.AttributeExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConstantExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ContextExpressions;
import com.github.unchuckable.jmush.mushcode.expressions.DynamicFunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.RegisterExpression;
import com.github.unchuckable.jmush.mushcode.expressions.UppercaseFirstExpression;
import com.github.unchuckable.jmush.mushcode.functions.MushFunctionHandler;
import com.github.unchuckable.jmush.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MushcodeParser {

  private Map<String, MushFunctionHandler> functionMap;

  public MushcodeParser(Map<String, MushFunctionHandler> functionMap) {
    this.functionMap = functionMap;
  }

  public Expression parse(String string) {
    return parse(string, EvalFlags.DEFAULT);
  }

  public Expression parse(String string, EvalFlags flags) {
    List<Expression> finishedExpressions = new ArrayList<>();
    StringBuilder builder = new StringBuilder();

    // Marks where the current function-name candidate starts within finishedExpressions --
    // mirrors eval.c's "oldp": it only advances once a function call actually dispatches, so
    // literal text, substitutions, and forced-eval/{}-group output accumulated since the last
    // dispatched call are all still eligible to be re-read as the *next* function name (see
    // the '(' case below).
    int nameBoundary = 0;

    // EV_FCHECK is a one-shot flag per eval.c invocation: the *first* unescaped '(' encountered
    // (whose matching ')' is found) consumes it unconditionally -- whether that '(' turns out to
    // name a real function or not -- and every subsequent '(' at this scan level is permanently
    // literal until a fresh nested scan (a [...] block, a {...} block, or a function argument,
    // each of which calls parse() recursively with its own local copy of this flag) re-enables
    // it. Oracle-verified: "add(1,2)add(3,4)" -> "3add(3,4)", not "37" -- see eval.c:908 (no
    // match found), :1012/:1086 (dispatch completed, success or error), all of which do
    // `eval &= ~EV_FCHECK` before falling through to plain literal-character handling.
    boolean functionCheckAvailable = flags.isFunctionCheckEnabled();

    int index = 0;

    // eval.c initializes at_space = 1, so a leading space is treated as an immediate
    // repeat and fully suppressed (not just compressed to one) -- see eval.c:444.
    boolean lastWasSpace = true;

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

      switch (string.charAt(index)) {
        case '\\':
          { // escape char
            index++;
            if (index < string.length()) {
              builder.append(string.charAt(index));
            }
            break;
          }

        case '{':
          { // literal grouping: braces are kept in the output (unless EV_STRIP), contents
            // still have %-substitutions evaluated but not function calls (eval.c:552-582)
            int endIndex = StringUtils.findIndexOf('}', string, index + 1);
            if (endIndex == -1) {
              builder.append('{');
            } else {
              if (!flags.isStripEnabled()) {
                builder.append('{');
              }
              flushBuilder(builder, finishedExpressions);
              EvalFlags innerFlags = flags.withFunctionCheck(false).withStrip(false);
              finishedExpressions.add(parse(string.substring(index + 1, endIndex), innerFlags));
              if (!flags.isStripEnabled()) {
                builder.append('}');
              }
              index = endIndex;
            }
            break;
          }

        case '[':
          { // forced evaluation: function checking is always forced on inside [...],
            // regardless of the ambient flags (eval.c:527-551, "eval | EV_FCHECK | EV_FMAND")
            int endIndex = StringUtils.findIndexOf(']', string, index + 1);
            if (endIndex == -1) {
              builder.append('[');
            } else {
              flushBuilder(builder, finishedExpressions);
              EvalFlags innerFlags = flags.withFunctionCheck(true);
              finishedExpressions.add(parse(string.substring(index + 1, endIndex), innerFlags));
              index = endIndex;
            }
            break;
          }

        case '(':
          { // start of function
            // look for closing bracket
            if (!functionCheckAvailable) {
              builder.append('(');
              break;
            }
            int endIndex = StringUtils.findIndexOf(')', string, index + 1);
            if (endIndex == -1) {
              builder.append('(');
              break;
            }

            // The function-name candidate is everything accumulated since the last dispatched
            // call: pending finishedExpressions entries plus the current literal run.
            List<Expression> pendingName =
                new ArrayList<>(
                    finishedExpressions.subList(nameBoundary, finishedExpressions.size()));
            if (builder.length() > 0) {
              pendingName.add(new ConstantExpression(Value.of(builder.toString())));
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
              // contract) pieces to a literal string and resolve the function right now, exactly
              // as before this method learned about dynamic names.
              StringBuilder nameBuilder = new StringBuilder();
              for (Expression piece : pendingName) {
                nameBuilder.append(piece.evaluateExpression(null).asString());
              }
              String functionName = nameBuilder.toString();
              MushFunctionHandler function = getFunction(functionName);
              if (function == null) {
                builder.append('(');
                functionCheckAvailable = false;
                break;
              }
              List<Expression> parameters = getParameters(string, index + 1, endIndex, flags);
              builder.setLength(0);
              finishedExpressions.add(new FunctionExpression(function, parameters));
              index = endIndex;
              nameBoundary = finishedExpressions.size();
              functionCheckAvailable = false;
            } else {
              // The name depends on a substitution/function result -- defer both name resolution
              // and argument parsing to evaluation time (see DynamicFunctionExpression).
              while (finishedExpressions.size() > nameBoundary) {
                finishedExpressions.remove(finishedExpressions.size() - 1);
              }
              builder.setLength(0);
              Expression nameExpression =
                  pendingName.size() == 1 ? pendingName.get(0) : new ConcatExpression(pendingName);
              String rawArguments = string.substring(index + 1, endIndex);
              finishedExpressions.add(
                  new DynamicFunctionExpression(this, nameExpression, rawArguments, flags));
              index = endIndex;
              nameBoundary = finishedExpressions.size();
              functionCheckAvailable = false;
            }
            break;
          }
        case '%':
          {
            index++;
            if (index == string.length()) {
              break;
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
              flushBuilder(builder, finishedExpressions);
              finishedExpressions.add(substitution);
            }
            break;
          }

        default:
          builder.append(thisChar);
      }
      index++;
    }

    // eval.c:1123-1131 -- if the string ended on a space eligible for compression, that
    // trailing space is deleted entirely (not merely compressed to one).
    if (flags.isSpaceCompressionEnabled() && lastWasSpace && builder.length() > 0) {
      builder.setLength(builder.length() - 1);
    }

    if (finishedExpressions.isEmpty()) {
      return new ConstantExpression(Value.of(builder.toString()));
    }
    if (builder.length() > 0) {
      finishedExpressions.add(new ConstantExpression(Value.of(builder.toString())));
    }
    return finishedExpressions.size() == 1
        ? finishedExpressions.get(0)
        : new ConcatExpression(finishedExpressions);
  }

  public MushFunctionHandler getFunction(String name) {
    return this.functionMap.get(name.toLowerCase());
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

  private void flushBuilder(StringBuilder builder, List<Expression> finishedExpressions) {
    if (builder.length() > 0) {
      finishedExpressions.add(new ConstantExpression(Value.of(builder.toString())));
      builder.setLength(0);
    }
  }
}
