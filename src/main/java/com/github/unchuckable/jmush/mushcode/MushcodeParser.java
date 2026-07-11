package com.github.unchuckable.jmush.mushcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConstantExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ContextExpressions;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.MushFunctionHandler;
import com.github.unchuckable.jmush.mushcode.expressions.RegisterExpression;
import com.github.unchuckable.jmush.mushcode.expressions.UppercaseFirstExpression;
import com.github.unchuckable.jmush.util.StringUtils;

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

    int index = 0;

    // eval.c initializes at_space = 1, so a leading space is treated as an immediate
    // repeat and fully suppressed (not just compressed to one) -- see eval.c:444.
    boolean lastWasSpace = true;

    while (index < string.length()) {
      char thisChar = string.charAt(index);

      if ( thisChar == ' ' ) {
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
            if (!flags.isFunctionCheckEnabled()) {
              builder.append('(');
              break;
            }
            int endIndex = StringUtils.findIndexOf(')', string, index + 1);
            if (endIndex == -1) {
              builder.append('(');
            } else {
              String functionName = builder.toString();
              MushFunctionHandler function = getFunction(functionName);
              if (function == null) {
                builder.append('(');
                break;
              }
              List<Expression> parameters = getParameters(string, index + 1, endIndex, flags);
              builder.setLength(0);
              finishedExpressions.add(new FunctionExpression(function, parameters));
              index = endIndex;
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
                substitution = ContextExpressions.getCallerRef;
                break;
              case 'n':
                substitution = ContextExpressions.getCallerName;
                break;
              case 'q':
                if (index + 1 < string.length() && Character.isDigit(string.charAt(index + 1))) {
                  index++;
                  substitution = new RegisterExpression(string.charAt(index) - '0');
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
    } else {
      if (builder.length() > 0) {
        finishedExpressions.add(new ConstantExpression(Value.of(builder.toString())));
      }
      return new ConcatExpression(finishedExpressions);
    }
  }

  public MushFunctionHandler getFunction(String name) {
    return this.functionMap.get(name.toLowerCase());
  }

  private List<Expression> getParameters(String string, int startIndex, int endIndex, EvalFlags flags) {
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
