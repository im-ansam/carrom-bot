# CB-29 validation

Synthetic JVM planner validation, 2026-11-15. No real-game accuracy, confidence,
global optimality, latency or throughput claim follows from these tests.

Nine new JUnit cases cover low/middle/high power capture outcomes, lowest-power
tie breaking, golden direct-before-bank utility and stable-ID ties, explicit empty
and no-solution results, fresh optimizer velocity/evidence, immutable plan lists,
duplicate identities, illegal placement/budget settings, independently expected
body and terminal-control penalties, striker fouls, truncation, obstructed targets,
angle/placement replacement and explicit simulator-budget failure propagation.

Intermediate checks (all executed on Windows with gradlew.bat):

- `gradlew.bat -PjvmOnly :core:planner:test --console=plain` failed at
  compileTestKotlin before implementation: unresolved LaunchSample/ShotPlanner/
  PlanningResult, establishing the red baseline.
- `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test detekt --console=plain`
  first passed the initial three tests but failed Detekt with 11 style findings
  (long lines, literal search cap and return count). The expanded nine-test run
  passed tests but failed Detekt on three remaining test line lengths.
- `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`
  first passed compilation, tests, assembly and Android lint but failed Detekt on
  one helper line reformatted by ktlint (1m25s, 208 tasks: 24 executed,
  184 up-to-date). Named arguments resolved the formatter/analyzer disagreement.

Final checks passed with JDK 18 locally (CI separately uses JDK 21):

- `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  PASS, 1m28s; 209 tasks, 10 executed and 199 up-to-date. Android SDK 36
  configured. Includes Android compilation/assembly/lint and ktlint/Detekt.
- `gradlew.bat -PjvmOnly projects test check --console=plain`, with ANDROID_HOME
  and ANDROID_SDK_ROOT unset: PASS, 1m2s; 18 tasks, 3 executed and 15 up-to-date.
- XML results: 166 JVM tests (17 model, 80 physics, 45 planner, 23 vision and
  1 app), zero failures, errors or skipped tests. Up-to-date tests reuse the
  corresponding already-executed results; planner's nine new tests executed in
  the full run. `git diff --check` passed.

Gradle reported existing deprecated features incompatible with Gradle 10; no
Gradle upgrade is part of this change. Focused LongParameterList suppressions on
the two search entry points keep caller calibration, physics, policy and budgets
explicit rather than introducing an unrelated configuration abstraction.

NO-SOURCE tasks mean absent inputs, not passing tests: overlay production Kotlin
and Java, overlay/capture JVM test sources and test tasks, JVM Java/resource tasks,
Android Java/resources where absent and native-library metadata/strip tasks.
Kotlin configuration-error tasks and app preDebugAndroidTestBuild are SKIPPED.
No instrumentation/device run is needed for this platform-free planner change;
`connectedDebugAndroidTest` was not run. Module boundaries remain unchanged.

Search is bounded by explicit simulation count (at most 4096) and per-simulation
step/contact budgets. All evaluated evidence is retained; memory and cost scale
with those budgets and scene size. Sampling is caller policy and may miss a valid
shot between samples. Changed power does not preserve a generator's exact ordered
route proof; retained events permit downstream stricter validation. Control is a
caller-selected terminal-position preference, not a prediction of future turns.
