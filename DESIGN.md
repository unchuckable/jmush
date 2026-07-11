# jmush: Design Document & Implementation Plan

## Context

The user runs a TinyMUSH 3.0-p4-based server (with local "Denver" softcode mods, see
`denver-functions.txt`) that has been live for 27 years, holding ~30,000 objects. It carries a
large softcode base including SQL integration. The goal is a from-scratch Java
re-implementation (`jmush`) that is behaviorally compatible enough that the existing softcode
base keeps working unmodified, while giving the maintainer a modern codebase to fix and extend.
The client-visible experience — including the ability to `@restart` the server without
dropping existing player connections — must be preserved exactly.

Today `jmush` is an early prototype: a mushcode `Expression`/`ExecutionContext`/`Value`
parser-evaluator core and a bare `MushObject`/`Flag`/`Power` model. There is no networking, no
persistence, no command dispatch, and no server process at all yet.

**Verdict: this is a promising, well-scoped rewrite.** Java is a strong fit for the parser,
object model, command dispatch, and SQL layers. Restart-without-disconnecting is solved cleanly
by splitting responsibility between a thin always-up relay process and a freely-restartable
backend (see Decisions) — no low-level fd tricks required. The real cost is the long tail of
~230 commands and ~300 built-in functions (67.5K lines of C in `../tinymush`), many with
undocumented, history-encrusted edge-case behavior that the live softcode silently depends on.
That tail is de-risked by treating the running C server as a compatibility oracle and
differential-testing against it continuously, rather than by reading `eval.c` and hoping.

## Key facts from the reference source (`../tinymush`, 3.0-p4 baseline)

- **Restart mechanism**: `@restart` / `SIGUSR1` → `predicates.c:do_restart()` → dumps db,
  closes logs, shuts down SQL, kills the DNS "slave" helper process, calls
  `db.c:dump_restart_db()` which writes a small `restart.db` recording every connected
  `DESC`'s raw socket file descriptor number plus session metadata, without closing those
  sockets — then calls `execl()` on the same binary. Because the sockets aren't `FD_CLOEXEC`,
  they survive the `exec()`; the new process reattaches `DESC`s to the same fd numbers. Players
  never see a TCP disconnect. (We are not porting this mechanism literally — see Decisions.)
- **Networking**: single-threaded `select()` loop in `bsd.c` (`shovechars`), inline telnet IAC
  handling, `netcommon.c` for implementation-independent parts. A forked `slave` process
  handles blocking reverse-DNS/ident lookups so the main loop never blocks on them.
- **Database**: plain-text flatfile, one object per record, versioned via `F_*`/`V_*` bitflags
  for cross-format import (`db_rw.c`, `dbconvert.c`). TinyMUSH optionally backs attributes with
  GDBM instead of storing them inline — we are not adopting this (see Decisions).
- **SQL integration**: tiny backend-agnostic API (`sql_init`/`sql_query`/`sql_shutdown`) behind
  a compile-time MySQL or mSQL backend, exposed to softcode as a function.
- **Scale of the compatibility surface**: ~231 top-level commands (`command.c`'s
  `command_table[]`), ~300 built-in softcode functions across `functions.c` (6.5K lines) and
  `funceval.c` (5K lines).
- **Codebase variant**: production is 3.0-p4 plus extra local functions not present in
  `../tinymush` (Denver mods and possibly others) — to be identified and reimplemented later;
  `../tinymush` is otherwise a valid compatibility oracle for the base engine.

## Decisions

- **Java 21 LTS.** No reason to stay on Java 8 for a 2026 rewrite. Gives virtual threads (a
  natural fit for per-connection I/O), records, and pattern matching.

