package app.cue.launcher.system

import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ActionRoutingTest {

    @Test
    fun `opening an app goes to the main activity`() {
        val route = actionRouteOf(TapAction.App(packageName = "com.spotify.music"))

        assertEquals(ActionRoute.MainActivity("com.spotify.music"), route)
    }

    @Test
    fun `an action of a work app keeps its profile on the way to the launch`() {
        val outlook = "com.microsoft.office.outlook"

        assertEquals(
            ActionRoute.MainActivity(outlook, AppProfile.WORK),
            actionRouteOf(TapAction.App(packageName = outlook, profile = AppProfile.WORK)),
        )
        assertEquals(
            ActionRoute.Shortcut(outlook, "compose", AppProfile.WORK),
            actionRouteOf(TapAction.Shortcut(packageName = outlook, shortcutId = "compose", profile = AppProfile.WORK)),
        )
        assertEquals(
            ActionRoute.Capability(outlook, "actions.intent.CREATE_MESSAGE", AppProfile.WORK),
            actionRouteOf(
                TapAction.Capability(
                    packageName = outlook,
                    capability = "actions.intent.CREATE_MESSAGE",
                    profile = AppProfile.WORK,
                ),
            ),
        )
    }

    @Test
    fun `a deep link opens as a uri`() {
        val route = actionRouteOf(TapAction.DeepLink(uri = "google.navigation:q=Domů"))

        assertEquals(ActionRoute.ViewUri("google.navigation:q=Domů"), route)
    }

    @Test
    fun `a uri without a scheme passes`() {
        assertEquals(ActionRoute.ViewUri("www.seznam.cz"), actionRouteOf(TapAction.DeepLink("www.seznam.cz")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["file:///sdcard/foto.jpg", "FILE:///sdcard/foto.jpg"])
    fun `a file uri is rejected`(uri: String) {
        assertEquals(ActionRoute.Malformed, actionRouteOf(TapAction.DeepLink(uri)))
    }

    // A serialized intent is not an address: ACTION_VIEW over it would resolve to nothing, so it
    // takes the route that parses it back into the intent it describes.
    @Test
    fun `a serialized intent takes its own route`() {
        val uri = "intent://devices/controller?hgs_device_id=38f96c5e#Intent;" +
            "scheme=googlehome;package=com.google.android.apps.chromecast.app;end"

        assertEquals(ActionRoute.IntentUri(uri), actionRouteOf(TapAction.DeepLink(uri)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["INTENT://x#Intent;end", "android-app://com.spotify.music"])
    fun `both serialized intent schemes are recognized`(uri: String) {
        assertEquals(ActionRoute.IntentUri(uri), actionRouteOf(TapAction.DeepLink(uri)))
    }

    @Test
    fun `a plain app scheme still opens as a uri`() {
        val uri = "googlehome://devices/controller?hgs_device_id=38f96c5e"

        assertEquals(ActionRoute.ViewUri(uri), actionRouteOf(TapAction.DeepLink(uri)))
    }

    @Test
    fun `a shortcut carries both package and id`() {
        val route = actionRouteOf(TapAction.Shortcut(packageName = "com.whatsapp", shortcutId = "chat-42"))

        assertEquals(ActionRoute.Shortcut("com.whatsapp", "chat-42"), route)
    }

    @Test
    fun `capability carries both package and capability name`() {
        val route = actionRouteOf(
            TapAction.Capability(packageName = "com.spotify.music", capability = "actions.intent.PLAY_MEDIA"),
        )

        assertEquals(ActionRoute.Capability("com.spotify.music", "actions.intent.PLAY_MEDIA"), route)
    }

    @Test
    fun `an activity is normalized to an absolute name`() {
        val route = actionRouteOf(
            TapAction.ActivityLaunch(packageName = "com.android.chrome", component = ".SearchActivity"),
        )

        assertEquals(ActionRoute.Activity("com.android.chrome", "com.android.chrome.SearchActivity"), route)
    }

    @Test
    fun `an HA action splits into a domain and a service`() {
        val ha = TapAction.HaAction(
            service = "Light.Turn_Off",
            entity = " light.obyvak ",
            data = JsonObject(emptyMap()),
        )

        val route = actionRouteOf(ha) as ActionRoute.HaService

        assertEquals("light", route.call.domain)
        assertEquals("turn_off", route.call.service)
        assertEquals("light.obyvak", route.call.entityId)
    }

    @Test
    fun `an HA action without a domain is a defect`() {
        val ha = TapAction.HaAction(service = "turn_off", entity = null, data = JsonObject(emptyMap()))

        assertEquals(ActionRoute.Malformed, actionRouteOf(ha))
    }

    @Test
    fun `HA failure has three different messages`() {
        assertEquals(ActionOutcome.DONE, haActionOutcome(HaProbe.Ok(entities = 1, millis = 40)))
        assertEquals(ActionOutcome.HA_REJECTED, haActionOutcome(HaProbe.Http(401)))
        assertEquals(ActionOutcome.HA_UNREACHABLE, haActionOutcome(HaProbe.Network("timeout")))
        assertEquals(ActionOutcome.HA_NOT_CONFIGURED, haActionOutcome(HaProbe.NotConfigured))
    }

    @Test
    fun `ringer and do not disturb are two different routes`() {
        assertEquals(
            ActionRoute.Ringer(RingerModeState.SILENT),
            actionRouteOf(TapAction.SystemAction(SystemActionType.RINGER_SILENT)),
        )
        assertEquals(
            ActionRoute.Ringer(RingerModeState.VIBRATE),
            actionRouteOf(TapAction.SystemAction(SystemActionType.RINGER_VIBRATE)),
        )
        assertEquals(
            ActionRoute.Ringer(RingerModeState.NORMAL),
            actionRouteOf(TapAction.SystemAction(SystemActionType.RINGER_NORMAL)),
        )
        assertEquals(ActionRoute.DoNotDisturb(enabled = true), systemActionRoute(SystemActionType.DND_ON))
        assertEquals(ActionRoute.DoNotDisturb(enabled = false), systemActionRoute(SystemActionType.DND_OFF))
    }

    @Test
    fun `flashlight and panels have their own route`() {
        assertEquals(ActionRoute.Torch, systemActionRoute(SystemActionType.FLASHLIGHT))
        assertEquals(
            ActionRoute.SystemScreen(SystemScreenTarget.VOLUME_PANEL),
            systemActionRoute(SystemActionType.PANEL_VOLUME),
        )
        assertEquals(
            ActionRoute.SystemScreen(SystemScreenTarget.INTERNET_PANEL),
            systemActionRoute(SystemActionType.PANEL_INTERNET),
        )
        assertEquals(
            ActionRoute.SystemScreen(SystemScreenTarget.WIFI_PANEL),
            systemActionRoute(SystemActionType.PANEL_WIFI),
        )
        assertEquals(
            ActionRoute.SystemScreen(SystemScreenTarget.NFC_PANEL),
            systemActionRoute(SystemActionType.PANEL_NFC),
        )
        assertEquals(
            ActionRoute.SystemScreen(SystemScreenTarget.SETTINGS),
            systemActionRoute(SystemActionType.OPEN_SETTINGS),
        )
    }

    @Test
    fun `the TV remote fires what Google TV's Quick Settings tile fires`() {
        assertEquals(ActionRoute.TvRemote, systemActionRoute(SystemActionType.TV_REMOTE))
        assertEquals("com.google.android.apps.googletv.ACTION_VIRTUAL_REMOTE", TV_REMOTE_ACTION)
        assertEquals("com.google.android.videos", GOOGLE_TV_PACKAGE)
        assertEquals(0x10880000, TV_REMOTE_FLAGS)
    }

    @Test
    fun `a TV remote without Google TV names the missing app`() {
        assertEquals(ActionOutcome.GOOGLE_TV_MISSING, tvRemoteOutcome(ActionOutcome.TARGET_MISSING))
        assertEquals(ActionOutcome.DONE, tvRemoteOutcome(ActionOutcome.DONE))
        assertEquals(ActionOutcome.FAILED, tvRemoteOutcome(ActionOutcome.FAILED))
    }

    @Test
    fun `the whole catalog of system actions has a route`() {
        SystemActionType.entries.forEach { action ->
            val route = systemActionRoute(action)
            assertNotEquals(ActionRoute.Malformed, route, action.jsonName)
        }
    }

    @Test
    fun `empty required fields are a defect, not an exception`() {
        val actions = listOf(
            TapAction.App(packageName = "  "),
            TapAction.DeepLink(uri = ""),
            TapAction.Shortcut(packageName = "com.whatsapp", shortcutId = " "),
            TapAction.Shortcut(packageName = "", shortcutId = "chat-42"),
            TapAction.Capability(packageName = "com.spotify.music", capability = ""),
            TapAction.ActivityLaunch(packageName = "com.android.chrome", component = ""),
        )

        actions.forEach { action ->
            assertEquals(ActionRoute.Malformed, actionRouteOf(action), "$action")
        }
    }

    @Test
    fun `whitespace around values does not matter`() {
        val route = actionRouteOf(TapAction.App(packageName = " com.spotify.music\n"))

        assertEquals(ActionRoute.MainActivity("com.spotify.music"), route)
    }

    @Test
    fun `an absolute class name is left as is`() {
        val name = absoluteClassName("com.android.chrome", "org.chromium.chrome.browser.SearchActivity")

        assertEquals("org.chromium.chrome.browser.SearchActivity", name)
    }

    @Test
    fun `a bare class name gets the package`() {
        assertEquals("com.android.chrome.SearchActivity", absoluteClassName("com.android.chrome", "SearchActivity"))
    }

    @Test
    fun `an inner class stays unchanged`() {
        val name = absoluteClassName("com.foo", "com.foo.Main\$Alias")

        assertEquals("com.foo.Main\$Alias", name)
    }

    @Test
    fun `capability is served by the first enabled shortcut that declares it`() {
        val candidates = listOf(
            ShortcutCandidate(id = "ctx", capabilities = listOf("actions.intent.GET_THING"), enabled = true),
            ShortcutCandidate(id = "play-1", capabilities = listOf("actions.intent.PLAY_MEDIA"), enabled = true),
            ShortcutCandidate(id = "play-2", capabilities = listOf("actions.intent.PLAY_MEDIA"), enabled = true),
        )

        assertEquals("play-1", shortcutForCapability(candidates, "actions.intent.PLAY_MEDIA"))
    }

    @Test
    fun `a disabled shortcut is skipped`() {
        val candidates = listOf(
            ShortcutCandidate(id = "play-1", capabilities = listOf("actions.intent.PLAY_MEDIA"), enabled = false),
            ShortcutCandidate(id = "play-2", capabilities = listOf("actions.intent.PLAY_MEDIA"), enabled = true),
        )

        assertEquals("play-2", shortcutForCapability(candidates, "actions.intent.PLAY_MEDIA"))
    }

    @Test
    fun `a capability nobody declares has no shortcut`() {
        val candidates = listOf(
            ShortcutCandidate(id = "ctx", capabilities = emptyList(), enabled = true),
        )

        assertNull(shortcutForCapability(candidates, "actions.intent.PLAY_MEDIA"))
        assertNull(shortcutForCapability(emptyList(), "actions.intent.PLAY_MEDIA"))
    }

    @Test
    fun `multiple actions report the first failure`() {
        assertEquals(
            ActionOutcome.DONE,
            combinedOutcome(listOf(ActionOutcome.DONE, ActionOutcome.DONE)),
        )
        assertEquals(
            ActionOutcome.PERMISSION_REQUIRED,
            combinedOutcome(
                listOf(ActionOutcome.DONE, ActionOutcome.PERMISSION_REQUIRED, ActionOutcome.FAILED),
            ),
        )
    }

    @Test
    fun `no actions is not a failure`() {
        assertEquals(ActionOutcome.DONE, combinedOutcome(emptyList()))
    }

    // One marked action gates the whole tap; everything else runs as it always did.
    @Test
    fun `only a marked HA action asks for confirmation`() {
        val sensitive = TapAction.HaAction(service = "cover.open_cover", confirm = true)
        val plain = TapAction.HaAction(service = "light.toggle")

        assertTrue(confirmationRequired(listOf(plain, sensitive)))
        assertFalse(confirmationRequired(listOf(plain)))
        assertFalse(confirmationRequired(listOf(TapAction.App("com.x"))))
        assertFalse(confirmationRequired(emptyList()))
    }
}
