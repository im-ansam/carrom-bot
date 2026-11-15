# CB-21 validation — 2026-11-15

Scope: official OpenCV Android integration, board-boundary preprocessing/detection,
immutable pixel/mm homography and image normalization. No live pipeline wiring.

## Environment and successful commands

Windows PowerShell, JDK 18.0.1.1 (bytecode target 17), wrapper Gradle 9.9.1,
ignored local Gradle cache and Android SDK 36/build-tools 36.0.0. Disposable API31
x86_64 emulator `CB13`, no personal accounts/content. Hosted CI uses JDK21.

* `py core/vision/src/androidTest/fixtures/generate.py`: generated 12 original
  synthetic PNGs using only the standard library, deterministic noise seed 21.
* `.\gradlew.bat ktlintFormat :core:vision:connectedDebugAndroidTest :core:vision:testDebugUnitTest detekt --console=plain`:
  PASS, 58s; 61 tasks, 12 executed, 49 up-to-date after the corner-jitter fix.
* `.\gradlew.bat projects assembleDebug test check lint connectedDebugAndroidTest --console=plain`:
  PASS, 2m20s; 330 tasks, 37 executed, 293 up-to-date. Compilation, all-module
  checks, ktlint, Detekt, Android Lint and connected instrumentation completed.
* With ANDROID_HOME and ANDROID_SDK_ROOT unset,
  `.\gradlew.bat -PjvmOnly projects test check --console=plain`:
  PASS, 27s; 14 tasks, 3 executed, 11 up-to-date. JVM model/physics/planner graph
  remains independent of Android and OpenCV.
* After adding upstream notice assets, `.\gradlew.bat assembleDebug --console=plain`:
  PASS, 17s; 129 tasks, 5 executed, 124 up-to-date. APK archive inspected for
  packaged OpenCV notices.

XML evidence: 103 JVM tests (17 model, 80 physics, 1 app, 5 vision), zero
failures/errors/skips. Device XML: 13 tests (5 app, 2 capture, 6 vision), zero
failures/errors/skips. Up-to-date test tasks reuse their successful XML results.

Vision JVM tests cover four-corner projective mapping against physical bounds,
interior/exterior round trips, invalid/reversed/nonconvex geometry, a projective
horizon and ownership after caller mutation. Device tests use actual packaged
OpenCV on RGBA frames: five golden corner annotations with 3px tolerance,
rotation/scale/perspective/noise, clean/noisy 45-degree boards, jitter stability,
warp dimensions/type/center marker, repeatability and unchanged input checksums,
blank/clipped/thin/multiple boundaries, invalid input/size, immutable results
after input release, plus 100 repeated detections after five warmups.

## Red/green evidence and resolved failures

Before changing canonicalization, the new
`smallCornerJitterDoesNotRotateNormalizedDiamondByNinetyDegrees` test failed in
`.\gradlew.bat :core:vision:connectedDebugAndroidTest --console=plain`:
6 tests, 1 failure, `Expected board, got Failed(reason=AMBIGUOUS_BOARD)`.
The fixture moves the top and left diamond corners by one pixel. Duplicate
Canny contours selected different starting corners. Tolerant corner tie-breaking
and cyclic duplicate matching fixed it; all six tests then passed with unchanged
assertions, followed by the full successful run above.

Initial `ktlintFormat :core:vision:testDebugUnitTest check` failed Detekt with
61 findings (matrix index magic numbers, formatting, generic cleanup catches,
spread operators and early returns). Subsequent focused runs narrowed findings
to line length; `ktlintFormat` also initially rejected adjacent KDoc/comment text.
These were corrected with structured cleanup, list conversion, formatting and
documented narrow suppressions for four-corner algebra/early rejection.
The first full `ktlintFormat projects assembleDebug test check lint connectedDebugAndroidTest`
run failed Detekt because the formatter collapsed a long function expression;
all its device tests passed. Rewriting that expression resolved the conflict.
No failing validation is being waived. Pillow was unavailable during tool discovery;
the fixture generator instead uses Python's standard library.

`git diff --cached --check` reports whitespace from copied upstream license/README
texts, which are retained as provided (repository line-ending normalization applies).
The same check excluding `core/vision/src/main/assets/third-party/**` passes;
no authored code/document whitespace findings remain.

## Measurements and limits

Final full-run test log: 600x700 synthetic square, 5 warmups + 100 detections,
median 1.9845ms, p95 3.2986ms on the API31 emulator. Timing includes detection,
not decoding, capture, initialization or image warp. It is a smoke measurement,
not a physical-device throughput/SLA or native heap leak claim.

The universal debug APK before adding notices was 142,693,908 bytes (~136MiB),
including the official AAR's four ABIs. Release ABI splitting/size optimization
is outside this ticket. Native symbol stripping reports `libc++_shared.so` and
`libopencv_java4.so` packaged unstripped; builds/lint succeed. Existing Gradle
deprecation warnings remain.

Expected NO-SOURCE tasks include Java/resources for Kotlin-only JVM modules,
empty planner and overlay code/tests, capture JVM tests, adapter Java sources,
and local native compilation (native binaries come from the AAR). NO-SOURCE is
not test evidence. Configuration-error/preDebugAndroidTestBuild SKIPPED tasks
are expected configuration tasks. No test cases were skipped.

Only synthetic images and API31 x86_64 native execution were verified. API26-30,
API32+, ARM devices and 16KiB runtime execution were not tested. Lint/build
checks are not device-compatibility evidence. Real game/rail discrimination,
low contrast, occlusion, strong perspective, player orientation and live capture
integration remain unverified. Confidence is geometric quality, not a calibrated
probability. See [ADR 0011](adr/0011-board-geometry.md).
