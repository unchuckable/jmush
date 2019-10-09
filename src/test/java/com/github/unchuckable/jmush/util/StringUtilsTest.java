package com.github.unchuckable.jmush.util;

import static com.github.unchuckable.jmush.util.StringUtils.STD_NESTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.common.collect.ImmutableSet;

public class StringUtilsTest {

  @Test
  @DisplayName("trivial non-match")
  public void testNonMatchNesting() {
    assertEquals(-1, StringUtils.findIndexOf(';', "Hans", 0, 4, STD_NESTINGS));
  }

  @Test
  @DisplayName("trivial matching")
  public void testEasyMatch() {
    assertEquals(2, StringUtils.findIndexOf(')', "%#)", 0, 3, STD_NESTINGS));
  }

  @Test
  @DisplayName("nested matching")
  public void testNestedMatch() {
    assertEquals(8, StringUtils.findIndexOf(')', "room(%#))", 0, 9, STD_NESTINGS));
  }

  @Test
  @DisplayName("nested mismatch")
  public void testNestedMismatch() {
    assertEquals(-1, StringUtils.findIndexOf(')', "[room(%#))", 0, 10, STD_NESTINGS));
  }

  @Test
  @DisplayName("escaped match")
  public void testEscapedMatch() {
    assertEquals(6, StringUtils.findIndexOf(')', "room%()", 0, 7, STD_NESTINGS));
  }

  @Test
  @DisplayName("nested comma")
  public void testNestedComma() {
    assertEquals(13, StringUtils.findIndexOf(',', "lcon(here,%#),name(##))", 0, 23, STD_NESTINGS));
  }

  @Test
  @DisplayName("match set")
  public void testMatchSet() {
    assertEquals(
        7,
        StringUtils.findIndexOfSet(
            ImmutableSet.of('(', '[', '{', '%'), "Hello, %#", 0, 9, STD_NESTINGS));
  }
}
