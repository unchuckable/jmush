package com.github.unchuckable.jmush.mushcode;

/**
 * Immutable wrapper around mushcode's string-typed values -- matching the C reference,
 * where every function call parses string input and formats string output; there is no
 * persistent typed value between calls. Construction only via the static {@code of*}
 * factories; consumption only via the {@code as*} accessors.
 *
 * <p>Numeric/dbref accessors are parse-on-demand and memoized (safe since {@code Value} is
 * immutable); the matching factory pre-populates both the canonical string and the cached
 * field to skip a redundant round-trip when a function already knows its native result type.
 */
public class Value {

  private final String value;

  private Long intCache;
  private Double doubleCache;
  private DbRef dbRefCache;

  public static Value of(String value) {
    return new Value(value);
  }

  public static Value ofInt(long value) {
    Value result = new Value(Long.toString(value));
    result.intCache = value;
    return result;
  }

  public static Value ofDouble(double value) {
    Value result = new Value(formatDouble(value));
    result.doubleCache = value;
    return result;
  }

  public static Value ofDbRef(DbRef value) {
    Value result = new Value(value.toString());
    result.dbRefCache = value;
    return result;
  }

  private Value(String value) {
    this.value = value;
  }

  public String asString() {
    return value;
  }

  public long asInt() {
    return asInt(MushErrors.MUST_BE_INTEGER);
  }

  public long asInt(String errorMessage) {
    if (intCache == null) {
      try {
        intCache = Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        throw new MushValueException(errorMessage);
      }
    }
    return intCache;
  }

  public double asDouble() {
    return asDouble(MushErrors.MUST_BE_NUMBER);
  }

  public double asDouble(String errorMessage) {
    if (doubleCache == null) {
      try {
        doubleCache = Double.parseDouble(value.trim());
      } catch (NumberFormatException e) {
        throw new MushValueException(errorMessage);
      }
    }
    return doubleCache;
  }

  public DbRef asDbRef() {
    return asDbRef(MushErrors.NOT_A_DBREF);
  }

  public DbRef asDbRef(String errorMessage) {
    if (dbRefCache == null) {
      String trimmed = value.trim();
      if (trimmed.length() < 2 || trimmed.charAt(0) != '#') {
        throw new MushValueException(errorMessage);
      }
      try {
        dbRefCache = DbRef.of(Integer.parseInt(trimmed.substring(1)));
      } catch (NumberFormatException e) {
        throw new MushValueException(errorMessage);
      }
    }
    return dbRefCache;
  }

  /**
   * Matches {@code functions.c}'s {@code fval()}: format with 6 decimal places, strip
   * trailing fractional zeros (and a then-dangling '.'), and normalize "-0" to "0". Does
   * not replicate {@code fp_check_weird()}'s bit-level denormal handling -- only the
   * directly observable NaN/Infinity and "-0" cases.
   */
  private static String formatDouble(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return value < 0 ? "-Inf" : "Inf";
    }

    String formatted = String.format("%.6f", value);
    if (formatted.charAt(formatted.length() - 1) == '0') {
      int end = formatted.length();
      while (end > 0 && formatted.charAt(end - 1) == '0') {
        end--;
      }
      if (end > 0 && formatted.charAt(end - 1) == '.') {
        end--;
      }
      formatted = formatted.substring(0, end);
    }
    if (formatted.equals("-0")) {
      formatted = "0";
    }
    return formatted;
  }

  @Override
  public String toString() {
    return value;
  }

}
