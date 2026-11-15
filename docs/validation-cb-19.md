# CB-19 validation

Base: origin/main 503af7052b34ddd0f5ab1dbba05be8232b2ca4af (CB-18).
Post-merge CI 43689436386 verified completed successfully. Read root AGENTS.md,
authoritative CB-19, parent CB-3, Done dependency CB-18, ADRs 0004-0008 and
CB-18 validation before implementing. ADR 0009 describes the test-only design.

## Commands actually executed

Windows Gradle wrapper 9.9.1, direct JDK 18.0.1.1, ignored Gradle cache and SDK 36
for full validation; hosted CI uses JDK 21. No local SDK paths/logs are committed.

- Initial `gradlew.bat -PjvmOnly :core:physics:test --tests '*ShotFixtureTest'
  --console=plain`: red, 12 tests / 6 failures for missing event snapshots.
  Adding independently calculated v1 traces made the same command pass.
- `gradlew.bat -PjvmOnly ktlintFormat :core:physics:test check
  :core:physics:benchmarkShots --console=plain`: failed, 74 tests / 1 failure
  because Properties.apply resolved javaClass to Properties. Corrected the
  resource lookup to the test class.
- `gradlew.bat -PjvmOnly :core:physics:test check :core:physics:benchmarkShots
  --console=plain`: physics tests and benchmark passed, Detekt failed on two long
  lines. A subsequent formatted `projects test check :core:physics:benchmarkShots`
  run passed tests and benchmark but still failed one line; formatter flattened the
  expression. Expanded the call arguments. One intervening full validation passed
  tests/Detekt but failed ktlint's expression-body rule; final syntax satisfies both.
- Deliberately changed direct contact snapshot 0.2 -> 0.21 seconds, then ran
  `gradlew.bat -PjvmOnly :core:physics:test --tests '*ShotFixtureTest'
  --console=plain`: red, 18 tests / exactly 1 time assertion failure. Restored 0.2
  before final validation. This verifies the tolerance gate detects a regression.
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: passed after final changes.
- Final `gradlew.bat projects assembleDebug test check lint --console=plain`:
  PASSED, 195 tasks: 9 executed, 186 up-to-date. Includes APK assembly, JVM tests,
  Android lint, ktlint and Detekt. Physics tests executed after restoring snapshot.
- Final `gradlew.bat -PjvmOnly projects test check :core:physics:benchmarkShots
  -PbenchmarkWarmup=5 -PbenchmarkRounds=10 --console=plain` with ANDROID_HOME and
  ANDROID_SDK_ROOT unset: PASSED, 15 tasks: 4 executed, 11 up-to-date. Tests were
  up-to-date from full validation; quality checks and 60-shot smoke ran.

Final XML: 80 physics, 17 model, 1 app JVM tests; zero failures/errors/skips.
Physics adds 18 parameterized cases and 1 seeded property test; the latter executes
64 configurations, not 64 separately counted JUnit tests. Each shot repeats three
times and also runs a fine clock. Exact event/capture/sample repeatability,
tolerant event/final-state agreement, capture removal/outcome correspondence,
containment/separation, dissipative energy and storage-order invariance pass.

Default benchmark executed twice during development: 100 warmup batches, 3000
measured shots; per-family elapsed time and counts are in benchmarks/README.md.
The second measured run supplies the baseline. Those commands failed later quality
checks as recorded above, but the benchmark task itself completed successfully.
Final CI smoke completed independently with 60 measured shots and expected counts.
No game accuracy or timing acceptance threshold is claimed.

NO-SOURCE: empty planner Kotlin/Java/resources/tests, JVM Java/main resources,
empty Android adapter sources/unit tests, app unit-test Java and native/debug-symbol
tasks. These are not behavioral test evidence. Configuration-error checks and
preDebugAndroidTestBuild were SKIPPED as configured. Existing Gradle deprecation
warnings remain. No instrumentation/device tests were run for test-only JVM tooling.

Reviewed complete changed sources, documentation, CI/build changes, staged paths
and whitespace before commit. Production simulation and module graph unchanged.
Pre-existing gradlew.bat normalization artifact excluded; raw no-filter hash still
matches HEAD blob 03754c4c3d9206f5c9ef69e41990dfe26a05251e.
