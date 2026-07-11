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
  - **Session reassociation**: surviving the TCP layer isn't enough — the new backend must also
    re-learn *who is logged in* on each connection, or players land back at a login prompt
    (C preserves this via `dump_restart_db`'s per-`DESC` metadata). The relay therefore speaks a
    small **framed protocol** on the local channel — not pure byte passthrough: a per-connection
    header (**session key**, client IP/hostname, connect timestamp) followed by framed data plus
    a few control messages. Control frames are needed because raw passthrough can't express the
    difference between "`@boot` this player → close their client TCP" and "backend restarting →
    hold everyone" — so at minimum: DATA, CLOSE (drop the client), RESTART-PENDING (hold and
    buffer). On `@restart`, the backend writes its per-session state (player dbref, connect
    time, command count — the `restart.db` analogue) keyed by session key; the new backend reads
    it and re-binds each session as the relay reconnects it.
  - **Crash semantics**: if the backend dies *without* writing the session-state file (crash,
    not clean `@restart`), the relay holds the TCP connections and reconnects them to the new
    backend as unauthenticated sessions — players land at the login prompt but never lose the
    TCP connection. Defined behavior, deliberately weaker than clean restart.
  - **Telnet IAC ownership**: the **backend** owns telnet protocol handling (IAC negotiation,
    GOAHEAD prompts) in both modes — it must implement it anyway for its direct-telnet dev port,
    and having exactly one implementation avoids drift. The relay stays telnet-agnostic: it
    frames and forwards opaque bytes. (Only exception if ever needed: MCCP compression, which
    would have to live relay-side since it wraps the client-facing stream — decide if/when
    MCCP turns out to be actually used.)
  - **The relay is optional**: the backend listens on **two ports** — a localhost-bound port
    speaking the relay protocol, and a plain-telnet port for development/testing without the
    relay. No sniffing/auto-detection; tests pick the layer they target. Direct connections
    simply don't survive `@restart`, which is correct (that's the relay's whole job).

- **Single game-logic thread for all game-state mutation.** Mushcode's semantics implicitly
  assume the original single-threaded execution model (a command/queued action runs to
  completion before another interleaves). Rather than fight this, keep one dedicated thread
  that drains a queue of parsed commands/actions and executes them one at a time; virtual
  threads handle per-connection I/O and hand work off to that queue via a thread-safe
  `BlockingQueue`. Consequence: game-state structures (attribute cache, object graph,
  `$command` index) are only ever touched by the one thread, so plain `HashMap`s are correct
  and sufficient — no `ConcurrentHashMap` needed outside the narrow I/O hand-off boundary.
  SQL calls run (initially) on this same thread, matching the original's blocking behavior
  rather than changing timing semantics softcode may depend on — but the SQL interface is
  defined async-capable from the start, since one slow query freezing every player is the
  single biggest availability wart worth being able to fix post-cutover.
  Note on dumps: the C server does **not** block during database dumps — `fork_and_dump`
  (`bsd.c`) forks so a copy-on-write child serializes while the parent keeps playing. The JVM
  can't fork, so jmush starts with **pause-and-dump** (serialize on the game thread; at ~30k
  objects likely a sub-second pause) and *measures* the real pause with production-sized data;
  if noticeable, upgrade to **copy-then-dump** (fast in-memory deep copy on the game thread,
  background thread serializes the copy). The dump goes through the persistence interface so
  that upgrade touches one place.

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
  **Nondeterminism handling is part of the harness from day one**, or it drowns in false
  diffs: a normalization layer (mask timestamps and other legitimately-varying output), a
  fixed-seed/shape-assertion convention for `rand`/`shuffle`/`pickrand`-style functions, and
  identical object-graph construction in both servers for tests of traversal functions.
  **List ordering is a compatibility surface, not an artifact**: the live softcode may depend
  on the order `lcon()`/`lexits()`/`lattr()` return (C's contents/exits are linked lists with
  specific insertion semantics), so jmush replicates C's ordering and the oracle compares
  these outputs exactly, not as sets.

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

