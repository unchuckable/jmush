package com.github.unchuckable.jmush.mushcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.github.unchuckable.jmush.model.MushObject;

public class MushcodeParserTest {
  
  @Test
  public void testParser() {
    MushcodeParser parser = new MushcodeParser( Collections.emptyMap() );
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));
    
    // %# and \t replacement
    assertEquals("Hello,\t#1.", parser.parse("Hello,%t%#.").evaluateExpression(ctx).toString());
    
    // space compression
    assertEquals("Hello, vexy", parser.parse("Hello,  vexy").evaluateExpression(ctx).toString());
  }
  
}
