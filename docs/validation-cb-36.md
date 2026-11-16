# CB-36 validation

2026-11-16. Base `origin/main` ffa4ff7141f3dbca040171d1219653d4b0a7a965.
Read root instructions, CB-36, parent CB-5, Done dependencies CB-19/24/35,
shared contracts and physics/vision/planner/integration ADRs before implementation.
Production behavior and dependency graph unchanged. Added acceptance/calibration
documentation and three stationary multi-pocket regression tests.

## Commands actually executed

Windows, local JDK 18.0.1.1, SDK 36; hosted CI uses JDK 21. Gradle commands use
`gradlew.bat --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'`
with ignored local Gradle cache. SDK variables removed for SDK-free commands.
Logs named below are ignored local evidence, not committed machine paths.

- `-PjvmOnly :core:planner:test --tests '*PhaseOneAcceptanceTest' --console=plain`:
  PASS, three tests, 17s (`cb36-targeted.log`). The fixture is new evidence rather
  than a production logic change. Negative control: changed second capture travel
  distance 15 -> 16mm; same command FAILED, exactly one timing assertion among
  three tests (`cb36-negative-control.log`, 3s). Restored independently derived 15mm.
- `ktlintFormat projects assembleDebug test check lint :app:assembleDebugAndroidTest
  --console=plain`: PASS, 46s, 246 tasks (13 executed, 233 up-to-date),
  `cb36-full.log`. Includes final green restored regression, Android assembly/lint,
  all configured JVM tests, ktlint and Detekt.
- `-PjvmOnly projects test check :core:physics:benchmarkShots
  -PbenchmarkWarmup=100 -PbenchmarkRounds=500 --console=plain`: PASS, 19s,
  19 tasks (4 executed, 15 up-to-date), `cb36-jvm.log`; tests up-to-date from full
  validation. Gradle daemon stopped before emulator use.
- `py -m unittest discover -s tools/vision-eval -v`: first sandbox attempt failed
  with Python process Access denied; reran with execution access, PASS nine tests,
  0.113s. Includes saved device prediction/report golden and asset hash checks.
  No detector changes or fresh vision report generation are claimed.

Current JUnit XML: **217 tests**, zero failures/errors/skips: model17, physics87,
planner66, vision25, overlay4, app18. Only changed/outdated tasks executed; cached
test results are identified above. New acceptance tests cover two stationary discs
captured in distinct pockets from one launch, complete analytic event times at
1e-7s, repeated/fine-clock evidence, and rejection of a partial one-pocket outcome.

NO-SOURCE: capture JVM tests; absent Java/resources/native-debug-symbol inputs.
Three JVM Kotlin configuration checks and app preDebugAndroidTestBuild are plugin
SKIPPED tasks, not skipped behavioral tests. Existing Gradle deprecation warning
remains. No failure is counted as successful validation.

## Physics measurement

Same unchanged synthetic-shot-v1 inputs and [method](../benchmarks/README.md):
100 warmup batches, 500 measured shots per family, 3000 total. Host JDK18;
one run, diagnostic only; no timing threshold, speedup or device inference.

| Family | Shots | Elapsed ms | Mean ns/shot | Events | Frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| direct | 500 | 23.840 | 47679.8 | 1000 | 1000 |
| disc-bank | 500 | 19.444 | 38887.0 | 1500 | 1000 |
| striker-bank | 500 | 20.826 | 41651.4 | 1500 | 1000 |
| combination | 500 | 35.089 | 70178.4 | 1500 | 1000 |
| multi-disc | 500 | 50.388 | 100775.0 | 2000 | 1000 |
| multi-pocket | 500 | 11.311 | 22621.0 | 1000 | 1000 |

Warmup checksum 26171.52330865054. Family checksums respectively 25000,
25357.616543, 25000, 15000, 15000, 11000. This older benchmark intentionally
retains its six versioned fixtures; stationary-split-v1 is separately regression
tested and not silently folded into the old throughput baseline.

## Device and release limits

Disposable API31 x86_64 CB13 emulator, 480x800 pixels/density160 (480dp wide),
boot_completed=1 and normal launcher focus verified before tests. Both
`adb install -r -t app-debug.apk` and `app-debug-androidTest.apk` returned Success
before instrumentation. No test assertions or interaction retries were weakened.

- `adb shell am instrument -w -r io.github.carrombot.test/androidx.test.runner.AndroidJUnitRunner`:
  initial run FAILED, eight tests / one failure, 52.707s (`cb36-adb-app.log`).
  `consentedSyntheticCaptureShowsRealRecommendationAndRepeatedStartStopClearsWindows`
  timed out at `Real capture-to-recommendation` after the 20s wait. Other seven
  passed, including native fixture, denial/retry/overlap and capture lifecycle.
  Numeric app logs show no crash. Emulator boot-era CPU report showed swapping/load,
  but this does not establish the cause; the intermittent timeout is unresolved.
- Same command with `-e class io.github.carrombot.SyntheticPipelineTest#consentedSyntheticCaptureShowsRealRecommendationAndRepeatedStartStopClearsWindows`:
  PASS, explicit OK (1 test), 6.787s (`cb36-adb-isolated.log`), without code changes.
  Genuine consented captured pixels produced displayed evaluated power, fresh
  resumed/restarted recommendations and End sharing cleanup. This repeat does not
  erase the original timeout or establish cold-start reliability.
- Repeated complete app command: PASS, explicit OK (8 tests), 23.753s
  (`cb36-adb-app-repeat.log`), unchanged APKs/assertions. This includes real
  capture-to-evaluated-power, Pause/Resume, repeated aiming Start/Stop, End sharing,
  denial/retry and overlap refusal. No further repeats were used to claim timing.
  Overlay/capture/vision standalone suites were not rerun for these test/docs-only
  changes; their prior 25 passing device tests remain historical CB-35 evidence.

Restored `wm size`/`wm density` defaults and shut down emulator after tests. No
Gradle daemon or emulator intentionally remains running. Reviewed complete diff,
documentation links, staged paths and whitespace before commit; preexisting
gradlew.bat normalization excluded (raw hash matches tracked original).

See the
[acceptance matrix/checklist](phase1-acceptance.md) for coverage and remaining
manual work. No real-game profile/accuracy, manual TalkBack, OEM, modern app-only
capture, hard end-to-end latency, battery or release-signing claim is made.
All data is original synthetic; captured images are never persisted.
