# Original synthetic integration fixtures

These fixtures are authored for this repository; no third-party captures are used.
They establish mechanics and contracts, not game calibration or accuracy.

## stationary-split-v1

Executable source: `core/planner/src/test/kotlin/io/github/carrombot/core/planner/PhaseOneAcceptanceTest.kt`.
Version changes require reviewing this derivation and analytic expectations together.
Board [0,100]² mm; all body radii 5mm, pocket radii 10mm; equal masses,
elastic contacts, 1mm/s² drag. Striker starts (20,50) with launch (100,0)mm/s.
Discs a=(40,55), b=(x+15,50-15√3), where x=40-5√3.
Both discs start at rest. Pockets p=(40+20√3,75), q=b+(10,-10√3).

First contact occurs at striker (x,50). Unit transfer normal (√3/2,1/2)
gives a speed u√3/2 and residual striker speed u/2 along (1/2,-√3/2),
where u=√(10000-2(x-20)). The striker travels 20mm to its second contact,
transfers its remaining speed completely to b and stops. Disc a travels 35mm
to aperture entry; b travels 15mm. For initial speed v and distance d under
unit drag, time is 2d/(v+√(v²-2d)). These independently calculated times define
ordered contact a/s, contact b/s, capture a/p, capture b/q within 1e-7 seconds.
Tests require completed accepted multi-pocket evidence, repeat/fine-clock agreement,
and rejection of a 0.6s partial outcome that captures only a. No geometric
multi-pocket proposal generator or Android presentation is implied.

## Android fixture-v1

`app/.../SyntheticDemoActivity.kt` draws original colored circles and two margin
markers. `SyntheticProfile` owns explicit synthetic bounds/detection/physics policy.
`SyntheticPipelineTest` checks generated packed RGBA through native vision and
actual consented MediaProjection through evaluated-power preview. There is exactly
one disc, pocket and striker; the demo samples direct/cut proposals only.
See [ADR 0025](../../docs/adr/0025-synthetic-aiming-pipeline.md) for layout, ownership,
stationarity and failure contracts and [acceptance](../../docs/phase1-acceptance.md)
for the broader library-family evidence. No captured pixels are stored here.
