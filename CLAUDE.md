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
- `ExecutionContext` — per-evaluation state: the calling `MushObject` and a small register array (`%q0`-`%q9`).
- `EvalFlags` — immutable, parse-time flags modeling the subset of `eval.c`'s `EV_*` constants that are structural rather than per-evaluation: `functionCheck` (`EV_FCHECK`, whether `(...)` is parsed as a function call), `strip` (`EV_STRIP`, whether a `{...}` group's braces are removed), `compressSpaces` (absence of `EV_NO_COMPRESS`). Threaded through `parse(String, EvalFlags)`; `parse(String)` defaults to top-level command flags. `{...}` keeps its braces and still evaluates substitutions inside by default — only function-call parsing is suppressed inside a brace group (matches real TinyMUSH; verified against the oracle).
- `Value` — immutable wrapper around mushcode's canonical string values, and also implements `Expression` directly (`evaluateExpression` returns itself; `isConstant()` is `true`) since a `Value` already *is* a constant expression — there's no separate literal-wrapper node type. Constructed only via `of`/`ofInt`/`ofDouble`/`ofDbRef`; consumed via `asString`/`asInt`/`asDouble`/`asDbRef` (each with a throwing zero-arg form using a default `#-1 ...` message, and a custom-message overload) plus `aton()`, a separate *non-throwing* `atof`-style lenient parser matching `functions.c`'s `aton()` macro (many arithmetic functions coerce garbage input to `0` rather than erroring — don't conflate this with the strict `asDouble()`; also recognizes the `NaN`/`Infinity`/hex-float leading forms C99 `strtod` does, oracle-verified). Numeric/dbref parses are memoized. `ofDouble`'s string formatting exactly replicates `fval()` (`%.6f`, strip trailing fractional zeros, normalize `-0`). `isTruthy()` is a separate *third* boolean notion, matching `functions.c`'s `xlate()` (used by `ifelse()`): a non-numeric non-empty string is truthy here (unlike `atoi()` truncation, which `not()`/`and()`/`or()` use and treats it as falsy) — don't conflate the three.
- `MushValueException` — thrown by the strict `Value` accessors on parse failure; caught by `FunctionRegistry`'s wrapper and converted to a `#-1 ...` `Value` rather than propagating as a Java exception.
- `MushErrors` — named constants for `#-1 ...` messages shared across functions (currently placeholders pending oracle verification against a real strict-validating function — see the class javadoc).
- `DbRef` — a dbref (`#123`-style object reference number, matching the C `dbref` `int` typedef).
- `expressions/` package holds `Expression` tree-node implementations only:
  - `ConcatExpression` — sequence of sub-expressions joined at evaluation time; tracks whether all children are constant so results can be folded.
  - `FunctionExpression` — a parsed `functionname(args...)` call whose name was resolved *statically*, at parse time; holds a `functions.MushFunctionHandler` and its unevaluated argument `Expression`s (the handler itself decides whether/when to evaluate each — see `MushFunctionHandler` below).
  - `DynamicFunctionExpression` — a function call whose name depends on a substitution (e.g. `%q0(1,2)` where the `%q0` register holds `"add"`), resolved at evaluation time; see the "Dynamic function names" note below.
  - `ContextExpressions` — enum of built-in expressions that read from `ExecutionContext` (e.g. `%#` enactor dbref, `%n` invoker name).
  - `RegisterExpression` — `%q0`-`%q9` register lookup.
  - `UppercaseFirstExpression` — wraps another expression to apply the uppercase-substitution-letter convention (`%N` vs `%n`, etc.).
