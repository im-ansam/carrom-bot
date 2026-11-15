# CB-18 validation

Base: origin/main ff5eab5c75cd636d8d9535e483f589fb21de965f, reviewed CB-17 merge.
Verified post-merge CI run 43687668817 completed successfully. Read AGENTS.md,
authoritative CB-18, epic CB-3, completed dependencies CB-16/17, ADRs 0004-0007
and dependency validation evidence. CB-18 alone is In Progress until PR review.

Scope and assumptions: ADR 0008 documents full-fit configurable apertures, shared
capture/body/wall clock, immutable timelines, captured striker representation,
physical outcomes versus player rules, finite precision and bounded failure.
Shared models, module graph and legacy closed-wall MotionSimulator are unchanged.

## Commands actually executed

Windows wrapper Gradle 9.9.1, direct JDK 18.0.1.1, ignored Gradle cache. Full builds
use the existing ignored Android SDK 36; hosted CI uses JDK 21. No SDK paths,
licenses, binaries, logs, credentials or captured screen data are committed.

- Initial `gradlew.bat -PjvmOnly :core:physics:test --console=plain` could not
  connect to its daemon in the sandbox. Elevated rerun passed (53 physics tests)
  after integration; the first intended red run was therefore not achieved.
- `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat check --console=plain`
  passed tests but failed formatting on wildcard test imports. Replaced imports.
- `gradlew.bat -PjvmOnly ktlintFormat check --console=plain` passed tests but
  failed Detekt: nested contact logging, long lines and pocket sweep return count.
  Extracted focused helpers and split statements without suppressions.
- `gradlew.bat -PjvmOnly ktlintFormat :core:physics:test check --console=plain`
  initially failed one expanded test assertion (the approaching disc also captured,
  so the assertion must select the striker outcome) and Detekt function count.
  Corrected the assertion; moved the pair geometry helper outside the engine.
  Subsequent runs passed at 58 and then 60 physics tests as coverage expanded.
- Red `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: 61 physics tests,
  one expected failure. A radius-10.1 striker captured exactly at the wall violated
  strict containment through floating-point rounding. This regression preceded
  the fix. Capture now applies existing bounded wall correction with zero velocity
  before retaining the inactive striker; no wall impulse/event occurs.
- Green `gradlew.bat -PjvmOnly ktlintFormat :core:physics:test check --console=plain`:
  passed, 14 tasks: 6 executed, 8 up-to-date. All 61 physics tests, formatting,
  Detekt and ktlint passed.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: passed,
  195 tasks: 21 executed, 174 up-to-date. Includes APK assembly, JVM tests,
  Android lint, ktlintCheck and Detekt. Final robustness edits then removed an
  unreachable striker fallback, validated public event clocks/IDs, rejected
  nonfinite aperture intersection and moved pocket sorting outside the body loop.
- Standalone `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed after
  those focused final edits. Final full and SDK-free results are recorded below.

Final `gradlew.bat projects assembleDebug test check lint --console=plain` after
robustness edits passed: 195 tasks, 20 executed and 175 up-to-date. Final
`gradlew.bat -PjvmOnly projects test check --console=plain` with ANDROID_HOME and
ANDROID_SDK_ROOT unset passed: 14 tasks, 3 executed and 11 up-to-date. Unit tests
were up-to-date from the full run; quality checks executed again.

Final XML: 61 physics (9 new), 17 model and one
app JVM tests, zero failures/errors/skips. shot-v1.csv has six analytic fixtures;
cases within loops are not additional JUnit test counts. Tests cover event timing,
final positions, bank ordering, chains, simultaneous captures, pocket selection,
input ownership, immutable results, coarse/fine clocks, drag, queen outcomes,
striker inactivity, fractional containment and explicit budget failure. Existing
body-contact sustained-load rejection and fractional wall regressions still pass.

NO-SOURCE: empty planner Kotlin/Java/resources/tests, JVM Java/main resources,
empty Android adapters' source/unit tests, app unit-test Java and native/debug-symbol
tasks. These are not behavioral test evidence. Configuration-error checks and
preDebugAndroidTestBuild are SKIPPED as configured. Existing Gradle deprecation
warnings remain. No instrumentation/device tests were run for platform-free work.

Known limits: full-fit apertures retain solid rails until capture; small corner
apertures may not accept large bodies. No lip/jaw/gravity/spin model or empirical
game calibration is claimed. Captured striker is a frozen inactive snapshot;
caller must use the capture outcome when starting another turn. Outcomes classify
disc capture/striker foul without assigning player points or queen-cover legality.
Finite precision, sequential simultaneous impulses, unsupported acceleration-driven
sustained contact and bounded-budget exhaustion remain explicit limitations.

The pre-existing gradlew.bat normalization artifact is excluded; its raw no-filter
hash matched HEAD at start: 03754c4c3d9206f5c9ef69e41990dfe26a05251e.
The same raw hash was verified again after final validation.
Full diff, staged paths and whitespace are reviewed before commit.
