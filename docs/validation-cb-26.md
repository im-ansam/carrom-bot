# CB-26 validation

Executed on Windows, 2026-11-15. Platform-free planner geometry only; no device
permissions, capture, vision or overlay integration changed.

## Commands and actual results

- `gradlew.bat -PjvmOnly :core:planner:test --console=plain`: intentional red,
  compileTestKotlin failed with unresolved new generator contracts before implementation.
- `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test check --console=plain`:
  tests passed; Detekt failed on three return counts and three overlong lines.
  Geometric guard methods have narrowly documented ReturnCount suppressions;
  path checks were expressed as boolean conditions and lines shortened.
- `gradlew.bat -PjvmOnly ktlintFormat projects test check --console=plain`:
  expanded tests passed; Detekt found two new overlong test lines.
- First `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  failed on one formatter-collapsed helper line. Named constructor arguments fixed it.
- Final `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  **PASS**, 26 seconds, 209 tasks: 10 executed, 199 up-to-date.
- `gradlew.bat -PjvmOnly projects test check --console=plain` with Android SDK
  environment variables removed: **PASS**, 17 seconds, 18 tasks: 5 executed,
  13 up-to-date.

Gradle reported deprecated features incompatible with Gradle 10; the pinned build
continues to pass. Kotlin configuration-error tasks were SKIPPED (not test cases).
NO-SOURCE includes overlay production/tests, capture JVM tests, absent Java/resource
inputs and absent native/strip inputs. NO-SOURCE does not establish test coverage.
No instrumentation run for this JVM-only change; no game accuracy/performance claims.

## Tests

Current JUnit XML reports 139 JVM tests: model 17, physics 80, planner 18 (9 new),
vision 23, app 1; zero failures, errors or skipped tests. Existing unchanged suites
were mostly up-to-date, not rerun. Planner ran during final validation.

The new tests independently check ghost/contact coordinates and calibrated velocity,
direct/cut partition, analytic back-facing and grazing rejection, corridor blockers,
tangent rejection, just-clear margin, caller legality, repositioning/input ownership,
pocket margin and rails, zero-length aiming, moving/overlapping scene failures,
invalid settings and early wrong-pocket/striker capture. Generated direct and angled
cut launches exercise the actual shared simulator; direct additionally passes the
existing evaluation contract. Exact repeated generation checks stable caller IDs
and value equality. Synthetic inputs establish mechanics, not calibrated game quality.

Reviewed source/docs and focused staged list before commit. Preexisting gradlew.bat
normalization is excluded; raw blob matches the original tracked content. No new
dependency, Android API, SDK path, credentials or screen data is committed.
