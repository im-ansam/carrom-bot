# CB-39 preparation validation — incomplete ticket

2026-11-17, Windows PowerShell, Python 3.13.14, JDK 18.0.1.1 fallback, existing
ignored Android SDK/cache. No new controlled phone evidence or measured metadata
available. This log covers acquisition preparation only. Numeric profile contract,
parameter estimation/identifiability, unit-conversion integration and actual held-out
production-simulator replay remain outstanding. No usable profile or live change.

## Commands actually executed

- `py -m unittest discover -s tools/calibration -p test_acquisition.py -v`:
  red, exit 1 (`ModuleNotFoundError: No module named 'acquisition'`) before implementation.
- `py -m unittest discover -s tools/calibration -v`: green, exit 0, 30 tests
  (24 existing and six acquisition/custody regressions). Ordinary synthetic fixtures;
  no private media, network services or optional decoder packages.
- `py tools/calibration/acquisition.py --new --output docs/calibration/controlled-v1/plan.json`:
  exit 0. Generated 64 preassigned attempts, all not recorded; metadata null.
- `py tools/calibration/acquisition.py --plan docs/calibration/controlled-v1/plan.json --output docs/calibration/controlled-v1/audit.json`:
  exit 0. Structurally valid, explicitly blocked, `usable=false`, zero recordings.
- `.\gradlew.bat -PjvmOnly projects test check --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --console=plain`:
  exit 0, BUILD SUCCESSFUL in 23s; 18 actionable tasks, three executed/15 up-to-date.
- `.\gradlew.bat projects assembleDebug test check lint --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8' --console=plain`:
  exit 0, BUILD SUCCESSFUL in 37s; 213 actionable tasks, seven executed/206 up-to-date.
  Kotlin unit test tasks UP-TO-DATE, not fresh test executions. Detekt and ktlintCheck
  executed. JAVA_HOME points to the local JDK, GRADLE_USER_HOME to the ignored cache;
  SDK-free run removes Android env; full run uses existing ignored SDK.
- `git diff --check`: exit 0 before staging. Initial branch creation without
  escalation failed with index.lock permission denied; escalated retry succeeded.
  `git diff --no-index -- /dev/null tools/calibration/acquisition.py` returned 1 as
  expected for a new-file diff, used for source review rather than a passing check.

NO-SOURCE: model/physics/planner Java compilation/resources and test Java sources;
Android module Java compilation, app native debug metadata, adapter native library
merge/strip, and capture unit-test Kotlin/Java/resources/test. Configuration-error
checks and app preDebugAndroidTestBuild SKIPPED. Existing Gradle 10 deprecation
notice persists. No instrumentation run for Python/docs-only preparation; CB-36's
synthetic-demo startup limitation remains unresolved.

Existing `gradlew.bat` normalization remains excluded; raw hash
`03754c4c3d9206f5c9ef69e41990dfe26a05251e` preserved. No media or machine source
paths added. The new manifest does not verify actual source bytes or completeness
of metadata claims. Full ticket acceptance/review/merge is not asserted.

## Concrete acquisition gate

Confirm authorized local/private isolated-shot setup and actual Nothing Phone (2a)
game build/equipment/display/recording configuration. Then freeze the completed
manifest and graphic measurement rule, collect the sweep and Stage A's 16 attempts
with separate sealed custody. Unknown metadata and unavailable private mode are
blockers, never defaults. Later contact/pocket evidence is conditional on Stage A
passing. Keep this ticket open and do not start dependent live work.
