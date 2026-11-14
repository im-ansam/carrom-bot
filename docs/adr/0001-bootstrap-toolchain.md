# ADR 0001: Minimal modular Android foundation

Status: Accepted for CB-10, 2026-11-14.

Use Gradle Kotlin DSL and a version catalog. Model, physics and planner are JVM libraries;
vision, capture and overlay are Android libraries. The app composes them. No implementation
interfaces or native libraries are added before a feature ticket needs them.

Toolchain: AGP 9.3.1, Kotlin 2.4.21, Gradle 9.5.0, Java/Kotlin target 17, CI JDK 21,
Android compile 37 / target 36, min 26, build tools 36.0.0. AGP 9 supplies built-in Kotlin;
do not apply org.jetbrains.kotlin.android. The root KGP dependency supplies the aligned
Kotlin plugin version. AGP 9.4 is newer, but the Kotlin compatibility table currently
lists full support only through AGP 9.3.1, so use that stable compatible combination.
JDK 21 avoids the JDK-17-specific Lint issue documented in AGP 9.3 release notes.

Run stable ktlint 1.8.0 and Detekt 1.23.8 as isolated CLI processes through Gradle.
Fat JARs prevent transitive multiplatform variant ambiguity and keep Detekt's bundled
Kotlin compiler separate from project compilation. Detekt performs syntax analysis only;
its older parser may require an upgrade when newer Kotlin syntax is introduced.
No alpha Detekt Gradle plugin is required. Android Lint checks platform behavior.
Use default Detekt rules plus modest complexity limits; do not create a blanket baseline.

A -PjvmOnly setting excludes Android projects so the domain build needs no SDK.
AGP and KGP are declared together at the root for a shared plugin classloader; AGP is
resolved but not applied to any project in JVM-only mode.
Normal settings include all seven modules. Targets remain consistent in both modes.

Sources checked on 2026-11-14:
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://github.com/ktlint/ktlint/releases
- https://detekt.dev/docs/introduction/compatibility/
- https://detekt.dev/docs/1.23.8/gettingstarted/cli/
