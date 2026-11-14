# CB-13 validation

Base: origin/main at 75b4fb4, after reviewed CB-12 merged. Read CB-13, parent CB-1
and completed dependencies CB-11/CB-12. Consulted CB-20 and CB-32 to keep capture
services and actual overlays outside this ticket.

## Environment

Windows, direct JDK 18.0.1.1 fallback; CI uses JDK 21. Main already pins Gradle 9.9.1.
Wrapper download from services.gradle.org timed out twice. Downloaded the same
distribution from official downloads.gradle.org and verified SHA-256 against the
unchanged wrapper property before placing it in the ignored wrapper cache. Initial
red tests used the existing cached Gradle 9.5.0 executable as a temporary fallback;
all final commands below used gradlew.bat / Gradle 9.9.1.

Found an older installed Android SDK. Copied its already-accepted license files into
the ignored workspace SDK; Gradle provisioned platform 36 revision 2, build-tools
36.0.0 and platform-tools 37.0.1 there. No SDK path, license file, SDK binary, emulator
data or log was committed. No new license prompt was automatically accepted.

Used an isolated headless AVD with an already-installed Android 31 Google APIs x86_64
image. The old emulator reported a Vulkan initialization warning but booted successfully
(sys.boot_completed=1) and ran the tests. This is Android 12/API 31 runtime evidence,
not min-26/API-36/OEM permission-dialog coverage. Emulator was stopped after validation.

## Actual commands and outcomes

- `gradlew.bat -PjvmOnly :core:model:test --console=plain`: initial tests failed on
  missing setup contracts before implementation. A later regression assertion for
  restoring overlay permission failed (17 tests, one failure) before the fix, then
  passed. The stale OVERLAY_DENIED stage now clears on a positive OS observation.
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed. Intermediate full builds
  found source naming/line-length diagnostics and two API-28-only XML heading attributes
  incompatible with minSdk 26. Fixed the source issues and used runtime API guards for
  headings; no lint/Detekt rule was disabled. An initial build launched before view-binding
  configuration was added failed on binding imports; the configured rebuild compiled them.
- `gradlew.bat projects assembleDebug test check lint :app:assembleDebugAndroidTest --console=plain`:
  passed, 223 actionable tasks (35 executed, 188 up-to-date), including debug APK,
  instrumentation APK, Android lint, ktlint and Detekt.
- `gradlew.bat connectedDebugAndroidTest --console=plain`: passed on the isolated
  API-31 emulator, four app instrumentation tests, zero failures/skips; 210 actionable
  tasks (88 executed, 122 up-to-date).
- Final SDK-free `gradlew.bat -PjvmOnly projects test check --console=plain`, with
  ANDROID_HOME unset: passed, 10 actionable tasks (6 executed, 4 up-to-date).
- Final `gradlew.bat projects assembleDebug test check lint connectedDebugAndroidTest --console=plain`:
  passed after the regrant fix and stronger denial assertion; 311 actionable tasks
  (59 executed, 252 up-to-date). Model JUnit XML reports 17 tests (five new setup tests);
  app JVM XML reports one diagnostic-format test. All 18 JVM tests pass, zero errors,
  failures or skips. All four Activity instrumentation tests pass again.
- `git diff --check` and staged whitespace checks passed; complete staged diff and
  file list reviewed before commit. Wrapper bytes were restored exactly to HEAD (raw
  blob hash 03754c4); Git still reports a normalization-only working-tree difference
  because main stores CRLF bytes under text attributes. It is excluded from this commit.

Gradle reports existing deprecated build-script APIs incompatible with a future Gradle
10. They are warnings, not a hidden validation failure; no unrelated toolchain cleanup
was performed. Kotlin configuration-error checks are SKIPPED when there are no errors.

NO-SOURCE tasks include model Java compilation/main resources; all physics/planner
main/test source and resource tasks; capture/vision/overlay Kotlin/Java main and test
compilation and Java resources; app test Java compilation and native library/debug-symbol
tasks. Empty adapter connected-test tasks add no behavioral coverage. Model Kotlin,
app Kotlin, generated binding Java, app JVM tests and app instrumentation do execute.

## Coverage limits

Instrumentation tests launch actual Activities, verify stopped startup/recreation,
overlay app-op denial/regrant, denied capture retry, synthetic granted capture result,
Stop and settings return. They temporarily modify only the test app's overlay app-op
and restore the original mode. System consent screens are intercepted, not approved;
no MediaProjection or real screen capture runs. JVM tests also cover late results,
revocation and unavailable permission screens. Real capture/rotation/revocation
resource tests belong to CB-20 and overlay attachment tests to CB-32.
