# CB-30 validation

Date: 2026-11-15. Synthetic JVM fixtures establish software behavior only.

Implemented deterministic explicit-scenario sensitivity evaluation and selection
among caller-proposed multi-pocket/single-target plans. No Android wiring or module
dependency changes. See ADR 0020 for confidence semantics and limitations.

## Executed checks

- Initial red: `gradlew.bat -PjvmOnly :core:planner:test --console=plain` failed
  at compileTestKotlin because the new evaluator/scenario APIs did not yet exist.
- `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test check --console=plain`
  first failed test compilation on nullable Double assertions. After correction,
  the four initial behavior tests passed; Detekt failed four MaxLineLength findings.
- First `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`
  built Android, ran tests/lint but failed Detekt with seven MaxLineLength findings
  after three additional boundary tests were added. Corrected the line lengths.
- The same full command then passed in 1m 2s (209 tasks: 23 executed, 186 up-to-date).
- Final full command after retaining scenario settings and strengthening repeatability
  assertions passed in 1m 3s (209 tasks: 25 executed, 184 up-to-date).
- With ANDROID_HOME and ANDROID_SDK_ROOT unset,
  `gradlew.bat -PjvmOnly projects test check --console=plain` passed in 38s
  (18 tasks: 3 executed, 15 up-to-date), confirming SDK-free domain validation.

Final XML reports: 173 tests, zero failures, errors or skips: model 17, physics 80,
planner 52, vision 23, app 1. Seven new planner tests cover multiple target reward,
retained physical capture evidence, detection-error misses and safer fallback,
drag/restitution/wall-friction/power samples, exact repeated event lists, fractional
confidence and inclusive thresholds, rejected nominal plans, empty proposals,
duplicate IDs/offsets, invalid geometry/power/thresholds, ownership, global budgets
and propagation of simulator work failures. The multi-pocket fixture deliberately
includes a supplied moving second disc; it is not a stationary-board generator claim.

Full validation includes ktlintCheck and Detekt through check. NO-SOURCE tasks
include absent Java sources/resources/native libraries, overlay production/unit-test
sources and tests, and capture JVM unit-test sources and tests. These are not tested
behavior. Kotlin plugin configuration-error tasks and preDebugAndroidTestBuild were
SKIPPED. Instrumentation was not run: this change is platform-free domain logic.
Gradle reports existing deprecated features incompatible with Gradle 10.

`git diff --check` passed. The preexisting gradlew.bat normalization remains excluded;
its raw hash still matches the original tracked bytes.

## Limits

Scenarios, calibration, target proposals and confidence threshold are caller policy.
The acceptance fraction is not calibrated probability; finite sampling cannot prove
real-game robustness or global optimality. Detection offsets cover centres only.
All requested targets must be captured; partial multi-pocket results are rejected.
No new game accuracy, latency or performance measurement is claimed. Simulator
exceptions propagate instead of selecting a misleading partial fallback.
