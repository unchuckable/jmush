package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

public enum ContextExpressions implements Expression {
  CALLER_REF {
    @Override
    public Value evaluateExpression(ExecutionContext context) {
      return Value.of(context.getCaller().getDbRefString());
    }
  },

  CALLER_NAME {
    @Override
    public Value evaluateExpression(ExecutionContext context) {
      return Value.of(context.getCaller().getName());
    }
  };

  @Override
  public boolean isConstant() {
    return false;
  }
}
