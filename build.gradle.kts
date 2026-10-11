// AGP 9 compiles Kotlin itself and ships KGP 2.2.10. The Kotlin that Metro needs (see `kotlin` in
// libs.versions.toml) is pulled in by raising KGP/KSP through classpath dependencies.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

// Plugins are only declared here; they are applied in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    // :baselineprofile is a com.android.test module. The baseline profile plugin is applied in
    // both modules: in :app it builds the profile consumer, in the test module the producer.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.android.screenshot) apply false
}
