# ADR 0023: Explicit Canvas trajectory scenes

Status: Proposed for CB-33 review, 2026-11-15.

## Contract and coordinates

The overlay keeps its dependency on model only, exported with api because public
rendering contracts expose shared geometry. OverlayScene, RenderedCandidate,
BodyTrajectory and ShotAnnotation are immutable render data owned by core:overlay;
the app composition layer will adapt planner/simulator evidence in CB-35. They
carry no Android resources, planner utilities, simulator instances or game input.
Collection inputs are copied, including nested trajectory vertices. Duplicate
candidate/body identities, invalid power/confidence, missing selection and geometry
outside the board fail explicitly. Empty scenes clear assistance.

Board geometry remains in the shared model's millimetres, including translated
bounds. BoardMapping wraps CoordinateTransform: board-mm positions map to **local
Canvas pixels**, not raw physical-display coordinates. The caller must subtract
the host's screen offset and supply rectified axis-aligned coordinates. Its explicit
normalizedToScreen adapter maps [0,1] over the complete board rectangle, satisfying
normalized-input consumers without silently reinterpreting millimetres. Rotation
and perspective must be rectified upstream; no guessed stretch or auto-fit is used.
Double mapping round trips retain the model's floating-point limits. Unrepresentable
Canvas Float results fail before drawing instead of disappearing silently.

Trajectories are ordered body-centre vertices. Every contact/rebound corner must be
supplied by the producer; the renderer never reconstructs an impulse from a sparse
fixed-time prediction or declares a pocket capture from a disappearing disc. The
current simulator's events supply identities/times, not exact contact positions;
pipeline integration must obtain event-resolved geometry before using these markers
as physical evidence. Synthetic renderer fixtures are not simulator accuracy claims.
Pocket annotations explicitly represent producer-declared destinations. Contacts,
rebounds and pocket destinations use circle, square and crosshair-ring shapes.

## Drawing and ownership

TrajectoryRenderer draws complete multi-leg polylines for striker and multiple
discs. Striker paths are thicker. Alternatives are dashed, the selection solid and
drawn last. Candidate colours agree with a legend displaying identity, selection,
normalized power percentage and supplied sensitivity percentage (unknown when null).
Sensitivity follows the planner's scenario acceptance interpretation, never a
real-game win probability. Utility scores must not be substituted for confidence.
Colours cycle for larger candidate sets; caller should select a small visible set.

Paths/markers clip to mapped board bounds; the Canvas clip/save state is restored.
The legend uses local pixels at the Canvas top-left, outside the board clip. Stroke,
marker and text sizes are fixed pixels independent of board zoom; the host owns
device-density/font-scale presentation and available legend space. No screenshot,
frame, token, clock or random source is retained. Work/allocation scales with supplied
vertices and markers; performance has not been measured.

TrajectoryView is a passive hostable Android View with main-thread submit/clear.
Resize clears geometry instead of showing stale mapping; a host must resubmit.
It creates no WindowManager surface, permission flow, lifecycle listener or input.
CB-32's bounded accessible Hide control and service lifecycle stay intact. Attaching
the trajectory surface to the service window/pipeline belongs to CB-35; any new
non-touchable overlay window must address Android obscuring-opacity rules then.
CB-34 owns richer controls. No fake trajectories appear in the app.

## Verification

JVM tests cover translated/normalized mapping round trips, invalid geometry and
identity, power/confidence validation and collection ownership. API31 device tests
compare two reviewed software-Canvas PNG goldens with multiple reflections, two
contact transfers, a destination and an alternative, at different scales/translations.
They also check empty rendering, invalid mapped coordinates, Canvas state, passive
view drawing and resize clearing. Goldens contain synthetic data only, are pinned
to API31 rasterization, and require explicit recordGoldens=true to regenerate;
ordinary tests fail when an asset is absent or differs. Existing lifecycle tests
continue to exercise actual WindowManager permissions/touches.

See [CB-33 validation](../validation-cb-33.md) for executed commands and limitations.
