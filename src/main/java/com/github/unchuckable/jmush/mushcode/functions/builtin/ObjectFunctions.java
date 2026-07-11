package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import java.util.List;

public class ObjectFunctions {

  public static Value getCallerName(ExecutionContext ctx, List<Expression> params) {
    return Value.of(ctx.getCaller().getName());
  }

  public static Value getCallerDbRef(ExecutionContext ctx, List<Expression> params) {
    return Value.of(ctx.getCaller().getDbRefString());
  }
}
