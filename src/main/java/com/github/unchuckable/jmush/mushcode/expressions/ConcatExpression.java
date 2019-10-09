package com.github.unchuckable.jmush.mushcode.expressions;

import java.util.List;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

public class ConcatExpression implements Expression {

  private final List<Expression> expressions;
  private final boolean constant;

  public ConcatExpression(List<Expression> expressions) {
    this.expressions = expressions;
    this.constant = areAllConstant(expressions);
  }

  private boolean areAllConstant(List<Expression> expressions) {
    for (Expression thisExpression : expressions) {
      if (!thisExpression.isConstant()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    StringBuilder builder = new StringBuilder();
    for (Expression thisExpression : expressions) {
      builder.append(thisExpression.evaluateExpression(context).asString());
    }
    return Value.of(builder.toString());
  }

  public boolean isConstant() {
    return this.constant;
  }
}
