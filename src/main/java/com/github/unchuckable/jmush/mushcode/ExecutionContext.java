package com.github.unchuckable.jmush.mushcode;

import com.github.unchuckable.jmush.model.MushObject;
import java.util.ArrayDeque;
import java.util.Deque;

public class ExecutionContext {

  private MushObject caller;

  private Value[] registers = new Value[10];

  /**
   * Active {@code iter()} loop frames, innermost on top -- mirrors eval.c's {@code
   * mudstate.loop_token}/{@code loop_number}, saved/restored around each nested loop call. An empty
   * stack means "not currently in a loop," which is the runtime signal {@code
   * LoopTokenExpression}/{@code LoopIndexExpression}/{@code LoopDepthExpression} (the {@code
   * ##}/{@code #@}/{@code #!} substitutions) use to fall back to literal text.
   */
  private final Deque<LoopFrame> loopStack = new ArrayDeque<>();

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

  public void pushLoop(Value token, long index) {
    loopStack.push(new LoopFrame(token, index));
  }

  public void popLoop() {
    loopStack.pop();
  }

  public LoopFrame peekLoop() {
    return loopStack.peek();
  }

  public int loopDepth() {
    return loopStack.size();
  }

  /**
   * One {@code iter()} frame: the current element ({@code ##}) and its 1-based index ({@code #@}).
   */
  public static final class LoopFrame {
    public final Value token;
    public final long index;

    public LoopFrame(Value token, long index) {
      this.token = token;
      this.index = index;
    }
  }
}
