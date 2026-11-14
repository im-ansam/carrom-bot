# Carrom Bot

Minimal Kotlin/Android foundation for [CB-10](https://ansamcd.atlassian.net/browse/CB-10),
under [CB-1](https://ansamcd.atlassian.net/browse/CB-1). No Carrom AI functionality is implemented.

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

## Build

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
Global Kotlin and Gradle installations are unnecessary. Minimum Android API is 26; target/compile API is 36.
JUnit 4 is available in every module; source and test directories will be added with real implementations.
NO-SOURCE means no tests ran, not that behavior was tested.

## Quality and delivery

See [AGENTS.md](AGENTS.md), [tool decisions](docs/adr/0001-bootstrap-toolchain.md),
[Codex setup](docs/codex-setup.md), and [validation evidence](docs/validation.md).
CI checks JVM modules without the SDK, then builds and lints all Android modules.
All Action revisions are pinned. Dependabot proposes Gradle and Action updates.
The private repository has no paid dependency-review integration; enable GitHub secret scanning and
dependency security alerts in repository settings where the account supports them.
Never commit captured screen data, tokens or release signing material.
