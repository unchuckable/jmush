package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * Wraps a %-substitution expression to capitalize the first character of its result -- the
 * uppercase-substitution-letter convention (%N vs %n, %Q vs %q, ...) from eval.c.
 */
public class UppercaseFirstExpression implements Expression {

  private final Expression inner;

  public UppercaseFirstExpression(Expression inner) {
    this.inner = inner;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    String value = inner.evaluateExpression(context).asString();
    if (value.isEmpty()) {
      return Value.of(value);
    }
    return Value.of(Character.toUpperCase(value.charAt(0)) + value.substring(1));
  }

  @Override
  public boolean isConstant() {
    return inner.isConstant();
  }
}
