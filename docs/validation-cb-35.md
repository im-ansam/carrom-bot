# CB-35 validation (work in progress)

CB-35 implements the explicitly authorized synthetic end-to-end path. Live-game
recommendations remain unavailable without measured calibration. Review and merge
evidence is recorded separately below when available.

## Preflight

Read root AGENTS.md, CB-35, parent CB-5 and dependencies CB-23/24/31/34.
All dependencies are Done. CB-34 post-merge main CI completed successfully:
<https://github.com/im-ansam/carrom-bot/actions/runs/43798597128>.
This evidence was added to CB-34 in Jira.

Branch: `codex/CB-35-aiming-pipeline`, based on `origin/main` at CB-34's merge.
The preexisting unstaged gradlew.bat normalization is excluded from this work;
its raw hash remains `03754c4c3d9206f5c9ef69e41990dfe26a05251e`.

## Authorized synthetic scope

App settings currently contain no runtime calibration loader. Existing vision
profiles are explicitly synthetic (tools/vision-eval/README.md). ADRs 0011–0015
require caller-measured bounds, calibrated recognition/tracking, independently
confirmed exact inventory and caller-supplied legal launch positions/power policy.
None may be inferred from synthetic fixtures or repeated detections.

The user explicitly chose an original synthetic fixture demo, keeping live aiming
unavailable until a measured profile exists. SyntheticDemoActivity generates the
source on screen and requires fresh full-display Android consent. The actual capture
feeds marker validation, native detection, stationary estimation, budgeted planning
and a separate synthetic trajectory preview. See ADR 0025. Physics now supplies
event-resolved centres alongside its unchanged event identity/time snapshot.

## Independent frame ownership work

Added a packed RGBA owned-copy boundary and three JVM regression tests for
row/pixel padding, borrowed buffer position, ownership, short planes, arithmetic
bounds, overlapping rows and invalid timestamp. No Image/plane is retained.
The copier is wired to CaptureService for configured synthetic aiming only. It rejects frames over 4,194,304
pixels explicitly rather than permitting unbounded copy allocations.

Windows environment for all Gradle runs:
JAVA_HOME=`C:\Program Files\Java\jdk-18.0.1.1`,
GRADLE_USER_HOME=`$PWD/.tools/gradle-home`,
ANDROID_HOME=`$PWD/.tools/android-sdk`.

Command (both red and green runs):

```text
./gradlew.bat :app:testDebugUnitTest --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'
```

Red: BUILD FAILED, unresolved RgbaFrame in compileDebugUnitTestKotlin (expected
missing implementation); `.tools/cb35-red.log`.
Green: BUILD SUCCESSFUL in 12s; eight app JVM tests (three new), zero failures,
errors or skips; `.tools/cb35-frame-green.log` and JUnit XML inspected.

NO-SOURCE tasks: model/physics/planner compileJava and processResources,
capture/overlay/vision compileDebugJavaWithJavac, app compileDebugUnitTestJavaWithJavac.
SKIPPED tasks: model/physics/planner checkKotlinGradlePluginConfigurationErrors.
Gradle reports deprecated features incompatible with Gradle 10.

`./gradlew.bat ktlintCheck detekt --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'`:
BUILD SUCCESSFUL in 23s, both tasks executed; `.tools/cb35-frame-quality.log`.

## Pipeline validation history

All Gradle commands below also use `--max-workers=1` and
`'-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'`.

- `ktlintFormat :app:testDebugUnitTest :core:physics:test`: BUILD SUCCESSFUL,
  1m3s (`.tools/cb35-first-build.log`).
- `ktlintFormat :app:testDebugUnitTest :core:physics:test detekt :app:lintDebug`:
  app/physics tests passed; Detekt FAILED with 34 issues (complexity, line length,
  UI constants and function count); lint was not reached (`cb35-pipeline-check.log`).
