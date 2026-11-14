# CB-10 validation

Checked 2026-11-14 on Windows. Initial repository had no files, commits or remotes.
Remote supplied by the owner: https://github.com/im-ansam/carrom-bot.git (private, empty).
An empty initial main commit provides a PR base; all project files belong to the CB-10 branch.

## Local environment

- Git and GitHub CLI available; authenticated GitHub access verified with network permissions.
- Java 18.0.1.1 works directly from C:\Program Files\Java\jdk-18.0.1.1.
  The PATH Java shim hung, so commands explicitly set JAVA_HOME.
- No global Kotlin/Gradle installation; the wrapper downloads Gradle 9.5.0.
- No Android SDK detected at the conventional location or via SDK environment variables.
- Atlassian MCP available; CB-10 and CB-1 read.
- Build caches are in ignored .tools/gradle-home; no machine-specific paths committed in build files.

## Executed checks

- Official wrapper downloaded; distribution pinned to its published SHA-256.
  Wrapper JAR SHA-256: 497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7.
- gradlew.bat -PjvmOnly projects test: domain projects configured and JARs built;
  compilation/test tasks reported NO-SOURCE. No production code or dummy tests were added.
- gradlew.bat -PjvmOnly ktlintFormat: passed after fixing analyzer artifact selection.
- gradlew.bat -PjvmOnly check: passed, including ktlintCheck and Detekt.
- gradlew.bat -PjvmOnly projects test check: passed again after the plugin classpath fix.
- gradlew.bat projects: passed with all seven requested modules.
- git diff --cached --check: passed. Staged paths reviewed; narrow credential-pattern
  scan found no matching token/private-key material. This is not a comprehensive secret audit.
- Wrapper JAR checksum matched Gradle's published wrapper checksum.
- gradlew.bat projects assembleDebug test check lint: blocked by missing Android SDK
  after resolving the initial shared Kotlin/AGP classloader issue.
  Android compilation, debug APK and Lint were not validated locally.

## GitHub Actions

[Run 43552089449](https://github.com/im-ansam/carrom-bot/actions/runs/43552089449)
failed before either job started. Both jobs had no steps or logs. GitHub's failure
annotation says recent account payments failed or the spending limit must be increased.
This is an account blocker, not a successful or failed build execution.

Open GitHub account Settings > Billing & plans, resolve the payment issue or increase
the Actions spending limit, then rerun CI from the Actions page (or use gh run rerun
43552089449). No billing settings were changed by the agent.
Android debug build and Lint remain outstanding until SDK setup or hosted CI is available.

## Local setup still required

1. Install JDK 21 and set JAVA_HOME to its installation directory.
2. Install Android Studio or the official Android command-line tools:
   https://developer.android.com/studio#command-line-tools-only
3. Set ANDROID_HOME to the SDK root and add cmdline-tools/latest/bin to PATH.
4. Review/accept SDK licenses with sdkmanager --licenses.
5. Run sdkmanager "platforms;android-37" "build-tools;36.0.0" "platform-tools".
6. Run gradlew.bat projects assembleDebug test check lint.

No emulator/device checks are claimed. There is no Android behavior or instrumentation
test source yet; add device tests with the first platform feature.
