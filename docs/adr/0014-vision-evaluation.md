# ADR 0014: Versioned offline vision evaluation

Accepted for CB-24.

Keep one hash-pinned manifest beside existing original Android fixture assets.
An instrumentation exporter executes the real BoardDetector and ObjectDetector;
it persists numeric predictions only, including explicit failures. A Python stdlib
tool scores complete saved runs without Android/native dependencies. It uses
maximum-cardinality within-class circle matching and ordered board corner errors,
and exposes edge-local mm/pixel calibration from caller-measured dimensions.

CI regression tests check scoring edge cases, asset integrity and a checked-in
device prediction/report golden. Native detector regression remains covered by
instrumentation, which must be run when changing detector behavior. Offline scoring
of a snapshot cannot replace it. Synthetic profiles and labels test component
mechanics only. Missing frames are invalid input, never silently treated as empty.

This adds no production dependencies or shared model types and does not alter
capture, tracking, planning or overlay behavior. Known dimensions are necessary:
the tool cannot derive physical mm from an uncalibrated screenshot. Perspective
edge scales are diagnostic; production point mapping uses BoardTransform.
