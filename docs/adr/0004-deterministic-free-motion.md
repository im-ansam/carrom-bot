# ADR 0004: Deterministic free-motion stepping

Status: Proposed for CB-14 review, 2026-11-14.

CB-14 adds platform-free constant-velocity integration in core:physics using the
shared BoardState, Disc, Striker and prediction contracts. Position advances by
velocity * dt in board millimetres; velocity is unchanged. No Android dependency,
wall clock, random generator, global mutable state or resource ownership is added.

PhysicsConfig requires a positive finite timestep and 1..100,000 steps. Its time
cap is dt * maxSteps and must be finite. Every step has exactly the same dt; there
is no partial final step. Elapsed time is computed from the integer step index,
rather than accumulating clock error. Motion uses Double arithmetic; deterministic
means exact repeatability for identical ordered inputs/configuration on the JVM,
not bitwise portability across future numerical implementations. Runtime and retained
snapshot count are bounded by the step budget (work/memory also scale with body count).

SimulationState wraps an immutable BoardState and a finite nonnegative elapsed
clock. simulate starts at zero, retains the initial state, preserves body order and
identity, and returns an unmodifiable owned list. Rest means all velocity components
are exactly zero, including signed zero; no tolerance silently removes slow motion.
Without forces, an initially moving body continues until the cap. Subnormal motion
may round to an unchanged position, but is still treated as moving.

predict adapts ShotInput to shared ShotPrediction. Frame zero is the original
pre-impulse BoardState; only the striker velocity is replaced before the first dt.
The input snapshot is never mutated. PredictionTermination.TIME_LIMIT explicitly
marks truncation. Zero-velocity BoardState simulation can stop immediately, while
ShotInput intentionally forbids a zero impulse.

CB-15 owns friction, stopping distance and power mapping. CB-16 owns body contact,
impulses and separation correction. Boundary response and pocketing also remain
future work. This integrator is only valid for collision-free free-motion horizons;
it does not detect or repair body overlaps, including initial overlaps accepted by
BoardState. The CB-14 penetration criterion is limited to such collision-free cases.
A body leaving the board violates the existing BoardState invariant and throws
IllegalArgumentException, as does nonfinite arithmetic. No clipping, fabricated
bounce or partial successful prediction is returned. Callers must handle failure.
The model's containment or prediction invariants are not weakened for this foundation.

The versioned free-motion-v1 fixture specifies an injected java.util.Random seed,
timestep, step count and analytic comparison tolerance. Randomness exists only in
tests; production free motion needs no seed. Tests compare every sampled position
against p0 + v * elapsed and compare repeated trajectories exactly, alongside rest,
time cap, timestep sensitivity, ordering, immutability and invalid-value checks.
