package com.github.unchuckable.jmush.mushcode.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;
import org.junit.jupiter.api.Test;

public class FunctionRegistryTest {

  private static final MushcodeParser PARSER = new MushcodeParser(FunctionRegistry.build());

  private Expression parse(String code) {
    return PARSER.parse(code);
  }

  private String eval(String code) {
    ExecutionContext ctx =
        new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));
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
    // add is genuinely variadic (FN_VARARGS in functions.c) -- it enforces its own minimum via
    // a manual body check, not FunctionRegistry's generic wrapper, so the message is the shared
    // "#-1 TOO FEW ARGUMENTS", not a generic "EXPECTS AT LEAST N ARGUMENTS" (oracle-verified).
    assertEquals("#-1 TOO FEW ARGUMENTS", eval("add(1)"));
    assertEquals("#-1 TOO FEW ARGUMENTS", eval("add()"));
    assertEquals("#-1 FUNCTION (SUB) EXPECTS 2 ARGUMENTS", eval("sub(1)"));
  }

  @Test
  public void testVariadicMul() {
    assertEquals("24", eval("mul(2,3,4)"));
    assertEquals("0", eval("mul(2,abc)"));
    assertEquals("#-1 TOO FEW ARGUMENTS", eval("mul(2)"));
    assertEquals("#-1 TOO FEW ARGUMENTS", eval("mul()"));
  }

  @Test
  public void testDivFdivMod() {
    // div/mod are integer (atoi-based, truncating toward zero); fdiv is float
    assertEquals("3", eval("div(7,2)"));
    assertEquals("-3", eval("div(-7,2)"));
    assertEquals("-3", eval("div(7,-2)"));
    assertEquals("#-1 DIVIDE BY ZERO", eval("div(7,0)"));
    assertEquals("3.5", eval("fdiv(7,2)"));
    assertEquals("#-1 DIVIDE BY ZERO", eval("fdiv(7,0)"));
    assertEquals("1", eval("mod(7,3)"));
    assertEquals("-1", eval("mod(-7,3)"));
    // mod's zero-divisor falls back to 1, not an error -- oracle-verified
    assertEquals("0", eval("mod(7,0)"));
  }

  @Test
  public void testComparisons() {
    assertEquals("1", eval("eq(1,1)"));
    assertEquals("0", eval("eq(1,2)"));
    assertEquals("1", eval("eq(1.0,1)"));
    assertEquals("1", eval("neq(1,2)"));
    assertEquals("1", eval("gt(2,1)"));
    assertEquals("1", eval("gte(2,2)"));
    assertEquals("1", eval("lt(1,2)"));
    assertEquals("1", eval("lte(2,2)"));
  }

  @Test
  public void testLogicFunctions() {
    assertEquals("1", eval("and(1,1)"));
    assertEquals("0", eval("and(1,0)"));
    assertEquals("#-1 TOO FEW ARGUMENTS", eval("and(1)"));
    assertEquals("1", eval("or(0,1)"));
    assertEquals("0", eval("or(0,0)"));
    assertEquals("1", eval("xor(1,0)"));
    assertEquals("0", eval("xor(1,1)"));
    assertEquals("1", eval("not(0)"));
    assertEquals("0", eval("not(5)"));
    // not() uses atoi (integer-prefix truncation), distinct from add/sub's aton -- "0.5"
    // truncates to integer 0, so not(0.5) is truthy-negated to 1, not 0 (oracle-verified)
    assertEquals("1", eval("not(0.5)"));
  }

  @Test
  public void testStringFunctions() {
    assertEquals("a b c", eval("cat(a,b,c)"));
    assertEquals("a", eval("cat(a)"));
    assertEquals("", eval("cat()"));
    assertEquals("5", eval("strlen(hello)"));
    assertEquals("0", eval("strlen()"));
    assertEquals("11", eval("strlen(hello world)"));
  }
}
