# ADR 0015: Candidate and evaluation contracts

Status: Proposed for CB-25 review, 2026-11-15.

The platform-free planner now exposes immutable proposals and advisory evaluation
contracts. It does not generate shots, optimize power, infer game rules or wire
capture/vision/overlay. The module graph stays planner -> physics/model; those
dependencies are exported with `api` because public contracts expose their types.

CandidateShot owns an ordered defensive copy of nonempty pocket targets, unique
by disc identity. Targets must reference existing snapshot discs and pockets.
The shared ShotInput supplies the complete immutable board, already-positioned
striker and nonzero velocity in mm/s. Power is separately normalized in (0,1];
the caller must supply consistent calibration using the physics PowerModel.
No guessed maximum speed, launch conversion or legal placement is introduced.
Family keys are case-sensitive strings with constants for direct, cut, striker
bank, disc bank, double bank, combination and multi-pocket. Custom keys allow
future families without changing stored keys. A family records generator intent;
it does not prove a route or restrict target count. Multiple targets can share a
pocket; the same disc cannot be assigned conflicting captures.

TrajectoryConstraints supplies inclusive minimum/maximum resolved wall and body
impulse counts over the full shot. Corners count two individual wall events.
Defaults impose no contact restriction. These are deliberately coarse constraints,
not ordered route matching or obstacle clearance; geometric candidate generators
and robustness/uncertainty optimization belong to subsequent tickets.

ShotEvaluation.fromSimulation requires exact shared ShotInput equality. It uses
the unified simulator's capture events (including pocket IDs), never disappearance
alone. Ordered typed rejections identify power outside configured inclusive bounds,
TIME_LIMIT truncation, captured striker, any missing target and contact-count
violations. Rejections retain diagnostic simulations but have no score. A caller
may explicitly represent a known simulation failure without a fabricated result;
this API catches no exceptions and must never be used to swallow cancellation.
Physics configuration and work budgets stay explicit at the simulation call site.

Accepted scores expose target reward minus incidental-disc-capture and wall-impulse
penalties. All weights are finite nonnegative caller policy with no uncalibrated
defaults. Arithmetic overflow fails explicitly. This utility is not player points,
queen covering, win probability or calibrated confidence. Striker capture is a
hard rejection regardless of utility. Ranking is accepted first, descending total,
ascending normalized power, then lexicographic stable candidate ID. Callers must
provide unique candidate IDs within each ranking batch; the comparator is not a
deduplication mechanism. There is no clock, random source or shared mutable state.

Tests use actual ShotSimulator results for direct, bank, multi-pocket, foul,
truncation and failure-boundary checks. candidates-v1.properties is a versioned
synthetic fixture with family keys, power, target IDs and contact caps reconstructed
against an independently supplied board. Properties text round-trip and value/hash
checks follow ADR 0002's fixture convention; no production wire codec, Java object
serialization or schema migration guarantee is introduced. Real-game accuracy and
performance remain unmeasured. See docs/validation-cb-25.md for executed checks.
