package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

public enum ContextExpressions implements Expression {
  
  getCallerRef {
    @Override
    public Value evaluateExpression(ExecutionContext context) {
      return Value.of(context.getCaller().getDbRefString());
    }
  };

  
  @Override
  public boolean isConstant() {
    return false;
  }

}
