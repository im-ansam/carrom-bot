# 0012: Calibrated conservative object recognition

Status: accepted for CB-22.

`core:vision` owns stateless synchronous OpenCV recognition on rectified RGBA frames.
The frame extents map to caller-supplied physical `BoardBounds`; x/y scale must agree
within 1%. Centers use each axis scale independently; enclosing radii use the smaller
pixels/mm scale to account conservatively for minor dimension rounding. Use
`BoardDetector.normalize` first for perspective input. Square normalization therefore
requires square physical bounds for circular recognition.
The API cannot establish that caller geometry belongs to a frame. Results use shared
`Circle`/`Vec2` in mm, with visual kinds for pockets, black/white/queen discs, striker
and obstacles. They do not create a `BoardState`, IDs or assumed zero velocities.
CB-23 owns tracking; CB-35 owns capture integration.

Each immutable caller profile defines inclusive OpenCV HSV bounds, fractional radius
limits and shape thresholds. There are no game-skin defaults. Hue wraparound uses
a wrapped hue interval (minHue > maxHue), united before contour extraction.
Masks use external contours, minimum enclosing circles, perimeter
circularity and foreground fill (including holes). Non-obstacles enforce minimum
0.80 circularity / 0.90 fill. Pocket centers must be in configurable corner squares;
no four-pocket template is fabricated. Obstacles may lower shape thresholds and
their returned circle conservatively encloses the observed contour, not its true
collision shape. Clipped contours fail even for pockets/obstacles. Confidence is
circularity times color-mask fill, a class-conditional fit score, not probability.

Reject conflicts between intersecting candidate circles instead of choosing a class
by profile order. Identical same-class candidates are deduplicated. Merged/partially
visible contours are never split or completed. Small missing areas can pass shape
tolerances; a circular decoration matching a profile can pass. Empty observations
do not assert an empty board or full visibility. Invalid input/aspect/native errors
are explicit failures. Native initialization throws when unavailable.

Input is borrowed and unchanged for the call; callers may not mutate/release it
concurrently. No pixels or native buffers are retained. All scratch Mats/contours
release in finally; results defensively copy collections. Independent frames can
run on different threads. No asynchronous queues, scopes or persistence are added.

The original objects-v1 fixtures exercise all types, size/noise changes, rejection
and measured precision/recall. Real-game calibration and representative authorized
datasets remain necessary; CB-24 owns broader regression/calibration tooling.
