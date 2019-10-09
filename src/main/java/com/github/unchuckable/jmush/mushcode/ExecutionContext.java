package com.github.unchuckable.jmush.mushcode;

import com.github.unchuckable.jmush.model.MushObject;

public class ExecutionContext {

  private MushObject caller;

  private Value[] registers = new Value[10];

  public void setCaller(MushObject caller) {
    this.caller = caller;
  }

  public ExecutionContext withCaller(MushObject caller) {
    setCaller(caller);
    return this;
  }

  public MushObject getCaller() {
    return caller;
  }

  public Value[] getRegisters() {
    return registers;
  }
}
