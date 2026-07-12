package com.github.unchuckable.jmush.mushcode.expressions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MandatoryMatchException;
import com.github.unchuckable.jmush.mushcode.Value;

/**
 * Wraps the parsed contents of a {@code [...]} forced-eval block, catching a {@link
 * MandatoryMatchException} thrown during evaluation (by a dynamically-resolved function name that
 * turned out not to catenate -- see {@link DynamicFunctionExpression}, whose statically-resolved
 * counterpart in {@code MushcodeParser.handleFunctionCall} instead throws at parse time, caught
 * directly by {@code handleForcedEval} without ever needing this wrapper) and converting it to the
 * error {@link Value} in its place. This is the evaluation-time half of scoping the catch to
 * exactly one {@code [...]} block, mirroring how {@code eval.c}'s {@code alldone} is local to one
 * nested {@code exec()} invocation: ordinary exception propagation through {@link ConcatExpression}
 * already stops evaluating this block's remaining children the instant one throws (matching C's
 * linear-scan abort exactly, since evaluation order already matches source order), so this wrapper
 * only needs to sit at the block's own boundary, not anywhere inside it.
 */
public class MandatoryMatchExpression implements Expression {

  private final Expression inner;

  public MandatoryMatchExpression(Expression inner) {
    this.inner = inner;
  }

  @Override
  public Value evaluateExpression(ExecutionContext context) {
    try {
      return inner.evaluateExpression(context);
    } catch (MandatoryMatchException e) {
      return Value.of(e.getMessage());
    }
  }

  @Override
  public boolean isConstant() {
    return inner.isConstant();
  }
}
