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

  @Test
  public void testInvokerNameSubstitution() {
    MushcodeParser parser = new MushcodeParser(Collections.emptyMap());
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("vexy").withDbRefString("#1"));

    // %n is the invoker's name -- not a newline
    assertEquals("vexy", parser.parse("%n").evaluateExpression(ctx).toString());

    // %N uppercases the first character of the substitution's result
    assertEquals("Vexy", parser.parse("%N").evaluateExpression(ctx).toString());
  }

  @Test
  public void testRegisterSubstitution() {
    MushcodeParser parser = new MushcodeParser(Collections.emptyMap());
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("vexy").withDbRefString("#1"));
    ctx.getRegisters()[0] = Value.of("hello");

    assertEquals("hello", parser.parse("%q0").evaluateExpression(ctx).toString());
    assertEquals("Hello", parser.parse("%Q0").evaluateExpression(ctx).toString());
    // unset register substitutes to empty, not a literal
    assertEquals("", parser.parse("%q1").evaluateExpression(ctx).toString());
  }

  @Test
  public void testForcedEvaluation() {
    MushcodeParser parser = new MushcodeParser(Collections.emptyMap());
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));

    assertEquals("#1", parser.parse("[%#]").evaluateExpression(ctx).toString());
    assertEquals("x#1y", parser.parse("x[%#]y").evaluateExpression(ctx).toString());
    // no closing bracket: the '[' itself is literal, but the rest of the string is still
    // parsed normally (%# still substitutes)
    assertEquals("x[#1", parser.parse("x[%#").evaluateExpression(ctx).toString());
  }

  @Test
  public void testFunctionCallFollowedByTrailingText() {
    MushcodeParser parser = new MushcodeParser(
        Collections.singletonMap("f", (ctx, params) -> Value.of("F(" + params.get(0).asString() + ")")));
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));

    // trailing text after the closing paren must not be swallowed/reparsed
    assertEquals("F(a)REST", parser.parse("f(a)REST").evaluateExpression(ctx).toString());
    assertEquals("F(a)F(b)", parser.parse("f(a)f(b)").evaluateExpression(ctx).toString());
  }

  @Test
  public void testLiteralGrouping() {
    MushcodeParser parser = new MushcodeParser(
        Collections.singletonMap("f", (ctx, params) -> Value.of("F(" + params.get(0).asString() + ")")));
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));

    // {} keeps its braces in the output by default (no EV_STRIP) -- it is not stripped,
    // literal-with-no-braces text as previously implemented
    assertEquals("a{plain text}b", parser.parse("a{plain text}b").evaluateExpression(ctx).toString());

    // %-substitutions still evaluate inside {}
    assertEquals("a{is #1}b", parser.parse("a{is %#}b").evaluateExpression(ctx).toString());

    // but function calls do not evaluate inside {} -- only substitutions
    assertEquals("{f(a)}", parser.parse("{f(a)}").evaluateExpression(ctx).toString());

    // EV_STRIP removes the braces
    assertEquals("plain text", parser.parse("{plain text}", EvalFlags.DEFAULT.withStrip(true))
        .evaluateExpression(ctx).toString());
  }

  @Test
  public void testLeadingAndTrailingSpaceStripping() {
    MushcodeParser parser = new MushcodeParser(Collections.emptyMap());
    ExecutionContext ctx = new ExecutionContext().withCaller(new MushObject().withName("Vexy").withDbRefString("#1"));

    // leading/trailing spaces are fully removed, not merely compressed to one
    assertEquals("a b", parser.parse("a b ").evaluateExpression(ctx).toString());
    assertEquals("a b", parser.parse(" a b").evaluateExpression(ctx).toString());
    assertEquals("a b", parser.parse("a  b").evaluateExpression(ctx).toString());
    assertEquals("", parser.parse("   ").evaluateExpression(ctx).toString());
    // trailing space after a substitution is still stripped
    assertEquals("a#1", parser.parse("a[%#] ").evaluateExpression(ctx).toString());

    // EV_NO_COMPRESS-equivalent (compression disabled): spaces pass through untouched
    EvalFlags noCompress = EvalFlags.DEFAULT.withSpaceCompression(false);
    assertEquals(" a  b ", parser.parse(" a  b ", noCompress).evaluateExpression(ctx).toString());
  }

}
