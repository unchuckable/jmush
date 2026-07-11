package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushErrors;
import com.github.unchuckable.jmush.mushcode.MushValueException;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;

public class RegisterFunctions {

  /**
   * Sets the {@code %q<digit>} register and returns "" (side-effect only, oracle-verified). Only
   * the digit registers {@code %q0}-{@code %q9} are modeled -- see {@link
   * MushErrors#INVALID_GLOBAL_REGISTER}.
   */
  @MushFunction(name = "setq")
  public static Value setq(ExecutionContext ctx, Value register, Value value) {
    Value error = setRegister(ctx, register, value);
    return error != null ? error : Value.of("");
  }

  /**
   * Like {@link #setq}, but also returns {@code value} itself rather than "" (oracle-verified:
   * {@code setr(0,add)%q0} -> {@code "addadd"}, vs. {@code setq(0,add)%q0} -> {@code "add"}).
   */
  @MushFunction(name = "setr")
  public static Value setr(ExecutionContext ctx, Value register, Value value) {
    Value error = setRegister(ctx, register, value);
    return error != null ? error : value;
  }

  /** Returns the {@code #-1 INVALID GLOBAL REGISTER} error Value on failure, else null. */
  private static Value setRegister(ExecutionContext ctx, Value register, Value value) {
    int index;
    try {
      index = (int) register.asInt(MushErrors.INVALID_GLOBAL_REGISTER);
    } catch (MushValueException e) {
      return Value.of(MushErrors.INVALID_GLOBAL_REGISTER);
    }
    if (index < 0 || index >= ctx.getRegisters().length) {
      return Value.of(MushErrors.INVALID_GLOBAL_REGISTER);
    }
    ctx.getRegisters()[index] = value;
    return null;
  }

  private RegisterFunctions() {
    // static provider class, do not instantiate
  }
}
