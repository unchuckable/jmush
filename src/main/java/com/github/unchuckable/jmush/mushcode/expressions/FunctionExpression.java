package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunctionHandler;
import java.util.List;

public class FunctionExpression implements Expression {

  private final MushFunctionHandler function;
  private final List<Expression> parameters;

  public FunctionExpression(MushFunctionHandler function, List<Expression> parameters) {
    this.function = function;
    this.parameters = parameters;
  }

  public Value evaluateExpression(ExecutionContext context) {
    return function.execute(context, parameters);
  }

  public boolean isConstant() {
    return false;
  }
}
