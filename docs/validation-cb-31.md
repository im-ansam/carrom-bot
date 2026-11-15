# CB-31 validation

2026-11-15, Windows x64, JDK 18.0.1.1, Gradle wrapper 9.9.1, Android SDK 36.
CI uses JDK 21. Commands below use the Windows wrapper.

## Executed checks

- Initial `gradlew.bat -PjvmOnly :core:planner:test --console=plain`: FAILED as
  expected before implementation, unresolved scheduler/budget/coroutine APIs.
- `gradlew.bat -PjvmOnly :core:planner:test ktlintFormat --console=plain`:
  PASS, 52s, 10 tasks (8 executed, 2 up-to-date), first scheduler regressions green.
- First `gradlew.bat -PjvmOnly ktlintFormat projects test check --console=plain`:
  FAILED, JUnit expression-body pruning test returned an exception instead of void;
  Detekt ReturnCount and test MaxLineLength also failed. Corrected test return type,
  extracted unpruned evaluation and used named arguments. Repeat: PASS, 24s,
  19 tasks (10 executed, 9 up-to-date).
- `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  PASS, 1m31s, 209 tasks (196 executed, 13 up-to-date). XML reports: 185 tests,
  zero failures/errors/skips: model 17, physics 81, planner 63, vision 23, app 1.
- With ANDROID_HOME and ANDROID_SDK_ROOT unset,
  `gradlew.bat -PjvmOnly projects test check --console=plain`: PASS, 20s,
  18 tasks (5 executed, 13 up-to-date); only model/physics/planner configured.
- `git diff --check`: PASS. The preexisting gradlew.bat normalization remains
  excluded; raw hash still matches its original tracked content.

Twelve new tests cover count limits and best-so-far ranking, exact request-local
physics caching and per-candidate constraint rescoring, cache disposal, power
pruning and immutable diagnostics, zero deadlines/empty input, in-flight deadline
interruption, completed best retention, coroutine cancellation during simulation
on a worker distinct from the caller, deterministic repeated predictions, invalid
budgets/duplicate IDs, propagated engine failures and unchanged checkpoint results.

NO-SOURCE: absent Java/resources in JVM modules, overlay production and unit-test
sources, capture JVM tests, absent adapter Java and local native inputs. Kotlin
configuration-error tasks and app preDebugAndroidTestBuild were SKIPPED. Existing
Gradle deprecation warnings remain. Android packaging could not strip existing
OpenCV/C++ libraries and packaged them unchanged. Instrumentation was not run:
CB-31 changes platform-free scheduling/physics, with JVM integration coverage.

## Dense synthetic workload

DenseSearchBenchmarkTest uses 19 stationary discs on an explicit 1000 mm board,
64 distinct velocities (100..163 mm/s), four warmup proposals, dt=0.01 s,
300 steps, drag=50 mm/s² and one target/pocket. No random source is used.
Full run: 64 started/completed, zero cache hits, elapsed 3,122,915,600 ns,
simulation 3,116,858,900 ns. Separate 5 ms budget: elapsed 5,173,200 ns,
simulation 4,971,700 ns, one started, zero completed, DEADLINE. Earlier runs
varied (full 4.198–5.453 s, deadline 5.188–5.612 ms). These are single-process
diagnostics affected by JIT, GC and concurrent build load, not speedup, device
latency, throughput percentiles or game accuracy claims. CI has no tight timing
threshold; fake-clock tests assert deadline semantics independently of host speed.

Cooperative overshoot is explicit in ADR 0021. Physics retains fixed steps and
stable numerical order; it reads no clock and has no coroutine dependency.
