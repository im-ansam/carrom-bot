# CB-14 validation

Base: origin/main e937293 (reviewed CB-13 merge). Read root AGENTS.md, CB-14,
parent CB-3, completed dependency CB-12, and later CB-15/CB-16 to delimit scope.
CB-14 implements only deterministic free motion; see ADR 0004 for assumptions,
units, ownership, clock policy and unsupported contact/boundary/pocket behavior.

## Environment and actual commands

Windows with direct JDK 18.0.1.1, cached wrapper Gradle 9.9.1 and ignored Gradle
home. Full validation used the existing ignored SDK 36 installation. No SDK,
license, binary, machine path or log was added to source control. CI uses JDK 21.

- `gradlew.bat -PjvmOnly :core:physics:test --console=plain`: first sandbox attempt
  failed to connect to the Gradle daemon. Retried with daemon access: red test failed
  at compileTestKotlin on missing MotionSimulator/PhysicsConfig, before implementation.
- `gradlew.bat -PjvmOnly :core:physics:test ktlintFormat --console=plain`: passed
  after implementation, including the first straight-motion test.
- `gradlew.bat -PjvmOnly ktlintFormat projects test check --console=plain`: all
  10 expanded physics tests passed; Detekt failed on three long lines. Corrected
  those lines without suppressing rules.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: passed,
  195 actionable tasks (23 executed, 172 up-to-date). Includes Kotlin compilation,
  debug APK, app JVM tests, physics tests, Android lint, ktlintCheck and Detekt.
- `gradlew.bat -PjvmOnly projects test check --console=plain`, ANDROID_HOME unset:
  passed. Final SDK-free task graph includes only model, physics and planner;
  previously successful test tasks were up-to-date.

JUnit XML: 10 physics + 17 model + 1 app JVM tests, zero failures/errors/skips.
The versioned seeded fixture runs 32 trajectories, comparing every sample to the
analytic free-motion solution with a stored 1e-10 mm tolerance and repeated runs
with exact value equality. Tests also cover dt changes, disc-only movement, stable
ordering, zero/signed-zero rest, time cap, prediction pre-impulse identity, immutable
collections, invalid clocks/configuration, overflow, bounds and tiny velocities.

NO-SOURCE: JVM Java compilation and physics main resources; planner Kotlin/Java
main/test compilation, resources and test; empty Android adapters' source/resource
and unit-test tasks; app unit-test Java compilation and native-library/debug-symbol
tasks. These provide no behavioral test evidence. Physics Kotlin/tests and test
resources now execute. Kotlin configuration-error checks are SKIPPED as no errors exist.
Existing Gradle deprecated-API warnings remain; no unrelated toolchain work was done.

No device/instrumentation command was run for this platform-free change. No new
Android behavior is introduced. Full local build/lint and JVM tests are the local
integration evidence; JDK-21 hosted CI remains pending when the PR is opened.

Reviewed complete staged diff and staged path list and ran whitespace checks before
commit. gradlew.bat still shows the pre-existing attribute-normalization difference,
but its raw hash matches HEAD exactly (03754c4c3d9206f5c9ef69e41990dfe26a05251e).
It is excluded. CI configuration, Android bootstrap and shared model are unchanged.
