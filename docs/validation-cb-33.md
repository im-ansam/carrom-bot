# CB-33 validation

Date: 2026-11-15. Ticket: [CB-33](https://ansamcd.atlassian.net/browse/CB-33).

## Scope and environment

Immutable renderer scenes, explicit normalized/board-mm/local-pixel mapping,
multi-body multi-leg Canvas paths, contact/rebound/destination markers, selected
and alternative candidates, normalized power and supplied sensitivity. Passive
main-thread view clears on resize. See [ADR 0023](adr/0023-trajectory-rendering.md).
The module graph remains overlay -> model; model is exported for public geometry.
Production capture/planner/window integration remains CB-35, controls CB-34.

Windows PowerShell, wrapper Gradle 9.9.1, JDK 18.0.1.1, Android compile/target SDK36,
bytecode17. Disposable API31 Google APIs x86_64 emulator, initially 720x1280,
software GPU; final direct-instrumentation runs use a 480x800/160dpi override,
1024MB emulator RAM and host OpenGL to reduce host pressure. Golden Canvases stay
640x480 and use software Bitmap Canvas regardless of the emulator GPU driver.
CI uses JDK21. Local SDK/cache/AVD paths and logs are ignored, not committed.
Only synthetic 640x480 software-Canvas PNGs are checked in; no captured pixels.

## Executed checks

- `gradlew.bat :core:overlay:testDebugUnitTest --console=plain`: expected RED
  before implementation, unresolved rendering-contract references; 39s, 15 tasks
  (1 executed/14 up-to-date).
- `gradlew.bat ktlintFormat :core:overlay:testDebugUnitTest detekt --console=plain`:
  contract tests passed; command failed on mixed boolean operators in validation.
  Parentheses corrected; 18s, 18 tasks (8 executed/10 up-to-date).
- `gradlew.bat ktlintFormat :core:overlay:testDebugUnitTest
  :core:overlay:compileDebugAndroidTestKotlin detekt --console=plain`:
  compilation/tests passed, failed static analysis on resize-condition complexity
  and two long test lines. Split named conditions/wrapped assertions; 1m13s,
  25 tasks (8 executed/17 up-to-date).
- `gradlew.bat ktlintFormat :core:overlay:connectedDebugAndroidTest
  '-Pandroid.testInstrumentationRunnerArguments.class=io.github.carrombot.core.overlay.TrajectoryRendererTest'
  '-Pandroid.testInstrumentationRunnerArguments.recordGoldens=true' --console=plain`:
  three renderer tests passed, 1m4s, 52 tasks (11 executed/41 up-to-date). Gradle
  uninstalled test storage before adb pull, so the first pull failed with missing
  directory. Added explicit synthetic-only export to /data/local/tmp during recording;
  rerun passed three tests, 48s, 52 tasks (8 executed/44 up-to-date). Pulled the two
  named PNGs and visually inspected both: two rebounds, two transfers, pocket marker,
  dashed alternative and power/sensitivity legend at two scales/translations.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint
  connectedDebugAndroidTest --console=plain`: FAILED, 3m43s, 335 tasks
  (42 executed/293 up-to-date). Detekt found nested golden-helper blocks; extracted
  recording/comparison helpers. Existing app consent lifecycle test failed finding
  Hide in accessibility windows, and existing overlay outside-touch test got zero taps.
  The three new renderer tests (including actual golden comparison) passed. Device
  inspection found SystemUI's Application Not Responding dialog held window focus.
  Reboot did not finish normally; stopped/relaunched the disposable emulator process.
  No product lifecycle/input changes were made to mask these environment failures.
- Final `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  PASS, 2m48s, 214 tasks (37 executed/177 up-to-date). All 189 JVM tests passed:
  model17, physics81, planner63, vision23, app1, overlay4; zero failures/errors/skips.
  Static analysis, formatting, Android lint and assembly passed. During emulator reboot,
  UTP printed device-property/Guava AbstractFuture class-loading diagnostics despite
  successful Gradle completion. Device tests were therefore run separately on a
  subsequently cold-started, fully booted emulator with normal launcher focus.
- SDK environment unset: `gradlew.bat -PjvmOnly projects test check --console=plain`:
  PASS, 1m34s, 18 tasks (3 executed/15 up-to-date). Domain tests were up-to-date;
  root ktlint/Detekt executed against all Kotlin sources. This does not run Android
  overlay JVM tests; those ran in the full Android-enabled build above.
- `gradlew.bat connectedDebugAndroidTest --console=plain`: FAILED, 4m9s,
  228 tasks (9 executed/219 up-to-date), with the same two existing UI-test failures;
  the other 25 device tests passed. SystemUI ANR focus recurred. Replaced immediate
  accessibility-list first() with a bounded readiness/action wait in the app test;
  the overlay test now waits for Activity focus, observes its one real injected tap,
  waits for Hide accessibility and observes detachment. Assertions still require
  the actual control and real pointer dispatch; no taps are retried and no product
  permission/lifecycle code was changed. Cold-started again and serialized workers.
- `gradlew.bat ktlintFormat :app:compileDebugAndroidTestKotlin
  :core:overlay:compileDebugAndroidTestKotlin check lint --max-workers=1 --console=plain`:
  PASS, 1m48s, 195 tasks (13 executed/182 up-to-date), validating test synchronization
  changes, all JVM checks, lint, ktlint and Detekt.
- `gradlew.bat :core:overlay:connectedDebugAndroidTest :app:connectedDebugAndroidTest
  :core:capture:connectedDebugAndroidTest :core:vision:connectedDebugAndroidTest
  --max-workers=1 --console=plain`: FAILED at
  the first (overlay) suite, 1m38s, 51 tasks (4 executed/47 up-to-date). Six tests
  passed; the outside-touch test correctly timed out waiting for Activity input
  focus **before any overlay was created**. Other requested suites did not run.
  SystemUI's ANR dialog again held focus. Host inspection found about 130MB free
  physical RAM on an 8GB host, explaining severe paging and UI stalls.
- `gradlew.bat :app:assembleDebugAndroidTest :core:overlay:assembleDebugAndroidTest
  --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --console=plain`:
  PASS, 1m5s, 145 tasks (4 executed/141 up-to-date). Stopped Gradle and the old
  emulator, then launched the disposable AVD with 1024MB RAM for direct adb
  instrumentation without Gradle/UTP host overhead. ANGLE failed OpenGLES initialization,
  so fell back to the previously working SwiftShader renderer. No repository
  Gradle defaults or personal apps were modified to work around host pressure.
- Installed the five built APKs with `adb install -r -t <apk>` (all SUCCESS), then
  `adb shell am instrument -w -r
  io.github.carrombot.core.overlay.test/androidx.test.runner.AndroidJUnitRunner`:
  7 tests, 32.157s, 6 passed/1 failed (outside-touch Activity focus timeout before
  overlay creation). All three new renderer tests, both pixel goldens, and the
  other three lifecycle tests passed. SystemUI ANR focus persisted; host free RAM
  again fell to about 140MB. Tried the supported host graphics driver next.
- Host OpenGL initialized successfully. The same direct overlay instrumentation
  command then PASS: `OK (7 tests)`, 14.209s, including actual pointer dispatch,
  permission/lifecycle tests and both unchanged pixel goldens. No Gradle process
  was running during these direct device tests.
- Remaining direct commands `adb shell am instrument -w -r <package>/androidx.test.runner.AndroidJUnitRunner`:
  `io.github.carrombot.test` PASS `OK (5 tests)`, 53.367s;
  `io.github.carrombot.core.capture.test` PASS `OK (2 tests)`, 7.610s;
  `io.github.carrombot.core.vision.test` PASS `OK (13 tests)`, 3.383s.
  Final direct-device total **27 passed, zero failures/errors/skips**. This includes
  all four suites, real consent, rotation, continued frames after Hide, actual touch
  dispatch and deterministic golden comparison with recording disabled. Earlier
  Gradle/UTP XML reports describe failed intermediate runs; final evidence is the
  direct runner output, not rewritten XML. Display size/density overrides were reset
  and the disposable emulator shut down after validation.

## NO-SOURCE, skips and limitations

Absent Java, production resources and native inputs are NO-SOURCE. Capture JVM test
compilation/resources/test remain NO-SOURCE; overlay now has four real JVM tests.
The three JVM Kotlin configuration-error checks and app preDebugAndroidTestBuild
are SKIPPED by their plugins. No test is counted as executed from NO-SOURCE tasks.
Gradle reported existing deprecated-feature warnings for Gradle10 compatibility.

Only API31 software Canvas goldens were verified. API26-30/34+, OEM hardware Canvas,
font/device scaling, large candidate legends, real-game accuracy and rendering latency
are unmeasured. Goldens intentionally pin API31 rasterization; do not silently update
assets for another platform. Renderer inputs must contain event-resolved vertices;
the current sparse simulator frames alone do not supply exact rebound/contact positions.
No fabricated runtime paths, score-to-confidence conversion, gesture automation,
screen capture persistence or new overlay window exists in this ticket.

Regenerate only intentionally using the quoted recording command above, pull
`/data/local/tmp/carrombot-chain-bank-v1.png` and
`/data/local/tmp/carrombot-translated-bank-v1.png`, review both images, then rerun
without recordGoldens. Ordinary tests require matching assets and never auto-record.
