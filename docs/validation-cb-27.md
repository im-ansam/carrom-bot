# CB-27 validation

Date: 2026-11-15. Synthetic JVM geometry/physics verification; no real-game accuracy,
confidence, power optimality or throughput measurement is claimed.

## Commands and actual outcomes

Windows wrapper commands ran with the available local JDK and ignored local Gradle
cache. Full Android validation used the local SDK; the SDK-free run unset both
ANDROID_HOME and ANDROID_SDK_ROOT. No machine-specific paths are committed.

- Red: `gradlew.bat -PjvmOnly :core:planner:test --console=plain` failed at
  `:core:planner:compileTestKotlin` because the new generators/contracts did not exist.
- Initial green attempt: `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test --console=plain`
  compiled but failed one of 25 planner tests: the oblique zero-drag double-bank
  produced no accepted result. Tiny residual striker motion correctly prevented REST.
  The golden fixture now supplies explicit drag and an independent analytic drag clock.
- `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test detekt --console=plain`
  passed 25 planner tests, then failed Detekt on 11 long lines. A subsequent run
  failed one of 26 tests due to an incorrectly chosen supposedly infeasible geometry;
  corrected its inputs to actually cross another rail first. Next run passed all
  27 planner tests but Detekt identified two formatter-collapsed long lines. Fixed
  these using a block body and named arguments, without disabling line checks.
- Final full command: `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`
  **PASS**, 28 s; 209 actionable tasks, 25 executed and 184 up-to-date. Includes
  Kotlin compilation, Android debug assembly, JVM tests, ktlintCheck, Detekt and lint.
- Final SDK-free command: `gradlew.bat -PjvmOnly projects test check --console=plain`
  **PASS**, 17 s; 18 actionable tasks, 5 executed and 13 up-to-date. Only model,
  physics and planner are included. Planner tests executed; existing model/physics
  tests were up-to-date, not rerun in this command.
- `git diff --check` passed before staging; staged diff/filenames reviewed before commit.

JUnit XML reports contain 148 JVM tests: model 17, physics 80, planner 27, vision 23,
app 1; zero failures/errors/skips. Existing suites may be up-to-date. Nine new tests
cover bank generation/reflections, including actual ShotSimulator and ShotEvaluation
integration. Single-bank event times match 0.25/0.7/0.95 s. Double-bank event identity,
order and independent drag-based times agree across 2 s/0.01 s clocks within 1e-7 s.

## Empty/skipped tasks and limits

NO-SOURCE tasks include overlay production Kotlin/Java/resources and unit tests,
capture JVM tests, absent Java compilation/resources in Kotlin-only modules, and
absent native libraries/symbol/debug metadata. These are not behavior coverage.
Kotlin Gradle plugin configuration-error tasks and app preDebugAndroidTestBuild were
SKIPPED. Instrumentation was not run: the change is confined to JVM geometry/planning
and introduces no Android behavior. Existing Gradle 10 deprecation notices remain.

Geometry is conservative and specular, with physical acceptance delegated to the
existing simulator. No angle/power search compensates wall losses. Corners, rail
endpoints, extra body/wall impulses, post-impact banks, fouls, missed captures and
TIME_LIMIT are rejected. Invalid scenes and simulator failures propagate explicitly.
See [ADR 0017](adr/0017-striker-bank-generation.md) for API and ownership contracts.

The preexisting gradlew.bat normalization remains untouched and excluded from the
commit; its raw content hash still matches the original tracked blob.
