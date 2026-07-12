package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * {@code #@} -- the current {@code iter()} element's 1-based index. Outside any loop it's literal
 * text ({@code "#@"}), same rule as {@link LoopTokenExpression}.
 */
public class LoopIndexExpression implements Expression {

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    ExecutionContext.LoopFrame frame = context.peekLoop();
    return frame != null ? Value.ofInt(frame.index) : Value.of("#@");
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
