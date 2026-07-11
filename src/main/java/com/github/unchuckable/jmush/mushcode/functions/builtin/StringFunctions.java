package com.github.unchuckable.jmush.mushcode.functions.builtin;

import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.Value;
import com.github.unchuckable.jmush.mushcode.functions.MushFunction;
import java.util.List;

public class StringFunctions {

  @MushFunction(name = "cat")
  public static Value cat(ExecutionContext ctx, List<Value> args) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < args.size(); i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(args.get(i).asString());
    }
    return Value.of(builder.toString());
  }

  // functions.c's strlen() runs the argument through strip_ansi() first -- jmush never produces
  // ANSI escape codes yet (see DESIGN.md's deferred "ANSI-aware string handling" item), so plain
  // String.length() is equivalent for now.
  @MushFunction(name = "strlen")
  public static Value strlen(ExecutionContext ctx, Value a) {
    return Value.ofInt(a.asString().length());
  }
}
