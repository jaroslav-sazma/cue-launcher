import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.licensee)
    alias(libs.plugins.android.screenshot)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.cue.launcher"
    compileSdk = 37
    // Compose 1.13, and therefore material3 1.5, requires minor API 37.1.
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "app.cue.launcher"
        minSdk = 37
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The HA blueprint is packaged straight from the repository rather than copied into assets:
    // a copy would sooner or later drift away from HA_EVENT_ACTION and the manifest, and that is
    // the worst kind of bug to debug. HaEventContractTest guards the match.
    sourceSets.getByName("main").assets.directories.add(rootProject.file("ha").path)

    // The screenshot plugin needs this flag twice: in gradle.properties, which it reads when it is
    // applied, and here at module level, from which AGP creates the screenshotTest variant.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    // The signing key never enters the repository. keystore.properties in the repository root
    // (gitignored) points at it; without that file the release build stays unsigned, which is what
    // CI and every other clone get.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release app instead of over it, so a phone that runs the release
            // launcher every day keeps it and its data. The namespace, and with it every class name,
            // stays app.cue.launcher. HA events reach the release app only: both blueprints address
            // the package app.cue.launcher, and that is a public contract.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // AGP would otherwise embed the dependency list in the APK as a blob encrypted with Google's key:
    // nobody else can read it, and it stands between a published APK and a rebuild from the same tag.
    // The bundle keeps it, which is where Play reads it from.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    // The app switches its language at runtime (Settings → Language, locales_config.xml). With
    // language splits Play would install only the device's language, and switching to Czech on an
    // English phone would show English strings.
    bundle {
        language {
            enableSplit = false
        }
    }

    // No baseline file on purpose: a finding is fixed or suppressed at the line with a reason, never
    // hidden in bulk. The security checks below are promoted from warnings so they cannot slip in.
    lint {
        abortOnError = true
        error += setOf(
            "UnsafeImplicitIntentLaunch",
            "UnsafeIntentLaunch",
            "MutableImplicitPendingIntent",
            "UnspecifiedRegisterReceiverFlag",
            "TrustAllX509TrustManager",
            "SetJavaScriptEnabled",
            "DataExtractionRules",
            "AppBundleLocaleChanges",
        )
    }
}

// Opt-in compiler metrics for recomposition audits: `./gradlew assembleDebug -PcomposeReports`
// writes stability/skippability reports into app/build/compose_reports/.
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

// The profile is not generated on every build: it is produced on demand with
// `./gradlew :app:generateBaselineProfile` and committed, so it reaches the APK even for someone
// who never runs the benchmark.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

// Room exports the schema into app/schemas/, which is committed — the basis for migrations from
// the moment the first version reaches a user's device.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
}

// detekt 1.23.8 derives jvmTarget from the Gradle daemon's JVM, so it is pinned to the same level
// as android.compileOptions to keep the result independent of the JVM the build runs on.
// detekt 1.23.8 also runs in-process and its kotlin-compiler-embeddable 2.0.21 cannot handle
// JVM 25+ (IllegalArgumentException: 25.0.3), so the daemon must run on JDK 21, like CI.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_21.toString()
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = JavaVersion.VERSION_21.toString()
}

// The contract tests read these straight from the repository, outside the test classpath; without
// declaring them, a change to only the README or the issue form would leave the tests UP-TO-DATE.
val testedRepositoryFiles = listOf(
    ".github/ISSUE_TEMPLATE", "README.md", "LICENSE", "NOTICE", "LICENSES", "ha", "fixtures/scenarios",
    "fixtures/baselineprofile",
).map(rootProject::file) + listOf(
    "build.gradle.kts", "schemas", "src/main/AndroidManifest.xml", "src/main/kotlin", "src/main/assets",
    "src/main/res/values/strings.xml", "src/main/res/values-cs/strings.xml",
).map(::file)

