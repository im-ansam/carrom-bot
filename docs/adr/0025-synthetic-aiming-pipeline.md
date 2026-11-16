# ADR 0025: Consented synthetic end-to-end aiming

Status: Proposed for CB-35 review.

## Scope and calibration

The user explicitly selected synthetic fixture validation while live-game calibration
is unavailable. Normal screen sharing remains available, but Start aiming explains
that a calibrated game profile is required and does not copy/process live pixels.
No settings are claimed to be calibrated for a third-party game.

Open synthetic fixture demo from the launcher after allowing overlay access. Share
the full display using fresh Android consent, then use Start aiming in the floating
controls. SyntheticDemoActivity generates original colored-circle fixture-v1 pixels.
Its measured screen viewport and full-display dimensions are passed once with consent.
Distinct request identities discard late cancelled consent results; recreation never
restores a pending grant. Permission/service startup failures remain visible and retryable.
The private service analyzes actual captured pixels in this viewport. Two colored
margin markers must be present; geometry, confirmed one-pocket/one-disc/one-striker
inventory, class confidence and stationary dwell must all qualify before planning.
Markers identify a test source, not a secure authentication mechanism. No automatic
game inventory, legal placement, physical dimensions or rules are inferred.

The profile has explicit 400mm square synthetic bounds, HSV/radius thresholds,
250ms stationary dwell, 1.5s maximum observation gap, three discrete powers and
synthetic equal-mass/friction/power policy. The observed stationary striker position
is the sole permitted position for this fixture's advisory search. Only direct/cut
proposals are sampled in this integration demo; existing other-family libraries
remain available independently. Outputs are best evaluated proposals, not global optima.

App-only/resized captures, changed source geometry, perspective/skew, missing markers,
missing/extra objects, movement and low confidence withhold advice. This revision
does not import a live profile or implement real-game calibration acquisition.

## Ownership, backpressure and cancellation

Capture still borrows each Image only synchronously. While configured aiming runs,
copy at most once per 100ms into owned packed RGBA bytes; honor row/pixel strides and
buffer position/limit. Reject incomplete planes and frames over 4,194,304 pixels.
No screen content, grants or exception text is persisted/logged. Image.timestamp's
unspecified source clock is not used: monotonic elapsed-realtime receipt timestamps
share the clock used for queue freshness checks and tracking.

A service-owned SupervisorJob/main Handler dispatcher owns AimingCoordinator.
Capture may offer owned frames from its looper into an atomic one-slot DROP_OLDEST
mailbox. Vision processing uses a serial worker dispatcher; each run owns a new
detector/estimator and every Mat releases in finally. There can be one processing
frame, one queued frame and one transient producer copy. Pixels are reclaimed by
the JVM after consumption/drop; no native image is retained across callbacks.

Planning is a separate child job on workers. Mode changes cancel observer/search/
expiry jobs, discard queued pixels, clear evidence and invalidate mode/request IDs.
Any changed/unavailable board or mapping cancels the current search and clears advice.
Repeated exactly equal ready observations refresh evidence age while allowing an
ongoing search to finish. A result requires matching request identity, active mode,
and current corroborating board evidence younger than 1.5s. Missing frames expire
displayed advice on a timer; freshness is also checked before/after worker vision.
Native work is synchronous and cannot be interrupted midway; cancellation suppresses
its result and all native memory releases before the call returns. CancellationException
propagates; runtime failures clear advice into retryable ERROR without exception text.

`core:vision.RgbaBoardEstimator` exclusively owns native allocation, marker sampling,
crop views, normalization and release. Its public boundary borrows packed owned RGBA
bytes synchronously and returns BoardEstimate plus a model CoordinateTransform.
FrameViewport and FrameMarker are validated value contracts. App composition owns
no OpenCV objects, including in its fixture integration tests; those use Android
Bitmap pixels to construct owned RGBA input. Vision retains its dependency on model
only, without an overlay/app dependency.

Search evaluates at most 32 fresh simulations or 500ms cooperatively, using CB-31.
These are work policies, not hard latency guarantees. Dense-board-v1 profiling
records elapsed cost and displayed proposal count, without a timing gate or accuracy claim.

## Event evidence and presentation

Physics adds immutable ResolvedShotEvent records alongside the unchanged event
identity/time contract. They record body centres (and both centres for body contact)
at the solver's resolved event, within existing numeric tolerances. Physics arithmetic,
iteration order and existing versioned event snapshots stay unchanged. This extra
geometry lets the app merge ordered event vertices with fixed-step centre samples;
captured paths end at actual capture time rather than guessing a disappeared body's
destination. Contact annotations derive the surface point from resolved centres and
the body's radius; pocket annotations use the event's actual destination pocket.
Only accepted complete simulations become scenes; utility never becomes confidence.

The synthetic trajectory window is a bounded, non-touchable, separately labeled
prediction preview at bottom/start. It is deliberately off the captured source
viewport to avoid overlay/capture feedback. Board-mm maps explicitly to local preview
pixels, with legend space above. This is not live-game screen alignment. The demo
refuses displays too small to separate source, controls and preview before requesting
consent. The source's measured bounds must clear the same FloatingControls.widthPixels
reservation used by the actual panel, including a 16dp gap inside the visible display
frame. In particular, the current 360dp portrait layout is explicitly refused;
480dp portrait is covered by the consented integration test. Android 12+ window alpha
is half the system obscuring-opacity limit, capped at 0.5; older versions use 0.5.
Controls remain the existing bounded touchable window. Hide/display invalidation,
permission loss, capture end, Stop and service destruction remove the preview too;
updating a hidden controller never reattaches either window.

See [validation](../validation-cb-35.md) for actual executed checks and limits.
