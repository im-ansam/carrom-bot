# ADR 0009: Shared synthetic shot regression and throughput inputs

Status: Proposed for CB-19 review, 2026-11-15.

CB-19 adds test tooling only. Production physics, shared contracts and module
dependency direction remain unchanged. ShotFixture loads the existing six v1
shot families once per runner invocation, and is shared by existing analytic
checks, JUnit 4 parameterized regressions and an opt-in JavaExec benchmark.
The benchmark source lives under benchmarks and compiles only with physics tests.

The separate shot-events-v1.csv records every ordered event's type, body, target
and time. IDs/order/counts compare exactly; elapsed seconds compare within 1e-7.
Times are independently derived from straight travel and equal-mass transfers:
direct contact at 0.2 then 25 mm to capture at 0.45; striker bank at 0.25,
contact at 0.7, capture at 0.95; chain contacts at 0.1/0.2; independently moving
discs capture at 0.25. For the oblique disc bank, contact time is
0.3 - 10 / hypot(100,40); the target then travels 35 mm right in 0.35 s and
80 mm left in 0.8 s after the rail removes tangent velocity.
Snapshots are reviewed analytic expectations, not automatically regenerated output.

Every fixture repeats three times with exact event, capture and sampled-board
equality; a 0.01-second grid compares all event identities/times, terminal body
positions/velocities and termination to the 2-second coarse grid. Capture checks
ensure removed identities never reappear and outcomes correspond to events.
Existing analytic final-position/capture assertions and solver edge tests remain.

shot-properties-v1 fixes seed 191919, 64 generated launch/loss configurations and
1e-6 distance/energy comparison tolerance. On an initially separated three-disc
board, every moved frame must preserve containment and pair separation, while
sum squared speed cannot increase under unit masses and dissipative parameters.
Frame zero deliberately precedes launch and is excluded from energy comparisons.
Reversing disc storage order retains exact stable-ID events and canonical terminal
states. These finite cases complement, not prove, physical invariants; unsupported
sustained contacts and budget exhaustion remain explicit production failures.

The external monotonic timer measures complete candidate simulations and consumes
terminal positions/event/frame counts. It never enters simulation. CI exercises
the harness with small counts without a noisy timing gate. Local throughput and
measurement limitations are recorded in benchmarks/README.md. No game accuracy,
planning quality or device latency is inferred from synthetic scenarios.
