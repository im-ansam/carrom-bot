# Controlled phone acquisition, version 1

CB-38 protocol declared before new collection. CB-39 must confirm feasibility and
freeze the manifest before recording; this document is not a request to upload
generic videos. Target: Nothing Phone (2a), Miniclip Carrom Pool. Emulator results
belong to a separate environment and cannot validate this phone.

## Scope and session metadata

Initial intended family is advisory direct/cut shots with one isolated target and
no rail contact. Bank, combination and rack-break advice remains outside that
scope until its additional parameters and outcomes have independent evidence.
Do not claim full-board readiness from the minimum isolated experiments below.

Before shooting, record the installed game version/build, Android/display version,
pixel resolution, orientation, refresh-rate setting, recording settings, board/theme,
striker identity/upgrade, equipped Power and effective Force/Aim/Time stats. Record
unknowns explicitly; unresolved effective Force or build blocks a transferable
profile. Miniclip's [equipment documentation](prior-art.md) makes these controls
necessary. Use one unchanged configuration, no other overlays or touch indicators
covering bodies. Keep private account/UI imagery local. Store sanitized metadata
and SHA-256 hashes of the unmodified originals; never upload originals to Git/Jira.

Use an available authorized local/private game mode and manual shots. Confirm that
the required isolated arrangements can actually be prepared. If they cannot, stop
and record that concrete constraint; do not silently replace them with rack shots,
install a modified game, or treat an emulator as this device. No automation needed.

## Input and clocks

Capture the complete board and input graphic at native resolution, with 1s of
stationary aiming before release and at least 1s of visible stationary rest after
motion. Aim away from pockets/discs for isolated striker tests; put the striker
near one end of a clear lane to lengthen pre-rail travel. Keep all later rebounds
visible until rest. Reject turn-reset animation as motion evidence.

First record a no-shot slow sweep of the available input graphic from minimum to
maximum and back, with pauses at four evenly spaced visible levels. Identify the
actual adjustable shot-power graphic separately from the aim guideline (Aim stats
change the latter). Declare graphic endpoints/measurement rule and uncertainty in
pixels before measuring launch speeds. If a graphic saturates, do not label its
flat region as distinct known powers. Use four distinguishable interior lengths
at nominal 20/40/60/80% of the **observed graphic range**, not claimed game power.
Record each achieved extent and ±pixel annotation uncertainty, not merely a level
name. If no measurable stable proxy exists, acquire only timing diagnostics and
stop before power-to-speed validation; a visible arrow is not proof of input force.

Decode actual PTS/time_base, reject missing/nonmonotonic PTS and preserve gaps.
Keep every usable repeated centre. Annotate last stationary/first moving frame
brackets, not an exact impulse time. Track visible body centres using two
independent annotation passes, retaining disagreement and systematic sprite-offset
uncertainty. No intermediate engine updates, subframe events or interpolated
observations may be invented. Use a board homography with explicit pixel-to-BW
mapping and measured radii; physical mm remain unknown unless independently given.

## Minimum staged matrix and allocation

Give every attempt an ID before capture. Each cell has four attempts: repetitions
1/2 are development, 3/4 are sealed validation. Alternate the repetition rounds
across cells to expose session drift; never assign split by outcome or apparent
quality. Store validation clips separately, unopened by the fitting analyst until
model, annotations/rules, tolerances and parameters are frozen and hashed. A
separate custodian can check file presence/technical integrity without revealing
trajectories. If the same analyst must inspect them to repair measurement code,
they lose freshness; collect a replacement sealed validation batch after freezing.

