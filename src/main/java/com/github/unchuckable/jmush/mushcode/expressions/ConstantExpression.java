package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

public class ConstantExpression implements Expression {

  public final Value value;
  
  public ConstantExpression( Value value ) {
    this.value = value;
  }
  
  @Override
  public Value evaluateExpression(ExecutionContext context) {
    return value;
  }
  
  public boolean isConstant() {
    return true;
  }
  
}