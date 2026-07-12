package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * {@code #!} -- the nesting depth of the *enclosing* loop (0 if there is none), i.e. one less than
 * the number of active {@code iter()} frames. Outside any loop it's literal text ({@code "#!"}),
 * same rule as {@link LoopTokenExpression}.
 */
public class LoopDepthExpression implements Expression {

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    int depth = context.loopDepth();
    return depth > 0 ? Value.ofInt(depth - 1) : Value.of("#!");
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
