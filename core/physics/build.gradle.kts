plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}

// Benchmark tooling shares versioned test inputs, without entering the published JVM library.
kotlin.sourceSets.test {
    kotlin.srcDir(rootProject.file("benchmarks/src/test/kotlin"))
}

tasks.register<JavaExec>("benchmarkShots") {
    group = "verification"
    description = "Measure synthetic candidate simulation throughput; no accuracy or timing gate."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.carrombot.core.physics.ShotBenchmark")
    args(
        providers.gradleProperty("benchmarkWarmup").getOrElse("100"),
        providers.gradleProperty("benchmarkRounds").getOrElse("500"),
    )
}
