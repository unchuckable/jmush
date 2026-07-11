package com.github.unchuckable.jmush.util;

/**
 * MUSH-style glob matching ({@code *} = any run of characters including none, {@code ?} = exactly
 * one character), case-insensitive -- matches {@code quick_wild()}'s observable behavior (e.g.
 * {@code switch()}'s pattern arguments), oracle-verified case-insensitive (e.g. pattern {@code
 * "A*"} matches subject {@code "abc"}).
 */
public class WildcardMatcher {

  public static boolean matches(String pattern, String subject) {
    return matches(pattern, 0, subject, 0);
  }

  private static boolean matches(
      String pattern, int patternIndex, String subject, int subjectIndex) {
    while (patternIndex < pattern.length()) {
      char patternChar = pattern.charAt(patternIndex);
      if (patternChar == '*') {
        while (patternIndex + 1 < pattern.length() && pattern.charAt(patternIndex + 1) == '*') {
          patternIndex++;
        }
        if (patternIndex + 1 == pattern.length()) {
          return true;
        }
        for (int i = subjectIndex; i <= subject.length(); i++) {
          if (matches(pattern, patternIndex + 1, subject, i)) {
            return true;
          }
        }
        return false;
      }
      if (subjectIndex >= subject.length()) {
        return false;
      }
      if (patternChar != '?'
          && Character.toLowerCase(patternChar)
              != Character.toLowerCase(subject.charAt(subjectIndex))) {
        return false;
      }
      patternIndex++;
      subjectIndex++;
    }
    return subjectIndex == subject.length();
  }

  private WildcardMatcher() {
    // static helper class, do not instantiate
  }
}