- `functions/` package holds the built-in-function *registration machinery* (not the tree-node types above, and not the function bodies themselves):
  - `MushFunctionHandler` — the uniform functional interface `(ExecutionContext, List<Expression>) -> Value` that the parser/`FunctionExpression`/`DynamicFunctionExpression` dispatch to. Parameters are unevaluated, so a handler can choose to evaluate all of them eagerly (the common case), only some of them, or none — mirroring `functions.c`'s `FN_NO_EVAL` functions (e.g. `switch()`, which only evaluates its matching branch; see `functions/builtin/ControlFunctions`).
  - `@MushFunction` — annotation marking a static provider method as a built-in function (`name`, `minArgs`, `maxArgs` — consulted only for the variadic `List<Value>` and `lazy` shapes; for fixed-arity `Value...` methods the arity is derived from the parameter count, not restated). By default an annotated method takes already-evaluated `Value`s, not `Expression`s — `FunctionRegistry` evaluates every argument eagerly before delegating to it, so most function bodies never need to think about laziness at all. Set `lazy = true` for a method that needs raw, unevaluated access instead (fixed signature `(ExecutionContext, List<Expression>)`, mirroring `functions.c`'s `FN_NO_EVAL` — e.g. `switch()`, which must only evaluate its matching branch; see `functions/builtin/ControlFunctions`); `FunctionRegistry` registers it as-is, with no evaluation wrapper.
  - `MushFunction1`/`MushFunction2` — natural-parameter shapes (`(ExecutionContext, Value[, Value])`) for fixed-arity annotated methods, for readability over the raw `List<Value>` form.
  - `FunctionRegistry` — reflects over an explicit, hand-maintained list of provider classes (`functions/builtin/*`), and for each `@MushFunction` method builds a `MethodHandle`/`LambdaMetafactory`-based adapter (not per-call `Method.invoke`). Non-lazy methods are adapted into an internal `Value`-based `EagerFunctionHandler`, then wrapped in a `MushFunctionHandler` that evaluates all `Expression` arguments before delegating; `lazy` methods are adapted straight to `MushFunctionHandler`, no wrapper. Generic arg-count validation and `MushValueException` → `#-1 ...` conversion are applied uniformly to both afterward. `FunctionRegistry.build()` returns a `FunctionRegistry` instance (not a raw `Map`) with a `getFunction(String)` lookup — `MushcodeParser` holds a `FunctionRegistry` reference rather than the map directly, and `DynamicFunctionExpression` (below) depends on `FunctionRegistry` directly too, since name resolution is all it needs (unlike the static path, argument parsing already happened in the parser before construction — see its class javadoc). `FunctionRegistry.of(Map)` wraps an arbitrary pre-built map instead, bypassing reflection entirely — for tests that want a stub handler.
  - `functions/builtin/` holds the provider classes with the actual function bodies, one class per category mirroring `functions.c` (e.g. `MathFunctions`: `add`/`sub`/`abs`; `RegisterFunctions`: `setq`/`setr`; `ControlFunctions`: `switch`/`ifelse`, `lazy = true`; `ObjectFunctions`: caller-identity stubs predating the registry, not yet migrated to `@MushFunction`).

The parser (`MushcodeParser.parse`) is a single-pass character scanner handling: `\` escapes, `{...}` literal grouping (flag-dependent, see `EvalFlags` above), `[...]` forced evaluation (function-checking always forced on inside, regardless of ambient flags), `(...)` function calls (with recursive comma-split parameter parsing via `getParameters`), `%` substitutions (`%%`, `%r`, `%n`/`%N`, `%b`, `%t`, `%#`, `%q0`-`%q9`, uppercase-first-letter convention for all of them), and space compression (leading/trailing spaces fully removed, interior runs collapse to one — matches `eval.c`'s `at_space` semantics exactly, including the trailing-space-deletion step). Nested delimiter matching (e.g. finding the matching `)` while skipping over nested `(`, `[`, `{`) is delegated to `StringUtils.findIndexOf*`, which understands `\` and `%` escapes and the `STD_NESTINGS` map.

**Dynamic function names.** Real TinyMUSH resolves a function call's name from whatever text has landed in its *output* buffer by the time it hits `(` (`eval.c`'s `oldp`/`hashfind` mechanism) — so a substitution can supply the name, e.g. `[setq(0,add)]%q0(1,2)` evaluates to `3`. `MushcodeParser` tracks a `nameBoundary` marker (mirroring `oldp`) into its pending-expressions list: everything accumulated since the last successfully-dispatched call is a name candidate. If that candidate is all-constant (the overwhelming common case — plain literal text), it's folded to a string at parse time and resolved exactly as before (the fast path, unchanged). If it depends on a substitution, parsing instead builds a `DynamicFunctionExpression` holding the name sub-expression and the *raw, unparsed* argument text — because whether that text is "arguments" (comma-split) or literal fallback text isn't decidable until the name resolves, which only happens at evaluation time. Known, deliberate divergence from the oracle: when a dynamic name fails to resolve, real TinyMUSH continues scanning with a *contaminated* accumulator (the failed name's text carries forward), so a nested, otherwise-valid call inside the failed call's "arguments" won't fire (e.g. `unknownfunc(add(1,2),3)` stays fully literal); `DynamicFunctionExpression` instead evaluates such nested calls independently. See its class javadoc.

### Game object model

`model/MushObject` is a mutable game object: dbref (`#123` style), name, a set of `Flag`s, a set of `Power`s, and a string attribute map. Builder-style `withX(...)` setters return `this` for chaining alongside conventional `setX`/`getX`. `Flag` enumerates TinyMUSH's single-character object flags (e.g. `WIZARD` = `W`, `DARK` = `D`) with `Flag.forSymbol(char)` for lookup. `Power` enumerates administrative powers with no associated data.

### Compatibility oracle

`src/test/java/.../oracle/` (see `DESIGN.md`'s Phase 0) differential-tests jmush's behavior against a real, locally-built `../tinymush` server: `OracleClient` (raw-byte-faithful, sentinel-marker response framing, telnet), `OracleCli` (interactive probe, `./oracle '<snippet>' '<snippet2>' ...` — batches multiple snippets over one connection), `OracleCorpus` (loads/saves the JSON fixture files), `CompatibilityOracleTest` (JUnit corpus runner), and `OracleFixtureRecorder` (regenerates fixtures from a live oracle).

`CompatibilityOracleTest` does **not** connect to a live oracle — it reads recorded snippet→expected-output pairs from JSON fixture files under `src/test/resources/oracle/corpus/` (one file per test category) straight off the classpath, so `mvn test`/`mvn clean install` run it with no network access and no external server. When a corpus file gains new snippets, or oracle behavior needs re-verification, run `mvn test -Dtest=OracleFixtureRecorder` against a live oracle (same `ORACLE_HOST`/`ORACLE_PORT`/`ORACLE_USER`/`ORACLE_PASS` env vars as before) to (re-)populate the `expected` fields in place, then review the diff and commit the updated fixtures. `OracleFixtureRecorder` is deliberately not named `*Test`/`*Tests`/`*TestCase`, so Surefire's default include globs skip it during normal runs.

Building `../tinymush` on a modern toolchain needs a few patches (current `config.guess`/`config.sub` for the bundled `gdbm-1.8.0`, `-fgnu89-inline`, `-lcrypt`, `sys_errlist`→`strerror`) — see git history on the initial oracle-setup commit if rebuilding from scratch. **Production is not vanilla 3.0-p4** — see `DESIGN.md`'s Decisions/Phase-0-checklist for the 34 local `-D` flags found and the plan once the patched source is available.

### Reference material

`denver-functions.txt` documents non-standard mushcode functions/behavior from a specific TinyMUSH codebase (Denver mods) — useful as a spec reference when implementing new built-in functions or mushcode semantics, not code to be imported directly. `../tinymush` (sibling directory, a locally-built vanilla 3.0-p4 checkout) is the compatibility oracle described above.
