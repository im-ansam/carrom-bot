# ADR 0008: Pocket capture in the shared event clock

Status: Proposed for CB-18 review, 2026-11-15.

ShotSimulator.simulate(BoardState, ShotInput, PhysicsConfig) runs every shot family
through the existing platform-free contact engine. The two board arguments must
agree. MotionSimulator retains its closed-wall behavior for existing callers.
No module edges or shared model invariants change.

## Geometry and ordering

A configured Pocket.circle is an aperture. A body is captured when its entire
circle fits within it: centre distance <= aperture radius - body radius, within
contactTolerance. Smaller apertures cannot capture larger bodies. Arbitrary
interior, edge and corner pockets are supported. This is an explicit full-fit
approximation, not a measured lip, jaw, gravity or partial-overhang model. A corner
aperture must be sufficiently large to contain a body while it still fits inside
the rectangle. There is no out-of-bounds flight through a small corner opening:
until capture, rectangular rails remain solid; capture substitutes for a rebound
when full fit is reached on or before wall contact. Geometry requires calibration.

Between impacts, drag preserves direction. Project the pocket centre onto the
motion ray and compute its first intersection with the eroded aperture. Analytic
drag displacement brackets travel to that entry; exactly 64 bisections find time.
Wall and pocket candidates bound the body-pair sweep horizon, so no invalidated
future body path is inspected. All bodies advance together. At each shared scan,
captures run first (stable body ID, then smallest matching pocket ID), body pairs
next, and individual walls last (existing X-before-Y order). Repeated same-time
scans propagate chains and correction-induced events. Sequential impulses are a
deterministic convention, not a global simultaneous constraint solution.

## State, outcomes and ownership

Captured discs disappear permanently. Captured striker is excluded permanently
from body/wall/pocket work and retained as a zero-velocity snapshot at its capture
position, preserving mandatory striker identity in BoardState and ShotPrediction.
At fractional wall ties the existing bounded inward correction repairs the frozen
circle before retention, without applying or recording a wall impulse.
SimulationResult.strikerCaptured distinguishes this inactive snapshot from an
active stationary striker. Consumers must carry that outcome when using finalState;
starting a new simulation is a new shot and requires caller-controlled game setup.

Prediction frame zero remains the original pre-impulse board. Later frames use
integer-indexed fixed sample times. SimulationResult owns unmodifiable event and
capture lists; ShotPrediction owns samples. Events record every resolved body
impulse, individual wall impulse and capture, with event times independent of the
sample grid. Separation corrections and analytic drag are represented by samples,
not additional impulses. Captures retain body/pocket IDs, elapsed time and disc
kind; null kind denotes striker capture/foul. Player points, ownership, turn
legality and queen covering are intentionally not inferred from visual disc kind.

Existing step and per-step collision budgets also bound capture work and retained
events. Work additionally scales with bodies times pockets. Nonfinite arithmetic,
unrepresentable geometry, failure to advance time or budget exhaustion throws
without returning partial success. Existing sustained-contact failure, strict
containment and inward rounding for fractional walls remain in force. Double
precision, dense inelastic chains and uncalibrated parameters remain limitations.

## Verification

shot-v1.csv specifies independent analytic capture times, final positions, event
counts, input geometry and tolerance for direct, disc-bank, striker-bank,
combination, multi-disc and multi-pocket cases. The disc bank launches a stationary
target obliquely then removes wall tangent speed; the striker bank reflects before
its first body impact. Multi-body cases also exercise supplied initial disc motion.
Every fixture runs coarse and fine clocks and repeats exactly. Edge tests cover
corner capture, queen classification, overlapping-pocket tie selection, wall ties,
undersized apertures, near misses, analytic drag stopping/capture, inactive striker,
event-position preservation, immutable lists, mismatched input and budget failure.
