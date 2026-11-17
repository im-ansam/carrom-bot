# CB-37 validation

2026-11-17. Base `origin/main` b6887931aa84563b3ccb7cb991fe98e9b5133e87.
Read root instructions, CB-37, parent CB-6, completed prerequisites CB-15/17/19/24/36,
architecture and their ADRs. Production library/app behavior and dependency edges
are unchanged. The only JVM addition is a test-tool replay adapter; raw media and
private inspection images remain external. Preexisting `gradlew.bat` normalization
is preserved and excluded; raw hash remains `03754c4c3d9206f5c9ef69e41990dfe26a05251e`.

## Executed commands and outcomes

Windows, JDK18.0.1.1/SDK36; Gradle wrapper with `--max-workers=1` and
`'-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8'`. Ignored local Gradle cache;
SDK environment removed for SDK-free commands. Python 3.13.14, PyAV19.0.2 and
Pillow13.0.0. Hosted CI uses JDK21. Log names below are ignored local evidence.

- Initial sandbox `py --version` FAILED, process Access denied. Subsequent Python
  commands ran with execution access; the failed attempt is not a test success.
- RED: `py -m unittest discover -s tools/calibration -v` FAILED with missing
  `analyze` module before implementing the meaningful numerical contracts.
  First implementation passed nine tests. Added high-gain recovery regression then
  FAILED twice (MSE 0.0042612 with coarse coordinate search; 0.00425234 with simplex
  and sparse coarse seeds). Expanded the fixed training-only grid; unchanged
  1e-7 assertion now passes. No synthetic tolerance or held-out data was used to
  conceal the optimizer issue.
- Final `py -m unittest discover -s tools/calibration -v`: PASS **12 tests**,
  6.932s. Known-parameter/perturbed synthetic recovery, optimizer regression,
  timestamp gaps/repeated positions, missing/duplicate/nonmonotonic/nonfinite data,
  reset rejection, insufficient samples, explicit scale, drag/bounce/rest,
  holdout isolation, plan/split version and unidentified tangent fields.
- `py -m unittest discover -s tools/vision-eval -v`: PASS existing **9 tests**,
  0.478s; no native detector changes or device regeneration claimed.
- `-PjvmOnly ktlintFormat :core:physics:compileTestKotlin --console=plain`:
  PASS, 1m6s, five tasks (two executed/three up-to-date), `cb37-compile.log`.
- `-PjvmOnly ktlintFormat :core:physics:test --tests '*CalibrationReplayTest'
  :core:physics:replayCalibration '-PcalibrationInput=../../docs/calibration/recordings-v1/replay.csv'
  --console=plain`: PASS, 24s, nine tasks (five executed/four up-to-date),
  `cb37-replay.log`. Three original analytic adapter tests pass, including
  malformed input and deliberately wrong expected endpoint. This earlier command
  used the task's original module working directory; final task uses root directory.
- First `ktlintFormat projects assembleDebug test check lint --console=plain`:
  FAILED at Detekt, 1m1s, 165 tasks; 22 issues in new tooling (source package/test
  location, function length, formatting and numeric CSV indices). Moved tooling
  under its proper `src/test/kotlin/io/github/...` path, using existing test-source
  conventions, and split the entry point. No static-analysis rule was disabled.
- `ktlintFormat projects assembleDebug test check lint :core:physics:replayCalibration
  '-PcalibrationInput=../../docs/calibration/recordings-v1/replay.csv' --console=plain`:
  PASS, 1m53s, **215 tasks, 12 executed/203 up-to-date**,
  `cb37-full-replay.log`. Android assembly/lint, all configured JVM tests,
  ktlint and Detekt pass. This was before the task working directory was made
  explicitly root-relative; numeric replay input was unchanged.
- Initial final SDK-free `-PjvmOnly projects test check :core:physics:replayCalibration
  -PcalibrationInput=docs/calibration/recordings-v1/replay.csv --console=plain`:
  FAILED before tasks, 15s: PowerShell split the unquoted property and Gradle reported
  `Task '.csv' not found`. Documentation now quotes the entire property.
- Corrected `-PjvmOnly projects test check :core:physics:replayCalibration
  '-PcalibrationInput=docs/calibration/recordings-v1/replay.csv' --console=plain`:
  PASS, 44s, **19 tasks, four executed/15 up-to-date**,
  `cb37-jvm-final-quoted.log`. Root-relative replay, Detekt and ktlint pass;
  domain test tasks reuse the successful full-run results.

Current JUnit XML totals **220 tests, zero failures/errors/skips**: prior217 plus
three replay-adapter tests (physics90, model17, planner66, vision25, overlay4, app18).

NO-SOURCE remains for capture JVM tests and absent Java/resources/native-debug-symbol
inputs. Plugin configuration-error checks are SKIPPED; no behavioral tests are
silently skipped. Existing Gradle10 deprecation warning remains. Cached/up-to-date
tasks are not claimed to be freshly executed tests. No Android instrumentation was
run: capture, vision, app and overlay production code are unchanged, and this scope
is offline. CB-36's unresolved intermittent synthetic-demo startup remains unchanged.

## Measured local analysis

Executed `extract.py` on each of the six supplied external originals, preserving
read-only inputs; repeated the break extraction after adding its narrow pre-rack
ROI. Final inventory hashes and actual PTS bounds/gaps are in numeric measurements.
No screenshot/video/path/account data is committed or uploaded.

Executed `prepare.py .validation --output docs/calibration/recordings-v1/measurements.json`,
`analyze.py .../measurements.json --output .../report.json`,
`sensitivity.py .../measurements.json --output .../sensitivity.json`, and
`render.py docs/calibration/recordings-v1`. All PASS. Visual checks of private frame
crops reviewed nine wall release/aim intervals and three break approaches/contact
brackets; conservative exclusions include maximum-wall repetition3 after30.45s.
Initial break contact estimates based on the measurement ROI edge were rejected
during visual checking; final brackets use visible rack motion instead of clipping.

Repeated final report generation into an ignored separate output: byte-identical
SHA-256 `f1a3df51a2e2d8617c3a0eabc6c7225baccb3ef49894b723a400fcb9f5b599dc`.
All generators explicitly write LF, avoiding Windows/Git newline differences.
After that portability adjustment, all 12 calibration tests passed again in2.691s.
Reproduction expectation is absolute 1e-8 numeric tolerance with the same versioned
measurements/configuration. Optional native decoder pixel effects are separately
documented; no untested cross-decoder bit identity claim.

Production fixed-1ms Kotlin replay on all nine measured wall tracks: PASS.
Maximum analytic/production discrepancy **1.2087074e-7 BW**, below the explicit
0.002 BW interpolation comparison tolerance. Held-out production RMSE is
0.03674133/0.04987669/0.07677892 BW for low/medium/maximum respectively, agreeing
with the analytic report. Replay is conditional on measured launch position/direction
and release proxy; it does not independently validate pre-shot aim acquisition.

All held-out trajectories fail the predeclared 0.02 BW exploratory target. The
combined fit reaches normal restitution1.0 at the bound; short local fits are timing
sensitive and sometimes implausible. Training-only sensitivity, event/stop errors,
all clip dispositions, missing measurements and unknown parameters are retained.
**No usable profile is emitted; live aiming remains disabled.** See
[analysis, plots and precise blockers](calibration.md). This negative calibration
result is an allowed CB-37 deliverable, not an engineering test failure to suppress.

Exact-head CI/review/merge evidence is recorded in the PR and Jira after completion.
