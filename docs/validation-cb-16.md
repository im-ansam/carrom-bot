# CB-16 validation

Base: origin/main 971643776b1bae8f37a23161374f09c949b0ac82 (reviewed CB-15 merge).
Read root AGENTS.md, CB-16, parent CB-3, completed CB-14/15 and neighboring CB-17/18.
Scope: body contacts only. See ADR 0006 for sweep, ordering, restitution, separation,
runtime bounds and numerical limits. Module dependencies and shared models are unchanged.

## Commands actually executed

Windows wrapper Gradle 9.9.1, direct JDK 18.0.1.1, ignored Gradle cache. Full builds
used the existing ignored SDK 36. CI uses JDK 21. No environment paths, binaries,
SDK/license files, credentials or raw build logs are committed.

- `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: red regression failed
  as expected: 25 tests, one assertion failure, existing simulator tunneled through
  a stationary disc. No implementation existed when this test ran.
- `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat --console=plain`: passed
  after initial contact integration (25 tests).
- `gradlew.bat -PjvmOnly projects test check --console=plain`: physics tests passed;
  Detekt failed on long lines, loop jumps and a magic number. Refactored without
  suppressions. A later SDK-free run passed (14 tasks: 3 executed, 11 up-to-date).
- `gradlew.bat -PjvmOnly ktlintFormat :core:physics:test check --console=plain`:
  repeated while extending fixtures. Tests passed at 33, then 35 physics tests;
  Detekt initially reported line lengths, then polynomial-root condition complexity
  and return count. Fixed each finding; a final remaining long line was refactored.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  passed the initial conservative contact implementation (196 tasks: 24 executed,
  172 up-to-date). Review replaced conservative drag advancement with swept
  quadratic trajectories to avoid tangency stalling. Added drag glancing/inelastic
  and stopping-time regression tests before final validation.
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed on standalone runs
  after source refinements. Formatting is complete before final validation.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: one
  intermediate run failed on an incorrectly shortened Vec2 import and the remaining
  Detekt line length. Corrected Vec2 and ShotInput imports and split the expression.
  The next run passed (195 tasks: 23 executed, 172 up-to-date).
- Added explicit restitution energy-loss, exact-simultaneous conservation and
  separation-at-boundary failure tests. Final
  `gradlew.bat projects assembleDebug test check lint --console=plain`: passed,
  195 tasks: 10 executed, 185 up-to-date. Includes debug APK assembly, JVM tests,
  Android lint, ktlintCheck and Detekt. Existing Gradle deprecation warnings remain.

Final JUnit XML: 38 physics tests (14 new), 17 model tests and one app JVM test;
zero failures/errors/skips. contact-v1 runs 128 seeded glancing scenarios with
1e-7 momentum/elastic-energy tolerance and exact repeated final-state comparisons.
Other tests check analytic unequal-mass restitution, tangential invariance and
energy loss, stationary/grazing/near-miss targets, chains, exact/near-simultaneous
contacts, storage-order invariance, overlap correction, prediction frame zero,
drag contacts at restitution 0/0.5/1, stopping-time splits, timestep agreement,
invalid inputs, iteration exhaustion and containment failure. Existing free-motion
and drag fixture tests pass. Test loops are cases within JUnit tests, not extra
reported test counts.

NO-SOURCE includes JVM Java compilation/main resources, empty planner source/tests,
empty Android adapters' source/unit tests, app unit-test Java compilation and native
library/debug-symbol tasks. They provide no behavioral test evidence. Physics Kotlin,
test compilation, resources and JUnit run in the final build. Configuration-error
checks are SKIPPED when no errors exist; preDebugAndroidTest is SKIPPED as configured.
No instrumentation/device test was run for this platform-free change.

Finite Double precision, sequential simultaneous-contact ordering and dense/inelastic
budget exhaustion remain explicit limitations. There is no wall/pocket response,
spin or empirically calibrated game accuracy. Hosted JDK-21 CI is pending PR creation.
The pre-existing gradlew.bat normalization difference is excluded: raw hash matches
HEAD (03754c4c3d9206f5c9ef69e41990dfe26a05251e). Full diff/staged paths and whitespace
are reviewed before commit; SDK, cache and sensitive data are excluded.

## PR #15 review follow-up

Addressed the P2 time-zero drag-contact finding. The reported touching radius-5
pair at (100,100)/(110,100), velocities (1,1)/(1,0), drag 10 now fails explicitly
with `Acceleration-driven sustained contact is unsupported` at both 0.1 and 0.0001
second steps. No end-of-step delayed-impact trajectory escapes. A separate control
checks constant separation and outward-curving tangency remain supported. Public
configuration KDoc and ADR 0006 document the sustained-contact limitation.

- Red `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: 39 tests, one
  failure because the reported input returned successfully instead of throwing.
- Green `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat --console=plain`:
  passed after the guard. Standalone `gradlew.bat -PjvmOnly ktlintFormat --console=plain`
  also passed after adding control coverage.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: passed,
  195 tasks, 23 executed and 172 up-to-date, including Android lint, ktlint and Detekt.
- `gradlew.bat -PjvmOnly projects test check --console=plain`, ANDROID_HOME unset:
  passed, 14 tasks, 3 executed and 11 up-to-date; tests up-to-date from the full run.

Final XML now reports 40 physics, 17 model and 1 app JVM tests, zero failures/errors/skips.
NO-SOURCE/SKIPPED tasks, existing deprecation warnings and no-device-test limits remain
as above. The wrapper artifact remains excluded. The review fetch helper initially
failed decoding GitHub punctuation under Windows cp1252; rerunning with PYTHONUTF8=1
succeeded. No helper/skill files were changed.
