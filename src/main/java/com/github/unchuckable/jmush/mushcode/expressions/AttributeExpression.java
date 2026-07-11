package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * {@code %v<A-Z>} variable-attribute substitution -- reads the caller's {@code V<letter>} attribute
 * (e.g. {@code %va} reads attribute {@code VA}), oracle-verified case-insensitive on the attribute
 * letter and empty (not a literal) when unset.
 */
public class AttributeExpression implements Expression {

  private final String attributeName;

  public AttributeExpression(String attributeName) {
    this.attributeName = attributeName;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    String value = context.getCaller().getAttribute(attributeName);
    return Value.of(value != null ? value : "");
  }

  @Override
  public boolean isConstant() {
    return false;
  }
}
