# ADR 0019: Bounded simulation search and discrete power optimization

Status: Proposed for CB-29 review, 2026-11-15.

ShotPlanner searches an explicit list of LaunchSample values. Each caller-owned
unique ID selects a striker position in board millimetres, a clockwise angle in
radians from +X and normalized positive power. Callers supply the finite samples
and exact legal-position list; no baseline dimensions, player rules or calibration
are guessed. This explicit sample set supports coupled placement/angle/power
search without forcing a Cartesian product or generating unstable IDs. PowerModel
converts power to speed. A new immutable snapshot replaces only the striker centre.
Out-of-board, overlapping/touching or unlisted placements fail as invalid requests.
An empty sample set returns no plans. Moving discs retain their supplied motion.

CandidateEvaluator runs the full ShotSimulator and the existing ShotEvaluation
policy. ShotPlanner.evaluate also accepts unchanged proposals from any generator.
Completed accepted plans rank by descending utility, ascending power and caller ID;
rejections retain simulations separately for diagnosis. PlanningResult owns both
lists; best is null when no sampled solution exists. Fouls, missed target/pocket
pairs, time limits, power bounds and coarse contact limits remain hard rejections.
Obstacles participate as physical bodies rather than being ignored by a ray test.
Arithmetic, unsupported geometry and simulator work failures propagate, as does
cancellation: no partial or fabricated result replaces a failed search.

PowerOptimizer preserves a template's placement, direction, targets, family intent
and coarse constraints while evaluating every supplied PowerSample with fresh
velocity calibration and full simulation. It makes no monotonic-success assumption
and performs no binary search. Captures and coarse counts do not re-establish an
original bank/combination generator's exact ordered-contact proof after power
changes. Consumers needing that stricter route must check the retained events.
Search results establish the best tested sample only, not global optimality,
completeness, robustness, confidence or measured real-game accuracy.

Optional nonnegative body-impulse and terminal-control-distance penalties extend
the existing score. Their zero defaults preserve CB-25 policy. Control distance is
the final striker-centre distance in mm to an explicit caller-selected Vec2; a
positive weight requires that target. It is a deterministic positional preference,
not expected turn value or player points. Rewards, incidental capture, wall/body
impulses and control distance remain individually inspectable. Overflow fails.

Each search requires an explicit simulation-count budget in 1..4096; oversized
requests fail before simulation rather than silently truncating. Each simulation
also retains its explicit PhysicsConfig step/contact budgets. Work and retained
evidence scale with sample count times simulator cost; this cap is not a latency
guarantee. All APIs are synchronous, stateless and platform-free, retain no input
lists and use no clocks, randomness, threads, native or Android resources. No
module edges change. Multi-pocket proposal generation and uncertainty handling
remain separate tickets. See validation-cb-29.md for executed checks.
