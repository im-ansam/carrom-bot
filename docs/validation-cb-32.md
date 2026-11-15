# CB-32 validation

Date: 2026-11-15. Ticket: [CB-32](https://ansamcd.atlassian.net/browse/CB-32).

## Scope and environment

Service-owned floating Hide control, WindowManager attach/detach, explicit permission
checks, own-UID permission watching, rotation invalidation and terminal cleanup.
No trajectory rendering, board mapping, gameplay input or captured-frame persistence.
See [ADR 0022](adr/0022-overlay-lifecycle.md). The README module graph is current.

Windows PowerShell, Gradle wrapper 9.9.1, JDK 18.0.1.1, compile/target SDK 36,
JVM bytecode 17; disposable API 31 Google APIs x86_64 emulator (720 x 1280).
Hosted CI uses JDK 21. Local SDK, cache, AVD and logs remain ignored.

## Executed validation and intermediate failures

- `gradlew.bat :core:overlay:compileDebugAndroidTestKotlin --console=plain`:
  expected RED before implementation (missing OverlayController/OverlayShowResult API),
  13 s, 15 tasks (6 executed, 9 up-to-date).
- `gradlew.bat ktlintFormat :core:overlay:connectedDebugAndroidTest --console=plain`:
  failed one of two initial tests, 1 m 3 s, 52 tasks (38 executed, 14 up-to-date).
  Android attaches after addView returns; the test now waits for the main looper to idle.
- Initial `gradlew.bat ktlintFormat projects assembleDebug test check lint
  connectedDebugAndroidTest --console=plain`: failed in 4 m 10 s, 334 tasks
  (153 executed, 181 up-to-date). Detekt flagged MagicNumber/ReturnCount (fixed).
  Two of four new device tests failed: rotation without a foreground rotatable Activity,
  and looking for a non-focusable overlay in the active window alone. Tests now launch
  a test-only Activity and enumerate interactive windows. Existing app (5), capture (2)
  and vision (13) instrumentation tests passed. A transient device-property shell timeout
  was printed; the device subsequently responded and all suites executed.
- `gradlew.bat ktlintFormat :core:overlay:connectedDebugAndroidTest detekt --console=plain`:
  all four overlay tests passed; command failed on two MaxLineLength findings in tests,
  1 m 8 s, 53 tasks (11 executed, 42 up-to-date). Lines were wrapped/import shortened.
- The next combined run found Android lint ViewConstructor: this view intentionally
  requires its owner-provided Hide callback and is never XML-inflated. A narrowly scoped
  annotated suppression documents that contract; no layout-editor constructor with a
  silent/no-op Hide behavior was added. Failed in 1 m 30 s, 321 tasks (36 executed,
  285 up-to-date). Capture/vision device suites passed; the modified app suite was not run.
- A further combined run failed ktlint's no-consecutive-comments rule on that suppression's
  explanation (1 m 24 s, 289 tasks: 15 executed, 274 up-to-date). The explanation now lives
  in the view's KDoc. Capture/vision suites again passed; dependent app/overlay checks
  were not claimed as passed from that run.

- The expanded app integration found a real regression: capture's virtual-display change
  notifications could immediately hide a restarted overlay. The combined command failed
  at the second session's window-presence assertion in 3 m 32 s, 337 tasks (45 executed,
  292 up-to-date); all other device tests and quality checks passed. Overlay notifications
  now filter to the default physical display, preserving rotation invalidation while
  ignoring unrelated virtual displays. The same app assertion is the regression test.
- An initial targeted invocation left the `-Pandroid.testInstrumentationRunnerArguments.class=...`
  argument unquoted in PowerShell; Gradle rejected the split argument as an unknown task
  (6 s, no tests executed). The corrected invocation quotes the complete `-P` argument.

The corrected targeted command `gradlew.bat ktlintFormat :app:connectedDebugAndroidTest
'-Pandroid.testInstrumentationRunnerArguments.class=io.github.carrombot.CapturePipelineTest'
--console=plain` passed in 1 m 15 s, 134 tasks (7 executed, 127 up-to-date), one real
integration test, zero failures/errors/skips.

## Final passing validation

| Command | Actual result |
| --- | --- |
| `gradlew.bat projects assembleDebug test check lint connectedDebugAndroidTest --console=plain` | PASS in 3 m; 338 tasks (36 executed, 302 up-to-date). Includes assembly, JVM tests, ktlintCheck, Detekt, Android lint and all four device suites. |
| `gradlew.bat -PjvmOnly projects test check --console=plain`, with Android SDK environment variables unset | PASS in 59 s; 18 tasks (3 executed, 15 up-to-date). Domain modules remain SDK independent. |
| `git diff --cached --check` | PASS on the intended staged diff. The pre-existing wrapper normalization is excluded. |

XML reports confirm 185 JVM tests (model 17, physics 81, planner 63, vision 23, app 1)
and 24 instrumentation tests (app 5, capture 2, vision 13, overlay 4), all with zero
failures, errors or skipped tests. Capture/overlay JVM tests are NO-SOURCE; Android
behavior is exercised by instrumentation, not by Android API stubs in JVM tests.
Other NO-SOURCE tasks are absent Java, resources and native-library/symbol inputs.
The three JVM `checkKotlinGradlePluginConfigurationErrors` tasks and
`:app:preDebugAndroidTestBuild` are SKIPPED, not passing behavioral tests. Gradle reports
existing deprecated features ahead of Gradle 10 and suggests configuration caching.

## Behavioral coverage and limits

Four overlay instrumentation tests exercise denied attach; explicit regrant; idempotent
show/hide/close; no show after close; actual app-op revocation without Activity resume;
physical rotation and no automatic reattach; real pointer input to an underlying Activity
outside the overlay and to the floating Hide control. Test APK fixtures are absent from
production. App-op modes, rotation and accessibility flags are restored.

The app integration uses genuine system capture consent and checks window presence across
Activity recreation, disappearance on rotation, continued frame delivery after Hide,
removal on explicit service destruction, and foreground/background app/notification Stop.
Existing permission rejection, invalid grant and resource-release regressions remain.
No screenshots or personal captured frames are retained or logged.

Runtime coverage is API 31 only. API 26-30, API 34+ capture modes, OEM settings/window
placement, accessibility services and multi-display hardware require separate device checks.
Rotation conservatively hides; restarting sharing shows the control again. No measured
latency, frame-rate, memory benchmark or actual-game accuracy is claimed.
