# CB-25 validation

2026-11-15, Windows, local JDK 18 targeting JVM 17; hosted CI uses JDK 21.
Read CB-25, parent CB-4 and completed dependencies CB-12/CB-18, shared model,
physics APIs, ADRs 0002-0009 and versioned regression/serialization fixtures.

Executed commands (PowerShell, repository wrapper):

* `gradlew.bat -PjvmOnly :core:planner:test --console=plain`: intentional red,
  failed at compileTestKotlin with unresolved new contracts before implementation.
* `gradlew.bat -PjvmOnly ktlintFormat :core:planner:test check --console=plain`:
  tests passed; Detekt failed on six overlong lines and a six-parameter helper.
  Split lines and reduced helper arguments; no suppressions or relaxed rules.
* `gradlew.bat -PjvmOnly ktlintFormat projects test check --console=plain`:
  passed in 23s, 19 actionable tasks (8 executed, 11 up-to-date).
* Final `gradlew.bat ktlintFormat projects assembleDebug test check lint --console=plain`:
  passed in 37s, 209 actionable tasks (25 executed, 184 up-to-date).
  Android SDK 36 available; app compiled, assembled and linted with the exported
  planner API. ktlintCheck and Detekt passed.
* Final `gradlew.bat -PjvmOnly projects test check --console=plain`, both SDK
  environment variables unset: passed in 18s, 18 actionable tasks (5 executed,
  13 up-to-date). Only model/physics/planner modules configured; planner tests
  executed and existing model/physics tests were up-to-date.

Final XML reports: 130 JVM tests (17 model, 80 physics, 9 planner, 23 vision,
1 app), zero failures/errors/skipped test cases. Existing suites were mostly
up-to-date; this is not a claim they all re-executed. Planner tests exercise
immutable targets/rejections, invalid references/scalars, extensible family
fixture text round-trip, target and incidental capture utility, wall penalties,
bank count bounds, deterministic score/power/ID order, truncation, foul, mismatch,
explicit failure representation and score overflow. Simulations are synthetic.

NO-SOURCE: overlay production Kotlin/Java and unit tests; capture JVM unit tests;
Java/test-Java where no Java sources exist; main JVM resources; absent Android
native merge/strip inputs and empty Java resource inputs. Planner production and
tests now execute and are no longer NO-SOURCE. Kotlin configuration-error tasks
were SKIPPED by Gradle; these are not skipped test cases. Gradle reports existing
deprecated features incompatible with Gradle 10; no upgrade made in this ticket.

No Android behavior changed; instrumentation/device tests were not run for this
JVM contract change. No real-game accuracy, calibrated confidence, throughput,
latency or resource-leak measurements claimed. The fixture is a test-only schema,
not a production persistence codec. Candidate generation/route validation,
calibration and uncertainty optimization remain later tickets. Full staged diff
and file list reviewed before commit; the pre-existing wrapper normalization is
excluded and its raw blob matches HEAD.
