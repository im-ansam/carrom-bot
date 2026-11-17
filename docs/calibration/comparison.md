# Timing and offline model comparison v1

CB-38, 2026-11-17. **No usable profile; live aiming remains unavailable.** A bounded
arrow-proxy curve is a better follow-up hypothesis in six internal development
folds, but still misses every fold's 0.02 BW trajectory target. Adding nonnegative
speed drag does not improve this dataset. The observations do not isolate a causal
game-physics error from input mapping, sprite/geometry and display timing errors.

The [prior-art audit](prior-art.md) preceded new fitting and identifies useful
equipment metadata and geometric approaches without adopting third-party code or
unverified accuracy figures. [ADR 0027](../adr/0027-training-only-model-comparison.md)
keeps every alternate equation offline. Production modules/settings are unchanged.

## Evidence isolation and reproducibility

[plan.json](comparison-v1/plan.json) was committed in `1066c23` before numerical
implementation/fitting. Its three fixed candidates add at most one parameter to
the baseline. All six leave-one-shot-out folds use only CB-37 repetitions 1/2 at
each input label. Every fold fits five shots and scores the omitted sixth. Neither
historical repetition 3 nor breaks enter any optimizer, ranking, perturbation,
residual plot or target comparison. No retrospective rescore is performed. Those
historical holdouts remain viewed evidence, never fresh independent validation.

The objective is equal-weight per-shot mean squared 2D centre error at original
PTS. This deliberately differs from CB-37's sample-weighted whole-fit objective;
new aggregate CV scores must not be compared to CB-37's historical holdout RMSE
as though they measure the same population. Conditional measured directions and
start annotations remain unchanged, so these are not blind predictions from UI aim.

The [numeric report](comparison-v1/report.json) includes input hashes, every
multistart solution, full-fit and fold parameters, time-stamped residuals,
bounce/rest metrics, perturbations and local identifiability diagnostics. Bounds
are offline search limits, not measured game coefficients. The original
`recordings-v1` plan/measurements/report/sensitivity/replay are unchanged.

## Timestamp and measurement diagnosis

All six source SHA-256 hashes were rechecked against the external read-only
originals. The retained full extractor outputs agree with original PTS/time_base;
all six training trajectories exactly match their extractor marker coordinates.
There is no annotation correction in this version. This checks consistency with
the existing colour/ROI method, not independent ground truth for body centres.

Training shots contain **94 exact repeated-centre pairs**, with run brackets
retained, and four missing-marker frames, all at the annotated release boundary.
The largest adjacent usable-sample gap is 0.01923s. Longest identical-centre runs
range 0.01768–0.10164s; terminal holds contribute, so these cannot establish an
engine update cadence. Pixel compression/UI change also makes exact whole-crop
duplicate counts unsuitable as an update clock. No missing motion is interpolated.
No observed evidence here justifies claiming that ROI loss during isolated moving
segments caused the mismatch, or that an engine simulation step is 1/60s.

Early quadratic diagnostics use requested 0.1/0.2/0.4s windows, clipped before
the first rail's ambiguity margin. At maximum input the actual early windows are
only 0.08–0.10s, and wider requested windows reuse the same short evidence rather
than creating extra observations. Early drag estimates range roughly −0.96 to
26.21 BW/s²; independent ±3px coordinate perturbations alone yield worst-case
sensitivities as large as ±17.45 BW/s². These are deterministic perturbation
envelopes, not confidence intervals; they do not explain every discrepancy or
include timing/sprite/geometry systematics. In particular the high maximum-2
estimate cannot be made into a reliable drag estimate by quoting pixel precision.

Longer terminal isolated segments yield local drag 0.5888–0.6465 BW/s² with
coordinate sensitivities ±0.0496–0.1206 BW/s² and residual RMSE 0.00296–0.00593 BW.
This is consistent with better conditioning over longer travel, not a measured
global drag law. A display hold/jump process, release offset and real nonconstant
loss remain plausible confounders; current observations cannot distinguish them.
No exact impulse/contact time or intermediate engine position is inferred.

## Fixed candidate results

