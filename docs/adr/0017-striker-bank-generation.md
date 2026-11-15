# ADR 0017: Ordered striker bank proposals with physical verification

Status: Proposed for CB-27 review, 2026-11-15.

CB-27 adds synchronous, platform-free ReflectionPathGenerator and
StrikerBankGenerator in core:planner. Dependencies and shared model contracts do
not change. Callers supply one candidate ID, target/aim/power, a discrete legal
placement list, ordered one/two BankWall values, power calibration and PhysicsConfig.
This boundary does not enumerate placements, wall sequences or powers, infer rules,
or optimize. IDs remain caller-owned and must be unique within ranking batches.

Reflection geometry works in board millimetres on the rectangle inset by the striker
radius. Reflect the ghost centre through the requested rails in reverse order;
intersect the unfolded ray with each physical rail, reflecting the image back after
each hit. Strict rail-interior intersections ensure the segment cannot cross another
rail first. Reject corners, start/end centres on rails, duplicate adjacent walls,
invalid order and circles that cannot fit. Translated boards are supported.
ReflectionPath owns immutable wall/point lists: start, ordered bounces, ghost centre.

The ghost centre follows ADR 0016's target-to-pocket normal. Reuse the direct/cut
contact validator from the last bounce to the ghost for the final approach and disc
corridor, including pocket entry, clearance and wrong-pocket interception. Its
temporary candidate is discarded. Earlier legs check every disc, including the
target, against striker-radius-plus-disc-radius-plus-clearance corridors. Every
striker leg rejects early full-fit pocket capture. The source scene must be stationary
with separated discs outside capture apertures; legal placement uses exact membership.
Only the original striker centre is replaced in a new immutable snapshot.

Mirrors are proposals for specular motion, not proof of a physical bank. Run the
actual ShotSimulator with the caller's drag, masses, wall/body losses and budgets.
Accept only REST without striker capture, capture of the target in its requested
pocket, and exactly the requested ordered striker wall impulses followed by exactly
one striker/target body impulse. Reject all extra wall/body impulses, including
post-contact banks. This identity/order check is stronger than coarse contact counts.
Candidates also carry exact inclusive impulse counts for ordinary ShotEvaluation.
Single rebounds use STRIKER_BANK; two sequential rebounds use DOUBLE_BANK.
StrikerBankShot retains the intended geometry and actual immutable SimulationResult;
intended bounce points need not equal physical bounce points under loss calibration.

Null means infeasible geometry or rejected physical outcome, including insufficient
energy, missed target, foul or TIME_LIMIT. Malformed scenes, arithmetic that cannot
be represented and simulator failures throw; no exception/cancellation is caught or
converted into plausible data. No clocks, randomness, native resources, Android types
or global state are introduced. Processing is bounded by one/two rails, finite scene
lists and the explicit simulator budget. Scene validation is quadratic in disc count;
the simulation retains its existing body-pair/event/sample costs.

This conservative generator may reject otherwise useful shots: loss-compensated
aiming, wall-start shots, simultaneous corners, contact chains and extra post-impact
rebounds are outside scope. It neither searches for a corrected launch angle under
anisotropic wall losses nor claims completeness. No real-game accuracy, confidence,
power optimality or throughput is established by synthetic tests. Floating-point
oblique head-on transfers can leave tiny residual velocities; zero-drag TIME_LIMIT
is rejected, not treated as rest. Tests use explicit drag where needed.

Analytic golden tests establish a single horizontal bank (wall at 0.25 s, contact
at 0.7 s, capture at 0.95 s) and a double LEFT/TOP bank with unfolded displacement
(-60,-100) mm. The double uses explicit 1 mm/s² drag and independent analytic
distance-to-time calculations, comparing 2 s and 0.01 s clocks within 1e-7 s.
Tests also cover all rails, opposite/invalid rail orders, translated bounds,
immutable ownership, placement replacement, blockers/tangency/early target contact,
early capture, insufficient energy, truncation, wall loss and propagated work-budget
failures. See [validation](../validation-cb-27.md) for executed checks.