| Stage | Cells and attempts | Purpose / gate |
| --- | --- | --- |
| A: isolated striker | Four visible input lengths × four repetitions = 16 shots. Clear near-normal lane; retain full trajectory/rest and every observed rail event. | Separate launch mapping, long-segment slowing, normal rebound and display clock. Do this stage first; do not collect later stages if clock/proxy/measurement gates fail. |
| B: isolated target contact | Three measured offsets (nominal 0, 0.45, 0.75 of striker+disc radius sum) × two distinguishable input lengths × four repetitions = 24 shots. Use labelled single target, clear approach/exit and no pocket/rail along the measured outgoing interval. | Fit conditional body response and check direct/cut contact order, outgoing directions, speeds and final positions. Actual measured geometry replaces nominal setup targets. If an unintended second contact occurs, retain attempt and classify outside the isolated matrix. |
| C: pocket approach | Three measured aim lanes (centre, near inner rim, just outside mouth) × two distinguishable input lengths × four repetitions = 24 shots. One target; label intended pocket, track striker and target through outcome. | Pocket geometry/capture transition and direct/cut end-to-end outcomes; no probability claim from these few repetitions. Rim/outside cases include expected misses and must not be discarded. |
| Optional D: rail extensions | Two oblique incidences (nominal 30°/60° from normal) × two input lengths × four repetitions = 16 shots, with isolated rail before any body contact. | Required before bank-family calibration; distinguishes tangent loss only to the precision the observations permit. Not a gate to claim banks from A alone. |

Minimum initial direct/cut evidence is 64 shots (32 development/32 sealed), plus
the input sweep and metadata. It is a diagnostic minimum, not a guarantee of
identifiability or accuracy. Freeze a new protocol version before any extra cells
or repetitions; do not keep adding validation shots until a desired result appears.
Start with the 16-shot Stage A so a failed timing/input setup costs little work.

## Measurement, exclusion and stop rules

All attempts remain in the manifest with disposition. Technical exclusions must be
decided without predicted residuals or outcomes: incomplete board/required body,
overlap hiding a required centre, unexpected body contact in an isolated interval,
turn reset before 1s of corroborated rest, power graphic ambiguity, equipment/build
change, missing/nonmonotonic PTS, or gaps >0.05s during moving/event intervals.
Retain usable prefixes as diagnostics but they cannot pass missing rest/event gates.
Withhold event scoring if the displayed bracket exceeds 0.05s; keep the failure
visible. Do not discard legitimate misses, slow shots or large prediction errors.

Two annotation passes must agree within 0.005 BW for centres/radii and 0.01 BW for
rail/pocket landmarks; disagreements require review and explicit uncertainty.
Those are acquisition targets, not current achieved precision. Stop a stage if
more than one of its four attempts in any cell is technically unusable, if game
configuration changes, or if updates repeatedly remain indistinguishable at the
needed event precision. Diagnose capture/appearance before scheduling a new batch.
No measured clock bracket can be narrowed solely to improve fit.

## Frozen validation metrics

Use original PTS and equal shot weighting. Report per-shot 2D RMSE/max error,
signed residuals over time, matched event order/count, bounce/contact location/time,
outgoing direction, rest location/time and capture outcome. Missing required
observations fail the applicable gate rather than contributing zero error.

For each sealed shot: trajectory RMSE ≤0.02 BW, maximum error ≤0.05 BW; every
required event count/order correct; event position/time errors ≤0.02 BW/0.05s;
rest position/time errors ≤0.03 BW/0.15s. For B, outgoing direction error ≤3°
for both moving bodies where at least 0.1 BW of visible outgoing travel exists;
otherwise that direction gate is unavailable and cannot pass. For C, observed
capture/miss and intended pocket must agree exactly in all sealed cases. Report
all results including rim cases and uncertainty; do not turn pass fractions into
calibrated confidence. These are proposed acceptance thresholds, not measured
performance. No mean can hide a failing cell or shot.

Select/refine models only with development data and shot-level internal validation.
Freeze the selected model, parameters, unknowns, device/build/equipment scope and
code hash before opening sealed data. Score once; failure requires a documented
new development cycle and newly untouched validation, never tuning on the opened
batch. Stage A alone cannot validate target contacts/pockets; B/C are required
before an initial scoped direct/cut profile. Real inventory/vision/mapping and
device overlay acceptance are separate CB-40/CB-41 gates. Readiness remains blocked
while any required parameter/measurement or validation gate is unavailable.
