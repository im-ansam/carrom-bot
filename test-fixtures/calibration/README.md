# Original numerical calibration fixtures v1

No private video, screenshots, account data or machine paths. `analytic-v1.json`
pins a seed, known parameters, coordinate perturbation and recovery tolerance for
the Python tests. `replay-v1.csv` independently specifies a horizontal constant-drag
launch at 1 BW/s, drag 0.5 BW/s², normal restitution 0.5, radius 23/680 BW.

At 0.25s, x=0.5+0.25-0.25²/4=0.734375. Right rail centre is 657/680;
distance before impact is 317/680; incoming speed²=363/680. Post-impact
stopping distance=(363/680)/4, so final x=0.8327205882352941. y stays 0.4.
These analytic values test the actual Kotlin replay/unit adapter. Its deliberately
wrong endpoint regression must fail. Physical mm are unknown; the runner's 1000
reference units/BW are arbitrary and do not enter shared contracts or app settings.
