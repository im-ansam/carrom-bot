# Carrom Bot

Kotlin/Android foundation under [CB-1](https://ansamcd.atlassian.net/browse/CB-1),
with shared geometry, board-state and prediction data contracts from
[CB-12](https://ansamcd.atlassian.net/browse/CB-12). No Carrom AI behavior is implemented.

## Modules

| Module | Platform | Direct project dependencies | Responsibility |
| --- | --- | --- | --- |
| app | Android app | all six core modules | Future composition and UI entry point |
| core:model | JVM | none | Shared state and coordinate contracts |
| core:physics | JVM | model | Future deterministic simulation |
| core:planner | JVM | model, physics | Future shot planning |
| core:vision | Android library | model | Future OpenCV adapter |
| core:capture | Android library | model | Future screen capture |
| core:overlay | Android library | model | Future aiming overlay |

Dependencies point inward to platform-free modules; core modules never depend on app.
OpenCV and coroutine dependencies are deferred until a ticket needs them.
The APK intentionally has no launcher Activity, permissions or services yet.

Shared types live in `io.github.carrombot.core.model`. Board geometry uses millimetres,
top-left origin, +X right and +Y down; velocities use mm/s and prediction times use
elapsed seconds. Snapshots own immutable ordered collections and stable caller-assigned
IDs. See the [model contract decision](docs/adr/0002-shared-model-contracts.md) for
validation, screen transforms, precision, ownership and fixture compatibility.

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
