# Offline vision evaluation v1

Python 3.10+ standard library; no SDK, network, screenshots or native library needed
to score a saved prediction file. Native detector execution still requires Android.
Run from the repository root:

```powershell
py -m unittest discover -s tools/vision-eval -v
py tools/vision-eval/evaluate.py core/vision/src/androidTest/assets/vision-v1.json test-fixtures/vision/sample-predictions.json
```

To regenerate **actual** detector predictions on a disposable emulator:

```powershell
.\gradlew.bat :core:vision:assembleDebugAndroidTest
adb install -r core/vision/build/outputs/apk/androidTest/debug/vision-debug-androidTest.apk
adb shell am instrument -w -e class io.github.carrombot.core.vision.VisionEvaluationTest io.github.carrombot.core.vision.test/androidx.test.runner.AndroidJUnitRunner
```

Gradle connected tests uninstall the test package after running, so use this manual
installation for export. The private file requires `run-as`. Use Python to preserve UTF-8
bytes (PowerShell 5 redirection can produce UTF-16):

```powershell
py -c "import subprocess,pathlib; pathlib.Path('predictions.json').write_bytes(subprocess.check_output(['adb','exec-out','run-as','io.github.carrombot.core.vision.test','cat','files/vision-predictions.json']))"
py tools/vision-eval/evaluate.py core/vision/src/androidTest/assets/vision-v1.json predictions.json --output report.json
```

The exporter reads only original synthetic assets, releases bitmap/Mat
resources, and records explicit detector failures. It never reads capture frames.
Profiles are the synthetic CB-22 HSV/radius calibration, not actual game settings.

## Format and metrics

The authoritative fixture manifest is `core/vision/src/androidTest/assets/vision-v1.json`.
Version 1 has a dataset ID and unique frame IDs. Each frame declares `mode` (`board`
or `objects`), asset path and SHA-256. The exporter verifies assets before detection;
scoring requires every prediction's hash to agree. Bump the dataset ID when changing
labels, physical dimensions, profiles or conventions; retain old results as versioned
artifacts. Unknown versions, missing/extra/duplicate frames, invalid statuses,
nonfinite coordinates, unknown classes and malformed circles/corners fail explicitly.

Board labels are four clockwise **original pixel** corners in detector order
(minimum x+y with detector tie rule), or null when the policy requires rejection.
Object labels are absolute **board millimetres**, top-left origin, +X right, +Y down;
`bounds_mm=[minX,minY,maxX,maxY]` maps rectified pixels 0..width-1 and 0..height-1.
Object radii use mm. No player orientation or inferred occluded labels.

Prediction version/dataset/IDs match the manifest. Board status is `found` with
`corners`, or `failed` with `reason`. Object status is `observed` with `objects`
(possibly empty), or `failed` with `reason`. Failures cannot carry geometry.
Detector failures on positive frames count all labels as false negatives; valid
rejections on negative boards also appear in `failed_frames`, so this is a raw
failure-status count, not an accuracy metric.

Objects match **within class and frame** using deterministic maximum-cardinality
bipartite matching, Euclidean center error <=2mm and absolute radius error <=2mm.
Sorted geometry and distance/index tie order make input permutation irrelevant.
Each prediction and label participates at most once. Wrong class, duplicates and
out-of-gate predictions count FP; unmatched labels count FN. Precision=TP/(TP+FP),
recall=TP/(TP+FN); zero denominators and absent matched-error samples yield JSON null.
Per-class center/radius means describe **matched pairs only**, alongside TP/FP/FN.
They cannot hide recall failures. The obstacle label is its enclosing circle.

Boards count TP when all four ordered corners are within 5px Euclidean distance;
bad found geometry counts both FP and FN on a positive frame. Rejected positives
count FN; found negative boards count FP. Mean/max corner error includes every
found positive frame, including out-of-gate results. Rejected frames have no
invented corner error.

## Board-scale calibration

Supply independently measured physical width/height in the manifest. For each
found board, the report exposes physical edge length / detected pixel edge length
in **mm/pixel**, separately for top/right/bottom/left. These are edge-local scale
diagnostics, not a single affine scale for perspective frames. Use the detector's
homography for interior points; normalized square output of N pixels has scale
width_mm/(N-1), height_mm/(N-1). Scale cannot be inferred in mm from a screenshot
alone. An erroneous caller dimension scales all object measurements; no automatic
profile or physical-bound update is performed. Check corner quality before using
calibration. The offline tool never changes production detector configuration.

CI runs regression and golden-report tests. Device detection is a separately
documented regeneration step; the checked-in prediction snapshot does not prove
that current Android detector code passes without device execution.
