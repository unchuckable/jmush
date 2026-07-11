package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushErrors;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import java.util.List;

public class MathFunctions {

  // add/mul/and/or/xor are genuinely variadic (FN_VARARGS in functions.c) and enforce their own
  // minimum-argument-count check in the function body rather than via FunctionRegistry's generic
  // arity wrapper -- minArgs is left at the default (0) here so that wrapper never intercepts and
  // produces the wrong message; oracle-verified these all report MushErrors.TOO_FEW_ARGUMENTS
  // (e.g. "add(2)" -> "#-1 TOO FEW ARGUMENTS", not a generic "EXPECTS AT LEAST 2" message).

  @MushFunction(name = "add")
  public static Value add(ExecutionContext ctx, List<Value> args) {
    if (args.size() < 2) {
      return Value.of(MushErrors.TOO_FEW_ARGUMENTS);
    }
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

  @MushFunction(name = "mul")
  public static Value mul(ExecutionContext ctx, List<Value> args) {
    if (args.size() < 2) {
      return Value.of(MushErrors.TOO_FEW_ARGUMENTS);
    }
    double product = args.get(0).aton();
    for (int i = 1; i < args.size(); i++) {
      product *= args.get(i).aton();
    }
    return Value.ofDouble(product);
  }

  @MushFunction(name = "div")
  public static Value div(ExecutionContext ctx, Value a, Value b) {
    long divisor = b.atoi();
    if (divisor == 0) {
      return Value.of(MushErrors.DIVIDE_BY_ZERO);
    }
    return Value.ofInt(a.atoi() / divisor);
  }

  @MushFunction(name = "fdiv")
  public static Value fdiv(ExecutionContext ctx, Value a, Value b) {
    double divisor = b.aton();
    if (divisor == 0) {
      return Value.of(MushErrors.DIVIDE_BY_ZERO);
    }
    return Value.ofDouble(a.aton() / divisor);
  }

  @MushFunction(name = "mod")
  public static Value mod(ExecutionContext ctx, Value a, Value b) {
    long divisor = b.atoi();
    if (divisor == 0) {
      divisor = 1;
    }
    return Value.ofInt(a.atoi() % divisor);
  }

  @MushFunction(name = "eq")
  public static Value eq(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() == b.aton() ? 1 : 0);
  }

  @MushFunction(name = "neq")
  public static Value neq(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() != b.aton() ? 1 : 0);
  }

  @MushFunction(name = "gt")
  public static Value gt(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() > b.aton() ? 1 : 0);
  }

  @MushFunction(name = "gte")
  public static Value gte(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() >= b.aton() ? 1 : 0);
  }

  @MushFunction(name = "lt")
  public static Value lt(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() < b.aton() ? 1 : 0);
  }

  @MushFunction(name = "lte")
  public static Value lte(ExecutionContext ctx, Value a, Value b) {
    return Value.ofInt(a.aton() <= b.aton() ? 1 : 0);
  }

  @MushFunction(name = "and")
  public static Value and(ExecutionContext ctx, List<Value> args) {
    if (args.size() < 2) {
      return Value.of(MushErrors.TOO_FEW_ARGUMENTS);
    }
    for (Value arg : args) {
      if (arg.atoi() == 0) {
        return Value.ofInt(0);
      }
    }
    return Value.ofInt(1);
  }

  @MushFunction(name = "or")
  public static Value or(ExecutionContext ctx, List<Value> args) {
    if (args.size() < 2) {
      return Value.of(MushErrors.TOO_FEW_ARGUMENTS);
    }
    for (Value arg : args) {
      if (arg.atoi() != 0) {
        return Value.ofInt(1);
      }
    }
    return Value.ofInt(0);
  }

  @MushFunction(name = "xor")
  public static Value xor(ExecutionContext ctx, List<Value> args) {
    if (args.size() < 2) {
      return Value.of(MushErrors.TOO_FEW_ARGUMENTS);
    }
    boolean result = args.get(0).atoi() != 0;
    for (int i = 1; i < args.size(); i++) {
      result = result ^ (args.get(i).atoi() != 0);
    }
    return Value.ofInt(result ? 1 : 0);
  }

  @MushFunction(name = "not")
  public static Value not(ExecutionContext ctx, Value a) {
    return Value.ofInt(a.atoi() == 0 ? 1 : 0);
  }
}
