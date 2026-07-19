# Compile-time cost of `sqlSyntaxCheck`

A benchmark of how much the opt-in compile-time SQL syntax check
(`kueryClient { sqlSyntaxCheck = "..." }`, introduced in #408) adds to Kotlin
compilation.

- **Date:** 2026-07-19
- **Machine:** Apple Silicon (darwin), Kotlin 2.4.10, Gradle 9.6.1, JVM toolchain 17
- **TL;DR:** For a module with ~100 SQL statements, enabling the check adds
  roughly **+90 ms (`generic`) / +110 ms (`mysql`)** to a full recompile of that
  module. The cost is a small fixed per-compile setup plus ~0.3 ms per statement.
  Negligible in practice.

## What was measured

The delta in `compileKotlin` time caused **purely by the syntax check**. The
Kotlin compiler plugin (string-interpolation → bind-parameter transform) is
applied in every configuration, so its cost is common baseline; only
`sqlSyntaxCheck` is toggled:

1. `off` — plugin applied, check disabled (baseline)
2. `generic` — JSqlParser syntax check only
3. `mysql` — syntax check + MySQL dialect feature allow-list

## Setup

A throwaway standalone consumer project (not part of this build) that applies the
**published** plugin by id, mirroring the setup in
`kuery-client-gradle-plugin/functional-test` (`PublishedPluginFunctionalTest`):

- Plain `kotlin("jvm")` module (no ktlint/detekt), so only `compileKotlin` runs.
- Artifacts published to a build-local Maven repo via
  `publishAllPublicationsToFunctionalTestRepository`; consumer resolves
  `dev.hsbrysk.kuery-client` from it via `exclusiveContent`.
- `src/main/kotlin/bench/Queries.kt`: N generated `Sql { +"..." }` functions,
  every one a statically-known literal/template (so the checker actually parses
  it — dynamic blocks are skipped), with a **diverse length mix**:
  - ~30% short one-liners (`SELECT`/`INSERT`/`UPDATE`/`DELETE`),
  - ~50% medium (1–2 joins, `WHERE`/`ORDER BY`/`LIMIT`, one-level subquery),
  - ~20% long (CTE / `GROUP BY … HAVING` / `UNION` / multi-join / upsert, 10–30 lines).

**Validity check:** with `mysql`, an intentionally broken statement
(`SELECT * FORM users`) produces a `KUERY_SQL_SYNTAX` warning while all N valid
statements stay warning-free — confirming the checker really parses every one.

**Timing:** warm Gradle daemon, each config `warmup=2` then `runs=8–10` of
`compileKotlin --rerun-tasks --offline` (full recompile every run), median
reported. For N ≤ 300 the times are sub-second so Gradle's own
`BUILD SUCCESSFUL in <ms>` is used (excludes gradle-client JVM startup); for
N ≥ 1000 wall-clock ms is used because Gradle truncates ≥ 1 s to whole seconds
(the constant client-startup offset cancels in same-N deltas).

## Results (median)

| N (statements) | off | generic | mysql | Δgeneric | Δmysql |
|---:|---:|---:|---:|---:|---:|
| **100** | 376 ms | 470 ms | 486 ms | **+94 ms (+25%)** | **+110 ms (+29%)** |
| 300 | 448 ms | 590 ms | 612 ms | +142 ms (+32%) | +164 ms (+37%) |
| 1000 | 742 ms | 1170 ms | 1222 ms | +428 ms | +480 ms |
| 2000 | 1147 ms | 1814 ms | 1918 ms | +667 ms | +771 ms |

Linear fit of the overhead:

- `generic` ≈ **0.31 ms/statement + 70 ms** fixed per compile
- `mysql`   ≈ **0.36 ms/statement + 79 ms** fixed per compile

## Interpretation

- The bulk of the fixed ~70–80 ms is **JSqlParser initialization / first-parse
  warmup**, not per-statement work. It is paid once per `compileKotlin` run.
- The per-statement cost is ~0.3 ms (`generic`) / ~0.36 ms (`mysql`); the dialect
  check adds ~15% over generic for the feature allow-list.
- The percentages look large only because the benchmark module is trivial (tiny
  baseline). A real module spends far more per file on type resolution etc., so
  the check is a much smaller fraction of a real compile.
- Incremental builds re-check only changed files; the check is warning severity;
  and the third-party FIR checker does not run in the K2 IDE editor, so typing
  latency is unaffected.

**Conclusion:** at ~100 statements the check costs about **0.1 s per full module
recompile** — no meaningful impact. `mysql` (dialect) is marginally heavier than
`generic`.

## Reproduce

The generator and runner scripts used for this run are not committed (they live
outside the repo). To recreate:

1. Publish the three artifacts to the build-local repo:
   ```bash
   ./gradlew \
     :kuery-client-core:publishAllPublicationsToFunctionalTestRepository \
     :kuery-client-compiler:publishAllPublicationsToFunctionalTestRepository \
     :kuery-client-gradle-plugin:publishAllPublicationsToFunctionalTestRepository
   ```
   (Default version `latest-SNAPSHOT` skips artifact signing.)
2. Create a standalone `kotlin("jvm")` consumer that applies
   `id("dev.hsbrysk.kuery-client") version "latest-SNAPSHOT"`, resolving the
   `dev.hsbrysk.kuery-client` group from the `build/functional-test-repo`
   directory via `exclusiveContent` (see `PublishedPluginFunctionalTest`).
3. Generate N diverse `Sql { +"..." }` functions, then time
   `compileKotlin --rerun-tasks --offline` under a warm daemon while toggling
   `kueryClient { sqlSyntaxCheck = "generic" | "mysql" }` (or removing it).
