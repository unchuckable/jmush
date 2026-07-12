package com.github.unchuckable.jmush.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.unchuckable.jmush.model.MushObject;
import com.github.unchuckable.jmush.mushcode.ExecutionContext;
import com.github.unchuckable.jmush.mushcode.MushcodeParser;
import com.github.unchuckable.jmush.mushcode.functions.FunctionRegistry;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Differential test: evaluates a corpus of mushcode snippets against jmush, asserting the result
 * matches oracle output recorded from a real TinyMUSH server. The corpus lives as JSON fixture
 * files under {@code src/test/resources/oracle/corpus/} (one per category below) and is read
 * straight off the classpath -- no live oracle connection or network access is needed to run this
 * test. When a corpus file gains new snippets, or oracle behavior needs re-verification, run {@link
 * OracleFixtureRecorder} against a live oracle to (re-)populate the {@code expected} fields, then
 * commit the updated fixtures.
 */
public class CompatibilityOracleTest {

  private static final String CALLER_NAME = "Wizard";
  private static final String CALLER_DBREF = "#1";

  // the parser is stateless (all per-evaluation state lives in ExecutionContext), so one
  // instance serves every snippet -- rebuilding the reflection/LambdaMetafactory registry per
  // dynamic test was pure waste
  private static final MushcodeParser PARSER = new MushcodeParser(FunctionRegistry.build());

  @TestFactory
  Stream<DynamicTest> substitutionsMatchOracle() throws IOException {
    return corpusTests("substitutions.json");
  }

  @TestFactory
  Stream<DynamicTest> variableAttributeAndRegisterSwallowBugMatchOracle() throws IOException {
    return corpusTests("variable-attribute-register-swallow-bug.json");
  }

  @TestFactory
  Stream<DynamicTest> forcedEvalMatchesOracle() throws IOException {
    return corpusTests("forced-eval.json");
  }

  @TestFactory
  Stream<DynamicTest> literalGroupingMatchesOracle() throws IOException {
    return corpusTests("literal-grouping.json");
  }

  @TestFactory
  Stream<DynamicTest> spaceStrippingMatchesOracle() throws IOException {
    return corpusTests("space-stripping.json");
  }

  @TestFactory
  Stream<DynamicTest> mathFunctionsMatchOracle() throws IOException {
    return corpusTests("math-functions.json");
  }

  @TestFactory
  Stream<DynamicTest> comparisonFunctionsMatchOracle() throws IOException {
    return corpusTests("comparison-functions.json");
  }

  @TestFactory
  Stream<DynamicTest> logicFunctionsMatchOracle() throws IOException {
    return corpusTests("logic-functions.json");
  }

  @TestFactory
  Stream<DynamicTest> stringFunctionsMatchOracle() throws IOException {
    return corpusTests("string-functions.json");
  }

  @TestFactory
  Stream<DynamicTest> stringFunctionsEdgeCasesMatchOracle() throws IOException {
    return corpusTests("string-functions-edge-cases.json");
  }

  @TestFactory
  Stream<DynamicTest> switchFunctionMatchesOracle() throws IOException {
    return corpusTests("switch-function.json");
  }

  @TestFactory
  Stream<DynamicTest> dynamicFunctionNamesMatchOracle() throws IOException {
    return corpusTests("dynamic-function-names.json");
  }

  @TestFactory
  Stream<DynamicTest> registerFunctionsMatchOracle() throws IOException {
    return corpusTests("register-functions.json");
  }

  @TestFactory
  Stream<DynamicTest> parserEdgeCasesMatchOracle() throws IOException {
    return corpusTests("parser-edge-cases.json");
  }

  @TestFactory
  Stream<DynamicTest> ifelseFunctionMatchesOracle() throws IOException {
    return corpusTests("ifelse-function.json");
  }

  @TestFactory
  Stream<DynamicTest> iterFunctionMatchesOracle() throws IOException {
    return corpusTests("iter-function.json");
  }

  @TestFactory
  Stream<DynamicTest> wordsFirstRestFunctionsMatchOracle() throws IOException {
    return corpusTests("words-first-rest-function.json");
  }

  @TestFactory
  Stream<DynamicTest> matchExtractFunctionsMatchOracle() throws IOException {
    return corpusTests("match-extract-function.json");
  }

  @TestFactory
  Stream<DynamicTest> beforeAfterIndexFunctionsMatchOracle() throws IOException {
    return corpusTests("before-after-index-function.json");
  }

  private Stream<DynamicTest> corpusTests(String resourceName) throws IOException {
    OracleCorpus.File corpus = OracleCorpus.loadFromClasspath(resourceName);
    return corpus.cases.stream()
        .map(
            c ->
                dynamicTest(
                    "\"" + c.input + "\"",
                    () ->
                        assertEquals(
                            c.expected, evaluate(c.input), () -> "mismatch for: " + c.input)));
  }

  private String evaluate(String mushcode) {
    ExecutionContext ctx =
        new ExecutionContext()
            .withCaller(new MushObject().withName(CALLER_NAME).withDbRefString(CALLER_DBREF));
    return PARSER.parse(mushcode).evaluateExpression(ctx).toString();
  }
}
