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

## 2026-11-20 emulator diagnostics and PTS audit

User explicitly authorized opening Pixel 10 and operating its practice controls.
This is a separate diagnostic environment; phone evidence is still absent. Thirteen
task-created recordings are hashed/inventoried in
`calibration/emulator-diagnostics-v1/capture-summary.json`. All are unsealed
diagnostics, not preassigned acquisition; none is sealed validation. Private videos,
screenshots, account imagery, logs, native captures and local controller scripts
remain ignored and are excluded from Git.
No product execution capability, dynamics change or profile assertion was added.

- `py -m unittest discover -s tools/calibration -p test_capture_timing.py -v`:
  initial red exit 1, missing `capture_timing`; first implemented six tests passed.
- After adding original-output protection, `py -m unittest discover -s
  tools/calibration -v`: exit 0, **37 tests in 15.222s**. Seven new regressions cover
  exact rational 50ms boundary, interval-crossing gaps, idle versus moving gaps,
  missing/noninteger/repeated/nonmonotonic PTS, invalid clocks/intervals, unknown
  game-update cadence and rejecting an output equal to the read-only original.
- Source-review follow-up: `py -m unittest discover -s tools/calibration -p
  test_capture_timing.py -k hard_link -v`: red exit 1, `Decoder must not run`,
  demonstrating that a hard-link output alias reached decoding. Added a same-file
  check before any media access; the synthetic original was protected by a mock
  during red, not overwritten. The final full-suite result is recorded below.
- Final `py -m unittest discover -s tools/calibration -v`: exit 0, **38 tests in
  12.741s**, including the hard-link regression; no private media/services required.
- `py tools/calibration/capture_timing.py <local original> --label
  emulator-feasibility-07 --interval motion-1 3.9 7.2 --interval motion-2 11.0 14.2
  --output .validation/cb39-emulator/timing-07.json`: exit 0. Actual PTS/time_base
  yields maximum interval gaps 0.033788889/0.049866667s; whole clip 0.081233333s.
  The command was also used in a local batch for every recorded MP4, with sanitized
  summaries and two complete numeric PTS reports versioned. Decoder PyAV 19.0.2.
- `py tools/calibration/capture_timing.py <local original> --label
  emulator-practice-resume-a1 --interval reviewed-motion 3.3 4.1 --output
  docs/calibration/emulator-diagnostics-v1/timing-resume-a1.json`: exit 0. Maximum
  moving-interval gap 0.217033333s, eight gaps over 0.05s: acquisition gate fails.
  Whole clip maximum gap 0.317344444s. This successful diagnostic does not pass
  end-of-motion or parameter/profile validation. A smaller-display test timed out
  before a usable shot; subsequent guard cancelled before firing. All retained.
- Re-ran both report commands with ignored `*-reproduced.json` output paths;
  explicit byte-equality checks passed. Report SHA-256: `timing-07.json`
  `665535b7a9396ba91a776d602882a1535c2d11b956bd2857f277920bb0a5151f`;
  `timing-resume-a1.json`
  `1d550f55e88ed455064a20f27e0d2df26cd2e967f87c3ca8af667843e0a01007`.
  Standard-library reassessment of both stored PTS sequences matched every timing
  field. Thirteen distinct original hashes and zero sealed validations asserted.

Failures/limits retained: Python without the private media environment initially
failed `import av`; selecting the existing pinned environment resolved it. The old
phone-specific marker reader returned no markers in the broad emulator ROI; its
dataset-specific offsets/thresholds were not promoted to a live detector. Local
input guards refused ambiguous marker/turn state; no such attempt is scored as a
shot. Desktop emulator snapshots proved stale; subsequent checks used fresh native
Android images. No intermediate engine positions or missing clock samples invented.

After interruption, first ADB access failed because emulator-5554 was absent;
boot-time captures also failed before storage was ready. Android 17 overrode a
requested 1536MB guest RAM to 4096MB. User closed unused apps and cold boot eventually
completed. A usage-limit automatic approval-review failure previously prevented a
command from executing; after user reported reset, the retry ran. These do not
establish the cause of capture loss. A reversible 720x1616/density280 display test
was restored with `wm size reset`/`wm density reset`; subsequent reads confirmed
physical 1080x2424/density420. No emulator wipe, game modification or data deletion.

No new Gradle configuration or Android production code was changed. Local Gradle
was not run concurrently with the memory-constrained emulator; November 17's
build evidence above remains historical, not a new execution. Clean hosted JVM
and Android build/lint results for the new head will be recorded after they finish.
No instrumentation run; no detector or app integration behavior changed. Full
CB-39 acceptance, production held-out replay, usable profile and reviewed merge
remain outstanding. Emulator results cannot unblock phone vision/live integration.