- **Restart = a thin, always-up relay process in front of a restartable backend process.**
  The relay is the only thing holding client TCP sockets: it accepts connections and forwards
  bytes to whichever backend instance is currently active over a local channel (loopback TCP or
  a Unix domain socket), buffering briefly across a backend restart. The backend (parser,
  object model, command dispatch, persistence, SQL) is a plain JVM process with no special
  exec/fd tricks. `@restart` becomes: backend flushes/dumps state and exits cleanly, relay
  detects the disconnect, waits for/launches the new backend, resumes forwarding — the client's
  TCP connection to the relay never drops. This avoids FFM/native syscalls entirely and mirrors
  the role TinyMUSH's own optional `concentrator` process already plays.

- **Single game-logic thread for all game-state mutation.** Mushcode's semantics implicitly
  assume the original single-threaded execution model (a command/queued action runs to
  completion before another interleaves). Rather than fight this, keep one dedicated thread
  that drains a queue of parsed commands/actions and executes them one at a time; virtual
  threads handle per-connection I/O and hand work off to that queue via a thread-safe
  `BlockingQueue`. Consequence: game-state structures (attribute cache, object graph,
  `$command` index) are only ever touched by the one thread, so plain `HashMap`s are correct
  and sufficient — no `ConcurrentHashMap` needed outside the narrow I/O hand-off boundary.
  Snapshot dumps and (initially) SQL calls run on this same thread, matching the original's
  actual blocking behavior rather than changing timing semantics softcode may depend on.

- **Persistence**: in-memory object graph with periodic full flatfile-style snapshot dump/load
  (object-per-record, like `db_rw.c`) — no GDBM, no embedded KV store, no incremental journal.
  At ~30k objects this is comfortably small for JVM memory and fast to dump/reload in full.
  A journal was considered and rejected: mushcode execution has side effects (SQL calls,
  timers, non-deterministic functions) that aren't safely re-executable on replay, so a
  snapshot-only model avoids replay ambiguity/corruption risk entirely.
  **Persistence sits behind an interface** (`load()`/`save()`/`getObject(dbref)`/etc.) so the
  concrete flatfile-snapshot implementation can later be swapped (e.g. for MapDB/LMDB) without
  touching game logic — cheap to do now, since the future need is already anticipated.

- **Compatibility oracle**: a differential-testing harness running the same mushcode
  snippets/command sequences against both the compiled `../tinymush` binary and `jmush`,
  diffing observable output. First-class, early deliverable — reading `eval.c`/`functions.c`
  alone will not surface the undocumented quirks 27 years of live softcode depends on.

- **Attribute compilation cache**: compiled `Expression` trees (and later, compiled Lua
  closures) cached per `(dbref, attribute name, version)`, invalidated on attribute write —
  avoids re-parsing mushcode on every trigger/command invocation. This is foundational
  infrastructure, not an optimization bolted on later, since it also anchors:
  - **`$command:` pattern index** — a per-object cache of compiled matchers for softcode-defined
    commands, built on attribute write, replacing the classic MUSH hot-path cost of re-scanning
    and re-matching raw `$pattern:` attribute text against every typed command.
  - **Built-in command lookup** as a hash map/trie (not linear scan) over the ~230 commands.
  - **Attribute invocation counters** — a `long` counter alongside each cache entry, incremented
    whenever that compiled form executes (via `u()`-call, `$command` match, or plain evaluation).
    Persisted cumulatively across restarts (piggybacking on the snapshot format), this gives
    real usage data — not just a static grep of softcode source — for deciding which
    frequently-invoked softcode attributes are worth promoting to native Java "intrinsic"
    functions. Feeds a future `@stats`-style wizard command and the future status/metrics page.

