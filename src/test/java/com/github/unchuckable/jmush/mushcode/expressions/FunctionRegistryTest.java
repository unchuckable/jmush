package com.github.unchuckable.jmush.mushcode.expressions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;

public class FunctionRegistryTest {

  private Expression parse(String code) {
    return new MushcodeParser(FunctionRegistry.build()).parse(code);
  }

  private String eval(String code) {
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));
    return parse(code).evaluateExpression(ctx).toString();
  }

  @Test
  public void testVariadicAdd() {
    assertEquals("15", eval("add(12,3)"));
    assertEquals("18", eval("add(10,5,3)"));
    // lenient atof-style parsing, not throwing -- garbage coerces to 0
    assertEquals("3", eval("add(abc,3)"));
    assertEquals("15", eval("add(12abc,3)"));
  }

  @Test
  public void testFixedAritySub() {
    assertEquals("7", eval("sub(10,3)"));
    assertEquals("-3", eval("sub(abc,3)"));
  }

  @Test
  public void testFixedArityAbs() {
    assertEquals("5", eval("abs(-5)"));
    assertEquals("5.5", eval("abs(-5.5)"));
    assertEquals("0", eval("abs(abc)"));
  }

  @Test
  public void testArgCountValidation() {
    assertEquals("#-1 FUNCTION (ADD) EXPECTS AT LEAST 2 ARGUMENTS", eval("add(1)"));
    assertEquals("#-1 FUNCTION (SUB) EXPECTS 2 ARGUMENTS", eval("sub(1)"));
  }

}
