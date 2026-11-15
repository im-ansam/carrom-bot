# Carrom Bot

Kotlin/Android foundation under [CB-1](https://ansamcd.atlassian.net/browse/CB-1),
with shared geometry, board-state and prediction data contracts from
[CB-12](https://ansamcd.atlassian.net/browse/CB-12). No Carrom AI behavior is implemented.
The launchable app from [CB-13](https://ansamcd.atlassian.net/browse/CB-13) provides
start/stop controls, settings navigation and privacy-safe local diagnostics. [CB-20](https://ansamcd.atlassian.net/browse/CB-20)
adds consent-based screen capture in a foreground service.

## Modules

| Module | Platform | Direct project dependencies | Responsibility |
| --- | --- | --- | --- |
| app | Android app | all six core modules | Launcher, permission setup, diagnostics and composition |
| core:model | JVM | none | Shared state, coordinates and permission setup contracts |
| core:physics | JVM | model | Fixed-step shots, drag, power, swept contacts, banks and pocket capture |
| core:planner | JVM | model, physics | Future shot planning |
| core:vision | Android library | model | Board geometry, object recognition, tracking and stationary state qualification |
| core:capture | Android library | model | Bounded MediaProjection frames, resize and resource ownership |
| core:overlay | Android library | model | Future aiming overlay |

Dependencies point inward to platform-free modules; core modules never depend on app.
OpenCV 4.12.0 is confined to the vision adapter; coroutine dependencies remain deferred.
The launcher checks overlay access and asks for screen-sharing consent only after Start.
Denied/revoked permissions are retryable. A successful, fresh consent result is handed once
to the foreground capture service. It continues across app backgrounding and recreation;
Stop in the app or capture notification ends sharing. Process death never restores capture.
Frames are borrowed synchronously in memory, closed after delivery and never saved or logged.
The controls show only frame counts and pixel dimensions. The standalone vision adapter
is not wired into capture yet; aiming and floating overlays remain future work.
On Android 13+, the foreground service can run without
notification permission; Android may show its controls only in Active apps when notifications
are disabled. The app's Stop control remains available.
See the [permission setup decision](docs/adr/0003-permission-setup.md) and its
[capture extension](docs/adr/0010-consented-screen-capture.md).

[CB-21 board geometry](docs/adr/0011-board-geometry.md) detects a complete high-contrast
near-square boundary, returns an immutable homography with geometric confidence,
and can normalize borrowed RGBA frames. Missing/ambiguous geometry fails explicitly.
Physical bounds must be supplied by the caller. Synthetic fixtures establish mechanics,
not actual game accuracy; see [validation](docs/validation-cb-21.md).

[CB-22 object recognition](docs/adr/0012-object-recognition.md) accepts rectified frames
and caller-calibrated HSV/size profiles for pockets, discs, striker and obstacles.
It returns owned typed circles in board mm with class-conditional fit confidence;
clipped, merged and ambiguous candidates are conservatively rejected. It does not
infer IDs, missing objects or a complete board state. Original synthetic fixtures
measure mechanics only; see [validation](docs/validation-cb-22.md).

[CB-23 tracking](docs/adr/0013-object-tracking.md) retains unambiguous IDs through brief
occlusions, smooths observed geometry and measures raw motion. Its board estimator emits
a shared BoardState only for a fresh, stationary, confidence-qualified scene matching an
independently confirmed caller inventory. Missing/ambiguous objects and obstacles withhold
readiness. Capture/planner/overlay integration remains CB-35; synthetic sequence tests
do not establish real-game accuracy. See [validation](docs/validation-cb-23.md).

[CB-24 offline vision evaluation](tools/vision-eval/README.md) scores hash-pinned
board/object fixtures and actual device prediction snapshots with per-class errors
and edge-local scale calibration. See the [synthetic dataset](test-fixtures/vision/README.md)
and [decision](docs/adr/0014-vision-evaluation.md); these results do not establish game accuracy.

Shared types live in `io.github.carrombot.core.model`. Board geometry uses millimetres,
top-left origin, +X right and +Y down; velocities use mm/s and prediction times use
elapsed seconds. Snapshots own immutable ordered collections and stable caller-assigned
IDs. See the [model contract decision](docs/adr/0002-shared-model-contracts.md) for
validation, screen transforms, precision, ownership and fixture compatibility.

The [CB-14 free-motion integrator](docs/adr/0004-deterministic-free-motion.md) uses
fixed steps and an explicit simulation clock, bounded by a configured step budget.
The [CB-15 drag and power models](docs/adr/0005-friction-and-power.md) add configurable
constant deceleration, analytic stopping distance and an invertible linear striker
power mapping. Zero configured drag preserves free motion. These models require
calibration. The [CB-16 contact solver](docs/adr/0006-body-contact-solver.md) adds swept
circle contacts, configurable masses/restitution, separation and deterministic chains.
The [CB-17 rebound solver](docs/adr/0007-wall-rebounds.md) adds closed rectangular walls,
normal/tangential restitution, wall friction and repeated banks in the same event loop.
The [CB-18 shot simulator](docs/adr/0008-pocket-shot-simulation.md) adds full-fit
pocket apertures, capture removal, typed outcomes and an ordered event timeline.
`ShotSimulator().simulate(board, ShotInput(board, velocity), config)` returns owned
samples and final state. A captured striker remains frozen in the snapshot and is
identified by `strikerCaptured`; discs are removed. Player scoring and turn rules
remain caller responsibilities. Invalid containment, nonfinite updates or
collision-budget exhaustion fail explicitly.

The [CB-19 regression harness](docs/adr/0009-physics-regression-harness.md) checks
ordered analytic event snapshots, repeated/fine-clock runs and seeded physical
properties in the ordinary JVM test suite. See [synthetic benchmarks](benchmarks/README.md)
for the SDK-free candidate evaluation command, measured baseline and limitations.

## Build

Start from a fresh checkout (GitHub access to this private repository is required):

```powershell
git clone https://github.com/im-ansam/carrom-bot.git
cd carrom-bot
```

Install JDK 21 (Java/Kotlin bytecode target 17), Android SDK command-line tools, platform 36,
and build-tools 36.0.0. Set JAVA_HOME and ANDROID_HOME, or set sdk.dir in ignored local.properties.
Use a current Android Studio version supporting AGP 9.3.

```powershell
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
.\gradlew.bat projects assembleDebug test check lint
```

Without an Android SDK:

```powershell
.\gradlew.bat -PjvmOnly projects test check
```

On Unix, substitute `./gradlew`. The wrapper downloads its checksum-pinned Gradle distribution.
Global Kotlin and Gradle installations are unnecessary. Minimum Android API is 26; target API is 36 and compile API is 36.
JUnit 4 is available in every module; core:model contains JVM contract tests and a versioned synthetic fixture.
NO-SOURCE means no tests ran, not that behavior was tested.

Compile instrumentation with `.\gradlew.bat :app:assembleDebugAndroidTest` and run it
with `.\gradlew.bat connectedDebugAndroidTest` on a disposable emulator. Permission
tests temporarily change this app's overlay app-op and restore its previous mode.
Denial tests intercept permission screens; capture integration tests accept real Android
consent on a disposable emulator, acquire frames in memory and verify rotation, background,
stop, revocation and failure cleanup. No captured pixels are persisted. Run only on a
disposable emulator with no personal accounts or screen content. See [CB-20 validation](docs/validation-cb-20.md).

## Quality and delivery

See [AGENTS.md](AGENTS.md), [tool decisions](docs/adr/0001-bootstrap-toolchain.md),
[Codex setup](docs/codex-setup.md), and [validation evidence](docs/validation.md).
For each contribution, read the Jira ticket, parent and dependencies, then branch from
current `main` as `codex/CB-<number>-<description>`. Implement one ticket, run the
applicable checks above, review the full diff, and commit as `CB-<number>: <summary>`.
Open a PR against `main` using the [PR checklist](.github/pull_request_template.md),
record actual validation and the PR in Jira, and request human review. Stop until review;
merging requires explicit authorization. Never commit local SDK paths or credentials.

CI checks JVM modules without the SDK and builds and lints all Android modules.
Both jobs in [.github/workflows/ci.yml](.github/workflows/ci.yml) run on pull requests;
they may run concurrently. No separate `android-ci.yml` is needed.
Push validation runs only on `main`, so updating a PR branch triggers one CI run.
All Action revisions are pinned. Dependabot proposes Gradle and Action updates.
The private repository has no paid dependency-review integration; enable GitHub secret scanning and
dependency security alerts in repository settings where the account supports them.
Never commit captured screen data, tokens or release signing material.
