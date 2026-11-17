# CB-38 executed validation

2026-11-17, Windows PowerShell, Python 3.13.14. JDK 18.0.1.1 local fallback;
CI uses JDK 21. Existing ignored Gradle cache and Android SDK 36; emulator off.
Source videos remain external/read-only. No screenshots, videos, account data,
machine source paths, equipment guesses or physical-mm estimates are committed.

## Red/green and numerical evidence

- `py -m unittest discover -s tools/calibration -p test_compare.py -v`: initial
  exit 1, expected `ModuleNotFoundError: No module named 'compare'` before implementation.
- First implemented run: exit 1, nine passed/one failed. The single-input test
  expected only exponent to be unobservable; the finite-difference diagnostic also
  correctly found normal restitution unobservable before any rail. Corrected the
  assertion to include both; no recording annotations or algorithm tuned for this.
- Final `py -m unittest discover -s tools/calibration -v`: exit 0, **24 tests**
  (12 previous, 12 new). New tests cover independent closed-form speed drag,
  small-b limit, analytic rebound/rest, explicit shifted-release observations,
  bounded failures, repeated-centre/gap handling, extraction PTS/coordinate/missing
  marker consistency, training/historical split isolation and internal fold
  omission, conditioning, independent midpoint-ODE parameter recovery, and an
  unidentifiable single-input/no-rail design. No private media/services in tests.
- `py -m unittest discover -s tools/vision-eval -v`: exit 0, nine tests.
- `py tools/calibration/compare.py --extractions .validation --output
  docs/calibration/comparison-v1/report.json`: exit 0. Three candidates × six
  internal folds; no historical holdout/break sample scoring. The plan was committed
  before fitting in `1066c23`.
- Repeated with `--output .validation/cb38-reproduced.json`: exit 0. Explicit Python
  bytes-equality assertion passed. SHA-256 of both JSON reports:
  `3b06dc24ead7315b8c63036b7aee06b2381d21bd649d3c955f85a96d8cef43e7`.
  Declared cross-runtime numeric reproduction tolerance: 1e-8 absolute.
- `py tools/calibration/render_comparison.py docs/calibration/comparison-v1`:
  exit 0, generated tables and signed-residual SVG. XML parsing passed; no visual
  device validation or physical calibration is implied by the plot.
- `Get-FileHash -Algorithm SHA256` for each of the six authorized originals:
  all hashes match the v1 inventory. Numeric extractor audit checks actual
  PTS/time_base and exact training-coordinate agreement, preserving four missing
  release-marker frames and 94 repeated-centre pairs without interpolation.
- `git diff --exit-code origin/main -- docs/calibration/recordings-v1`: exit 0;
  previous measurements/plans/reports/replay unchanged.

Measured internal CV RMSE: constant 0.10897912, speed drag 0.10903237, proxy curve
0.08393731 BW. Curve meets the predeclared follow-up hypothesis rule (22.98%
aggregate improvement, five/six folds) but every fold fails 0.02 BW; bounce/rest
errors also fail. No usable profile, real-game accuracy or live enablement claimed.

## Gradle commands actually run

Environment: `JAVA_HOME` set to installed JDK; `GRADLE_USER_HOME` points to the
ignored repository cache. SDK-free run removed `ANDROID_HOME`/`ANDROID_SDK_ROOT`;
full run set `ANDROID_HOME` to the existing ignored SDK. Both used
`--max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --console=plain`.

- `.\gradlew.bat -PjvmOnly projects test check` with those flags: exit 0,
  **BUILD SUCCESSFUL in 24s**, 18 actionable tasks, three executed/15 up-to-date.
  Detekt and ktlintCheck executed; model/physics/planner test tasks UP-TO-DATE.
- `.\gradlew.bat projects assembleDebug test check lint` with those flags:
  exit 0, **BUILD SUCCESSFUL in 28s**, 213 actionable tasks, seven executed/206
  up-to-date. Build/lint/check succeeded; app/model/physics/planner/vision/overlay
  unit test tasks UP-TO-DATE, not freshly executed. Existing XML inventory totals
  220 tests (model 17, physics 90, planner 66, vision 25, overlay 4, app 18).
  These cached results are not represented as 220 newly run tests.

NO-SOURCE: model/physics/planner Java compilation/resources (and JVM test Java
sources), Android module Java compilations, app Android native debug metadata,
capture/overlay/vision native library merge/strip tasks, and capture unit-test
Kotlin/Java/resources/test tasks. Capture supplied no JVM behavior tests.
Kotlin configuration-error checks and app preDebugAndroidTestBuild were SKIPPED.
The existing Gradle-10 deprecation notice remains. Instrumentation was not run:
this ticket changes offline Python/docs only; no Android/native detector behavior
changed. CB-36 synthetic-demo startup timeout remains an unrelated unresolved
device limitation; these build checks do not resolve it.

## Review and delivery safeguards

Complete source/doc diff and generated report summaries reviewed; numerical input
hashes and output schema contain no raw frames or local source paths. Original
wrapper normalization is preexisting and excluded; raw `gradlew.bat` hash remains
`03754c4c3d9206f5c9ef69e41990dfe26a05251e`. `git diff --check` passes.
Production module graph/contracts and synthetic configuration are unchanged.

PR review, exact-head approval, required hosted checks and guarded merge evidence
will be recorded in Jira after they actually complete. Readiness remains blocked
by the concrete [controlled acquisition protocol](calibration/controlled-phone-protocol.md)
and separate real-game vision/integration gates.
