package com.github.unchuckable.jmush.mushcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.expressions.ConcatExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ConstantExpression;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ObjectFunctions;

public class ConcatExpressionTest {

  @Test
  public void testConcatExpression() {
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Caller"));

    Expression e =
        new ConcatExpression(
            Arrays.asList(
                new ConstantExpression(Value.of("Hello, ")),
                new FunctionExpression(ObjectFunctions::getCallerName, Arrays.asList())));
    
    assertEquals("Hello, Caller", e.evaluateExpression(ctx).asString());
  }
}
