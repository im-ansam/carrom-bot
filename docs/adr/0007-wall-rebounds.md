# ADR 0007: Swept wall rebounds in the shared contact clock

Status: Proposed for CB-17 review, 2026-11-15.

CB-17 adds platform-free closed rectangular walls to MotionSimulator for discs and
striker. Shared model/prediction contracts, module dependencies, owned snapshots,
original disc storage order, analytic drag and integer-indexed fixed sample times
remain unchanged. Pocket openings, capture, outcomes and the complete shot simulator
API belong to CB-18. Walls currently include positions occupied by pocket circles.

## Detection and event ordering

WallCollision computes signed surface clearance from all four inner board edges,
accounting for each body's radius. Between impulses drag never changes direction;
outward displacement is monotone until rest. ReboundEngine brackets each reachable
wall and uses exactly 64 bisections of analytic displacement to find its first hit.
Stopping before a wall produces no candidate; stopping at it needs no impulse.
The minimum of wall and swept body candidates advances every body together. The
remaining fixed-step time is retained, allowing repeated banks inside one sample.

At each event the solver scans body pairs in lexicographic stable-ID order, then
walls in stable body-ID order. Per body the wall order is left, right, top, bottom:
X before Y at corners. Repeated same-time scans propagate wall/body chains before
advancement. This is a deterministic sequential impulse convention, not a global
constraint solve or a claim that simultaneous real impacts have a unique outcome.
In a lossy corner, the second wall uses velocity already changed by the first.

## Response and calibration

PhysicsConfig adds independent wallRestitution e and wallTangentialRestitution t,
both finite in [0,1], plus finite nonnegative wallFriction mu. For approaching
outward normal speed n > 0, the new normal component is -e*n. Tangential speed q
retains its sign and becomes max(0, t*abs(q) - mu*n*(1+e)). This is a simple
Coulomb impulse loss after tangential restitution, without spin or reversal.
Tangential restitution and friction are independent calibration controls; selecting
both applies both losses. Mass cancels against the immovable wall. Defaults e=t=1,
mu=0 preserve speed. Receding/tangent/stationary contacts receive no wall impulse
or tangential damping. There is no speed cutoff to discard slow motion.

## Bounds and failure semantics

contactTolerance remains a distance in mm (default 1e-8), shared with body contacts.
Approaching walls within that distance resolve immediately; this may reflect up to
that distance early, including grouping near-corner contacts. Only a negative wall
clearance within tolerance is moved inward by its penetration amount to repair
floating-point event rounding. This does not clamp arbitrary out-of-bounds motion.
Larger penetration, including body separation that pushes a body beyond tolerance,
fails explicitly. Strict BoardState containment remains unchanged and is checked
after each wall scan; an unrepresentable correction fails rather than weakening it.

The existing maxCollisionIterations budget counts shared scans and advancements,
including wall impacts and chains. Sweeps use fixed bisection work per wall, memory
is proportional to bodies and retained samples, and no randomness/wall clock is used.
Budget exhaustion, nonfinite arithmetic, unrepresentable containment or nonadvancing
time throws IllegalArgumentException with no partial successful prediction. Dense
contacts, degenerate confinement and inelastic impact accumulation may exhaust the
budget. The CB-16 acceleration-driven sustained body-contact guard remains intact.
Finite Double precision limits very large scale ratios and near-simultaneous events;
64 bisections are not an arbitrary-precision guarantee. Parameters need measurement
before claiming game accuracy. No Android/native dependency or new shared API is added.

## Verification

rebound-v1 fixes seed 171717, 128 unfolded rectangular trajectories and tolerance
1e-6. Analytic folding independently checks positions after many elastic rebounds;
energy and exact repeated results are checked too. Regressions cover all walls,
straight/45-degree/oblique angles, corners with losses, double/many banks, normal
and tangent restitution, friction saturation, low speeds, receding/tangent contact,
analytic drag banks, timestep agreement, stopping before/at a wall, wall/body chains,
disc banks, prediction frame zero, input ownership, invalid coefficients and explicit
budget failure. Existing body-contact and free-motion/drag regressions are retained.
