# ADR 0013: Conservative object tracking and stationary board qualification

Status: Accepted for CB-23

## Context

CB-22 returns per-frame circles, not identities or evidence that every object is visible.
The shared BoardState requires a real striker, unique IDs and fully contained bodies;
it cannot represent obstacles. A planner must not receive an invented or incomplete scene.

## Decision

Keep ObjectTracker and BoardStateEstimator in core:vision, using only shared model and
JDK/Kotlin types. They run synchronously under one caller's ownership, without Android
or native resource access. No dependency directions or shared model contracts change.
Capture, planner and UI composition remain CB-35 work.

The caller supplies fixed board geometry, calibrated TrackingConfig values, nonnegative
monotonic frame timestamps in nanoseconds and same-clock current time. Default current
time equals frame time for immediate processing; queued/delayed callers must supply actual
current time. There is no wall-clock read, queue or retained image. Reject stale and
nonincreasing timestamps without advancing history. Failed or invalid observations
break dwell. Time gaps expire tracks; no ID crosses a new tracker/session.

Association uses raw position and visual kind within a calibrated fixed mm gate.
Both sides must have exactly one candidate. Ambiguous associations publish no guessed
object and retain missing IDs only until the configured maximum gap. Reacquisition
preserves an unambiguous ID but resets smoothing and stationary dwell. There is no
extrapolation, occlusion completion, or inference that a missing disc was pocketed.
New IDs follow kind/position ordering; outputs have stable ID ordering independent of
input order. Crossings or movements beyond the gate can lose identity, by design.

Smooth circle position and radius with an exponential blend. Compute velocity in mm/s
from raw positions and elapsed seconds, with explicit rejection of nonfinite velocity.
Stationary status needs at least three consecutive observations, the configured dwell,
raw center/radius within an anchored tolerance and speed below the calibrated gate.
Smoothed geometry must also be within that tolerance of the current raw circle, so a
strong smoothing lag cannot publish a stale position as stationary planner geometry.
An anchor prevents cumulative slow drift from qualifying as rest. This means observed
rest within tolerances, not proof of no movement between frames or exact zero velocity.

BoardStateEstimator additionally requires an independently confirmed, exact inventory by
kind. Missing kinds mean zero; one striker is mandatory. It defensively copies inventory.
All expected objects must be present now, qualified by the caller's minimum detector
confidence and stationary; retained missing tracks also block readiness. Low-confidence
objects break their dwell. Unexpected kinds/counts and obstacles block readiness.
The caller must create a new estimator after a confirmed inventory or geometry change.
This intentionally defers game rules and inventory acquisition rather than assuming a
fixed starting board or silently inferring completeness from repeated detections.

Only BoardEstimate.Ready carries BoardState, minimum current detector fit quality and
observation timestamp. Ready uses zero velocities under the calibrated rest tolerance.
Other outcomes explicitly carry an unavailable reason. Owned outputs are immutable.
Consumers must invalidate old recommendations and check timestamp freshness at use;
the adapter has no background timer or cached ready-state getter.

## Consequences and limitations

Synthetic JVM sequences exercise the detection-to-model contract without native loading.
No real-game identity accuracy, confidence calibration, latency, or dense-board capacity
is asserted. Matching conservatively abstains in dense/ambiguous scenes and uses polynomial
list scans appropriate to small board inventories; no performance budget has been measured.
Unknown inventory and boards with obstacles cannot become planner-ready with this model.
Future integration must obtain explicit inventory confirmation, calibrate gates, supply
actual monotonic current time for queued frames, and dispose the estimator on session or
geometry changes. Track IDs are local to that instance, not persistent game identifiers.
