package com.github.unchuckable.jmush.mushcode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.functions.builtin.ObjectFunctions;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class ConcatExpressionTest {

  @Test
  public void testConcatExpression() {
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Caller"));

    Expression e =
        new ConcatExpression(
            Arrays.asList(
                Value.of("Hello, "),
                new FunctionExpression(ObjectFunctions::getCallerName, Arrays.asList())));

    assertEquals("Hello, Caller", e.evaluateExpression(ctx).asString());
  }
}
