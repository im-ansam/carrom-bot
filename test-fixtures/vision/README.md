# Vision regression dataset

`core/vision/src/androidTest/assets/vision-v1.json` is the single authoritative
manifest, colocated with Android assets to avoid duplicate annotation copies.
It references 12 board-v1 and 6 objects-v1 original synthetic PNGs by SHA-256.
All artwork and annotations were generated for this repository, contain no game
captures or personal content, and may be used and redistributed with this project.
No external asset permission is required. Existing seed-21/seed-22 stdlib generators
and their asset READMEs describe provenance and regeneration.

Eight positive boards cover scale, rotation, perspective, noise and diamond corner
ties; four negatives cover missing, clipped, thin and ambiguous geometry. Three
positive object frames contain four pockets and one each black disc, white disc,
queen, striker and obstacle (27 labels); occluded/false-positive/empty frames expect
zero detections under the conservative visibility policy. This policy is **not**
full-object recall under occlusion. Board and object fixtures are separate stages,
not a capture-to-planner integration dataset.

`sample-predictions.json` contains actual native detector output from the disposable
API 31 x86_64 emulator. `sample-report.json` is its reproducible offline score.
These are synthetic component regression results, not real-game accuracy or a
held-out performance estimate. Profiles were calibrated on this same dataset.
No timing, leak or cross-device claim is made. Future real images require explicit
permission/provenance, independent calibration/evaluation splits and new versions.

See [format, metrics, units and commands](../../tools/vision-eval/README.md).
