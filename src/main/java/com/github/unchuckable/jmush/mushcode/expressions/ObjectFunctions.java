package com.github.unchuckable.jmush.mushcode.expressions;

import java.util.List;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;

public class ObjectFunctions {
  
  public static Value getCallerName( ExecutionContext ctx, List<Value> params ) {
    return Value.of( ctx.getCaller().getName() );
  }
  
  public static Value getCallerDbRef( ExecutionContext ctx, List<Value> params ) {
    return Value.of( ctx.getCaller().getDbRef() );
  }
}
