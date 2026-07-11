package com.github.unchuckable.jmush.mushcode;

/** An object reference number ({@code dbref} in the C reference, a plain {@code int}). */
public final class DbRef {

  /** {@code NOTHING} in db.h -- the null dbref. */
  public static final DbRef NOTHING = new DbRef(-1);

  private final int number;

  private DbRef(int number) {
    this.number = number;
  }

  public static DbRef of(int number) {
    return new DbRef(number);
  }

  public int getNumber() {
    return number;
  }

  @Override
  public String toString() {
    return "#" + number;
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof DbRef) && ((DbRef) other).number == number;
  }

  @Override
  public int hashCode() {
    return number;
  }
}
