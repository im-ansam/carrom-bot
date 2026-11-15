# CB-17 validation

Base: origin/main 6b8d8aed7271fbe2038675998852979cf8a05fbb (reviewed CB-16 merge).
Read root AGENTS.md, authoritative CB-17, parent CB-3, completed dependency CB-16,
neighbor CB-18, ADRs 0004/0005/0006 and CB-16 validation before implementing.
Scope: closed wall rebounds only; see ADR 0007 for coefficients, event/corner order,
distance tolerance, bounds, budgets and failure semantics. No shared contract or
module-dependency changes. Pocket capture and complete shot API remain CB-18.

## Commands actually executed

Windows Gradle wrapper 9.9.1, direct JDK 18.0.1.1 and ignored Gradle cache. Full
validation used the existing ignored Android SDK 36. No local SDK paths, licenses,
binaries, credentials or raw logs are committed. Hosted CI uses JDK 21.

- `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: red regression failed
  as expected, 41 tests with one failure: straight wall crossing threw instead of
  rebounding. No wall implementation existed when this test ran.
- `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat check --console=plain`:
  41 physics tests passed after integration; Detekt failed on three long source
  lines. Split those lines without suppressions.
- `gradlew.bat -PjvmOnly ktlintFormat :core:physics:test check --console=plain`:
  expanded 50 physics tests passed; Detekt found one long test statement.
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed on standalone runs
  before full validation. Formatting does not resolve every Detekt line length.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: first full
  run assembled the app, passed all tests and Android lint; Detekt failed on the
  remaining long assertThrows statement. Split it. Restricted body sweep horizons
  to the next wall event so future invalidated trajectories are not inspected.
- Final `gradlew.bat projects assembleDebug test check lint --console=plain`:
  passed, 195 tasks: 21 executed, 174 up-to-date. Includes debug APK assembly,
  physics Kotlin/test compilation, JVM tests, Android lint, ktlintCheck and Detekt.
- Final `gradlew.bat -PjvmOnly projects test check --console=plain`, with
  ANDROID_HOME and ANDROID_SDK_ROOT unset: passed, 14 tasks: 3 executed,
  11 up-to-date. Tests were up-to-date from the full run; quality checks ran again.

Final JUnit XML: 50 physics tests (10 new), 17 model tests and one app JVM test;
zero failures/errors/skips. rebound-v1 includes 128 seeded unfolded trajectories
with 1e-6 tolerance plus explicit double/many-bank examples; cases inside test
loops are not extra JUnit test counts. Existing CB-16 sustained-contact failure
and other body/drag/free-motion regressions remain unchanged and pass.

NO-SOURCE: JVM Java compilation/main resources, empty planner Kotlin/Java/tests,
empty Android adapters' Kotlin/Java/resources/unit tests, app unit-test Java and
native library/debug symbol tasks. These provide no behavioral test evidence.
Configuration-error checks and preDebugAndroidTestBuild are SKIPPED as configured.
Existing Gradle deprecation warnings remain. No instrumentation/device test run
is claimed for this platform-free physics change.

The pre-existing wrapper normalization artifact is excluded: raw no-filter hash
matches HEAD blob 03754c4c3d9206f5c9ef69e41990dfe26a05251e. Full source/test/docs
diff and staged paths are reviewed before committing; caches/SDK/logs are ignored.
Finite precision, sequential simultaneous impulses, unsupported acceleration-driven
sustained body contact, bounded-budget exhaustion and uncalibrated coefficients
remain explicit limitations. Hosted JDK-21 CI is pending PR creation.
