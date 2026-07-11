package com.github.unchuckable.jmush.mushcode.functions.builtin;

import java.util.List;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;

public class MathFunctions {

  @MushFunction(name = "add", minArgs = 2)
  public static Value add(ExecutionContext ctx, List<Value> args) {
    double sum = args.get(0).aton();
    for (int i = 1; i < args.size(); i++) {
      sum += args.get(i).aton();
    }
    return Value.ofDouble(sum);
  }

  @MushFunction(name = "sub")
  public static Value sub(ExecutionContext ctx, Value a, Value b) {
    return Value.ofDouble(a.aton() - b.aton());
  }

  @MushFunction(name = "abs")
  public static Value abs(ExecutionContext ctx, Value a) {
    return Value.ofDouble(Math.abs(a.aton()));
  }
}