Preexisting local files preserved/excluded: `gradle.properties` raw Git hash
`3a2aa96abdecd64884e725797185baa146b48e57`, `gradlew.bat`
`03754c4c3d9206f5c9ef69e41990dfe26a05251e`, and untracked
`gradle/gradle-daemon-jvm.properties` `fa4ed510e60d04aa68cf65ecb699f5c9d15b4b15`.

### Codex review follow-up

Review of `ec6c27a` found that acquisition audit output could alias and overwrite
its populated input plan. `py -m unittest discover -s tools/calibration -p
test_acquisition.py -k audit_cli -v` reproduced it: exit 1, two failing alias subcases
(same path/hard link); the separate-output test passed. Only disposable synthetic
manifests were affected. Added resolved-path and same-file checks before auditing
or writing. New regressions preserve source bytes for direct, hard-link and actual
symbolic-link outputs and verify the populated separate-report success case.

Final `py -m unittest discover -s tools/calibration -v`: exit 0, **41 tests in
12.811s**, no skips; all three alias forms tested on Windows. No numeric profile,
capture reports or phone manifest changed. Skill review fetch initially failed on
Windows cp1252 Unicode decoding; retry with `PYTHONUTF8=1` succeeded. Authentication
was verified; no skill or machine configuration file was edited.

Hosted evidence for reviewed precursor `ec6c27a`: JVM/quality passed (1m13s), Android
build/lint passed (2m23s). Logs show actual `./gradlew -PjvmOnly projects test check`
BUILD SUCCESSFUL in 48s, benchmark task in 1s, and `./gradlew projects assembleDebug
test check lint` in 1m48s (all `--console=plain`). Hosted Python: nine vision-evaluator
tests and 38 calibration tests passed. Domain and app/vision/overlay unit-test tasks
executed without UP-TO-DATE/FROM-CACHE suffix; capture unit tests were NO-SOURCE,
capture aggregate `test` UP-TO-DATE. Java/resource/native NO-SOURCE categories remain
as listed above. This is actual hosted compilation/build/check/lint evidence, not
a claim that instrumentation ran. Fresh review and CI for the fix head are still
required; exact-head results are recorded in Jira after completion. Draft remains
unmerged because acquisition/profile validation is incomplete.

### Physical-phone preflight and diagnostic layout regression (2026-11-20)

See `calibration/phone-preflight-v1/README.md` for sanitized observations. No
private phone media, identifiers, SDK paths or signing material are versioned.
Earlier statements about no app changes/instrumentation describe the emulator
precursor; this follow-up changes only synthetic-demo sizing.

- Isolated worktree at ad24ec8, JDK 18: `gradlew.bat :app:assembleDebug
  --max-workers=1 -Dorg.gradle.jvmargs="-Xmx768m -Dfile.encoding=UTF-8"
  --console=plain`: exit 0, BUILD SUCCESSFUL in 2m40s, 99 tasks executed.
  APK SHA-256 `94008b55967160a04267b94adeaa4b4b00eabdac9111255e9eac427627df53e3`;
  ADB install succeeded, cold launch 1344ms.
- Same isolated environment, explicitly targeted physical phone:
  `gradlew.bat :core:vision:connectedDebugAndroidTest --max-workers=1
  -Dorg.gradle.jvmargs="-Xmx768m -Dfile.encoding=UTF-8" --console=plain`:
  exit 0, BUILD SUCCESSFUL in 34s, 53 tasks (33 executed, 20 up-to-date).
  Connected-result XML contains 13 cases, zero failures/errors/skips. App tests
  that automate consent were not run on the personal phone.
- Red, main worktree/JDK 25.0.4: `gradlew.bat :app:testDebugUnitTest --tests
  io.github.carrombot.DemoLayoutTest --max-workers=1
  -Dorg.gradle.jvmargs="-Xmx768m -Dfile.encoding=UTF-8" --console=plain`:
  exit 1, BUILD FAILED in 1m17s (68 tasks, 9 executed); new `fitSourceSize`
  references unresolved before implementation.
- Green, same main environment: `gradlew.bat projects assembleDebug test check
  lint --max-workers=1 -Dorg.gradle.jvmargs="-Xmx768m -Dfile.encoding=UTF-8"
  --console=plain`: exit 0, BUILD SUCCESSFUL in 1m48s, 213 tasks (44 executed,
  169 up-to-date). App/domain/vision/overlay unit-test tasks executed. Result XML:
  app 21, model 17, physics 90, planner 66, vision 25, overlay 4: **223 tests**,
  zero failures/errors. Capture unit tests NO-SOURCE; Java/resource/native
  NO-SOURCE categories remain as above. Build/check/lint/ktlint/detekt passed.
  Existing safe-call warning, native-library strip warning, Java Unsafe and
  Gradle 10 deprecations retained; no failure ignored.
- Layout-fixed APK from e52f98b SHA-256
  `b3ce676696a141dae40c29f9b60be663782e008f1b691483047f200042034ec4`:
  ADB install succeeded, cold launch 980ms. Native UI confirmed 412 × 412 source,
  fresh consent, synthetic trajectories, 40% to 50% candidate switching and pause
  clearing. Ending sharing verified `CarromBotListedInProjectionState=False`.

