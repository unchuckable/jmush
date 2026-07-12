package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * {@code ##} -- the current {@code iter()} element. Outside any loop it's literal text (oracle-
 * verified: bare {@code ##} evaluates to {@code "##"}, not empty), matching eval.c's behavior of
 * only substituting when {@code mudstate.in_loop} is set.
 */
public class LoopTokenExpression implements Expression {

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    ExecutionContext.LoopFrame frame = context.peekLoop();
    return frame != null ? frame.token : Value.of("##");
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