- **`Value` design**: stays fundamentally one canonical string (matching the C reference, where
  every function call parses string input and formats string output — there is no persistent
  typed value between calls in the real implementation). Construction only via static factories
  (`Value.of(String)`, `Value.ofInt(long)`, `Value.ofDouble(double)`, `Value.ofDbRef(DbRef)`);
  consumption only via accessors (`asString()`, `asInt()`, `asDouble()`, `asDbRef()`), never a
  public constructor or exposed field. Numeric/dbref accessors are parse-on-demand and memoized
  (safe since `Value` is immutable); factories pre-populate both the canonical string and the
  matching cached field to skip a redundant round-trip when a function already knows its native
  result type. Because everything routes through this factory/accessor surface, the internal
  representation (single class vs. a future sealed-type union) is fully swappable later without
  touching any of the ~300 function implementations — no need to over-decide this now.
  `asInt()`/`asDouble()`/`asDbRef()` each have a zero-arg form (using a sensible default
  `#-1 ...` message) and an overload taking a custom error message, thrown as a
  `MushValueException` and caught/converted to the returned `Value` by the same wrapper that
  already handles arg-count validation — needed because some functions return bespoke error
  text rather than the generic message, and exact error-string text is part of the
  compatibility surface. Error message strings that recur across many functions (e.g. the
  generic `#-1 ARGUMENT MUST BE INTEGER`) are collected as named constants in one place (a
  small `MushErrors` holder), so there's exactly one spot to get each shared message right and
  verify against the oracle — not applied to genuinely one-off, function-specific messages.
  `MushValueException` disables stack-trace capture (`super(message, null, false, false)`)
  since it signals an expected mushcode-level error condition, not a Java-level bug — nothing
  ever inspects its stack trace, and skipping `fillInStackTrace()` removes the one part of
  Java exceptions that's actually expensive, leaving throw/catch here close to as cheap as a
  plain `return`.

- **Built-in functions as annotated, self-registering providers.** A `@MushFunction(name=...,
  minArgs=..., maxArgs=...)` annotation on static methods in per-category provider classes
  (`StringFunctions`, `MathFunctions`, `ObjectFunctions`, ...), mirroring `functions.c`'s
  `FUNCTION()` macro table. A small `FunctionRegistry` reflects over an explicit, hand-maintained
  list of provider classes (not a full classpath scan — no new scanning-library dependency) and
  builds the `Map<String, MushFunction>` the parser already expects, wrapping each function with
  generic arg-count validation and `MushValueException` → mushcode-style `#-1 ...` error
  conversion, so individual function bodies stay free of that boilerplate. Adding function #301
  later means writing one annotated method — no central registration list to edit.
  Annotated methods may be written with **distinct, named `Value` parameters** instead of the
  raw `List<Value>` (e.g. `add(ExecutionContext ctx, Value a, Value b)`), for readability. A
  small family of arity-specific functional interfaces (`MushFunction1`, `MushFunction2`, ...,
  falling back to the list-based `MushFunction` for genuinely variadic functions) is adapted
  to the uniform interface the parser expects via `MethodHandle`/`LambdaMetafactory`-generated
  wrappers built once at registry-build time — not raw `Method.invoke` per call — so the
  natural-parameter style costs nothing at runtime (equivalent to a hand-written direct call
  once JIT-warmed, the same mechanism the JVM already uses for ordinary lambdas/method
  references). The adapter only unpacks positional `Value` arguments; numeric parsing and
  `MushValueException` handling stay inside the function body, keeping error semantics in one
  place.

- **No full application framework (Quarkus/Spring) for the core engine.** The game engine is a
  long-lived, stateful, connection-oriented simulation with a bespoke single-game-thread
  execution model — the opposite of what reactive/microservice frameworks optimize for, and
  telnet handling isn't something such a framework provides anyway. Keep the core as plain Java
  with minimal dependencies (matching the existing minimal `pom.xml`). If a lightweight embedded
  HTTP listener is needed later (REST layer, metrics, status page — see Future considerations),
  reach for something narrow (`com.sun.net.httpserver.HttpServer` or Javalin), not a full
  framework — Quarkus would only pay for itself narrowly, for OpenAPI-doc auto-generation, if
  that specific feature is ever built.

## Implementation checklist

### Phase 0 — Risk spikes (must precede all else)
- [ ] Relay/backend restart spike — accept a client connection, forward to a backend over
      loopback TCP, kill and relaunch the backend, confirm the client TCP connection never
      drops and output resumes cleanly.
