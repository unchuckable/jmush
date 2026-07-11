package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/** {@code %q0}-{@code %q9} local-register substitution. */
public class RegisterExpression implements Expression {

  private final int index;

  public RegisterExpression(int index) {
    this.index = index;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    Value value = context.getRegisters()[index];
    return value != null ? value : Value.of("");
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
