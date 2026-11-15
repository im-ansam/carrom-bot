# CB-28 validation

Date: 2026-11-15. Synthetic JVM geometry and unified-physics verification only.
No game accuracy, calibrated confidence or throughput measurement is claimed.

## Commands and actual outcomes

Windows wrapper commands used the available local JDK and ignored Gradle cache.
Full Android validation used the local SDK. No machine paths are committed.

- `gradlew.bat -PjvmOnly :core:planner:test --console=plain` initially failed
  production compilation: a new vector helper conflicted with an existing helper.
  Renamed the new helper. The next planner test run failed one of 32 tests because
  the straight return-bank fixture physically hit the stopped striker; replaced
  the success fixture with an oblique path and independent analytic event times.
- `gradlew.bat -PjvmOnly :core:planner:test ktlintFormat detekt --console=plain`
  then passed 32 planner tests but failed Detekt with 15 style/complexity findings.
- Red for bounded enumeration: `gradlew.bat -PjvmOnly :core:planner:test --console=plain`
  failed test compilation for the not-yet-implemented enumeration APIs.
- `gradlew.bat -PjvmOnly :core:planner:test ktlintFormat --console=plain` after
  enumeration implementation failed one of 34 tests: the carom fixture lacked
  sufficient energy with its supplied drag. Adjusted explicit drag from 100 to
  60 mm/s², which permits capture and stops the residual before any rail contact.
- `gradlew.bat -PjvmOnly :core:planner:test detekt --console=plain` passed 34
  planner tests, but Detekt failed with 19 style/complexity findings. Refactored
  enumeration state, split completion predicates and used named arguments/blocks.
- `gradlew.bat -PjvmOnly ktlintFormat detekt :core:planner:test --console=plain`
  passed, 22 s; 11 tasks, 6 executed and 5 up-to-date.
- Added two-rail and four-disc regressions. `gradlew.bat -PjvmOnly ktlintFormat
  :core:planner:test detekt --console=plain` failed one of 36 tests: unknown chain
  IDs threw NoSuchElementException. Added explicit argument validation.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`
  **PASS**, 1 m 51 s; 209 tasks, 25 executed and 184 up-to-date. Includes Android
  assembly/lint, JVM tests, Kotlin compilation, ktlintCheck and Detekt.

The first attempt to launch full validation was not executed because automatic
approval review could not complete under the usage limit. The user reported the
limit reset; the same command was then executed successfully. An unprivileged
Python README edit could not launch its Windows shim; the edit was completed
using PowerShell instead. Neither failure is reported as successful validation.

## Coverage and limits

Nine new planner tests exercise the actual ShotSimulator, with one ShotEvaluation
integration. Single-bank event identities and independent analytic drag times agree
at 2 s and 0.01 s sampling clocks within 1e-7 s. Other cases cover two ordered
rails, two/three/four-disc chains, cut-transfer carom, blockers/tangency, simultaneous
corner rejection, immutable nested lists, enumeration budgets, invalid input,
illegal placement, early capture, truncation, energy loss and propagated work failure.

NO-SOURCE tasks include overlay production/tests, capture JVM tests, absent Java
and resources in Kotlin-only modules and absent native libraries/symbol metadata.
These are not coverage. Kotlin Gradle configuration-error tasks and app
preDebugAndroidTestBuild were SKIPPED. No instrumentation ran: no Android behavior
changed. Existing Gradle 10 deprecation warnings remain.

Specular geometry is a proposal only; exact actual impulse order, requested capture,
REST and no striker foul are mandatory. No optimizer compensates losses. Aim endpoints
must fit inside bounds; conservative full-corridor checking can reject obstacles past
capture. Sequence enumeration returns a documented bounded prefix. See
[ADR 0018](adr/0018-disc-banks-combinations.md) for public contracts and assumptions.

The preexisting gradlew.bat normalization is excluded; raw content hash remains
03754c4c3d9206f5c9ef69e41990dfe26a05251e, matching the original tracked blob.

Final SDK-free command: `gradlew.bat -PjvmOnly ktlintFormat projects test check
--console=plain` with ANDROID_HOME and ANDROID_SDK_ROOT unset **PASS**, 1 m 6 s;
19 tasks, 6 executed and 13 up-to-date. Planner tests executed; existing model/physics
tests were up-to-date. JUnit XML contains 157 JVM tests (17 model, 80 physics,
36 planner, 23 vision, 1 app), zero failures/errors/skips. Nine planner tests are new.

git diff --cached --check passed; complete staged diff and seven-file list reviewed before commit.
