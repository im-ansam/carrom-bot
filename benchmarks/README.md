# Synthetic shot candidate microbenchmark

Run from the repository root with JDK 21; an Android SDK is unnecessary:

```powershell
.\gradlew.bat -PjvmOnly :core:physics:benchmarkShots --console=plain
```

On Unix use `./gradlew`. Optional `-PbenchmarkWarmup=100 -PbenchmarkRounds=500`
sets warmup batches and measured shots **per family**; both accept 1..100000.
Invalid arguments or simulation failures fail the task. CI runs a 5/10 smoke
configuration to verify the harness runs; elapsed time is never a CI threshold.

The harness uses the same versioned `shot-v1.csv` inputs as the parameterized JVM
regressions: direct, disc-bank, striker-bank, combination, multi-disc, multi-pocket.
Each evaluation invokes the complete ShotSimulator, including immutable prediction
allocation, events, outcomes and terminal state. The configuration has one 2-second
sample interval, up to three discs and two pockets. It represents candidate physics
evaluation, not a planner search, full rack, device workload or game calibration.

Fixture parsing and warmup are outside the measured region. System.nanoTime is
read only by this test-only driver, never inside simulation. Each family reports
measured shots, elapsed milliseconds, mean nanoseconds/shot, total events/frames
and a consumed position checksum. The header reports JVM version, rounds, total
measured shots and warmup checksum. Failures propagate; failed evaluations are
never counted as successful shots. The driver is compiled into physics test output
from `benchmarks/src/test/kotlin`, not the production library or Android APK.

## Local baseline (2026-11-15)

Windows x64, AMD64 Family 25 Model 80, JDK 18.0.1.1, Gradle 9.9.1; default 100
warmup batches (600 shots), 500 measured shots per family (3000 total). CI uses
JDK 21, so these numbers are not a comparable hosted or Android baseline.

| Family | Shots | Elapsed ms | Mean ns/shot | Events | Frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| direct | 500 | 28.787 | 57573.6 | 1000 | 1000 |
| disc-bank | 500 | 21.822 | 43643.8 | 1500 | 1000 |
| striker-bank | 500 | 18.463 | 36926.0 | 1500 | 1000 |
| combination | 500 | 29.190 | 58380.0 | 1500 | 1000 |
| multi-disc | 500 | 60.972 | 121943.4 | 2000 | 1000 |
| multi-pocket | 500 | 9.068 | 18135.4 | 1000 | 1000 |

This single-process batch timer is a diagnostic microbenchmark, not JMH: JIT/GC,
family order, CPU scheduling and power state affect results. Two development runs
already varied materially. Repeat separate invocations on an idle comparable host
before inferring a change; retain counts/checksums to compare work. No latency
percentiles, confidence intervals, speedup, real-game accuracy or performance budget
are claimed. Longer/finer predictions retain more frames and cost more; changing
the fixtures or config requires a new baseline.
