pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// JDK toolchain resolver (foojay): Gradle uses it to fetch JVM 21 for the daemon and the
// toolchains, which gradle/gradle-daemon-jvm.properties requires — JDK 21 gives parity with CI
// and detekt 1.23.8 does not run on JVM 25. The version catalog is not available in settings,
// so this version is written out here.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cue"

include(":app")

// Macrobenchmark and baseline profile generator. Purely a test module: it links into no :app
// variant, it only measures :app and produces the profile for it.
include(":baselineprofile")
