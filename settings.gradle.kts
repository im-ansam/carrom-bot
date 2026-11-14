pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CarromBot"
include(":core:model", ":core:physics", ":core:planner")

// Domain development and unit tests must also work on machines without an Android SDK.
if (!providers.gradleProperty("jvmOnly").isPresent) {
    include(":app", ":core:vision", ":core:capture", ":core:overlay")
}
