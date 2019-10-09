package com.github.unchuckable.jmush.mushcode.expressions;

import java.util.List;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;

public interface MushFunction {
  
  public Value execute( ExecutionContext context, List<Value> parameters );
  
}
