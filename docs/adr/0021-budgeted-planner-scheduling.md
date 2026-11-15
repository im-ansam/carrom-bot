# ADR 0021: Cooperative budgeted planner scheduling

Status: Proposed for CB-31 review, 2026-11-15.

PlannerScheduler.search is the suspend boundary for evaluating existing proposals.
It dispatches input copying, validation, pruning, simulation, scoring and ranking to
Dispatchers.Default, using the caller's structured coroutine job. It owns no scope,
thread or background task. The synchronous APIs remain available for JVM consumers;
UI consumers must use the scheduler. No Android dependency or module edge changes.
Only planner adds kotlinx-coroutines-core 1.10.2. Consumers own lifecycle cancellation.

SearchBudget specifies fresh simulation count (1..4096) and elapsed milliseconds
(zero means no work). At most 4096 unique-ID proposals are accepted. Input lists
must remain stable until copied on the worker. Traversal preserves caller order;
ranking uses existing utility/power/ID policy. A simulation limit stops at the first
uncached admissible proposal needing another simulation. Empty input completes.
Deterministic count-bounded searches repeat exactly for identical inputs; elapsed
deadlines can select different prefixes under different machine load.

CandidatePruner excludes only power outside explicit policy bounds. Pruned proposals
retain their identity and reason separately, without pretending to have simulated
evidence. There is no heuristic geometry, top-K or score pruning that might remove
the only feasible plan. A request-local cache keys complete immutable ShotInput,
including board geometry, motion, list order and initial velocity. PhysicsConfig is
constant for the request. Every candidate is rescored with its own targets, power,
constraints and family metadata. IDs are preserved, and there is no cross-request
cache, stale detection data or shared mutable state. Cached work does not spend a
fresh simulation slot; retained evidence still scales with completed simulations
times trajectory size. Large caller physics budgets can consume substantial memory.

ShotSimulator accepts a default no-op checkpoint hook and passes it into contact
iteration, pair and pocket scans. Hooks throw to abort; an interrupted simulation
never returns partial evidence. Physics still has no coroutine dependency, clocks,
randomness or changed numerical order. Existing callers retain their default
deterministic behavior. The scheduler's hook checks coroutine cancellation first,
then its monotonic System.nanoTime deadline. Only its private deadline exception
is converted into a partial result. CancellationException and arithmetic, geometry,
contact/work exhaustion or scoring failures propagate unchanged.

ScheduledSearchResult contains ranked fully evaluated plans/rejections, pruned
diagnostics, unexamined IDs and COMPLETE / SIMULATION_LIMIT / DEADLINE. The active
interrupted candidate remains unexamined; no truncated trajectory is scored.
Best is nullable and means best tested proposal, not a global optimum. External
cancellation throws rather than delivering a result into a cancelled lifecycle.
Timing instrumentation reports elapsed nanoseconds (including dispatch delay),
simulation nanoseconds (including interrupted attempts), started/completed fresh
simulations and cache hits. A completed physics run may still be unexamined if its
scoring crosses the deadline. These counters do not imply accepted-shot counts.

This is a cooperative latency budget, not a hard real-time guarantee. Deadline
checks interrupt active simulations instead of waiting for a whole candidate.
A single bounded contact/root calculation, allocation/GC, validation, scoring,
result construction or OS dispatch can overshoot. No timing threshold is asserted
for hosted CI or Android devices. The synthetic 19-disc benchmark records actual
work/timing, and fake-clock tests verify interruption without flaky latency gates.
Real-game accuracy, statistical confidence and device latency remain unmeasured.
RobustnessEvaluator remains a separate exhaustive scenario API; this scheduler
does not present partially evaluated uncertainty scenarios as confidence estimates.