- [ ] Compatibility oracle setup — get `../tinymush` building and runnable locally, build a
      driver that sends raw command/mushcode input over telnet and captures output.
- [ ] `[...]` inline-eval + full substitution set gap check — extend `MushcodeParser` to
      handle `[...]` forced evaluation and the full `%`-substitution set (`%q0-9`, `%v`, `%!`,
      `%l`, `%c`, `%s`, etc.), validated against the oracle.

### Phase 1a — Evaluator core + object-independent functions
- [ ] Extend `MushcodeParser`/`Expression` to full `eval.c` semantics (substitutions, `[...]`,
      `%q0-9` registers, iteration state).
- [ ] Add `Value.asInt()`/`asDouble()`/`asDbRef()` (each with a default and a
      custom-error-message overload, throwing `MushValueException`) and `ofInt`/`ofDouble`/
      `ofDbRef` factories, matching TinyMUSH's exact numeric formatting/error-string
      conventions. Collect frequently-recurring error strings as named constants
      (`MushErrors`).
- [ ] Design ANSI-aware string handling (visible-length-aware `STRLEN`/`MID`/etc.) as part of
      the `Value`/evaluator core.
- [ ] Build the attribute-compilation cache + invocation counters.
- [ ] Stand up the `@MushFunction` annotation + `FunctionRegistry`, including the
      arity-specific functional interfaces (`MushFunction1`, `MushFunction2`, ...) and
      `MethodHandle`/`LambdaMetafactory`-based adapter generation for distinct-parameter
      function signatures.
- [ ] Port string/math/list functions that don't touch the object graph, each verified against
      the oracle.

### Phase 2 — Object model & persistence
- [ ] Extend `MushObject`/`Flag`/`Power` into a full object graph: rooms/exits/players,
      attribute inheritance, locks (`boolexp.c` equivalent) — check `db.h`/`object.c` for exact
      structure.
- [ ] Persistence interface + in-memory graph + periodic flatfile-style snapshot dump/load,
      close enough to `db_rw.c`'s format to import the live production database if required.
- [ ] Confirm/handle live database text encoding (likely Latin-1 or 8-bit ASCII, not UTF-8).
- [ ] Invocation-counter persistence riding along with the snapshot.

### Phase 1b — Object-dependent functions
- [ ] Port remaining built-in functions that need the object graph (`LOC`, `CONTENTS`, `EXITS`,
      `LATTR`, lock/permission-checking functions, etc.), verified against the oracle.

### Phase 3 — Command dispatch & networking
- [ ] Command table modeled on `command.c`'s `CMDENT`, dispatched via hash map/trie.
- [ ] `$command:` pattern index built on the attribute-compilation cache.
- [ ] Backend connection handling (virtual-thread-per-connection) replacing `bsd.c`'s
      `select()` loop; connect screens/text file serving (`file_c.c` equivalent).
- [ ] Harden the Phase 0 relay spike into the real always-up front door (telnet IAC handling,
      site bans, output buffering across backend restarts); wire `@restart` through it.
- [ ] Determine and implement telnet protocol extensions actually needed (MCCP/MXP/GMCP/NAWS/
      MSSP) based on real client usage.
- [ ] Design and implement queued/scheduled command mechanism (`@wait`/semaphores, `cque.c`
      equivalent) feeding back into the single game-logic thread, preserving TinyMUSH's
      queue-draining order guarantees.

### Phase 4 — SQL integration
- [ ] JDBC-backed `@sql`/softcode SQL functions replacing `db_mysql.c`/`db_msql.c`.

### Phase 5 — Local mods & cutover
- [ ] Reimplement extra local functions beyond vanilla 3.0-p4 (Denver mods + any others found,
      ideally identified via a real export of the live softcode).
