package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunctionHandler;
import java.util.ArrayList;
import java.util.List;

public class FunctionExpression implements Expression {

  private final MushFunctionHandler function;
  private final List<Expression> parameters;

  public FunctionExpression(MushFunctionHandler function, List<Expression> parameters) {
    this.function = function;
    this.parameters = parameters;
  }

  public Value evaluateExpression(ExecutionContext context) {
    return function.execute(context, evaluateParameters(context));
  }

  private List<Value> evaluateParameters(ExecutionContext context) {
    if (parameters == null) {
      return null;
    }
    List<Value> parameterValues = new ArrayList<>(parameters.size());
    for (Expression thisParameter : parameters) {
      parameterValues.add(thisParameter.evaluateExpression(context));
    }
    return parameterValues;
  }

  public boolean isConstant() {
    return false;
  }
}