Diagnostic tooling limitations: a live-counter UIAutomator dump could not obtain
idle state (subsequent pull failed); native screenshots supplied fresh evidence.
Python initially required escalation; an initial XML parser expected a testsuite
root, then corrected iteration verified all 13 connected cases. No failed attempt
was counted as a passing test. No game shots/calibration parameters produced.
Preexisting three Gradle files' raw hashes rechecked and unchanged from above.
Fresh hosted checks/review of the final follow-up head are required; draft remains
unmerged and CB-39 In Progress pending actual controlled acquisition/validation.

### Physical-phone input survey and one timing-only shot

User operated practice manually after the synthetic preflight. Two private native
recordings were stopped with SIGINT and preserved with distinct hashes. First
survey pull raced MP4 finalization and failed PyAV `InvalidDataError`; waiting for
the existing recorder process to exit, then pulling a separate finalized copy,
resolved decoding. No original was edited or partial transfer accepted. Second
recording was pulled only after recorder exit. Source hash verification and
sanitized timing/observation evidence are described in
`calibration/phone-diagnostics-v1/README.md`; no new profile or matrix result.

Executed `py tools/calibration/capture_timing.py <private-original> --label
phone-input-survey-01 --output <ignored-report>` and corresponding
`phone-timing-diagnostic-01` command in the existing PyAV environment: both finalized
inputs decoded, exit 0. Private overview/detail readers also exited 0; an early
attempt to view the survey overview before its process completed failed because
the image did not yet exist, then succeeded after completion. Standard-library
`assess` on their stored PTS produced sweep 57–69.5s max gap 0.034533333s and
shot 66–72.8s max gap 0.019844444s. Original full-clip gaps retained. Narrow sprite
feature reader is unvalidated, not a live detector or body-centre calibration.

One low-pull diagnostic shot had no observed rail/disc contact, but less than one
second of corroborated terminal rest before reset. Collection pauses at this
concrete measurement gate. Current protocol/thresholds and split remain unchanged;
formal attempts/heldouts remain zero. This follow-up changes documentation only;
no new Gradle execution or additional Android test claim. Previous code-head
validation remains historical; fresh hosted checks and review apply to this head.

Finalized numeric reports generated with the same CLI into
`calibration/phone-diagnostics-v1/input-survey-timing.json` (`--interval
input_sweep 57 69.5`) and `shot-timing.json` (`--interval aim_hold_and_motion 66
72.8 --interval motion_and_turn_reset 71 73.4`): both exit 0. Standard-library
reassessment of every stored timing field matched; local original SHA-256 matched
each report and Android `sha256sum`. `pidof screenrecord` returned no PID (exit 1,
expected absence) after cleanup. All three preexisting Gradle raw hashes unchanged.

### Terminal observation contract (2026-11-21)

Resumed from saved checkpoint without requesting more phone shots or starting
capture. Re-read CB-39, parent CB-6 and prerequisite CB-38; inspected saved terminal
frames and existing protocol/tooling. Added ADR0028, a separate terminal observation
contract and standard-library `terminal_observation.censor_prefix`. Two adjacent
source-frame boundary annotations yield a conservative union bracket and prefix
strictly before it; the entire later tail is excluded. Invalid clocks/hashes,
nonadjacent points and duplicate/missing passes fail explicitly. Disagreement and
>50ms prefix/transition gaps remain blockers. Outputs cannot assert rest or a
usable profile. Annotation independence and true body centres remain unverified;
different IDs alone are not independent evidence. No existing plan/gate changed.

Red: `py -m unittest discover -s tools/calibration -p
test_terminal_observation.py -v`, exit 1, ModuleNotFoundError before implementation.
Green: same command, exit 0, 8 tests in 0.004s. Full `py -m unittest discover -s
tools/calibration -v`, exit 0, 49 tests in 4.964s, no skips. Fixtures are original
synthetic clocks/annotations, independent of recordings/services/devices. No new
local Gradle or instrumentation execution claimed for this Python-only change;
fresh hosted JVM/quality and Android build/lint plus exact-head Codex review remain
required. Physical stopping/independent validation/profile acceptance still absent;
PR remains draft, CB-39 incomplete, no CB-40/41 work.

After making both missing stopping fields explicit (`stop_time_s` and
`stop_position_bw` null), full calibration suite passed again: 49 tests in 4.577s,
exit 0, no skips. `py -m py_compile tools/calibration/terminal_observation.py
tools/calibration/test_terminal_observation.py` and `git diff --check` exited 0.
The three preexisting Gradle raw hashes were rechecked unchanged. No independent
pair of real body/phase annotations is asserted; the saved shot has not been
converted into accepted prefix measurements by the synthetic tests.