// The *UnitTest filter is required: screenshot tests are Test tasks too, and useJUnitPlatform()
// would overwrite the configuration the screenshot plugin sets on them (its own engine and system
// properties), after which it finds no @Preview at all.
tasks.withType<Test>().matching { it.name.endsWith("UnitTest") }.configureEach {
    useJUnitPlatform()
    inputs.files(testedRepositoryFiles)
        .withPropertyName("testedRepositoryFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// A library's own <provider> or androidx.startup initializer runs in every process before any app
// code. The source manifest test cannot see what a dependency bump adds, so the merged manifest is
// checked here against the entries known to be the app's own or inert; anything new fails `check`.
abstract class VerifyMergedManifest : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val ownPrefix: Property<String>

    @get:Input
    abstract val allowedProviders: SetProperty<String>

    @get:Input
    abstract val allowedInitializers: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val android = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest.get().asFile)
        fun named(tag: String): List<String> {
            val nodes = document.getElementsByTagName(tag)
            return (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element) }
                .filter { tag != "meta-data" || it.getAttributeNS(android, "value") == "androidx.startup" }
                .map { it.getAttributeNS(android, "name") }
        }
        val own = ownPrefix.get()
        val providers = named("provider").filterNot { it.startsWith(own) || it in allowedProviders.get() }
        val initializers = named("meta-data").filterNot { it.startsWith(own) || it in allowedInitializers.get() }
        val unexpected = providers.map { "provider $it" } + initializers.map { "startup initializer $it" }
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "The merged manifest starts code outside the allowlist in app/build.gradle.kts:\n" +
                    unexpected.joinToString("\n") { "  $it" } +
                    "\nRemove it with tools:node=\"remove\" in AndroidManifest.xml, or allowlist it once it is known " +
                    "to stay inert.",
            )
        }
        report.get().asFile.writeText((named("provider") + named("meta-data")).joinToString("\n"))
    }
}

// Allows exactly the licenses whose texts are in LICENSES/. A dependency that brings another one
// fails `check`; allowing it means adding its text to LICENSES/, NOTICE and LicenseNotice too.
// SLF4J names MIT only by a URL Licensee does not map to the SPDX id, hence allowUrl; MIT stays
// allowed for the next dependency that names it properly, so its warning while unused is silenced.
licensee {
    allow("Apache-2.0")
    allow("MIT")
    allowUrl("https://opensource.org/license/mit") {
        because("MIT")
    }
    unusedAction(app.cash.licensee.UnusedAction.IGNORE)
}

// The app has to hand its users the license texts (Apache-2.0 §4, MPL-2.0 §3.2), so LICENSE, NOTICE
// and LICENSES/ go into assets/licenses/. They stay in the repository root, where GitHub and readers
// look for them; adding the root itself as an asset directory would package the whole repository.
abstract class LicenseAssets : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val texts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = output.get().asFile
        target.deleteRecursively()
        val licenses = target.resolve("licenses").apply { mkdirs() }
        texts.files.forEach { it.copyTo(licenses.resolve(it.name)) }
    }
}

val licenseAssets = tasks.register<LicenseAssets>("licenseAssets") {
    texts.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.fileTree("LICENSES"))
    output.set(layout.buildDirectory.dir("generated/licenseAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(licenseAssets, LicenseAssets::output)
    }
    // The baseline profile plugin creates nonMinifiedRelease and benchmarkRelease with initWith(release)
    // after buildTypes.configureEach has run, so a signingConfig set there is lost and they carry the
    // real key: installable over the release app on the daily phone, which the connected task
    // uninstalls after the run. The variant API runs after the plugin.
    onVariants { variant ->
        if (variant.buildType in setOf("nonMinifiedRelease", "benchmarkRelease")) {
            variant.signingConfig.from(android.signingConfigs.getByName("debug"))
        }
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercaseChar)
        tasks.register<VerifyMergedManifest>("verify${name}MergedManifest") {
            group = "verification"
            description = "Fails on a provider or startup initializer outside the allowlist."
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            ownPrefix.set("app.cue.launcher.")
            allowedProviders.set(setOf("androidx.startup.InitializationProvider"))
            allowedInitializers.set(
                setOf(
                    "androidx.lifecycle.ProcessLifecycleInitializer",
                    "androidx.profileinstaller.ProfileInstallerInitializer",
                ),
            )
            report.set(layout.buildDirectory.file("reports/manifest/${variant.name}-startup.txt"))
        }
    }
}

tasks.named("check") { dependsOn("verifyDebugMergedManifest") }

dependencies {
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    // On devices without Play Store the profile is only converted to its AOT form by
    // ProfileInstaller on first start.
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.compose.ui.tooling)

    baselineProfile(project(":baselineprofile"))

    screenshotTestImplementation(platform(libs.compose.bom.alpha))
    screenshotTestImplementation(libs.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
}