- **Evaluator limits are compatibility surface, modeled in the core (Phase 1a), not bolted on.**
  Two families, both of which 27-year-old softcode can silently depend on:
  - **Output buffer truncation**: C truncates everywhere at `LBUF_SIZE` (8000 bytes —
    `alloc.h`, enforced in `eval.c`'s buffer accumulation and function output). Java's
    unbounded `String`s won't replicate this unless deliberate: jmush enforces the limit at
    the same points C does (function output, substitution expansion, concat accumulation),
    centrally (e.g. in `ConcatExpression`/function-return paths), not scattered ad hoc. The
    limit is **configurable, default 8000** — compatibility mode is the only mode until
    cutover succeeds, but raising it later must not require code changes.
  - **Invocation/recursion limits**: `func_invk_lim`/`func_nest_lim` with C's exact error
    strings (`#-1 FUNCTION RECURSION LIMIT EXCEEDED`, `#-1 FUNCTION INVOCATION LIMIT
    EXCEEDED`, per `eval.c`), checked in `FunctionExpression` evaluation. **Scoping matters
    and is per-command, not per-evaluation**: in C these are `mudstate.func_invk_ctr`/
    `func_nest_lev`, reset once per command in `process_command` and shared across every
    nested `u()`/`eval` within that command — a fresh budget per nested evaluation would
    diverge from softcode that relies on hitting/avoiding the limit. Likewise `%q0-9`
    registers are command-global (`mudstate.global_regs`), shared across nested evaluations
    and *snapshotted into queue entries* by `cque.c` for `@wait`-style delayed execution.
    Consequence for the design: one `ExecutionContext` (or a shared command-scope object it
    references) must be threaded through all nested evaluations of a command, with `%q`
    snapshot/restore semantics for queued entries. (The instruction-budget hook planned for
    future Lua is the same idea; mushcode needs it first.)

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
      drops and output resumes cleanly. Includes the framed session-key relay protocol
      (header + DATA/CLOSE/RESTART-PENDING frames) and a minimal session-reassociation
      round-trip, so the "player stays logged in" half of `@restart` is proven, not just the
      TCP half.
- [ ] Compatibility oracle setup — get `../tinymush` building and runnable locally, **run
      under the production `netmush.conf`** (not defaults — config gates evaluator limits,
      space compression, and more), build a driver that sends raw command/mushcode input over
      telnet and captures output. Includes the nondeterminism layer from day one: output
      normalization (timestamps etc.), fixed-seed/shape-assertion conventions for random
      functions, identical object-graph setup on both sides for traversal-order tests.
- [x] **Validate that vanilla 3.0-p4 is actually the spec** — settled, and the answer is no.
      Production's in-game `version` (build #206, 2022-02-20) reports 34 local `-D` flags
      (`ANSI_SUBST_FIX`, `FIX_ANSI`, `STRING_MODS`, `BOOLFIX`, `PEMIT_FIX`, `COMSYS_BUG`,
      `ITERSQ`, `POSALL`, `ANYOF`, `MOVEHOOK`, and 24 more — see git history for the full list)
      that **do not exist anywhere in our vanilla `../tinymush` checkout** — not inert, the
      `#ifdef`-guarded code itself is absent. This confirms real source patches beyond added
      functions, several touching core string/ANSI/evaluator behavior, not just
      `denver-functions.txt`'s documented additions. **We do not have that source yet.** Plan:
      once obtained, diff it against vanilla 3.0-p4 to isolate exactly what changed, then port
      each change deliberately (Phase 5 scope) rather than reverse-engineering from black-box
      behavior probes now. Until then, vanilla `../tinymush` remains the working oracle with
      this caveat attached to every diff result.
- [x] `[...]` inline-eval + full substitution set gap check — done for the context/literal
      subset that's oracle-testable without any functions yet: `[...]` forced evaluation,
      `%n`/`%N` (was wrongly wired to newline; real semantics is invoker name, per
      `eval.c`'s `case 'N'/'n': safe_name(cause,...)`), and the general uppercase-first-letter
      rule (`%N` vs `%n` etc., `eval.c:855`) applied centrally in the parser rather than
      per-substitution. `%q0-9` wired to the existing register array (unit-tested only --
      no `setq()` yet to oracle-diff against). Found and fixed two pre-existing bugs along the
      way: `(`/`{` closing-delimiter handling was off-by-one and failed to advance the parse
      index past the delimiter, corrupting anything parsed after a function call or `{}` group
      (e.g. `f(a)REST` produced garbage); `%r` emitted bare `\r` instead of `\r\n`.
      **Deferred, catalogued gaps** (need infrastructure this spike doesn't have):
      - `%0-9` (command/function args, `cargs` in `eval.c`) is **not** the same thing as
        `%q0-9` (registers, `global_regs`) -- don't conflate them. `%0-9` needs a per-frame
        arg holder pushed by `u()`, which doesn't exist yet, so it's untestable until then.
      - `%#` (enactor/`cause`) and `%!` (executor/`player`) are distinct in C; jmush's single
        `caller` field conflates them (harmless in `think`, where they coincide). Needs an
        executor field once anything other than `think` exists.
      - `%v`/`%=<attr>` (attribute read), `%o/%p/%s/%a` (gender pronouns, need a `SEX`
        attribute), `%l` (location) all need the Phase 2 object/attribute model.
      - `%_` (x-variables) needs a `vars_htab`-equivalent; `%c` (`curr_cmd`) needs Phase 3
        command dispatch; `%|` needs piped-command output tracking.
      - **`{...}` is not literal-with-braces-stripped-and-no-eval, as jmush currently
        implements it.** Oracle probe of `a{literal %# here}b` returned
        `a{literal #1 here}b` -- braces are *kept* in the output and substitutions still
        evaluate inside; only function-call parsing (the `(` handling) is suppressed
        (`eval.c:552-582`: recurses via `exec()` with `EV_FCHECK` cleared). Brace-stripping
        itself is conditional on the caller's `EV_STRIP` flag, which doesn't exist in jmush at
        all -- `{}` needs the eval-flags model (`EV_STRIP`/`EV_FCHECK`/`EV_NO_COMPRESS`/...)
        before it can be fixed correctly, which is real Phase 1a scope ("full `eval.c`
        semantics"), not this gap-check. The already-present-but-dormant `functionViable`
        field in `MushcodeParser` was evidently meant for exactly the FCHECK-suppression half
        of this.

### Phase 1a — Evaluator core + object-independent functions
- [ ] Extend `MushcodeParser`/`Expression` to full `eval.c` semantics (substitutions, `[...]`,
      `%q0-9` registers, iteration state).
      **Progress so far**: added `EvalFlags` (`functionCheck`/`strip`/`compressSpaces`,
      modeling `EV_FCHECK`/`EV_STRIP`/absence-of-`EV_NO_COMPRESS`), threaded through
      `parse()`, replacing the old dead `functionViable` field. Fixed `{...}` to match real
      semantics (braces kept unless `EV_STRIP`, substitutions still evaluate inside, function
      calls suppressed -- oracle-verified) and fixed leading/trailing space handling: spaces
      at the very start/end of a parsed string are fully removed, not merely compressed to
      one (`eval.c:444` inits `at_space=1`; `eval.c:1123-1131` deletes the last written char
      if still `at_space` at the end) -- also oracle-verified, including combined with `[...]`
      substitutions. Also fixed a latent bug found along the way: `lastWasSpace` was only
      ever set `true`, never reset on non-space content, so a space immediately after a
      substitution/bracket was wrongly treated as a compression duplicate (e.g.
      `"a{literal %# here}b"` lost the space before "here"). Not modeled: `EV_FMAND` (needs
      the `Value` error-message system), `EV_NO_LOCATION` (needs `%l`), `EV_EVAL`/`EV_TOP`/
      `EV_NOTRACE`/`EV_STRIP_TS`/`EV_STRIP_LS`/`EV_STRIP_ESC`/`EV_STRIP_AROUND` (narrower
      call-site concerns, not general parse-time semantics, deferred until something needs
      them). Also left unverified: escaped-space interaction with the trailing-strip rule
      (`'a\ '` appeared to lose its escaped trailing space via the oracle, contradicting a
      literal reading of `eval.c`'s escape handling -- plausibly `think`-command-line
      trimming rather than evaluator behavior; not chased further, low real-world impact).
- [x] Add `Value.asInt()`/`asDouble()`/`asDbRef()` (each with a default and a
      custom-error-message overload, throwing `MushValueException`) and `ofInt`/`ofDouble`/
      `ofDbRef` factories, matching TinyMUSH's exact numeric formatting/error-string
      conventions. Collect frequently-recurring error strings as named constants
      (`MushErrors`). Added a minimal `DbRef` value type (none existed). `ofDouble`'s
      canonical-string formatting replicates `functions.c`'s `fval()` exactly (`%.6f`, strip
      trailing fractional zeros and a then-dangling `.`, normalize `-0`->`0`) --
      oracle-verified via `add(x,0)` for several values (`100`, `100.5`, `120`,
      `1.123456789`->`1.123457`, `-0.0000001`->`0`, `0.1+0.2`->`0.3`). Does *not* replicate
      `fp_check_weird()`'s bit-level denormal handling, only the observable NaN/Infinity/-0
      cases. **`MushErrors`'s three messages are unverified placeholders**: vanilla 3.0-p4's
      `add()`/`sub()` use `aton()` (plain `atoi`/`atof`), which silently coerces bad input to
      `0` rather than erroring -- there's no directly-observed canonical `#-1 ARGUMENT MUST
      BE ...`/`#-1 NO SUCH OBJECT`-style string in the reference source for strict
      validation. Confirm/replace once a real ported function actually validates strictly,
      oracle-tested.
- [ ] Design ANSI-aware string handling (visible-length-aware `STRLEN`/`MID`/etc.) as part of
      the `Value`/evaluator core.
- [ ] Enforce the LBUF output-truncation limit (configurable, default 8000 bytes) centrally at
      the same points C enforces it (function output, substitution expansion, concat
      accumulation), verified against the oracle with over-limit softcode.
- [ ] Implement `func_invk_lim`/`func_nest_lim` invocation/recursion limits with C's exact
      error strings — **per-command scope** shared across nested evaluations (see Decisions),
      with `%q0-9` registers likewise command-global and snapshot/restorable for queueing.
- [ ] Port `wild.c` wildcard/regex matching semantics (case-insensitivity rules, `*`-group
      capture into `%0`-`%9`, regexp mode) with oracle tests — used by `switch()`, `match()`,
      `lattr()` patterns, and later by the Phase 3 `$command` index.
- [ ] Configuration layer compatible with `netmush.conf` directives (`conf.c` equivalent) —
      hundreds of `mudconf` options gate behavior softcode depends on (limits, master room,
      zones, space compression, queue/money settings). At minimum: parse the production conf
      file and expose typed access; individual options get honored as their subsystems land.
- [ ] Build the attribute-compilation cache + invocation counters.
- [ ] Stand up the `@MushFunction` annotation + `FunctionRegistry`, including the
      arity-specific functional interfaces (`MushFunction1`, `MushFunction2`, ...) and
      `MethodHandle`/`LambdaMetafactory`-based adapter generation for distinct-parameter
      function signatures.
- [ ] Port string/math/list functions that don't touch the object graph, each verified against
      the oracle.

### Phase 2 — Object model & persistence
- [ ] Extend `MushObject`/`Flag`/`Power` into a full object graph: rooms/exits/players,
      attribute inheritance (`@parent` chains), zones, locks (`boolexp.c` equivalent) — check
      `db.h`/`object.c` for exact structure. Contents/exits lists must replicate C's insertion
      -order semantics (softcode may depend on `lcon()`/`lexits()` ordering).
- [ ] Full attribute model — richer than a name→string map: each attribute is a numbered
      entry carrying **(name/number, owner, `AF_*` flags, text)**, with the fixed built-in
      attribute table (`attrs.h`) plus the user-defined attribute name table (`vattr.c`
      equivalent). Attribute flags change real behavior (`AF_PRIVATE` alters `@parent`
      inheritance; `AF_REGEXP`/`AF_NOPROG` alter `$command` scanning) and owner/flags are
      encoded inline in the flatfile — required for both correct db import and correct
      inheritance/matching.
- [ ] Persistence interface + in-memory graph + snapshot dump/load, close enough to
      `db_rw.c`'s format to import the live production database if required. Dump strategy:
      pause-and-dump on the game thread first, measure the real pause with production-sized
      data, upgrade to copy-then-dump behind the same interface only if noticeable.
- [ ] Live-database import path: the production server likely stores attributes in GDBM, not
      inline — use the C server's own `dbconvert`/unload tooling to flatten to a pure flatfile
      first, so jmush never reads GDBM.
- [ ] Confirm/handle live database text encoding (likely Latin-1 or 8-bit ASCII, not UTF-8).
- [ ] Invocation-counter persistence riding along with the snapshot.

### Milestone 0 — Walking skeleton (minimal connectable demo)
Goal: connect, create a character, log into a single Limbo room, `look`, `say`, quit — built on
the *real* evaluator (Phase 1a) and *real* object model/persistence (Phase 2), not a throwaway
stand-in, so nothing here gets rewritten later. Deliberately narrow on breadth: only the
handful of commands needed for a minimal experience, not the full command table.
- [ ] Minimal backend connection handling on top of the Phase 0 relay/backend split
      (virtual-thread-per-connection feeding the single game-logic thread) — a small subset of
      what full Phase 3 networking will grow into, not a separate throwaway listener.
- [ ] A handful of hardcoded command handlers: `create <name> <password>`, `connect <name>
      <password>`, `look`, `say <message>`, `quit` — using the real `MushObject`/room/player
      model from Phase 2 and the real evaluator from Phase 1a for attribute text (e.g. `@desc`).
- [ ] Outbound fan-out: `notify`-style room broadcast (game thread → per-connection output
      queues), so `say` is visible from other connections — the outbound half of the I/O
      boundary, not just the inbound command path.
- [ ] End-to-end manual test: telnet in, create a character, log in, `look`, `say`, see it from
      a second connection, `quit`.

### Phase 1b — Object-dependent functions
- [ ] Port remaining built-in functions that need the object graph (`LOC`, `CONTENTS`, `EXITS`,
      `LATTR`, lock/permission-checking functions, etc.), verified against the oracle.
- [ ] `@function` user-defined global functions: a mutable, softcode-managed resolution layer
      on top of the built-in `FunctionRegistry` (the registry must not be designed as
      immutable) — softcode-defined functions resolve alongside the ~300 built-ins.

### Phase 3 — Command dispatch & networking (full scope)
- [ ] Command table modeled on `command.c`'s `CMDENT`, dispatched via hash map/trie, growing
      Milestone 0's five hardcoded handlers into the full ~230-command table.
- [ ] Replicate `process_command`'s exact match-precedence order, oracle-tested: prefix
      tokens → HOME → built-in table → exits (`goto`) → master-room exits → enter/leave
      aliases → `$commands` on self/contents/location/inventory → local-master and zone
      checks → master room → `huh`. Softcode that shadows a built-in name or an exit depends
      on this exact order.
- [ ] `$command:` pattern index built on the attribute-compilation cache — including the
      **master-room global scan** and zone-object scopes, not just room contents/exits (huge
      swaths of softcode-defined commands live in the master room and won't fire without it).
- [ ] Timer subsystem (`timer.c` equivalent) on the game-logic thread's scheduler: periodic
      dump trigger, idle-timeout check/booting, `do_dbck` consistency pass, the **`@cron`
      scheduler**, and **`A_STARTUP` attribute execution at boot** — `@cron` and `@startup`
      are confirmed heavily used on the production server, so these are core scope, not
      optional extras.
- [ ] Full backend connection handling replacing `bsd.c`'s `select()` loop, owning all telnet
      IAC negotiation/GOAHEAD (see Decisions: the relay stays telnet-agnostic); connect
      screens/text file serving (`file_c.c` equivalent).
- [ ] Login-flow semantics beyond the connect screen: guest logins (`make_guest`,
      `guest_prefix`, per-site guest bans), create-at-login, registration/badsite handling
      (`netcommon.c`), and indexed help/news file serving (`help.c`).
- [ ] Harden the Milestone 0 relay/backend usage into the real always-up front door (site
      bans, output buffering across backend restarts, CLOSE/RESTART-PENDING control-frame
      handling); wire `@restart` through it.
- [ ] Determine and implement telnet protocol extensions actually needed (MCCP/MXP/GMCP/NAWS/
      MSSP) based on real client usage.
- [ ] Design and implement queued/scheduled command mechanism (`@wait`/semaphores, `cque.c`
      equivalent) feeding back into the single game-logic thread, preserving TinyMUSH's
      queue-draining order guarantees. (Queue state is not preserved across `@restart` — the C
      server's `restart.db` doesn't preserve `cque` either, so this matches.)
- [ ] Comsys/`@channel` system (`comsys.c` equivalent) — has its own persisted state
      (`comsys.db` analogue) riding alongside the object-graph snapshot, not inside it.
- [ ] `@mail` system (`mail.c` equivalent) — likewise its own persisted state alongside the
      snapshot.

### Phase 4 — SQL integration
- [ ] JDBC-backed `@sql`/softcode SQL functions replacing `db_mysql.c`/`db_msql.c` — blocking
      on the game thread initially (matching C's timing behavior), but behind an
      async-capable interface so non-blocking mode is possible post-cutover.

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
- ~~Is vanilla 3.0-p4 actually the spec?~~ **Settled: no** (see Phase 0 checklist). Production's
  34 local `-D` flags gate source patches absent from our vanilla checkout. Locating the
  patched source tree (or its patch set) is now the concrete blocker for closing this gap —
  see Decisions/checklist for the diff-then-port plan once it's found.
- A copy of the production `netmush.conf` is needed early — the oracle must run under it, and
  the Phase 1a config layer parses it.
- Is importing/migrating the live production database a hard requirement, or is a fresh start
  acceptable? Affects how strict Phase 2's flatfile-format compatibility needs to be.
- Deployment environment constraints (OS, existing process-supervision tooling) that should
  shape how the relay process is launched/supervised.
- **ANSI color codes**: embedded ANSI escapes in output/attributes (see `denver-functions.txt`'s
  "ANSI fix") need visible-length-aware handling in string functions (`STRLEN`/`MID`/etc.).
- **Telnet protocol extensions actually in use** by real player clients (MCCP, MXP, GMCP, NAWS,
  MSSP) — determines how much protocol surface Phase 3 needs. Backend owns all of these except
  MCCP, which — if actually used — would have to live relay-side (see Decisions).
- **Queued/scheduled command semantics** (`@wait`, semaphores, `cque.c`) — needs an explicit
  design for how delayed actions re-enter the single game-logic thread while preserving
  TinyMUSH's queue-draining order guarantees, which softcode may depend on.
- **Attribute/database text encoding** of the live flatfile (likely Latin-1 or 8-bit
  ASCII-with-high-bit, not UTF-8) — getting this wrong silently corrupts imported attribute text.
- **Cutover operational expectations**: acceptable downtime for the final switch, and the
  rollback plan (e.g. keep the C server's data directory untouched and ready to relaunch) if
  something breaks post-cutover.
- **Numeric formatting fidelity**: C's `fval()` output rules (trailing-zero trimming,
  `%.6f`-style float formatting, integer overflow behavior, `#-1 DIVIDE BY ZERO` and friends)
  differ materially from Java's default double→string conversion — treat as its own dedicated
  oracle target within Phase 1a's `Value`/math-function work, not an incidental detail.

## Verification approach

- Unit tests per ported function/command against known input/output pairs (start from
  `denver-functions.txt` and vanilla `functions.c` doc comments).
- The oracle-diff harness (Phase 0.2) run continuously in CI alongside `mvn test`, comparing
  `jmush` output to the compiled `../tinymush` for a growing corpus of real softcode snippets.
- End-to-end telnet session tests exercising connect/build/restart-without-disconnect once
  Phase 3 lands — the one feature with an explicit "must look identical" requirement, so it
  needs a real telnet-client-style test, not just unit coverage.
