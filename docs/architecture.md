# Architecture and operational contracts

The [README module table](../README.md#modules) is the dependency inventory.
`app` composes all adapters; `model` is platform-free; `physics -> model` and
`planner -> physics, model` remain runnable without Android. Android adapters
`capture`, `vision` and `overlay` depend on model, never on app or each other.
No production module edges change in CB-36.

## State, simulation and search

[Shared immutable snapshots](adr/0002-shared-model-contracts.md) use board mm,
top-left origin, +X right/+Y down, mm/s and elapsed seconds. Explicit transforms
map display/rectified board/preview coordinates; normalization is never inferred.
[Physics](adr/0009-physics-regression-harness.md) uses fixed steps, stable event
order, explicit configuration and failure budgets. Resolved event centres accompany
typed contact/capture events; removed bodies never reappear. No simulation reads a
clock or uncontrolled randomness. Spin, curved travel and sustained contact are absent.

[Planner families](adr/0015-planner-contracts.md) are proposals with explicit legal
placements, targets, power and caller policy. Banks/chains validate actual ordered
simulator events. Completed accepted evidence is required before ranking;
utility is not confidence. [Scheduling](adr/0021-budgeted-planner-scheduling.md)
owns no scope, propagates cancellation, uses request-local exact caches and returns
explicit budget-limited prefixes. Wall-clock deadlines are cooperative.

## Consented synthetic pipeline

[ADR 0025](adr/0025-synthetic-aiming-pipeline.md) specifies the integration:
fresh full-display consent -> service-owned capture -> synchronous borrowed Image
copy to bounded owned RGBA -> single-slot DROP_OLDEST mailbox -> serial vision
worker -> stationary complete BoardState -> cancellable planner -> event-resolved
scene -> separate passive preview. Only direct/cut proposals are sampled here.
Normal live capture does not enable recommendations without real calibration.

Capture owns/closes borrowed Images; consumers never retain planes. Vision's
RgbaBoardEstimator owns every OpenCV allocation, crop and deterministic release;
app has no native objects. The service owns lifecycle jobs and windows. Request,
generation, board identity and 1.5s freshness gates suppress late/stale results.
Changed/unavailable observations clear advice; exact corroboration refreshes age.
Errors are explicit and retryable; cancellation is propagated, not converted to data.

Source, opaque controls and non-touchable preview must remain disjoint. The preview
is labeled synthetic and maps board mm to its local pixels; it is not a live-game
alignment claim. Hide/revocation/display changes/Stop/destruction remove windows.
Consent is never restored after process death; pixels/grants/private errors are
not persisted. See [acceptance and calibration gates](phase1-acceptance.md).
