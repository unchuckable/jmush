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

  /**
   * Oracle-verified, directly observed literal (unlike the three above): genuinely variadic
   * functions declared {@code FN_VARARGS} in {@code functions.c}'s table (e.g. {@code add}/{@code
   * mul}/{@code and}/{@code or}/{@code xor}) enforce their own minimum-argument-count check in the
   * function body rather than via {@code eval.c}'s central {@code nargs}-based dispatch, and all
   * use this exact message.
   */
  public static final String TOO_FEW_ARGUMENTS = "#-1 TOO FEW ARGUMENTS";

  /** Oracle-verified, directly observed literal: {@code div()}/{@code fdiv()} on a zero divisor. */
  public static final String DIVIDE_BY_ZERO = "#-1 DIVIDE BY ZERO";

  /**
   * Oracle-verified, directly observed literal: {@code setq()} with a register outside the
   * supported range (jmush only models the digit registers {@code %q0}-{@code %q9}, matching {@link
   * com.github.unchuckable.jmush.mushcode.expressions.RegisterExpression}; production TinyMUSH also
   * allows letter-named extended registers that jmush has no substitution syntax to read back, so
   * those aren't accepted either).
   */
  public static final String INVALID_GLOBAL_REGISTER = "#-1 INVALID GLOBAL REGISTER";

  /**
   * Best-effort placeholder, not yet oracle-verified: {@code mid()} on a negative {@code start} or
   * {@code len}.
   */
  public static final String OUT_OF_RANGE = "#-1 OUT OF RANGE";

  /**
   * Best-effort placeholder, not yet oracle-verified: a fill/trim/separator character argument
   * (e.g. {@code ljust()}/{@code rjust()}/{@code trim()}) longer than one character.
   */
  public static final String SEPARATOR_MUST_BE_ONE_CHARACTER =
      "#-1 SEPARATOR MUST BE ONE CHARACTER";

  private MushErrors() {
    // static holder, do not instantiate
  }
}
