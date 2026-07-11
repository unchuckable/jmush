package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;
import java.util.List;

/**
 * The uniform, arity-erased interface the parser dispatches to; adapters from the arity-specific
 * {@code @MushFunction}-annotated methods target this.
 */
public interface MushFunctionHandler {

  Value execute(ExecutionContext context, List<Value> parameters);
}
