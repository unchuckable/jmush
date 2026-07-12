package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;

/** A three-argument {@code @MushFunction}-annotated method's natural shape. */
public interface MushFunction3 {
  Value apply(ExecutionContext context, Value a, Value b, Value c);
}
