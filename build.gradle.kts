plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val ktlint by configurations.creating
val detektCli by configurations.creating

subprojects {
    apply(plugin = "base")
}

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:${libs.versions.ktlint.get()}:all@jar")
    detektCli("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}:all@jar")
}

val kotlinFiles =
    fileTree(rootDir) {
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/.gradle/**", "**/.tools/**", "**/.validation/**", "**/.git/**")
    }

fun JavaExec.configureKtlint(format: Boolean) {
    group = "verification"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    inputs.files(kotlinFiles)
    inputs.file(".editorconfig")
    if (format) args("--format")
    args("**/*.kt", "**/*.kts", "!**/build/**", "!**/.gradle/**", "!**/.tools/**", "!**/.validation/**")
}

val ktlintCheck by tasks.registering(JavaExec::class) {
    description = "Check all Kotlin sources and Gradle scripts."
    configureKtlint(false)
}

tasks.register<JavaExec>("ktlintFormat") {
    description = "Format all Kotlin sources and Gradle scripts."
    configureKtlint(true)
}

// CLI isolation avoids coupling the stable Detekt compiler to the project's newer KGP/AGP.
// This is syntax analysis, without type resolution; Android Lint complements it.
val detekt by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run stable Detekt syntax analysis in an isolated classpath."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    inputs.files(kotlinFiles)
    inputs.file("config/detekt/detekt.yml")
    args(
        "--input",
        rootDir.absolutePath,
        "--excludes",
        "**/build/**,**/.gradle/**,**/.tools/**,**/.validation/**,**/.git/**",
        "--config",
        file("config/detekt/detekt.yml").absolutePath,
        "--build-upon-default-config",
        "--report",
        "html:${layout.buildDirectory.get()}/reports/detekt/detekt.html",
    )
}

tasks.check {
    dependsOn(ktlintCheck, detekt)
    dependsOn(subprojects.map { "${it.path}:check" })
}
