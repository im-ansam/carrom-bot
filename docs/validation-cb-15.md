# CB-15 validation

Base: origin/main 3e1b2be (reviewed CB-14 merge). Read root AGENTS.md, CB-15,
parent CB-3 and completed dependency CB-14. Scope is configurable constant drag,
analytic stopping distance and invertible linear power calibration. Contact/wall
response and pocketing remain later work. See ADR 0005 for assumptions and precision.

## Actual commands and outcomes

Windows, direct JDK 18.0.1.1, wrapper Gradle 9.9.1 and existing ignored Gradle cache.
Full validation used the existing ignored SDK 36 installation. CI uses JDK 21.
No local paths, SDK/license files, binaries or logs are committed.

- `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: red tests failed at
  compileTestKotlin on missing FrictionModel/PowerModel before implementation. After
  implementing them, the same command passed (12 physics tests).
- Added simulator integration test before adding drag configuration. The same command
  failed at compileTestKotlin on the missing PhysicsConfig deceleration argument.
- `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat --console=plain`: integration
  ran and failed on an overly exact stop-time expectation (2.5 vs 2.75 seconds).
  Repeated vector normalization leaves a small residual at an analytic stop tick.
  Retained exact-zero rest detection; tests now use a timestep-scaled time envelope
  and numerical position tolerance. Documented the policy, normalized terminal signed
  zero and expanded edge-case, property and integration coverage. A subsequent run
  before that correction also failed on the same time assertion (20 tests, one failure).
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed.
- `gradlew.bat -PjvmOnly projects test check --console=plain`: all 24 physics tests
  passed; Detekt failed on ReturnCount in FrictionModel.step. Combined zero-interval
  and zero-speed handling without suppressing the rule.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: passed,
  195 actionable tasks (22 executed, 173 up-to-date), including Kotlin compilation,
  debug APK, JVM tests, Android lint, ktlintCheck and Detekt.
- Final `gradlew.bat -PjvmOnly projects test check --console=plain`, ANDROID_HOME
  unset: passed. Test tasks were up-to-date from the successful full build.

JUnit XML: 24 physics (14 new), 17 model and 1 app JVM tests; zero failures/errors/skips.
Seeded drag-v1 checks 128 speeds against analytic motion/stopping distance with a
stored 1e-10 tolerance, monotonicity and exact repeated calls. Tests exercise inverse
power across calibration scales, zero/max power, invalid and extreme values, split
steps, multiple simulator timesteps, both body types, REST/TIME_LIMIT and shared shot
contracts. Existing free-motion fixture and all 10 CB-14 tests still pass.

NO-SOURCE includes JVM Java compilation and main resources, empty planner main/test
Kotlin/Java/resource/test tasks, empty Android adapter source and unit-test tasks,
app unit-test Java compilation and native-library/debug-symbol tasks. They add no
behavioral evidence. Physics Kotlin, tests and fixture resources execute. Kotlin
configuration-error checks are SKIPPED when no errors exist. Existing Gradle API
deprecation warnings remain; no unrelated build/CI changes were made.

No instrumentation/device run: this change is entirely platform-free physics.
Parameters are uncalibrated approximations, not empirical game-accuracy evidence.
Hosted JDK-21 CI is pending at PR creation. Full staged diff/path list and whitespace
checks were reviewed before commit. The pre-existing gradlew.bat normalization-only
difference is excluded; raw hash matches HEAD (03754c4c3d9206f5c9ef69e41990dfe26a05251e).
