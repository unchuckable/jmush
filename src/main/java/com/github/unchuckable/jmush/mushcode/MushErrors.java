package com.github.unchuckable.jmush.mushcode;

/**
 * Named constants for {@code #-1 ...} error strings shared across many functions, so there's
 * exactly one spot to get each shared message right and verify against the oracle. Not for
 * genuinely one-off, function-specific error text.
 *
 * <p><b>Caveat:</b> {@link #MUST_BE_INTEGER}, {@link #MUST_BE_NUMBER}, and {@link #NOT_A_DBREF} are
 * best-effort placeholders, not yet oracle-verified. Vanilla 3.0-p4's arithmetic functions (e.g.
 * {@code add()}/{@code sub()} in {@code functions.c}) use {@code aton()} (a plain {@code
 * atoi}/{@code atof}), which silently coerces non-numeric input to {@code 0} rather than erroring
 * -- there is no directly-observed canonical string for strict numeric/dbref validation failures in
 * the reference source. Confirm and replace these once a real ported function exercises strict
 * validation against the oracle.
 */
public final class MushErrors {

  public static final String MUST_BE_INTEGER = "#-1 ARGUMENT MUST BE INTEGER";
  public static final String MUST_BE_NUMBER = "#-1 ARGUMENT MUST BE NUMBER";
  public static final String NOT_A_DBREF = "#-1 NO SUCH OBJECT";

  private MushErrors() {
    // static holder, do not instantiate
  }
}
