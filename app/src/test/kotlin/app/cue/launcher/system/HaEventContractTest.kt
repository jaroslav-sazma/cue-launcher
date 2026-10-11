package app.cue.launcher.system

import app.cue.launcher.data.ha.haBlueprintFor
import app.cue.launcher.engine.HA_EVENT_ACTION
import app.cue.launcher.engine.HA_EXTRA_CODE
import app.cue.launcher.engine.HA_EXTRA_ICON
import app.cue.launcher.engine.HA_EXTRA_OP
import app.cue.launcher.engine.HA_EVENT_VERSION
import app.cue.launcher.engine.HA_EXTRA_SECRET
import app.cue.launcher.engine.HA_EXTRA_VERSION
import app.cue.launcher.engine.HA_OP_END
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class HaEventContractTest {

    private val manifest: String by lazy { repoFile("app/src/main/AndroidManifest.xml").readText() }

    private fun blueprint(path: String): String = repoFile(path).readText()

    @Test
    fun `the manifest declares a receiver for the event action`() {
        assertTrue(manifest.contains("""<action android:name="$HA_EVENT_ACTION" />""")) {
            "the manifest has no $HA_EVENT_ACTION action"
        }
        assertTrue(manifest.contains("""android:name=".system.HaEventReceiver"""")) {
            "the manifest has no .system.HaEventReceiver receiver"
        }
    }

    @Test
    fun `the event receiver is exported`() {
        val declaration = manifest.substringAfter("""android:name=".system.HaEventReceiver"""")
            .substringBefore("</receiver>")

        assertTrue(declaration.contains("""android:exported="true"""")) {
            "the event receiver must be exported=\"true\""
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint fires at the same action and package`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("intent_action: $HA_EVENT_ACTION")) {
            "the blueprint does not target $HA_EVENT_ACTION"
        }
        assertTrue(blueprint.contains("intent_package_name: app.cue.launcher")) {
            "the blueprint does not target the launcher package"
        }
        assertTrue(blueprint.contains("$HA_EXTRA_CODE:")) {
            "the blueprint does not send the \"$HA_EXTRA_CODE\" extra"
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint is taken into the APK directly from the repo`(path: String) {
        val blueprint = blueprint(path)
        val build = repoFile("app/build.gradle.kts").readText()

        assertTrue(build.contains("""assets.directories.add(rootProject.file("ha")""")) {
            "app/build.gradle.kts does not add the ha/ directory to the assets"
        }
        assertTrue(blueprint.contains("blueprint:")) { "$path is not a blueprint" }
    }

    // The receiver has to stay exported, so the secret is the only thing standing between the
    // user's own automations and any other app on the phone. If the blueprint stops sending it,
    // every event the user has set up is silently rejected.
    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint sends the pairing secret`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("$HA_EXTRA_SECRET:")) {
            "the blueprint does not send the \"$HA_EXTRA_SECRET\" extra"
        }
        assertTrue(blueprint.contains("pairing_secret: !input pairing_secret")) {
            "the blueprint has no pairing_secret variable"
        }
        assertTrue(blueprint.contains("    pairing_secret:")) {
            "the blueprint has no pairing_secret input"
        }
    }

    // The launcher writes the secret in as the input's default and the phone's name into the title when
    // it uploads the blueprint. A default of the blueprint's own would make that a duplicate key, and a
    // moved input or a reworded title line would stop the upload.
    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the launcher can fill the pairing secret and the phone into the blueprint`(path: String) {
        val blueprint = blueprint(path)
        val filled = haBlueprintFor(blueprint, "ABCD2345EFGH6789", "Pixel")

        assertTrue(filled != null) {
            "the blueprint needs a \"  name: \"…\"\" title line and a \"    pairing_secret:\" input line"
        }
        val input = filled.orEmpty().substringAfter("    pairing_secret:\n").substringBefore("\n    event_code:")
        assertEquals(1, Regex("(?m)^      default:").findAll(input).count()) {
            "the pairing_secret input must carry exactly the one default the launcher puts in"
        }
    }

    @Test
    fun `the event receiver checks the secret before it delivers`() {
        val receiver = repoFile("app/src/main/kotlin/app/cue/launcher/system/HaEventReceiver.kt").readText()

        assertTrue(receiver.contains("haEventSecretAccepted")) {
            "HaEventReceiver does not check the pairing secret"
        }
        assertTrue(receiver.indexOf("haEventSecretAccepted") < receiver.indexOf("onHaEvent")) {
            "HaEventReceiver delivers the event before it checks the secret"
        }
    }

    // Bundle.get() instantiates whatever class a foreign sender names in an extra; only the typed
    // getters leave a Parcelable or Serializable undecoded.
    @Test
    fun `the event receiver never decodes an extra through the generic getter`() {
        val receiver = repoFile("app/src/main/kotlin/app/cue/launcher/system/HaEventReceiver.kt").readText()

        assertFalse(Regex("""\bget\(""").containsMatchIn(receiver)) {
            "HaEventReceiver reads an extra through Bundle.get()"
        }
        assertTrue(receiver.contains("getString(HA_EXTRA_SECRET)")) {
            "HaEventReceiver does not read the secret through the typed getter"
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint can send the end code`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("$HA_EXTRA_OP:$HA_OP_END")) {
            "the blueprint cannot send \"$HA_EXTRA_OP:$HA_OP_END\""
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint sends the icon without the mdi prefix`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("$HA_EXTRA_ICON:")) {
            "the blueprint does not send the \"$HA_EXTRA_ICON\" extra"
        }
        assertTrue(blueprint.contains("""replace('mdi:', '')""")) {
            "the blueprint does not strip the \"mdi:\" prefix — only \"mdi\" reaches the launcher"
        }
    }

    // The launcher reads the stamp to tell an outdated automation apart from a current one. A blueprint
    // that stops sending it would make every event look legacy.
    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint stamps the contract version on every event`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("'$HA_EXTRA_VERSION:$HA_EVENT_VERSION'")) {
            "the blueprint does not send \"$HA_EXTRA_VERSION:$HA_EVENT_VERSION\""
        }
    }

    // Values go out URL-encoded and the companion app decodes them, which is the only way a value with
    // a colon, a comma or a space survives the trip. Both halves have to stay in place.
    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint urlencodes the payload values`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("urlencode")) {
            "the blueprint does not encode the payload values"
        }
        assertTrue(blueprint.contains(":urlencoded'")) {
            "the blueprint does not mark the payload values as urlencoded for the companion app"
        }
    }

    // Typing the notify service name by hand was the step users got wrong most often; the device
    // selector derives it, and the free-text field stays only as an override.
    @ParameterizedTest
    @ValueSource(strings = [BLUEPRINT, BLUEPRINT_CS])
    fun `the blueprint picks the phone instead of asking for a service name`(path: String) {
        val blueprint = blueprint(path)
        assertTrue(blueprint.contains("integration: mobile_app")) {
            "the blueprint has no device selector for the companion app"
        }
        assertTrue(blueprint.contains("notify.mobile_app_")) {
            "the blueprint does not derive the notify service"
        }
    }

    // The translation is a second copy of the whole file, so nothing but the texts a person reads may
    // differ: a fix to the template that lands in one copy only would break events in one language.
    @Test
    fun `the Czech blueprint differs from the English one only in its texts`() {
        assertEquals(skeleton(blueprint(BLUEPRINT)), skeleton(blueprint(BLUEPRINT_CS))) {
            "$BLUEPRINT_CS differs from $BLUEPRINT outside of name, description and label"
        }
    }

    @Test
    fun `each language uploads its own blueprint`() {
        mapOf("values" to BLUEPRINT, "values-cs" to BLUEPRINT_CS).forEach { (values, path) ->
            val strings = repoFile("app/src/main/res/$values/strings.xml").readText()
            assertTrue(strings.contains("""<string name="ha_blueprint_asset">${File(path).name}</string>""")) {
                "$values/strings.xml does not point ha_blueprint_asset at $path"
            }
        }
    }

    @Test
    fun `the README describes the event action and the blueprint`() {
        val readme = repoFile("README.md").readText()

        assertTrue(readme.contains(HA_EVENT_ACTION)) { "the README does not mention the $HA_EVENT_ACTION action" }
        assertTrue(readme.contains("ha/launcher_event.yaml")) {
            "the README does not point at ha/launcher_event.yaml"
        }
    }

    @Test
    fun `the README describes the pairing secret`() {
        val readme = repoFile("README.md").readText()

        assertTrue(readme.contains("$HA_EXTRA_SECRET:")) {
            "the README does not show the \"$HA_EXTRA_SECRET\" extra in the examples"
        }
    }

    private fun skeleton(blueprint: String): List<String> {
        val kept = mutableListOf<String>()
        var blockIndent = -1
        for (line in blueprint.lines()) {
            val indent = line.length - line.trimStart().length
            if (blockIndent >= 0 && (line.isBlank() || indent > blockIndent)) continue
            blockIndent = -1
            val key = TEXT_KEY.find(line)?.value
            kept += key ?: line
            if (key != null && line.trimEnd().endsWith(">-")) blockIndent = indent
        }
        return kept
    }

    private fun repoFile(path: String): File = File(REPO_ROOT, path).also {
        assertTrue(it.isFile) { "the file $path is not in the repository" }
    }

    private companion object {

        const val BLUEPRINT = "ha/launcher_event.yaml"

        const val BLUEPRINT_CS = "ha/launcher_event.cs.yaml"

        val TEXT_KEY = Regex("""^\s*(- )?(name|description|label):""")

        val REPO_ROOT: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "fixtures/scenarios").isDirectory }
    }
}
