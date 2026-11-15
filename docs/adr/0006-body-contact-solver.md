# ADR 0006: Swept body contacts

Status: Proposed for CB-16 review, 2026-11-15.

CB-16 adds platform-free circle contacts to MotionSimulator. Shared model types,
fixed sample intervals, integer-indexed clock, pre-impulse prediction frame zero,
owned immutable snapshots and original disc storage order remain unchanged.
Wall rebounds and pocket capture belong to CB-17/18. A sampled body outside board
bounds still fails explicitly; containment is not weakened or clamped.

## Detection and advancement

Between impulses each body's drag trajectory is quadratic until its analytic
stopping time, then stationary. SweptContact partitions each pair's remaining
interval at both stopping times. On each segment, relative position is quadratic;
squared separation minus squared radius sum is a polynomial of degree at most four.
Coefficients use a common length scale to avoid unnecessary squared overflow.
Derivative roots recursively partition normalized time [0,1] into monotone intervals;
sign-changing roots use exactly 64 bisections. The earliest approaching root is the
candidate. Tangency without approach needs no impulse. This detects crossings even
when both interval endpoints are separated and handles drag without linear chords.
It supersedes an initial conservative-advancement implementation that could stall
at drag-enabled tangency. No production randomness or wall-clock reads are used.

CollisionEngine advances all bodies analytically to the earliest candidate, resolves
contacts, then recomputes candidates over the remaining interval. ContactTolerance
is an explicit distance in mm (default 1e-8); it is not a velocity cutoff. At each
event, all pairs within this distance are resolved in lexicographic stable-ID order.
Repeated same-time scans propagate chains and catch correction-induced contacts.
This sequential simultaneous-contact convention is deterministic, but is not a
global constraint solve and does not assert a unique real-world simultaneous outcome.

## Impulses and separation

PhysicsConfig supplies a common disc mass, striker mass and normal restitution in
[0,1]. Mass units are arbitrary but shared; only ratios matter. Equal unit masses
and elastic restitution default to uncalibrated values. Normal impulses conserve
linear momentum; elastic impulses conserve kinetic energy within floating-point
tolerances, and lower restitution dissipates normal energy. Tangential velocity
is unchanged; there is no contact friction, spin, deformation or game-specific tuning.
Inverse-mass weights are normalized before summing to reduce intermediate overflow.

Only approaching pairs receive impulses. Penetration is corrected using those same
weights, with a tolerance-sized separation margin; velocity is unchanged by correction.
Coincident centers use +X from the earlier ID to the later ID as a deterministic
fallback normal. Stationary overlaps accepted by BoardState are corrected on the
first moved frame while the original frame zero is retained. Invalid packing or a
correction beyond board containment fails rather than manufacturing a valid board.

## Bounds and precision

Each fixed step allows at most maxCollisionIterations pair scans/advancements
(default 4096, maximum 100000). Pair work is quadratic in body count; polynomial
degree, recursion depth and bisection count are fixed. Memory remains proportional
to bodies and retained samples. Exhaustion, nonfinite arithmetic or a nonadvancing
clock throws IllegalArgumentException; no partial prediction is returned as success.
Dense/inelastic contacts may exhaust the budget. No arbitrary speed threshold is
introduced to force convergence or rest.

Double root finding and correction have finite precision. Extremely ill-conditioned
geometry (tiny radii against huge travel), subnormal underflow and near-multiple
roots are not claims of arbitrary-precision physical accuracy. Tolerances must be
chosen for the board scale; parameters need empirical calibration. Exact repeatability
means identical ordered input/configuration on the JVM, not cross-platform bitwise
identity or invariance to renaming IDs in a simultaneous event.

## Verification

contact-v1 fixes a seed, 128 glancing cases and 1e-7 conservation tolerance. Tests
cover equal/unequal masses, stationary targets, high-speed crossing, tangent/near
miss, elastic momentum/energy, restitution, same-time chains, near-simultaneous
contacts, reversed storage order, coincident stationary bodies, input ownership,
prediction contracts, stopping-time segment splits, drag glancing contacts at three
restitutions and coarse/fine timesteps, invalid settings and explicit work-budget
failure. Existing free-motion and analytic drag fixtures remain regressions.