- `ktlintFormat projects assembleDebug test check lint :app:assembleDebugAndroidTest
  :core:overlay:assembleDebugAndroidTest :core:capture:assembleDebugAndroidTest
  :core:vision:assembleDebugAndroidTest`: tests passed; Detekt FAILED with two
  remaining issues (capture method length, tuple destructuring); lint/device package
  tasks not all reached (`cb35-full.log`). After fixes, identical command BUILD
  SUCCESSFUL in 1m16s (`cb35-full-2.log`), including lint and all four test APKs.
- `-PjvmOnly projects test check`, with ANDROID_HOME and ANDROID_SDK_ROOT removed:
  BUILD SUCCESSFUL in 20s (`cb35-jvm-only.log`).
- Freshness timer corrected to expire relative to observation receipt, not processing
  completion; preview errors made explicit. Subsequent `ktlintFormat assembleDebug
  test check lint :app:assembleDebugAndroidTest :core:overlay:assembleDebugAndroidTest`
  passed tests but Detekt FAILED with two preview method size/complexity findings
  (`cb35-preview-check.log`). Factored preview view creation before rerun.

App JVM suite currently contains 16 tests, including three owned-copy tests and
eight coordinator/profile tests: bounded latest-frame mailbox, unavailable live mode,
motion cancellation, pause/resume reset, worker errors/retry, stopped noncooperative
results, observation expiry, actual simulated fixture evidence and dense-board cost.
Existing versioned physics fixtures additionally check resolved event identities,
determinism, containment, body-target presence and collection ownership.

The 19-disc dense-board-v1 app JVM sample recorded 209,597,800ns with three displayed
plans (`app/build/test-results/testDebugUnitTest/TEST-io.github.carrombot.AimingCoordinatorTest.xml`
from the full-2 run). This is one synthetic host measurement, not a hard latency or
real-game accuracy guarantee.

Further validation and corrections:

- `cb35-preview-check-2.log` and `cb35-preview-check-3.log`: Detekt failed on the
  same overlong expression; ktlint had converted the first attempted block fix back
  to an expression. A named local removed the formatter/line-limit conflict.
- Same full preview validation/package command: BUILD SUCCESSFUL in 1m24s
  (`cb35-preview-check-4.log`). `ktlintFormat ktlintCheck detekt
  :core:overlay:assembleDebugAndroidTest` then passed in 42s
  (`cb35-preview-device-package.log`), packaging real touch-through/revocation/Hide
  preview regression coverage.
- Initial device synthetic run FAILED (2/2, 23.979s, `cb35-adb-synthetic.log`):
  actual captured fixture produced no recommendation, and direct generated fixture
  was INCOMPLETE_INVENTORY. Normalization moved the pocket outside the default
  0.15 corner gate. Explicit synthetic cornerFraction=0.20 corrected this fixture
  calibration. Inventory/completeness and confidence checks were not relaxed.
- `ktlintFormat assembleDebug test check lint :app:assembleDebugAndroidTest`:
  BUILD SUCCESSFUL in 1m11s (`cb35-fixture-fix.log`). Corrected native fixture
  test then passed, OK (1 test), 0.903s (`cb35-adb-fixture-corrected.log`).
- Stronger versioned event geometry assertions compare actual wall gaps, body
  contact distances and pocket aperture membership against existing fixture
  dimensions/tolerances. `ktlintFormat projects assembleDebug test check lint`
  passed in 45s (`cb35-final-full.log`).
- Self-review additionally guarded runtime failures from superseded planner
  requests, so stale errors cannot fail the current board. `projects assembleDebug
  test check lint :app:assembleDebugAndroidTest` passed in 51s
  (`cb35-request-guard.log`).
- Final SDK-free `-PjvmOnly projects test check` (both Android SDK environment
  variables removed) passed in 20s (`cb35-final-jvm.log`).
- Synthetic consent now uses distinct request codes, discards cancelled/recreated
  requests and makes permission/service startup failures visible. Added native
  denial/retry regression. `ktlintFormat projects assembleDebug test check lint
  :app:assembleDebugAndroidTest` passed in 1m17s (`cb35-consent-final.log`).
