# Contribution workflow and validation audit (CB-11)

Audited 2026-11-14 against CB-11, parent CB-1 and dependency CB-10.
Base: `4950f2897692e6fd43e7bfcac0090de6ff625d2b` on main, delivered by
[merged PR #2](https://github.com/im-ansam/carrom-bot/pull/2).

## Acceptance criteria audit

| Requirement | Evidence |
| --- | --- |
| One ticket, keyed branch/commit, review and Jira handoff | Root AGENTS.md and README contribution steps |
| Reproducible setup | README clone, JDK/SDK prerequisites and wrapper commands |
| PR checklist | .github/pull_request_template.md covers scope, tests, contracts, resources and secrets |
| CI on pull requests | Existing .github/workflows/ci.yml has pull_request trigger and JVM/Android jobs; no duplicate android-ci.yml |
| Build, unit-test tasks and lint | JVM projects/test/check and Android projects/assembleDebug/test/check/lint in CI |
| Keep credentials and local data out of Git | .gitignore excludes local.properties, .env, signing files, caches and captures; AGENTS.md requires diff review |

No application code or dummy tests were needed. Existing checklist and
engineering instructions already satisfy the corresponding criteria. Ignore rules
reduce accidental commits; they are not a substitute for reviewing staged files.
Following review, restricted the existing workflow's push trigger to `main`:
PR branch updates run through `pull_request` once, while merged changes still get
main validation. Manual dispatch remains available. Both JVM and Android jobs remain.

## Verified hosted evidence

[Main CI 43606138170](https://github.com/im-ansam/carrom-bot/actions/runs/43606138170)
and [branch CI 43606191610](https://github.com/im-ansam/carrom-bot/actions/runs/43606191610)
passed both `JVM and quality` and `Android build and lint` at the base commit.
The previously reported billing blocker is historical and resolved.
Run 43606144713 is a successful Dependabot run, not build evidence.

These jobs run on JDK 21. The Android job installs command-line tools 15859902,
platform-tools, platform 36 and build-tools 36.0.0. Explicit platform-tools avoids
requesting the obsolete tools package. Compile/target SDK remain 36 because SDK 37
installation failed during bootstrap even with those command-line tools.
`config/lint/lint.xml` suppresses only the exact compileSdk 36-to-37 upgrade message
and OldTargetApi in app/build.gradle.kts; remove these when platform 37 is installable.
Warnings-as-errors remains enabled. App vector icon and explicit legacy/modern backup
exclusions are intentional bootstrap fixes and are preserved.

## CB-11 local validation

Windows, direct JDK 18.0.1.1 via JAVA_HOME (local fallback; CI uses JDK 21), no local
Android SDK. Used a separate `git clone --no-hardlinks --single-branch --branch
codex/CB-11-workflow-audit` of the repository at the base commit, outside the source
checkout, with no project build outputs or local.properties. Reused the ignored
.tools/gradle-home dependency cache; this is clean-source validation, not an empty-cache
or offline-install test. Only documentation changes follow that base.

- `actionlint.exe .github/workflows/ci.yml` (actionlint 1.7.7): exit 0, no diagnostics.
- `gradlew.bat -PjvmOnly projects test check --console=plain`: exit 0; six tasks executed,
  including ktlintCheck and Detekt. Model, physics and planner compile/resource/test
  tasks are NO-SOURCE. Kotlin configuration-error checks SKIPPED because no errors exist.
  No domain behavior was compiled or tested.
- An initial clone under .validation also returned success but ktlint warned that no
  files matched, because that path is excluded. Discarded as analyzer evidence and
  repeated outside .validation; the repeat above had no such warning.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: exit 1,
  dependency resolution for `:app:compileDebugJavaWithJavac` failed with `SDK location
  not found`. Android build/test/lint tasks did not execute locally. Hosted base CI
  evidence above covers the unchanged build configuration.
- `git diff --check`: exit 0 (Git reports CRLF-to-LF normalization notices).

No emulator/device or instrumentation validation is claimed: no platform behavior or
test sources exist yet. Full local Android validation requires the README SDK setup.