`q` means visible arrow length/BW, **not normalized game power**. Baseline uses
`v0=gain*q`, `dv/dt=-a`. Speed drag adds `-b*v`, with `b≥0`; the proxy curve uses
`v0=gain*q*(q/0.2)^(exponent-1)` under constant drag. Each retains normal loss `e`
and assumes tangent retention 1/friction 0 solely for comparison. Those tangent
values remain uncalibrated. Physical mm, game build and equipment remain unknown.

| Candidate | Internal CV RMSE BW | Full training RMSE BW | Result |
| --- | ---: | ---: | --- |
| Constant | 0.10897912 | 0.08636501 | Baseline |
| Constant + speed drag | 0.10903237 | 0.08636501 | No useful improvement; full-fit b=0 |
| Bounded proxy curve | 0.08393731 | 0.06038259 | Follow-up hypothesis only |

The curve improves aggregate CV RMSE 22.98% and five of six folds, meeting the
predeclared 10%/five-fold hypothesis-selection rule. It worsens low-2 (0.05143 to
0.07844 BW). All curve folds remain above 0.02 BW; matched bounce timing errors
reach 0.136s, available stop-time errors reach 0.310s and stop-position errors reach
0.116 BW. Low-2 rest remains unavailable. Thus neither selected-hypothesis status
nor an improved aggregate score can pass readiness. See the
[per-shot tables and residual plot](comparison-v1/metrics.md).

Full-fit curve parameters are `a=0.96321252 BW/s²`, `gain=12.28030154 s⁻¹`,
`e≈1` at its bound and `exponent=1.18403415`. These are rejected-for-use model-fit
values. Across six folds and four coherent single perturbations (release ±0.0334s,
arrow ±5px), `a` spans 0.8779–1.0558, gain 11.7560–12.8162 and exponent
1.1246–1.2430. No free per-shot timing shift is fitted; observations before a
positive shifted release predict the stationary start and remain in the objective.
The sensitivity ranges are neither joint uncertainty bounds nor confidence limits.

Local normalized residual-Jacobian columns are strongly coupled: baseline
drag/gain/loss cosine magnitudes exceed 0.96; speed-loss/normal-loss magnitude is
about 0.995. Curve curvature is less locally coupled, but finite columns do not
establish global identifiability. Restitution stays at its bound in every curve
fold/perturbation. Near-normal rails cannot identify tangential response; occluded
rack breaks cannot supply isolated body/pocket parameters.

The fixed coarse-grid/five-start normalized simplex is a bounded local search,
not a proof of a global optimum. Individual starts retain substantially worse
basins. Near-zero speed-drag fold solutions differ slightly from baseline from
finite local search; the 0.00005 BW aggregate difference is not evidence of a
physical effect. An independent midpoint-ODE synthetic regression recovers known
nonzero speed drag/high gain within 0.001, and closed-form/small-b/rest tests check
the analytic implementation independently of recording fit.

## Next evidence and reproduction

The [controlled phone protocol](controlled-phone-protocol.md) fixes metadata,
input sweep, shot cells, sealed split, exclusions, acquisition-stop rules and
per-shot acceptance metrics before collection. Start with 16 long isolated striker
shots; proceed to isolated contacts/pocket outcomes only after timing/proxy gates
pass. Direct/cut readiness also needs CB-40 real-game detection and CB-41 integration
evidence. Emulator work can help UI/vision testing but supplies a separate scope.

Python 3.10+ standard library; use original CB-37 numeric extraction outputs in an
ignored directory, or regenerate them with the documented pinned media decoder.
The CLI needs all six hashed inventories for audit but selects training IDs before
trajectory analysis. No raw media paths enter the output.

```powershell
py -m unittest discover -s tools/calibration -v
py tools/calibration/compare.py --extractions .validation --output docs/calibration/comparison-v1/report.json
py tools/calibration/render_comparison.py docs/calibration/comparison-v1
```

Use `python3` on Unix. Identical plan/measurements/extractor inputs reproduce
numeric outputs within 1e-8 absolute; same recorded Python runtime yields identical
JSON bytes. Renderer tables/plot are derived from this report, not new fits.
Actual executed checks and limitations are in [validation](../validation-cb-38.md).
