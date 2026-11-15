# CB-20 validation

Date: 2026-11-15. Ticket: [CB-20](https://ansamcd.atlassian.net/browse/CB-20).

## Scope and environment

Consent-only setup now starts a private mediaProjection foreground service after a fresh
successful Android consent result. The Android capture adapter owns one virtual display,
two ImageReader slots and synchronous borrowed-image delivery. No pixels or consent tokens
are saved or logged. Model, physics and planner production code/dependencies are unchanged.
See [ADR 0010](adr/0010-consented-screen-capture.md) for ownership and thread contracts.

Local validation used Windows, the checked-in Gradle wrapper (9.9.1), JDK 18.0.1.1,
compile/target SDK 36 and a disposable API 31 Google APIs x86_64 emulator (720 by 1280).
JVM bytecode target remains 17; hosted CI uses JDK 21. Local SDK/cache/AVD paths and
emulator logs remain ignored. No personal screen data was used or retained.

## Executed commands and results

Commands below use Windows PowerShell and `gradlew.bat`.

| Command | Actual result |
| --- | --- |
| `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.carrombot.CapturePipelineTest --console=plain` before implementation | Expected RED: compiled, real system consent accepted, timed out waiting for active capture; 1 failed test, 124 tasks (5 executed, 119 up-to-date). |
| `.\gradlew.bat ktlintFormat projects assembleDebug test check lint connectedDebugAndroidTest --console=plain` on final implementation | PASS in 39 s; 320 tasks (26 executed, 294 up-to-date). Includes ktlint, Detekt, Android lint, debug assembly, JVM tests and both device suites. |
| `.\gradlew.bat -PjvmOnly projects test check --console=plain` with Android SDK environment variables unset | PASS in 14 s; 14 tasks (3 executed, 11 up-to-date); platform-free module build and tests remain SDK independent. |
| `git diff --check` | PASS; no whitespace errors in the intended diff. |

XML reports confirm 80 physics, 17 model and 1 app JVM tests, plus 5 app and 2 capture
instrumentation tests. All report zero failures, errors and skipped tests in the final run.
Capture and other Android adapter JVM test tasks are NO-SOURCE; their behavior is covered
by the real instrumentation suites rather than mocked JVM Android APIs.

The app suite accepts genuine Android consent, observes more frames than reader capacity,
recreates the Activity, rotates portrait to landscape and checks swapped frame dimensions,
backgrounds/resumes the Activity, stops, restarts with fresh consent and sends the actual
notification Stop PendingIntent. It verifies no remaining capture service/binding and null
Android media-projection state. Existing tests cover launch/recreation without implicit
requests, overlay denial/regrant, capture denial/retry, invalid grant failure and settings.
The capture suite uses test-only consent Activity/service fixtures to verify platform
revocation, a 50 ms synchronous consumer, closed borrowed Images, no post-terminal delivery,
and consumer-exception cleanup. It never fabricates a successful capture.

Intermediate validation failures were investigated and corrected: Detekt complexity/line
length findings; an overly broad service-record assertion matching an unrelated system
service; rotation setup that needed a deterministic initial orientation; stale service
display metrics during rotation; and dead non-creating bindings dropping terminal failure
state. The rotation regression now uses fresh display-context metrics, and disconnection
releases its registration while preserving the last terminal snapshot. Asynchronous worker
results are awaited by the invalid-grant assertion. The full combined run above is green.

Expected NO-SOURCE tasks include empty planner production/tests, vision/overlay Kotlin and
unit/instrumentation sources, capture unit tests, Java sources, absent resources and native
libraries/symbols. `checkKotlinGradlePluginConfigurationErrors` for the three JVM modules
and `:app:preDebugAndroidTestBuild` are SKIPPED. These are not additional passing tests.
Gradle reports deprecated features ahead of Gradle 10 and suggests configuration caching;
neither warning was hidden or treated as a behavioral test.

## Limits

Device execution covers API 31 only. API 26–30 and API 34+ app-only sharing, modern status
chip/revocation, Android 13+ notification denial and OEM devices still need runtime checks.
Those branches compile and lint against SDK 36; this does not establish their runtime
behavior. No device-level frame-rate, memory benchmark, CV or actual-game accuracy is claimed.
Consumers must obey the borrowed-image contract and keep synchronous processing bounded;
a blocking consumer delays owner-thread cleanup. The app currently observes only frame
metadata, with no vision, overlay rendering or gameplay behavior.