- [ ] Shadow-run against production traffic before cutover.
- [ ] Define and execute cutover plan (acceptable downtime, rollback plan keeping the C
      server's data directory untouched and ready to relaunch).
- [ ] Migrate the live database.

## Future considerations (not phases — revisit only after the core above is solid)

- **Optional Lua scripting** via sandboxed LuaJ: omit `io`/`os`/`luajava` libraries from the Lua
  globals table (standard Lua sandboxing pattern — no filesystem/OS/Java-reflection access), add
  an instruction-count debug hook for CPU budgeting (mirroring TinyMUSH's own function-invocation
  limits). Stored in attributes via an `AF_LUA`-style flag, reusing the Phase 1a attribute
  cache (same key, different compiled-value type). A host-function bridge exposes game
  operations to Lua through the same permission checks `MushFunction`s already enforce.
- **REST/OpenAPI layer**: endpoints defined as stored mushcode expressions (same mechanism as
  `$command:`, different trigger source), executed via the same evaluator/cache, output
  returned as the HTTP response instead of written to a connection buffer. Session key ≈
  authenticated caller identity, analogous to a connection's `DESC`. Must enqueue onto the same
  single game-logic thread as any other command (no concurrent execution) — the HTTP handler
  runs on its own thread but awaits the queued unit of work's result. Needs an explicit decision
  on whether API-triggered expressions may cause side effects or should be effectively read-only.
- **Prometheus metrics + HTML status page**: via Micrometer (`micrometer-registry-prometheus`),
  exposing aggregate operational metrics (connections, commands/sec, latency, memory, object
  count, cache hit rate) — deliberately *not* one Prometheus series per attribute, since that's a
  cardinality explosion; per-attribute invocation hotspot data belongs on the HTML status page /
  `@stats` command instead. Shares one small embedded internal HTTP listener (bound to an
  internal interface, not the player-facing telnet port) with the REST layer above — one
  component gaining `/metrics`, `/status`, and API routes over time, not three separate builds.

## Open questions (revisit before/at the start of the relevant phase)

- Can you get a copy or representative export of the live softcode database? It's the actual
  compatibility spec — it lets Phase 1/1b function-porting be prioritized by real usage instead
  of guesswork, and is the most reliable way to find local-mod functions beyond
  `denver-functions.txt`.
- Is importing/migrating the live production database a hard requirement, or is a fresh start
  acceptable? Affects how strict Phase 2's flatfile-format compatibility needs to be.
- Deployment environment constraints (OS, existing process-supervision tooling) that should
  shape how the relay process is launched/supervised.
- **ANSI color codes**: embedded ANSI escapes in output/attributes (see `denver-functions.txt`'s
  "ANSI fix") need visible-length-aware handling in string functions (`STRLEN`/`MID`/etc.).
- **Telnet protocol extensions actually in use** by real player clients (MCCP, MXP, GMCP, NAWS,
  MSSP) — determines how much protocol surface the Phase 3 relay actually needs versus plain
  byte passthrough.
- **Queued/scheduled command semantics** (`@wait`, semaphores, `cque.c`) — needs an explicit
  design for how delayed actions re-enter the single game-logic thread while preserving
  TinyMUSH's queue-draining order guarantees, which softcode may depend on.
- **Attribute/database text encoding** of the live flatfile (likely Latin-1 or 8-bit
  ASCII-with-high-bit, not UTF-8) — getting this wrong silently corrupts imported attribute text.
- **Cutover operational expectations**: acceptable downtime for the final switch, and the
  rollback plan (e.g. keep the C server's data directory untouched and ready to relaunch) if
  something breaks post-cutover.

## Verification approach

- Unit tests per ported function/command against known input/output pairs (start from
  `denver-functions.txt` and vanilla `functions.c` doc comments).
- The oracle-diff harness (Phase 0.2) run continuously in CI alongside `mvn test`, comparing
  `jmush` output to the compiled `../tinymush` for a growing corpus of real softcode snippets.
- End-to-end telnet session tests exercising connect/build/restart-without-disconnect once
  Phase 3 lands — the one feature with an explicit "must look identical" requirement, so it
  needs a real telnet-client-style test, not just unit coverage.
