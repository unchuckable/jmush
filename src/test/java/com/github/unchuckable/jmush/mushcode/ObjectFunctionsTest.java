package com.github.unchuckable.jmush.mushcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.expressions.FunctionExpression;
import com.github.unchuckable.jmush.mushcode.expressions.ObjectFunctions;

public class ObjectFunctionsTest {

  @Test
  public void testCallerName() {
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Caller"));
    Expression e = new FunctionExpression(ObjectFunctions::getCallerName, Collections.emptyList());

    assertEquals("Caller", e.evaluateExpression(ctx).toString());
  }
}
