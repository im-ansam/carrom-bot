# Phase 1 acceptance and release readiness

CB-36, 2026-11-16. Phase 1 is advisory: the user takes every shot manually.
**Synthetic development/demo only; the real-game release gate is not met.**
There is no calibrated real-game profile, empirical game success rate, release-signed
distribution, or claim that all library families are wired into the Android demo.
The user selected synthetic validation while real calibration is unavailable.

## Traceable shot acceptance matrix

Paths below are relative to `core/planner/src/test/kotlin/io/github/carrombot/core/planner/`.
Each named test executes the production ShotSimulator directly or through a generator;
these are synthetic mechanics checks, not successful shots in a third-party game.

| Outcome | Executable test and assertion | Android source-to-preview coverage |
| --- | --- | --- |
| Direct | `ContactShotGeneratorTest`: `generated straight proposal captures target through existing simulator evaluation`; accepted completed target capture | Direct/cut proposals sampled; device smoke requires evaluated power, not a particular family |
| Cut | `ContactShotGeneratorTest`: `angled approach is a cut and back facing approach is unreachable`; angled launch captures requested disc in pocket | Same bounded direct/cut demo; individual cut presentation not asserted |
| Striker bank | `StrikerBankGeneratorTest`: `single bank matches analytic golden events and retains input`; LEFT/contact/capture at 0.25/0.7/0.95s | Library only |
| Double bank | `StrikerBankGeneratorTest`: `double bank matches unfolded length and ordered golden events at two clocks`; LEFT/TOP/contact/capture, 1e-7s tolerance | Library only |
| Disc bank | `ContactSequenceGeneratorTest`: `disc bank has analytic ordered transfer reflection and capture at two clocks`; contact/RIGHT/capture; also `double disc bank completes the requested two rail order` | Library only |
| Combination | `ContactSequenceGeneratorTest`: `two and three disc chains transfer in order and preserve ownership` and `cut transfer carom completes without extra impulses` | Library only |
| Multi-disc | Same two/three-disc test and `four disc bound succeeds and unknown or oversized chains throw`; stationary ordered transfers to final target | Library only; uses COMBINATION family key |
| Multi-pocket | `PhaseOneAcceptanceTest`: `one launch on a stationary board captures two discs in distinct pockets`; two initially stationary discs, two captures, completed accepted result; repeated/fine-clock and partial-outcome rejection tests | Library only |

`core/physics/.../ShotFixtureTest.kt` additionally checks six versioned `shot-v1.csv`
families, complete ordered event snapshots, repeatability, fine clocks, resolved
contact geometry and capture removal. Its historical multi-disc/multi-pocket inputs
include initially moving discs; they do not establish stationary-shot generation.
The new [stationary split fixture](../test-fixtures/end-to-end/README.md) closes that
specific outcome-evidence gap without claiming a multi-pocket geometric generator.
`RobustnessEvaluatorTest` covers sampled sensitivity and safer fallback; acceptance
fractions are not measured real-game probabilities. Generator rejection tests cover
blocked/tangent corridors, illegal placements, early capture, energy loss and truncation.

## Reproduction and device checklist

Follow [README build setup](../README.md#build). CI runs SDK-free tests/checks,
vision evaluator goldens, the physics benchmark smoke, Android assembly/tests/lint.
CI does not run Android instrumentation. The initial CB-36 emulator suite timed out
waiting for advice; an isolated repeat passed. This unresolved intermittent startup
failure limits demo reliability; no cause or timing guarantee is inferred.
See [CB-36 execution evidence](validation-cb-36.md)
for this revision's actual runs; prior [CB-35 evidence](validation-cb-35.md) is historical.

On a disposable emulator with no personal content, install debug app and test APKs.
Use a display with sufficient separate source/control/preview space (480dp portrait
tested; 360dp portrait deliberately refused). Allow overlay access in system settings.

| Check | Required observation | Evidence/status |
| --- | --- | --- |
| Open synthetic fixture demo, share full display with fresh system consent, Start aiming | Evaluated recommendation and displayed power; labeled preview below source | Automated `SyntheticPipelineTest`; current run in CB-36 validation |
| Pause/Resume, Stop/restart aiming, End sharing | Preview clears; resumed result is fresh; service/windows end | Same instrumented smoke |
| Deny consent then retry; move source into control reservation | Retry works; overlap shows space error before another consent request | Instrumented denial/overlap regression |
| Hide, underlying real touch, permission revocation | Both windows removed; preview passes touch through | `core:overlay` instrumentation; historical CB-35 run |
| Background/recreation, rotation, stop capture | No restored grant, stale advice or retained capture resources | Capture/app instrumentation; historical CB-35 run |
| Normal live capture, Start aiming | Calibration-required message; synthetic profile never enables live analysis | Coordinator/service implementation; no real-game demo claimed |
| TalkBack, OEM devices, Android 14+ app-only capture, battery/long-session behavior | Record device/build and actual observations before claiming support | Outstanding manual/device coverage |

Automated emulator interaction is not a manual accessibility or real-game playtest.
No new manual device successes are inferred from the checklist.

## Calibration and empirical release gates

Live advice remains unavailable. Adding it requires authorized game data and an
explicit profile acquisition/integration design; there is currently no profile-import UI.
Record game/version, device, display/insets, orientation, physical board bounds in mm,
homography/corner uncertainty, pockets and disc/striker radii, class HSV/size profiles,
tracking/confidence/stationarity thresholds, independently confirmed full inventory,
legal striker placements and player rules. Calibrate mass ratios, drag in mm/s²,
body/wall restitution, tangential losses and the normalized-power-to-mm/s mapping
from controlled shots. Synthetic equal masses/elastic settings are not substitutes.

Keep held-out authorized shots separate from calibration samples, covering every
family, crowding, motion, occlusion, scale and lighting. Record predicted versus
observed paths, contact order, destinations, power and failures with explicit units
and sample counts. Set acceptance targets before evaluation; none are currently
claimed met for actual games. Obtain explicit capture consent; do not commit captures,
credentials or personal content. Missing/ambiguous evidence must withhold advice.

## Measurements versus targets

| Evidence | Measured result | Limit |
| --- | --- | --- |
| [Physics benchmark](../benchmarks/README.md) | Historical 3000-shot baseline with family times/counts; CB-36 rerun in validation | Up to three discs, coarse samples, host/JIT dependent; not full-rack/device latency |
| [Vision report](../test-fixtures/vision/sample-report.json) and [method](../tools/vision-eval/README.md) | Synthetic board TP8/FP0/FN0, object TP27/FP0/FN0; mean corner 0.4578213px, max 2.236068px | Saved native predictions, original fixtures; not held-out game accuracy or a latency benchmark |
| [CB-35 profiling](validation-cb-35.md) | Dense host sample 209,597,800ns, three plans; native five-observation-plus-plan 157,783,500ns after warmup | Excludes real stationary dwell; neither is end-to-end latency |
| Work policies | 32 simulations/500ms cooperative search; 100ms pixel receipt interval; 1.5s evidence TTL | Configuration, not achieved timing/accuracy targets |

Release readiness requires passing actual build/quality checks and exact-head review.
That engineering gate does not certify real-game calibration, optimal shots, a hard
latency bound, queen/rule correctness, or empirical accuracy.
