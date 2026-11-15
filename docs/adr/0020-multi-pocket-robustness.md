# ADR 0020: Explicit multi-pocket sensitivity evaluation

Status: Proposed for CB-30 review, 2026-11-15.

MultiPocketEvaluator compares caller-provided single/multiple target proposals.
The existing scoring contract rewards every requested disc/pocket capture and
penalizes incidental captures according to caller policy. All requested targets
must succeed; partial multi-pocket outcomes remain rejected diagnostics. No player
ownership, queen covering, dimensions, legal placements or target subsets are guessed.
Proposals can come from the explicit simulation search; this ticket adds selection,
not a new geometric proposal generator. The planner/module graph is unchanged.

RobustnessEvaluator runs the nominal proposal and every explicitly identified
UncertaintyScenario in list order. Each scenario supplies its full PhysicsConfig,
allowing independent or coupled drag, body/wall restitution and wall-friction
perturbations. PositionOffset translates existing pocket, disc or striker centres
in mm; bounds, radii and supplied body motion are preserved. A positive powerScale
scales both normalized power and launch velocity using the existing linear-power
assumption. Scaled power must remain in (0,1]; geometry and numeric failures throw,
without clamping or manufacturing a plausible result. Detection radius, missing
objects, uncertain identities and nonlinear calibration are outside this contract.

Confidence is accepted scenario count divided by supplied scenario count. The
separate nominal run is excluded. Samples are equally weighted, explicitly supplied,
deterministic and need not be random. This is a sensitivity indicator, not an
estimated real-game success probability or statistical confidence interval. Callers
own scenario coverage, calibration and threshold selection. Duplicate sample IDs,
duplicate/unknown offset IDs, empty scenarios and invalid thresholds fail. Collections
are defensively owned. There is no clock, random generator or mutable global state.

Nominal plans rank by existing utility/power/ID rules. The preferred plan is the
highest-ranked nominally feasible proposal. A nominal rejection or confidence below
the caller's inclusive minimum marks a result uncertain. If the preferred plan is
uncertain, saferFallback selects the highest-confidence nominally feasible alternative
that meets the threshold and strictly improves confidence; ties retain nominal order.
Null explicitly means no tested fallback exists. This comparison does not prove
safety outside the supplied scenario set. Results retain every scenario's identity,
fresh input, complete simulation, score and hard rejection reasons. Coarse route
constraints remain enforced; changed scenarios do not prove original ordered routes.

Each API requires a simulation-count budget in 1..4096. Multi-candidate work is
candidate count times (1 + scenario count); excessive batches fail before simulation.
Every run also obeys its PhysicsConfig budgets. Arithmetic, unsupported geometry,
work exhaustion and cancellation propagate; no partial fallback hides engine failure.
Retention scales with this product times trajectory size; no latency or game-accuracy
claim is made. See validation-cb-30.md for actual commands and outcomes.
