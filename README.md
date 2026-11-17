# Carrom Bot

Kotlin/Android foundation under [CB-1](https://ansamcd.atlassian.net/browse/CB-1),
with shared geometry, board-state and prediction data contracts from
[CB-12](https://ansamcd.atlassian.net/browse/CB-12). Planning is advisory; real-game accuracy is unverified.
See the [Phase 1 acceptance matrix and release limits](docs/phase1-acceptance.md)
and [architecture contracts](docs/architecture.md). Synthetic demo readiness does
not establish real-game readiness; a calibrated live-game profile is unavailable.
The launchable app from [CB-13](https://ansamcd.atlassian.net/browse/CB-13) provides
start/stop controls, settings navigation and privacy-safe local diagnostics. [CB-20](https://ansamcd.atlassian.net/browse/CB-20)
adds consent-based screen capture in a foreground service. [CB-32](https://ansamcd.atlassian.net/browse/CB-32)
adds a permission-checked floating Hide control owned by that service.

## Modules

| Module | Platform | Direct project dependencies | Responsibility |
| --- | --- | --- | --- |
| app | Android app | all six core modules | Launcher, permission setup, diagnostics and composition |
| core:model | JVM | none | Shared state, coordinates and permission setup contracts |
| core:physics | JVM | model | Fixed-step shots, drag, power, swept contacts, banks and pocket capture |
| core:planner | JVM | model, physics | Shot generation, simulation ranking, cooperative budgeted scheduling |
| core:vision | Android library | model | Board geometry, object recognition, tracking and stationary state qualification |
| core:capture | Android library | model | Bounded MediaProjection frames, resize and resource ownership |
| core:overlay | Android library | model | Permission-safe floating controls, passive trajectories and lifecycle cleanup |

Dependencies point inward to platform-free modules; core modules never depend on app.
OpenCV 4.12.0 is owned by vision; app composition consumes its public adapters.
kotlinx-coroutines-core 1.10.2 supports planner scheduling and app-owned pipeline jobs.
The launcher checks overlay access and asks for screen-sharing consent only after Start.
Denied/revoked permissions are retryable. A successful, fresh consent result is handed once
to the foreground capture service. It continues across app backgrounding and recreation;
Stop in the app or capture notification ends sharing. Process death never restores capture.
Frames are borrowed synchronously in memory, closed after delivery and never saved or logged.
The launcher shows frame counts and pixel dimensions. CB-35 connects consented capture
to vision, stable-state qualification, planning and trajectories in an explicitly labeled
synthetic fixture demo. Live aiming explains that a calibrated game profile is required.
The floating Hide
control consumes touches only inside its bounds; other touches reach the underlying app.
Hide removes the overlay immediately while sharing continues. Rotation/display changes hide
it conservatively; restart sharing to show it again. Stop, service destruction and capture
termination release the window. Permission revocation while visible removes it and stops sharing.
On Android 13+, the foreground service can run without
notification permission; Android may show its controls only in Active apps when notifications
are disabled. The app's Stop control remains available.
See the [permission setup decision](docs/adr/0003-permission-setup.md) and its
[capture extension](docs/adr/0010-consented-screen-capture.md) and
[overlay lifecycle](docs/adr/0022-overlay-lifecycle.md).

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
readiness. CB-35 integrates this gate in the synthetic demo; synthetic sequence tests
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

[CB-25 planner contracts](docs/adr/0015-planner-contracts.md) describe all seven shot
families and custom family keys, pocket targets, normalized power and coarse contact
constraints. Evaluation consumes the unified simulator and rejects truncation,
fouls and missed targets before scoring advisory utility.
[CB-26 direct/cut generators](docs/adr/0016-direct-cut-geometry.md) expose ghost/contact
geometry and conservatively check corridors and pocket apertures using caller-supplied
legal positions, aim points, power and calibration. Their proposals require physics evaluation.
[CB-27 striker-bank generation](docs/adr/0017-striker-bank-generation.md) proposes
one/two ordered wall reflections and returns only completed simulations with the
requested striker-wall/target collision order and capture.
[CB-28 disc banks and combinations](docs/adr/0018-disc-banks-combinations.md) enumerate
bounded wall/disc sequences, prune corridors and require completed simulator-verified
contact order and capture, including cut-transfer caroms and multi-disc chains.
[CB-29 simulation search](docs/adr/0019-simulation-search.md) evaluates explicit
placement/angle/power samples and sweeps calibrated powers with full simulated
evidence, ranked feasible plans and diagnostic rejections. Optional body-contact
and terminal striker-position penalties express caller policy. No sampled solution
returns an empty plan list.
[CB-30 multi-pocket sensitivity](docs/adr/0020-multi-pocket-robustness.md) compares
explicit target sets, retains deterministic parameter/detection/power scenarios,
marks uncertain plans and exposes a higher-confidence feasible fallback when tested.
Confidence is a sampled acceptance fraction, not a calibrated game success probability.
[CB-31 scheduling](docs/adr/0021-budgeted-planner-scheduling.md) runs proposal evaluation
on coroutine workers with simulation/time budgets, cooperative cancellation, exact
request-local physics caching and power-policy pruning. Partial results identify
unexamined candidates and retain only complete evidence; deadlines are cooperative.

[CB-33 trajectory rendering](docs/adr/0023-trajectory-rendering.md) supplies an explicit
board-mm/local-pixel Canvas renderer and passive view for multi-body paths, rebound,
contact and pocket markers, selected/alternative candidates, power and sensitivity.
Normalized [0,1] inputs have an explicit mapping adapter. Synthetic goldens verify
rendering; CB-35 adds event-resolved physics geometry for faithful simulated paths.
The floating Hide control clears both controls and synthetic prediction preview.

[CB-34 aiming controls](docs/adr/0024-aiming-controls.md) add Start/Stop aiming,
Pause/Resume, direct candidate selection, Next best shot, recommended power and
accessible status/errors. Aiming mode changes clear predictions; screen sharing
continues until End screen sharing, launcher Stop or notification Stop. The bounded
panel scrolls for large text and candidate lists. No shots are executed automatically.

[CB-35 integration](docs/adr/0025-synthetic-aiming-pipeline.md) provides **Open synthetic
fixture demo** from the launcher. Allow overlay access first, then share the demo's
full screen and press Start aiming. Original generated source pixels pass through
actual MediaProjection, bounded owned RGBA copying, native detection, stationary
tracking, budgeted planning and a separately labeled prediction preview below the
source board. Source and preview require sufficient display space. Missing fixture
markers, changed geometry, motion, stale frames or errors clear advice. Pause/Stop
cancel jobs; ending sharing releases resources. Live-game calibration/inventory/legal
placement acquisition is not implemented and synthetic settings never enable live
game recommendations. See [actual validation](docs/validation-cb-35.md).

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

[Offline recording analysis](docs/calibration.md) measures nine wall shots and three
exploratory breaks with external private media, actual PTS and held-out errors.
CB-37's fitted model fails its exploratory trajectory targets; it supplies a
reproducible numeric report, **no usable live-game profile**. Synthetic demo settings
remain separate and live aiming remains unavailable.

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
