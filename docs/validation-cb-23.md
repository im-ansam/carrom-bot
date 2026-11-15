# CB-23 validation — object tracking and stationary board qualification

Date: 2026-11-15. Scope: standalone core:vision adapters; no live capture/planner wiring.
Windows, Gradle 9.9.1, local JDK 18 (bytecode target 17), SDK 36 and a disposable
API 31 x86_64 emulator. Hosted CI uses JDK 21. Local SDKs/caches/logs are ignored.

## Executed validation

All commands below used `gradlew.bat` from the repository root, with the local Java,
Gradle cache and SDK configured in the process environment.

| Command | Actual result |
| --- | --- |
| `ktlintFormat projects assembleDebug test check lint connectedDebugAndroidTest` | PASS, 1m19s; 331 tasks, 43 executed / 288 up-to-date. 120 JVM tests at this point (80 physics, 17 model, 1 app, 22 vision), zero failures/errors/skips. 19 device tests (5 app, 2 capture, 12 vision), zero failures/errors/skips. |
| `ktlintFormat assembleDebug :core:vision:test :core:vision:lint ktlintCheck detekt` after the smoothing-lag fix | App assembly, vision tests and lint passed; overall FAIL solely on detekt line length in the new helper (34s, 150 tasks). Fixed by wrapping the expression. |
| `ktlintCheck detekt :core:vision:test` after that formatting correction | PASS, 18s; 19 tasks, 6 executed / 13 up-to-date. Final vision suite: 23 JVM tests, including 16 new tracking/estimator tests, zero failures/errors/skips. |
| `-PjvmOnly projects test check` with Android SDK environment variables unset | PASS, 18s; 14 tasks, 3 executed / 11 up-to-date. Model/physics JVM tests up-to-date; planner NO-SOURCE. |

The final Kotlin change after the device run is the pure Kotlin smoothing-readiness gate;
it was rebuilt into the app and covered by the final vision tests/lint. The existing native
detector and Android lifecycle code are unchanged. No new Android-specific behavior requires
device-only testing in this ticket.

## Red/green and corrected intermediate failures

* `:core:vision:testDebugUnitTest` initially failed at compilation with unresolved tracker
  and estimator APIs (12s), establishing the new sequence tests before implementation.
* `ktlintFormat :core:vision:testDebugUnitTest ktlintCheck detekt` first passed the behavior
  tests but failed formatting on wildcard imports and line length (18s); explicit imports
  and wrapping fixed these. The next run passed tests/ktlint but failed detekt on method
  complexity, return count and line lengths (24s). Focused helper extraction and wrapping
  resolved them; the subsequent identical command passed (21s).
* `:core:vision:testDebugUnitTest` with the confidence-dwell regression failed one of 15
  tests (1m21s): low-confidence observations incorrectly contributed to stationary dwell.
  Filtering unqualified observations out of tracking breaks their dwell; the passing runs
  above include this regression.
* The first `ktlintFormat projects assembleDebug test check lint connectedDebugAndroidTest`
  run failed (32s, 312 tasks) on one of 22 vision tests and one detekt line length. The
  extreme-value test input still produced a finite velocity; its displacement was corrected
  to actually overflow mm/s. The implementation rejects nonfinite velocity explicitly.
  The full passing run in the table followed both corrections.
* `:core:vision:testDebugUnitTest` with the smoothing-lag regression failed one of 23 tests
  (5s): a strong EMA lag could publish old geometry after the raw object stopped. Requiring
  smoothed geometry to agree with the raw circle within tolerance fixed this; final tests
  and the focused app/vision build above include the fix.

## Coverage and limits

Synthetic sequences cover brief occlusion/reacquisition, missing geometry, raw mm/s motion,
jitter smoothing, stationary dwell and anchor drift, radius creep, ambiguous matching,
expired identity, stale/nonincreasing timestamps, detector failure, confidence recovery,
invalid geometry/confidence/time/configuration, nonfinite velocity, exact inventory,
input ownership and mapping all shared model kinds with unique IDs. Strong smoothing lag
and NaN-confidence filtering have explicit regression cases. Tests use fixed timestamps;
they load no native runtime, read no clock, and need no image/device/secret.

NO-SOURCE does not mean tested: planner has no implementation/tests; overlay has no Kotlin
or tests; capture has no JVM unit tests. Empty Java/resource/native-source tasks also report
NO-SOURCE. Kotlin configuration checks and app preDebugAndroidTestBuild are SKIPPED Gradle
tasks, not skipped test cases. Existing Gradle deprecations remain.

No real-game detection/tracking accuracy, performance, dense-board latency, native leak rate,
API 26–30/32+ or ARM behavior was measured. The conservative association gate can lose IDs
at crossings or large movements. Exact inventory must be independently confirmed, obstacles
cannot be represented in BoardState, and downstream code must invalidate old recommendations
and check timestamp freshness. Calibration and end-to-end composition remain future tickets.
See [ADR 0013](adr/0013-object-tracking.md) for ownership, clocks and qualification contracts.
