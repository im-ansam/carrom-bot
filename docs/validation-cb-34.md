# CB-34 validation

Date: 2026-11-16. Synthetic data/disposable API31 emulator only. No game accuracy,
device latency or automatic shot execution is claimed.

## Host and commands

Windows PowerShell, JDK18.0.1.1 (JVM target17), repository-local ignored Gradle cache
and Android SDK36. Every Gradle shell sets JAVA_HOME, GRADLE_USER_HOME and ANDROID_HOME;
SDK-free validation instead removes ANDROID_HOME/ANDROID_SDK_ROOT. Builds serialize
with `--max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --console=plain`.
These are host settings, not committed project defaults. The preexisting gradlew.bat
normalization is excluded; raw file hash remains 03754c4c3d9206f5c9ef69e41990dfe26a05251e.

- RED: `gradlew.bat :app:testDebugUnitTest` failed compilation on absent AimAction/
  AimViewModel APIs before implementation, as expected (24s, 68 tasks).
- GREEN: `gradlew.bat :app:testDebugUnitTest ktlintFormat` passed after implementation
  (22s, 79 tasks); four new state tests plus the existing diagnostics test.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint
  :app:assembleDebugAndroidTest :core:overlay:assembleDebugAndroidTest
  :core:capture:assembleDebugAndroidTest :core:vision:assembleDebugAndroidTest`:
  first failed Detekt (33s, 165 tasks): service function-count threshold and three
  long lines. Inlined the action callback and wrapped lines. Second run failed lint
  (51s, 211 tasks): two percentage accessibility strings triggered PluralsCandidate;
  changed to percent notation. Third run passed checks/lint but failed instrumentation
  compilation (35s, 223 tasks): ACTION_SHOW_ON_SCREEN belongs to AccessibilityAction;
  corrected its API reference. No failed command is counted as successful validation.
- Final same command without the already-applied `ktlintFormat`: **PASS**, 27s,
  337 tasks (20 executed/317 up-to-date). Assembly, all JVM tests, root check,
  ktlint, Detekt, lint and all four test APK assemblies completed.
  XML totals: **193 JVM tests**, zero failures/errors/skips (model17, physics81,
  planner63, vision23, app5, overlay4). Unchanged suites can be up-to-date.
- `gradlew.bat -PjvmOnly projects test check`: **PASS**, 17s, 18 tasks
  (3 executed/15 up-to-date). Android modules are deliberately excluded here;
  their tests ran in the full build. `gradlew.bat --stop` stopped the daemon.

Python/py launch attempts were unavailable under host access restrictions; no Python
was used to modify or validate the implementation. A missing docs/module-graph.md
read was corrected by inspecting the authoritative module table in README.

## Coverage and device execution

New JVM tests exercise explicit start/pause/resume/stop, fresh-generation results,
late-result/failure rejection, no-candidate results, ranked cycling/wrap, direct and
unknown selection, matching selected scene/power, error clearing/retry and invalid/
duplicate actions. Android tests exercise native button/accessibility semantics,
selected candidate descriptions, stable button identity, unavailable power,
error/reversible intents, 2x font scale and long-list scrolling. Existing overlay
permission-denial/regrant and outside-touch tests now use the richer controls.
The app's real-consent integration additionally exercises aiming start/pause/resume/
stop, continued captured frames while paused and terminal End screen sharing.

Emulator CB13/API31 uses host OpenGL, 1024MB RAM, no window/audio/snapshot. Gradle
is stopped before device execution to avoid the known 8GB-host memory pressure.
Temporary `adb shell wm size 480x800` / `wm density 160` reduce host load.
Five APKs installed with `adb install -r -t <apk>`: app debug, app AndroidTest,
overlay AndroidTest, capture AndroidTest and vision AndroidTest; all returned Success.

An initial overlay instrumentation was mistakenly started before installation had
finished. It ran the old seven-test APK and was terminated by its replacement
(Process crashed after three tests). That run is invalid evidence. Subsequent
instrumentation waits for installation completion and uses the current APKs.

The first current ten-test overlay run failed two tests (24.728s): a detached
view's node had no clickable semantics, and the outside-touch test timed out
before overlay creation because a SystemUI ANR held input focus. Added explicit
node initialization and rebuilt with `gradlew.bat check lint
:core:overlay:assembleDebugAndroidTest` (PASS, 1m29s, 212 tasks). After a clean
restart with normal launcher focus, targeted FloatingControlsTest still failed
the detached-node assertion (3 tests, 1 failure, 1.115s). The final test correction
attaches controls to a real Activity before inspecting accessibility nodes; it
retains clickable/selected/class-name assertions. Same build/check/lint command
then PASS (1m12s, 212 tasks). Gradle stopped and emulator restarted after each
build. These are test-harness changes; no product lifecycle workaround or
accessibility assertion removal was used to hide a failure.

The first attached-test run passed the seven existing overlay tests but JUnit
rejected the three new expression-body methods: the ActivityScenario helper had
inferred a non-Unit result. Runner reported 8 entries/1 initialization failure
(11.911s). Changed the helper to a block body with Unit return; no product code
changed. Rebuilt/check/lint results and final device results follow.
`gradlew.bat check lint :core:overlay:assembleDebugAndroidTest` then PASS,
2m4s, 212 tasks (12 executed/200 up-to-date); daemon stopped before device restart.
The next ten-test overlay run passed 9/10, including attached accessibility and all
existing lifecycle/renderer tests (29.202s). Its immediate scroll-position assertion
ran before smooth scrolling advanced. Disabled smooth scrolling in that test only,
retaining fullScroll and positive scroll-position/content-height/bounded-height
assertions. This makes the layout check deterministic without changing product
scroll behavior.
Final `gradlew.bat check lint :core:overlay:assembleDebugAndroidTest` PASS,
1m37s, 212 tasks (12 executed/200 up-to-date); daemon stopped before final overlay run.

Direct `adb shell am instrument -w -r <package>/androidx.test.runner.AndroidJUnitRunner`:

- `io.github.carrombot.test`: **PASS**, `OK (5 tests)`, 78.155s.
- `io.github.carrombot.core.capture.test`: **PASS**, `OK (2 tests)`, 9.869s.
- `io.github.carrombot.core.vision.test`: **PASS**, `OK (13 tests)`, 4.617s.
- `io.github.carrombot.core.overlay.test`: **PASS**, `OK (10 tests)`, 18.896s.

Final total: **30 device tests passed**, zero final failures/errors/skips. Each
runner ended with normal INSTRUMENTATION_CODE:-1 and no failure/negative status
codes. The overlay run includes the three new control tests, real pointer dispatch,
permission/revocation/rotation lifecycle and unchanged Canvas goldens. Final stdout
is in ignored `.tools/cb34-adb-overlay-clean.log`, `cb34-adb-app.log`,
`cb34-adb-capture.log` and `cb34-adb-vision.log`; earlier logs remain distinct.
Display overrides were reset and the emulator shut down after validation.

## NO-SOURCE, skips and limits

Absent Java, ordinary production resources and native inputs remain NO-SOURCE;
capture JVM test compilation/resources/test are NO-SOURCE. Three JVM Kotlin
configuration-error checks and app preDebugAndroidTestBuild are SKIPPED by plugins.
No NO-SOURCE task is counted as an executed test. Existing Gradle10 deprecation
warnings remain. Direct instrumentation requires `OK (N tests)` and no runner
failure/status errors; shell exit status alone is insufficient.

Only API31 emulator behavior is verified. TalkBack end-to-end navigation, OEM
permission/window differences, API26-30/34+, physical devices and empirical game
accuracy/latency remain unmeasured. Accessibility checks inspect native node
semantics/actions; they do not claim a manual TalkBack audit. Runtime has no
candidate producer, fabricated trajectories, input injector or persisted frames.
