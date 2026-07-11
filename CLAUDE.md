# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

jmush is a Java re-implementation of TinyMUSH (a text-based multiplayer game/MOO server). Development is early-stage: the codebase currently implements the mushcode expression parser/evaluator and a minimal in-memory game object model.

## Commands

- Build and run tests: `mvn clean install`
- Run tests only: `mvn test`
- Run a single test class: `mvn test -Dtest=MushcodeParserTest`
- Run a single test method: `mvn test -Dtest=MushcodeParserTest#testParser`
- Coverage report (JaCoCo): generated at `target/site/jacoco/index.html` as part of `mvn test`/`mvn install`

CI (`.gitlab-ci.yml`) runs `mvn clean install -B`, publishes JUnit XML from surefire/failsafe reports, and converts JaCoCo output to Cobertura format for coverage reporting.

Java target is 1.8 (see `pom.xml`); avoid using newer language features.

## Architecture

### Mushcode parsing and evaluation

`MushcodeParser.parse(String)` (`src/main/java/.../mushcode/MushcodeParser.java`) turns raw mushcode text into a tree of `Expression` objects, which are then evaluated against an `ExecutionContext` to produce a `Value`. This split (parse once, evaluate many times against different contexts) is the core design of the interpreter:

- `Expression` — interface with `evaluateExpression(ExecutionContext)` and `isConstant()`. All parsed nodes implement this.
- `ExecutionContext` — per-evaluation state: the calling `MushObject` and a small register array (`%0`-`%9` style substitutions).
- `Value` — immutable wrapper around mushcode's string-typed values.
- `expressions/` package holds `Expression` implementations:
  - `ConstantExpression` — literal text.
  - `ConcatExpression` — sequence of sub-expressions joined at evaluation time; tracks whether all children are constant so results can be folded.
  - `FunctionExpression` — a parsed `functionname(args...)` call; holds a `MushFunction` and its unevaluated argument `Expression`s, evaluating arguments lazily at call time.
  - `ContextExpressions` — enum of built-in expressions that read from `ExecutionContext` (e.g. `%#` caller dbref substitution).
  - `MushFunction` — functional interface `(ExecutionContext, List<Value>) -> Value` implemented by built-in functions (see `ObjectFunctions` for examples like `getCallerName`/`getCallerDbRef`). `MushcodeParser` is constructed with a `Map<String, MushFunction>` (function name -> implementation) used to resolve `(...)` calls during parsing.

The parser (`MushcodeParser.parse`) is a single-pass character scanner handling: `\` escapes, `{...}` literal grouping, `(...)` function calls (with recursive comma-split parameter parsing via `getParameters`), `%` substitutions (`%%`, `%r`, `%n`, `%b`, `%t`, `%#`), and space compression (repeated spaces collapse to one). Nested delimiter matching (e.g. finding the matching `)` while skipping over nested `(`, `[`, `{`) is delegated to `StringUtils.findIndexOf*`, which understands `\` and `%` escapes and the `STD_NESTINGS` map.

### Game object model

`model/MushObject` is a mutable game object: dbref (`#123` style), name, a set of `Flag`s, a set of `Power`s, and a string attribute map. Builder-style `withX(...)` setters return `this` for chaining alongside conventional `setX`/`getX`. `Flag` enumerates TinyMUSH's single-character object flags (e.g. `WIZARD` = `W`, `DARK` = `D`) with `Flag.forSymbol(char)` for lookup. `Power` enumerates administrative powers with no associated data.

### Reference material

`denver-functions.txt` documents non-standard mushcode functions/behavior from a specific TinyMUSH codebase (Denver mods) — useful as a spec reference when implementing new `MushFunction`s or mushcode semantics, not code to be imported directly.
