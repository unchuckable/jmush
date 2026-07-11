package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;

/** A one-argument {@code @MushFunction}-annotated method's natural shape. */
public interface MushFunction1 {
  Value apply(ExecutionContext context, Value a);
}
