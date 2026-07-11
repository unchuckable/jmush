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
    return Value.of("");
  }

  private RegisterFunctions() {
    // static provider class, do not instantiate
  }
}
