# CB-24 validation

2026-11-15, Windows, local JDK 18, Android SDK 36; disposable API 31 x86_64
emulator CB13. Hosted CI uses JDK 21. Original synthetic assets only.

## Executed commands and outcomes

- `py -m unittest discover -s tools/vision-eval -v`: intentional initial red,
  missing `evaluate` module; initial green 5 tests; final green 9 tests including
  actual device snapshot golden, asset SHA-256, crowded maximum matching,
  duplicates/wrong class, failure FN, bad/rejected board, nonfinite/gate/geometry
  rejection, complete IDs, ordering and undefined ratios.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint connectedDebugAndroidTest`:
  first attempt failed configuration when adding an external Android test asset
  directory (AGP source-set class-cast exception). Removed that configuration;
  manifest now resides in existing test assets. Second attempt ran device tests
  successfully but failed Detekt on two long Kotlin lines (1m2s, 324 tasks,
  38 executed/286 up-to-date). First formatting correction still failed Detekt
  (27s, 236 tasks, 17 executed/219 up-to-date); multiline arguments fixed it.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint :core:vision:assembleDebugAndroidTest`:
  PASS, 24s, 237 tasks, 14 executed/223 up-to-date. JUnit XML totals 121 JVM tests
  (17 model, 80 physics, 23 vision, 1 app), zero failures/errors/skips; unchanged
  tests were up-to-date. Connected XML from the device run: 20 tests (5 app,
  2 capture, 13 vision), zero failures/errors/skips. One new vision exporter test.
- Direct prediction retrieval after connected tests failed because Gradle uninstalls
  the test package: `run-as: unknown package`. The evaluator rejected the non-JSON
  output. Corrected the documented workflow to manually install/instrument first.
- `adb install -r core/vision/build/outputs/apk/androidTest/debug/vision-debug-androidTest.apk`:
  Success. `adb shell am instrument -w -e class io.github.carrombot.core.vision.VisionEvaluationTest io.github.carrombot.core.vision.test/androidx.test.runner.AndroidJUnitRunner`:
  PASS, one test, 0.746s. Retrieved numeric JSON using Python subprocess bytes and
  `adb exec-out run-as io.github.carrombot.core.vision.test cat files/vision-predictions.json`.
- `py tools/vision-eval/evaluate.py core/vision/src/androidTest/assets/vision-v1.json test-fixtures/vision/sample-predictions.json --output test-fixtures/vision/sample-report.json`:
  PASS. Eight board TP, zero board FP/FN. Mean ordered corner error 0.4578213px,
  maximum 2.236068px. Objects TP27, FP0, FN0 (12 pockets; 3 each black, white,
  queen, striker, obstacle). Four failure statuses are expected negative-board
  rejections. Per-class matched center/radius errors and edge scales in report.
- With Android SDK environment variables unset,
  `gradlew.bat -PjvmOnly projects test check`: PASS, 19s, 14 tasks,
  3 executed/11 up-to-date. No domain dependency changes.

NO-SOURCE: planner implementation/tests, overlay implementation/tests, capture JVM
tests, absent Java/resources/native debug sources across modules. These are not
executed tests. Kotlin configuration checks and app preDebugAndroidTestBuild SKIPPED
are Gradle tasks, not skipped test cases. Gradle emits existing deprecation warnings.

## Scope and limits

The exporter executes current native detectors; CI golden scoring consumes a saved
snapshot and cannot replace device regression. CI adds the Python suite to its JVM
job. This dataset is calibrated synthetic component evidence, not held-out game
accuracy, capture integration, performance, memory-leak or cross-device validation.
Scale uses independently supplied dimensions; perspective edge coefficients are
diagnostics, never a global affine replacement for the homography. No production
behavior or dependency graph changed. No captures, credentials or local paths added.