- Final smoke assertions require displayed evaluated power, not merely a synthetic
  label/empty scene. Test-only `ktlintFormat ktlintCheck detekt
  :app:assembleDebugAndroidTest` passed in 46s (`cb35-smoke-assertion.log`).

Final inspected JVM XML: **210 tests**, zero failures/errors/skips: model 17,
physics 87, planner 63, vision 23, overlay 4, app 16. Capture JVM tests are
NO-SOURCE; its behavior is covered by device tests.

NO-SOURCE in full validation: JVM-module compileJava/compileTestJava and
processResources; capture/overlay/vision compileDebugJavaWithJavac,
mergeDebugNativeLibs and stripDebugDebugSymbols; app mergeDebugNativeDebugMetadata;
all Android modules compileDebugUnitTestJavaWithJavac; capture
compileDebugUnitTestKotlin/processDebugUnitTestJavaRes/testDebugUnitTest. Test APK
packaging also has absent Java/native inputs (NO-SOURCE). The three JVM modules'
checkKotlinGradlePluginConfigurationErrors and app preDebugAndroidTestBuild are
plugin-SKIPPED. These are not passing test cases.

## API 31 device validation

Disposable CB13 emulator, host OpenGL, 1024MB; Gradle stopped while emulator runs.
Waited for sys.boot_completed=1 AND normal launcher focus; temporary 480x800/160dpi
display, all adb install -r -t commands completed Success before testing. Ran directly
through adb rather than UTP to avoid another memory-heavy host JVM.

```text
adb shell am instrument -w -r PACKAGE/androidx.test.runner.AndroidJUnitRunner
```

Actual successful runs (all require explicit OK, not shell exit status alone):

| Package | Result | Time | Local log |
| --- | --- | --- | --- |
| io.github.carrombot.test | OK (8 tests) | 23.085s | .tools/cb35-adb-app-power.log |
| io.github.carrombot.core.overlay.test | OK (10 tests) | 8.291s | .tools/cb35-adb-overlay.log |
| io.github.carrombot.core.capture.test | OK (2 tests) | 2.846s | .tools/cb35-adb-capture.log |
| io.github.carrombot.core.vision.test | OK (13 tests) | 1.317s | .tools/cb35-adb-vision.log |

**33 device tests**, zero final failures. Original synthetic failure is retained
above. The app suite obtains genuine Android consent, uses actual captured source
pixels to produce evaluated recommendations, clears preview on Pause/Stop, produces
fresh recommendations with displayed evaluated power on Resume/restart and releases
service/windows on End sharing. Denied synthetic consent can be retried without
starting capture. Earlier app runs also passed seven tests (21s, cb35-adb-app.log)
and eight tests (18.331s, cb35-adb-app-final.log) before the stronger power assertions.
Existing capture lifecycle covers denial, rotation, background/recreation and cleanup.
Overlay tests attach the real preview, verify one real tap reaches the underlying
Activity, and verify revocation/Hide clear both windows and hidden updates never
reattach. Software Canvas synthetic trajectory goldens are unchanged.

Synthetic native five-observation-plus-plan processing recorded 556,779,300ns on
first warmup and 157,783,500ns on a subsequent run, three displayed proposals. The
fixture uses explicit logical observation timestamps; these numbers exclude real
stationary dwell and are not end-to-end real-time latency or accuracy measurements.

Display size/density reset before emulator shutdown. No captured screen images
were persisted. No manual TalkBack, OEM, modern app-only capture, cross-application
game accuracy or hard latency/battery claim is made.

Complete staged source/test/documentation diff reviewed, whitespace checks clean;
preexisting gradlew.bat change excluded. Remaining delivery gates: PR/CI/Codex review
and guarded merge. Jira remains In Progress until the PR is opened; no Done claim
is made before reviewed merge.
