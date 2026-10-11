// Instrumentation module with no application of its own: it generates the baseline profile for
// :app and measures its cold start. Nothing from here is linked into the release APK.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.cue.launcher.baselineprofile"
    compileSdk = 37
    // Parity with :app — a different minor would mean a different android.jar against the same device.
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 37
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The library treats an emulator as an untrusted device and refuses to run otherwise.
        // Absolute numbers from it mean nothing — only before/after comparisons do. The one
        // absolute measurement is taken on a real phone.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // The journeys seed the measured app from fixtures/baselineprofile/home.json, served straight
    // from there so it stays the file BaselineProfileFixtureTest in :app checks.
    sourceSets.getByName("main").assets.directories.add(rootProject.file("fixtures/baselineprofile").path)

    // The module under measurement. From it the plugin derives nonMinifiedRelease (profile
    // collection) and benchmarkRelease (measurement).
    targetProjectPath = ":app"
}

baselineProfile {
    // Profile and benchmark run on a connected device rather than a Gradle Managed Device: the
    // AVD is started by hand and GMD would download its own images all over again.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
