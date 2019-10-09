package com.github.unchuckable.jmush.util;

import java.util.Map;
import java.util.Set;
import com.google.common.collect.ImmutableMap;

public class StringUtils {
  
  public interface CharPredicate {
    boolean apply(char character);
  }
  
  public static final Map<Character, Character> STD_NESTINGS =
      ImmutableMap.<Character, Character>builder()
          .put('(', ')')
          .put('[', ']')
          .put('{', '}')
          .build();

  
  private StringUtils() {
    // static helper class, do not instantiate
  }

  
  public static int findIndexOf(
      char terminator, String string, int start) {
    return findIndexOfPredicate(c -> c == terminator, string, start, string.length(), STD_NESTINGS);
  }

  public static int findIndexOf(
      char terminator, String string, int start, int end) {
    return findIndexOfPredicate(c -> c == terminator, string, start, end, STD_NESTINGS);
  }

  
  public static int findIndexOf(
      char terminator, String string, int start, int end, Map<Character, Character> nesting) {
    return findIndexOfPredicate(c -> c == terminator, string, start, end, nesting);
  }

  public static int findIndexOfSet(
      Set<Character> terminators,
      String string,
      int start,
      int end,
      Map<Character, Character> nesting) {
    return findIndexOfPredicate(terminators::contains, string, start, end, nesting);
  }

  public static int findIndexOfPredicate(
      CharPredicate predicate,
      String string,
      int start,
      int end,
      Map<Character, Character> nesting) {
    for (int i = start; i < end; i++) {
      char currentChar = string.charAt(i);

      if (predicate.apply(currentChar)) {
        return i;
      }

      if (currentChar == '\\' || currentChar == '%') {
        i++;
        continue;
      }

      Character nestingEnd = nesting.get(currentChar);
      if (nestingEnd != null) {
        i = findIndexOf(nestingEnd, string, i + 1, end, nesting);
        if (i == -1) {
          break;
        }
      }
    }

    return -1;
  }
}
