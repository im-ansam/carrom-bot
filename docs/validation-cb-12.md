# CB-12 validation

Base: origin/main at fbf8d03. Read CB-12, parent CB-1 and completed dependency CB-10.
Windows validation on 2026-11-14 uses the direct local JDK 18.0.1.1 fallback through
JAVA_HOME and the ignored .tools/gradle-home dependency cache; CI uses JDK 21.
No local Android SDK is installed. No build, CI, module dependency or lint-exception
configuration was changed.

## Commands and actual results

- `gradlew.bat -PjvmOnly :core:model:test --console=plain`: initial sandbox execution
  failed to connect to the local Gradle daemon. Retried outside the sandbox. The
  geometry tests first failed compilation on missing contracts, then passed after
  implementation. BoardState and prediction tests likewise failed on missing
  contracts before their implementations were added. These were expected red steps.
- `gradlew.bat -PjvmOnly projects test check --console=plain`: intermediate runs
  compiled and passed model tests but failed Detekt on seven long test lines, then
  one fixture line. Corrected all diagnostics; none suppressed.
- `gradlew.bat -PjvmOnly ktlintFormat --console=plain`: exit 0. Only new Kotlin
  sources/tests changed; existing build scripts and configuration remained unchanged.
- Final `gradlew.bat -PjvmOnly projects test check --console=plain`: exit 0,
  BUILD SUCCESSFUL, 10 actionable tasks (5 executed, 5 up-to-date). Model test XML:
  GeometryTest 4, BoardStateTest 4, PredictionTest 4; total 12, zero failures,
  errors or skipped tests. ktlintCheck and Detekt executed and passed. Model Kotlin
  main/test compilation succeeded, with explicit API mode on main sources.
- `gradlew.bat projects assembleDebug test check lint --console=plain`: exit 1;
  could not determine dependencies of :app:compileDebugJavaWithJavac because
  `SDK location not found`. No Android compilation, tests or lint ran locally.

NO-SOURCE in JVM-only mode: model Java compilation, main resources and test Java
compilation; physics/planner Kotlin and Java main/test compilation, resource tasks
and test tasks. Model test resources and JVM tests do execute. Kotlin configuration
error checks are SKIPPED because no configuration errors were reported. No physics
or planner behavioral tests, device tests, instrumentation, capture or CV validation
are claimed. Downstream modules retain their existing model dependencies; actual
adapter implementations remain future tickets.

## Review scope and limitations

Reviewed model contracts, tests, synthetic fixture and documentation for immutable
ownership, dependency direction, input failures, stable IDs and numerical boundaries.
There are no asynchronous tasks or native/platform resources in these contracts.
The versioned fixture demonstrates reconstruction/equality, not a production codec.
Transforms are explicitly limited to uniform axis-aligned views. Prediction data
validation does not establish simulation accuracy. Hosted PR CI must validate Android
integration on the configured JDK 21/SDK 36 environment before review and merge.
