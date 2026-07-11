package com.github.unchuckable.jmush.mushcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ValueTest {

  @Test
  public void testIntRoundTrip() {
    assertEquals("42", Value.ofInt(42).asString());
    assertEquals(42L, Value.of("42").asInt());
    assertEquals(-7L, Value.of("  -7  ").asInt());
  }

  @Test
  public void testIntParseFailureThrowsWithDefaultMessage() {
    MushValueException e = assertThrows(MushValueException.class, () -> Value.of("abc").asInt());
    assertEquals(MushErrors.MUST_BE_INTEGER, e.getMessage());
  }

  @Test
  public void testIntParseFailureWithCustomMessage() {
    MushValueException e =
        assertThrows(MushValueException.class, () -> Value.of("abc").asInt("#-1 CUSTOM ERROR"));
    assertEquals("#-1 CUSTOM ERROR", e.getMessage());
  }

  @Test
  public void testDoubleFormatting() {
    // fval()'s exact rules, oracle-verified against add(x, 0):
    // %.6f, strip trailing fractional zeros (and a then-dangling '.'), normalize "-0" -> "0"
    assertEquals("100", Value.ofDouble(100).asString());
    assertEquals("100.5", Value.ofDouble(100.5).asString());
    assertEquals("120", Value.ofDouble(120).asString());
    assertEquals("1.123457", Value.ofDouble(1.123456789).asString());
    assertEquals("0", Value.ofDouble(-0.0000001).asString());
    assertEquals("0.3", Value.ofDouble(0.1 + 0.2).asString());
  }

  @Test
  public void testDoubleRoundTrip() {
    assertEquals(3.5, Value.of("3.5").asDouble());
  }

  @Test
  public void testDoubleParseFailureThrowsWithDefaultMessage() {
    MushValueException e = assertThrows(MushValueException.class, () -> Value.of("abc").asDouble());
    assertEquals(MushErrors.MUST_BE_NUMBER, e.getMessage());
  }

  @Test
  public void testAtoi() {
    // C atoi() semantics: leading-integer-prefix only, stops at the first non-digit
    // (notably '.' -- no float parsing), 0 on anything that doesn't start looking like an
    // integer. Distinct from aton()'s float parsing -- oracle-verified via not(0.5) -> 1
    // (atoi("0.5") is 0, a different result than aton("0.5") != 0 would give).
    assertEquals(5L, Value.of("5abc").atoi());
    assertEquals(0L, Value.of("0.5").atoi());
    assertEquals(5L, Value.of(" 5").atoi());
    assertEquals(-5L, Value.of("-5").atoi());
    assertEquals(0L, Value.of("abc").atoi());
    assertEquals(0L, Value.of("").atoi());
  }

  @Test
  public void testDbRefRoundTrip() {
    assertEquals("#42", Value.ofDbRef(DbRef.of(42)).asString());
    assertEquals(DbRef.of(42), Value.of("#42").asDbRef());
    assertEquals(DbRef.of(-1), Value.of("#-1").asDbRef());
  }

  @Test
  public void testDbRefParseFailureThrowsWithDefaultMessage() {
    MushValueException e =
        assertThrows(MushValueException.class, () -> Value.of("not-a-ref").asDbRef());
    assertEquals(MushErrors.NOT_A_DBREF, e.getMessage());

    assertThrows(MushValueException.class, () -> Value.of("#").asDbRef());
    assertThrows(MushValueException.class, () -> Value.of("#abc").asDbRef());
  }
}
