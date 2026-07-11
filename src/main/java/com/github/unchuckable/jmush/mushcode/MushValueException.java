package com.github.unchuckable.jmush.mushcode;

/**
 * Signals an expected mushcode-level error condition (e.g. "argument must be an integer"), not a
 * Java-level bug -- callers convert the message directly into the returned {@code #-1 ...}-style
 * {@link Value}. Stack-trace capture is disabled since nothing ever inspects it, keeping
 * throw/catch here close to as cheap as a plain return.
 */
public class MushValueException extends RuntimeException {

  public MushValueException(String message) {
    super(message, null, false, false);
  }
}
