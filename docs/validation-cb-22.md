# CB-22 validation

2026-11-15, Windows, local JDK 18 (bytecode 17), SDK 36; disposable CB13 API 31
x86_64 emulator. Hosted CI uses JDK 21. No real game or personal screen pixels used.

## Commands and outcomes

- `gradlew.bat :core:vision:compileDebugAndroidTestKotlin --console=plain`: expected
  initial red failure, unresolved detector/profile/result types before implementation.
- `gradlew.bat ktlintFormat :core:vision:connectedDebugAndroidTest :core:vision:testDebugUnitTest detekt --console=plain`:
  initial implementation passed nine device tests but failed detekt (18 style findings).
  Added fixture/contract tests exposed JUnit lambda typing at compile time; fixed.
  The next run found noisy fixture recall 4/9 and two long lines. Fixed the dark
  pocket saturation profile and implemented wrapped HSV hue masks before contour
  extraction. All eleven device tests then passed (27 TP / 0 FP / 0 FN), with one
  remaining long test line fixed before full validation.
- `gradlew.bat projects assembleDebug test check lint connectedDebugAndroidTest --console=plain`:
  PASS, 2m25s, 330 tasks (49 executed / 281 up-to-date). XML: 105 JVM tests (80
  physics, 17 model, 1 app, 7 vision); 18 device tests (5 app, 2 capture, 11 vision).
  Zero failures/errors/skips. This preceded the final dimension-rounding regression.
- `gradlew.bat :core:vision:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.carrombot.core.vision.ObjectDetectorTest#roundingOfRectifiedDimensionsPreservesBothPhysicalAxes --console=plain`:
  expected RED, 34s: physical Y expected 220 mm, actual 221 mm for 403x401 pixels.
- `gradlew.bat ktlintFormat assembleDebug :core:vision:test :core:vision:lint :core:vision:connectedDebugAndroidTest ktlintCheck detekt --console=plain`:
  final GREEN after separate physical axis scaling and conservative radius mapping;
  PASS, 1m6s, 183 tasks (29 executed / 154 up-to-date). All 12 vision device tests
  and 7 vision JVM tests pass, zero failures/errors/skips. App rebuilt successfully.
- With `ANDROID_HOME` and `ANDROID_SDK_ROOT` unset,
  `gradlew.bat -PjvmOnly projects test check --console=plain`: PASS, 37s, 14 tasks
  (3 executed / 11 up-to-date). Model/physics tests were up-to-date; planner is NO-SOURCE.

Empty planner/overlay tests and capture JVM tests are NO-SOURCE, as are ordinary
Java/resources/native compilation tasks with no matching sources. Those are not
behavioral coverage. Gradle configuration-error tasks and Android pre-test build
are configuration SKIPPED; no test cases skipped. Existing Gradle deprecations remain.
No OpenCV dependency or native packaging changes were made in this ticket.

## Fixture measurements and limits

objects-v1: six original annotated rectified synthetic PNG screenshots, three with
nine fully identifiable objects each. Class + center + radius matching is one-to-one,
with <2 mm tolerances. Measured TP=27, FP=0, FN=0: precision 1.0, recall 1.0 on
these complete fixture objects only. Occluded/merged/clipped shapes have no expected
recognitions and are separately checked for zero invented objects, as are hollow
rings, tiny circles, a line and an interior pocket-colored circle. They are not
included as recall positives. See the fixture README for generation/label details.

The suite uses caller profiles calibrated to its colors and sizes. It is not an
independent real-game benchmark. No empirical game accuracy, latency, sustained
capture throughput or leak-rate claim is made. Arbitrary matching decorations,
patterned discs, shadows, unknown skins, mild occlusion and touching objects remain
limitations. Clipped pockets are deliberately rejected. API 26-30/32+, ARM and
16 KiB runtime behavior are unverified. Capture integration/tracking/calibration
tooling remain assigned to CB-35/CB-23/CB-24 respectively.

Input preservation, owned immutable output, explicit invalid-frame/aspect failures,
ambiguous classes, public configuration validation and the rounded-axis regression
are exercised. Native-error injection and stress/concurrent/leak testing were not run.
