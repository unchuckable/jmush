package com.github.unchuckable.jmush.mushcode.functions;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Expression;
import com.github.unchuckable.jmush.mushcode.Value;
import java.util.List;

/**
 * The uniform interface the parser dispatches to. Parameters are unevaluated {@link Expression}s
 * rather than {@link Value}s -- most handlers (built via {@link FunctionRegistry} from
 * {@code @MushFunction}-annotated methods) evaluate all of them eagerly before delegating to the
 * annotated method, but a handler is free to evaluate selectively or not at all (functions.c's
 * {@code FN_NO_EVAL}, e.g. {@code switch()} only evaluating its matching branch) -- see {@code
 * functions/builtin/ControlFunctions}.
 */
public interface MushFunctionHandler {

  Value execute(ExecutionContext context, List<Expression> parameters);
}
