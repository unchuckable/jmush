package com.github.unchuckable.jmush.mushcode.expressions;

import java.util.ArrayList;
import java.util.List;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

public class FunctionExpression implements Expression {

  private final MushFunction function;
  private final List<Expression> parameters;

  public FunctionExpression(MushFunction function, List<Expression> parameters) {
    this.function = function;
    this.parameters = parameters;
  }

  public Value evaluateExpression(ExecutionContext context) {
    return function.execute(context, evaluateParameters(parameters, context));
  }
  
  private List<Value> evaluateParameters(List<Expression> parameters, ExecutionContext ctx) {
    if ( parameters == null ) {
      return null;
    }
    List<Value> parameterValues = new ArrayList<>(parameters.size());
    for (Expression thisParameter : parameters) {
      parameterValues.add(thisParameter.evaluateExpression(ctx));
    }
    return parameterValues;
  }

  public boolean isConstant() {
    return false;
  }
}
