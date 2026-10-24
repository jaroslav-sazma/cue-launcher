package app.cue.launcher.data.aichat

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaEntityFilter
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.scenario.AppProfile
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.placeholderFor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioGroundingTest {

    private val inventory = InventorySnapshot(
        apps = listOf(InventoryApp("Maps", "com.maps"), InventoryApp("Clock", "com.clock")),
        devices = listOf(InventoryDevice("Car", "AA:BB:CC:DD:EE:FF")),
        haEntities = listOf(HaEntity("cover.gate", "Gate")),
        widgets = listOf(InventoryWidget("Weather", "Forecast", "com.forecast", "com.forecast.WeatherWidget")),
    )

    // A gate that opens and closes but cannot stop, a lock that cannot open the door, one whose state
    // reports no features, and services filtered as Home Assistant's services.yaml filters them.
    private val home = inventory.copy(
        haEntities = listOf(
            HaEntity("cover.gate", "Gate", features = OPEN or CLOSE),
            HaEntity("lock.front_door", "Front door", features = 0),
            HaEntity("lock.back_door", "Back door"),
            HaEntity("light.desk", "Desk"),
            HaEntity("group.living_room", "Living room"),
        ),
        haServices = listOf(
            service("cover.close_cover", "cover", CLOSE),
            service("cover.open_cover", "cover", OPEN),
            service("cover.stop_cover", "cover", STOP),
            service("cover.toggle", "cover", OPEN, CLOSE),
            service("homeassistant.toggle"),
            service("homeassistant.turn_off"),
            service("homeassistant.turn_on"),
            service("light.toggle", "light"),
            service("light.turn_off", "light"),
            service("light.turn_on", "light"),
            service("lock.lock", "lock"),
            service("lock.open", "lock", OPEN),
            service("lock.unlock", "lock"),
            HaService("script.night"),
        ),
    )

    private fun service(id: String, domain: String? = null, vararg features: Int) = HaService(
        id = id,
        targets = if (domain == null) {
            emptyList()
        } else {
            listOf(HaEntityFilter(setOf(domain), features = features.toList().takeIf { it.isNotEmpty() }))
        },
    )

    private fun widgetScenario(packageName: String, provider: String) = Scenario(
        id = "draft",
        name = "Draft",
        size = TileSize.HERO_4X2,
        content = TileContent.AppWidget(packageName, provider, placeholderFor("widget")),
    )

    private fun scenario(conditions: List<Condition> = emptyList(), vararg actions: TapAction) = Scenario(
        id = "draft",
        name = "Draft",
        size = TileSize.GRID_1X1,
        conditions = conditions,
        content = TileContent.Template(
            TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = actions.toList())),
        ),
    )

    @Test
    fun `references the inventory holds pass`() {
        val grounded = scenario(
            listOf(Condition.BtConnected(mac = "aa:bb:cc:dd:ee:ff")),
            TapAction.App("com.maps"),
            TapAction.Shortcut("com.clock", "timer"),
            TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate"),
            TapAction.SystemAction(SystemActionType.DND_ON),
            TapAction.DeepLink("geo:0,0"),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(grounded, inventory))
    }

    @Test
    fun `invented packages, devices and entities are named once each`() {
        val invented = scenario(
            listOf(Condition.BtDisconnected(mac = "11:22:33:44:55:66")),
            TapAction.App("com.invented"),
            TapAction.Capability("com.invented", "play"),
            TapAction.ActivityLaunch("com.other", "com.other.Main"),
            TapAction.HaAction(service = "light.turn_on", entity = "light.nowhere"),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownPackage("com.invented"),
                GroundingProblem.UnknownPackage("com.other"),
                GroundingProblem.UnknownBluetoothDevice("11:22:33:44:55:66"),
                GroundingProblem.UnknownHaEntity("light.nowhere"),
            ),
            groundingProblems(invented, inventory),
        )
    }

    @Test
    fun `a system action the phone cannot run is refused`() {
        val remote = scenario(actions = arrayOf(TapAction.SystemAction(SystemActionType.TV_REMOTE)))
        val withoutGoogleTv = inventory.copy(unavailableSystemActions = setOf(SystemActionType.TV_REMOTE))

        assertEquals(
            listOf(GroundingProblem.UnavailableSystemAction(SystemActionType.TV_REMOTE)),
            groundingProblems(remote, withoutGoogleTv),
        )
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(remote, inventory))
    }

    @Test
    fun `an icon's state entity is held to the inventory like the action's entity`() {
        fun gate(stateEntity: String) = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate", stateEntity = stateEntity),
            ),
        )

        assertEquals(
            listOf(GroundingProblem.UnknownHaEntity("binary_sensor.nowhere")),
            groundingProblems(gate("binary_sensor.nowhere"), inventory),
        )
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(gate("cover.gate"), inventory))
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(gate(placeholderFor("contact")), inventory))
    }

    @Test
    fun `the entity a state condition watches is held to the inventory`() {
        fun watching(entity: String) = scenario(listOf(Condition.HaState(entity = entity, states = listOf("open"))))

        assertEquals(
            listOf(GroundingProblem.UnknownHaEntity("cover.nowhere")),
            groundingProblems(watching("cover.nowhere"), inventory),
        )
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(watching("cover.gate"), inventory))
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(watching(placeholderFor("gate")), inventory))
        assertEquals(
            emptyList<GroundingProblem>(),
            groundingProblems(watching("cover.nowhere"), inventory.copy(haEntities = null)),
        )
    }

    @Test
    fun `apps a notification or media condition watches must be installed`() {
        val watching = scenario(
            listOf(
                Condition.NotificationFrom(packageName = "com.invented.chat"),
                Condition.MediaPlaying(packages = listOf("com.maps", "com.invented.music")),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownPackage("com.invented.chat"),
                GroundingProblem.UnknownPackage("com.invented.music"),
            ),
            groundingProblems(watching, inventory),
        )
    }

    @Test
    fun `the app whose notification ending is watched must be installed`() {
        val watching = scenario(
            listOf(
                Condition.NotificationEnded(packageName = "com.maps", durationSec = 1800),
                Condition.NotificationEnded(packageName = "com.invented.navigation", durationSec = 1800),
            ),
        )

        assertEquals(
            listOf(GroundingProblem.UnknownPackage("com.invented.navigation")),
            groundingProblems(watching, inventory),
        )
    }

    @Test
    fun `the device and the app an end event names are held to the inventory like a condition's`() {
        val invented = scenario(
            listOf(
                Condition.HaEvent(code = "doorbell", until = EndEvent.BtDisconnected(mac = "11:22:33:44:55:66")),
                Condition.Alarm(until = EndEvent.Notification(packageName = "com.invented.chat")),
            ),
        )
        val grounded = scenario(
            listOf(
                Condition.HaEvent(code = "doorbell", until = EndEvent.BtDisconnected(mac = "aa:bb:cc:dd:ee:ff")),
                Condition.Alarm(until = EndEvent.Notification(packageName = "com.maps")),
                Condition.PlaceSaved(until = EndEvent.Notification(packageName = placeholderFor("chat_app"))),
                Condition.TimeInstant(at = "08:00", until = EndEvent.BtDisconnected()),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownPackage("com.invented.chat"),
                GroundingProblem.UnknownBluetoothDevice("11:22:33:44:55:66"),
            ),
            groundingProblems(invented, inventory),
        )
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(grounded, inventory))
        assertEquals(
            listOf(GroundingProblem.UnknownPackage("com.invented.chat")),
            groundingProblems(invented, inventory.copy(devices = null)),
        )
    }

    @Test
    fun `a condition's device and its end event's device are each named`() {
        val both = scenario(
            listOf(
                Condition.BtDisconnected(
                    mac = "11:22:33:44:55:66",
                    until = EndEvent.BtDisconnected(mac = "22:33:44:55:66:77"),
                ),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownBluetoothDevice("11:22:33:44:55:66"),
                GroundingProblem.UnknownBluetoothDevice("22:33:44:55:66:77"),
            ),
            groundingProblems(both, inventory),
        )
    }

    @Test
    fun `a watched app left as a placeholder is not an unknown package`() {
        val placeholder = scenario(
            listOf(
                Condition.NotificationFrom(packageName = "{{PLACEHOLDER:chat_app}}"),
                Condition.MediaPlaying(packages = listOf("{{PLACEHOLDER:player}}")),
            ),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(placeholder, inventory))
    }

    @Test
    fun `any device and an entity-less service need no inventory`() {
        val open = scenario(listOf(Condition.BtConnected()), TapAction.HaAction(service = "script.night"))

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(open, inventory))
    }

    @Test
    fun `an entity left as a placeholder is not an unknown entity`() {
        val placeholder = scenario(
            actions = arrayOf(TapAction.HaAction(service = "lock.unlock", entity = "{{PLACEHOLDER:front_door}}")),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(placeholder, inventory))
    }

    @Test
    fun `lists that could not be read are not held against the candidate`() {
        val unknown = InventorySnapshot(apps = inventory.apps, devices = null, haEntities = null)
        val draft = scenario(
            listOf(Condition.BtConnected(mac = "11:22:33:44:55:66")),
            TapAction.HaAction(service = "light.turn_on", entity = "light.nowhere"),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(draft, unknown))
    }

    @Test
    fun `a widget the inventory lists passes, an invented one is named`() {
        val listed = widgetScenario("com.forecast", "com.forecast.WeatherWidget")
        val wrongClass = widgetScenario("com.forecast", "com.forecast.RadarWidget")
        val wrongPackage = widgetScenario("com.weather", "com.forecast.WeatherWidget")

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(listed, inventory))
        assertEquals(
            listOf(GroundingProblem.UnknownWidget("com.forecast", "com.forecast.RadarWidget")),
            groundingProblems(wrongClass, inventory),
        )
        assertEquals(
            listOf(GroundingProblem.UnknownWidget("com.weather", "com.forecast.WeatherWidget")),
            groundingProblems(wrongPackage, inventory),
        )
    }

    @Test
    fun `a widget left as a placeholder or an unreadable widget list is not held against the candidate`() {
        val placeholder = widgetScenario(placeholderFor("widget_pkg"), placeholderFor("widget_provider"))
        val invented = widgetScenario("com.invented", "com.invented.Widget")

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(placeholder, inventory))
        assertEquals(emptyList<GroundingProblem>(), groundingProblems(invented, inventory.copy(widgets = null)))
    }

    @Test
    fun `an in-app action must be one its app offers, unless the actions could not be read`() {
        val home = TapAction.Shortcut("com.maps", "home")
        val offered = inventory.copy(appActions = mapOf("com.maps" to listOf(InventoryAppAction("Home", home))))
        val draft = scenario(
            emptyList(),
            home,
            TapAction.Shortcut("com.maps", "gym"),
            TapAction.Capability("com.maps", "actions.intent.GET_THING"),
            TapAction.ActivityLaunch("com.maps", "com.maps.Settings"),
            TapAction.Shortcut("com.clock", "timer"),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownAppAction("com.maps", "gym"),
                GroundingProblem.UnknownAppAction("com.maps", "actions.intent.GET_THING"),
                GroundingProblem.UnknownAppAction("com.maps", "com.maps.Settings"),
            ),
            groundingProblems(draft, offered),
        )
    }

    @Test
    fun `an in-app action of the work profile is not held against what the personal app offers`() {
        val offered = inventory.copy(appActions = mapOf("com.maps" to emptyList()))
        val draft = scenario(
            emptyList(),
            TapAction.Shortcut("com.maps", "gym", profile = AppProfile.WORK),
            TapAction.Capability("com.maps", "actions.intent.GET_THING", profile = AppProfile.WORK),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(draft, offered))
    }

    @Test
    fun `an in-app action left as a placeholder is not held against the candidate`() {
        val offered = inventory.copy(appActions = mapOf("com.maps" to emptyList()))
        val draft = scenario(actions = arrayOf(TapAction.Shortcut("com.maps", placeholderFor("place"))))

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(draft, offered))
    }

    @Test
    fun `a shortcut or capability the brief kept back, and did not read, still passes when the draft uses it`() =
        runTest {
            val chat = TapAction.Shortcut("com.clock", "chat-alice")
            val alarm = TapAction.Capability("com.clock", "actions.intent.CREATE_ALARM")
            val link = InventoryAppAction("Alarms", TapAction.DeepLink("clock://alarms"))
            val reads = mutableListOf<Boolean>()
            val cached = CachedInventory(object : InventorySource {
                override suspend fun snapshot(share: AiChatShare) = inventory

                override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction> {
                    reads += shortcuts
                    val fromShortcuts = listOf(InventoryAppAction("Alice", chat), InventoryAppAction("Alarm", alarm))
                    return if (shortcuts) fromShortcuts + link else listOf(link)
                }
            })
            cached.appActions("com.clock", shortcuts = false)

            val problems = InventoryGrounding(cached).problems(scenario(actions = arrayOf(chat, alarm)))

            assertEquals(emptyList<GroundingProblem>(), problems)
            assertEquals(listOf(false, true), reads)
        }

    @Test
    fun `grounding holds the draft to the whole phone, whatever the brief kept back`() = runTest {
        val asked = mutableListOf<AiChatShare>()
        val grounding = InventoryGrounding { share -> inventory.also { asked += share } }

        grounding.problems(scenario(actions = arrayOf(TapAction.App("com.maps"))))

        assertEquals(listOf(AiChatShare.ALL), asked)
    }

    @Test
    fun `grounding reads the actions of the installed apps the draft's in-app actions name`() = runTest {
        val asked = mutableListOf<String>()
        val source = object : InventorySource {
            override suspend fun snapshot(share: AiChatShare) = inventory

            override suspend fun appActions(packageName: String, shortcuts: Boolean): List<InventoryAppAction> {
                asked += packageName
                return listOf(InventoryAppAction("Timer", TapAction.Shortcut("com.clock", "timer")))
            }
        }
        val draft = scenario(
            emptyList(),
            TapAction.App("com.maps"),
            TapAction.Shortcut("com.clock", "timer"),
            TapAction.Shortcut("com.clock", "stopwatch"),
            TapAction.Shortcut("com.invented", "x"),
        )

        val problems = InventoryGrounding(source).problems(draft)

        assertEquals(listOf("com.clock"), asked)
        assertEquals(
            listOf(
                GroundingProblem.UnknownPackage("com.invented"),
                GroundingProblem.UnknownAppAction("com.clock", "stopwatch"),
            ),
            problems,
        )
    }

    @Test
    fun `a service that is not domain dot service is named, with or without an entity`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "open_settings"),
                TapAction.HaAction(service = "cz.airbank.android/widget.QuickToolsWidgetProvider"),
                TapAction.HaAction(service = "light.toggle"),
                TapAction.HaAction(service = placeholderFor("service")),
            ),
        )

        val problems = groundingProblems(draft, inventory.copy(haEntities = null))

        assertEquals(
            listOf(
                GroundingProblem.MalformedHaService("open_settings"),
                GroundingProblem.MalformedHaService("cz.airbank.android/widget.QuickToolsWidgetProvider"),
            ),
            problems,
        )
        assertTrue("like light.toggle" in problems.first().description(), problems.first().description())
    }

    @Test
    fun `a service of another domain than its entity is named`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "light.turn_on", entity = "cover.gate"),
                TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate"),
            ),
        )

        val problems = groundingProblems(draft, inventory)

        assertEquals(listOf(GroundingProblem.ServiceDomainMismatch("light.turn_on", "cover.gate")), problems)
        assertTrue("cover.* or homeassistant.*" in problems.single().description(), problems.single().description())
    }

    @Test
    fun `services that act on any entity, groups and placeholders pass the domain check`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "homeassistant.toggle", entity = "cover.gate"),
                TapAction.HaAction(service = "script.flash", entity = "light.desk"),
                TapAction.HaAction(service = "light.turn_on", entity = "group.living_room"),
                TapAction.HaAction(service = "lock.unlock", entity = placeholderFor("front_door")),
            ),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(draft, inventory.copy(haEntities = null)))
    }

    @Test
    fun `scene, automation, button and notify services keep to their own domain`() {
        listOf("scene.turn_on", "automation.trigger", "button.press", "notify.send_message").forEach { service ->
            val draft = scenario(actions = arrayOf(TapAction.HaAction(service = service, entity = "cover.gate")))

            assertEquals(
                listOf(GroundingProblem.ServiceDomainMismatch(service, "cover.gate")),
                groundingProblems(draft, inventory),
                service,
            )
        }
    }

    @Test
    fun `an empty services list is as unknown as none and keeps the domain check`() {
        val draft = scenario(actions = arrayOf(TapAction.HaAction(service = "light.turn_on", entity = "cover.gate")))

        assertEquals(
            listOf(GroundingProblem.ServiceDomainMismatch("light.turn_on", "cover.gate")),
            groundingProblems(draft, inventory.copy(haServices = emptyList())),
        )
    }

    @Test
    fun `with the instance's services an invented one is named with what fits the entity instead`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "light.blink", entity = "light.desk"),
                TapAction.HaAction(service = "cover.turn_on", entity = "cover.gate"),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownHaService("light.blink", LIGHT_SERVICES),
                GroundingProblem.UnknownHaService(
                    "cover.turn_on",
                    listOf("cover.open_cover", "cover.close_cover", "cover.toggle"),
                ),
            ),
            groundingProblems(draft, home),
        )
    }

    @Test
    fun `an invented service without a listed entity is named with its domain's services or the generic ones`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "lock.unlatch", entity = placeholderFor("front_door")),
                TapAction.HaAction(service = "light.blink", entity = "light.nowhere"),
                TapAction.HaAction(service = "vacuum.start"),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownHaEntity("light.nowhere"),
                GroundingProblem.UnknownHaService("lock.unlatch", listOf("lock.unlock", "lock.lock", "lock.open")),
                GroundingProblem.UnknownHaService("light.blink", LIGHT_SERVICES),
                GroundingProblem.UnknownHaService(
                    "vacuum.start",
                    listOf("homeassistant.toggle", "homeassistant.turn_on"),
                ),
            ),
            groundingProblems(draft, home),
        )
    }

    @Test
    fun `a service the instance offers must fit a listed entity, and the fitting ones are named`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "light.turn_on", entity = "cover.gate"),
                TapAction.HaAction(service = "lock.open", entity = "lock.front_door"),
                TapAction.HaAction(service = "cover.stop_cover", entity = "cover.gate"),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.HaServiceMisfit(
                    "light.turn_on",
                    "cover.gate",
                    listOf("cover.open_cover", "cover.close_cover", "cover.toggle"),
                ),
                GroundingProblem.HaServiceMisfit(
                    "lock.open",
                    "lock.front_door",
                    listOf("lock.unlock", "lock.lock", "homeassistant.toggle"),
                ),
                GroundingProblem.HaServiceMisfit(
                    "cover.stop_cover",
                    "cover.gate",
                    listOf("cover.open_cover", "cover.close_cover", "cover.toggle"),
                ),
            ),
            groundingProblems(draft, home),
        )
    }

    @Test
    fun `a script takes any entity as a variable, another service without a target takes none`() {
        val withScript = home.copy(
            haEntities = home.haEntities.orEmpty() + HaEntity("script.night", "Night"),
            haServices = home.haServices.orEmpty() + HaService("notify.mobile_app_pixel"),
        )
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "script.night", entity = "script.night"),
                TapAction.HaAction(service = "script.night", entity = "light.desk"),
                TapAction.HaAction(service = "notify.mobile_app_pixel"),
                TapAction.HaAction(service = "notify.mobile_app_pixel", entity = placeholderFor("phone")),
                TapAction.HaAction(service = "notify.mobile_app_pixel", entity = "light.desk"),
                TapAction.HaAction(service = "notify.mobile_app_pixel", entity = "group.living_room"),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.HaServiceTakesNoEntity("notify.mobile_app_pixel", "light.desk"),
                GroundingProblem.HaServiceTakesNoEntity("notify.mobile_app_pixel", "group.living_room"),
            ),
            groundingProblems(draft, withScript),
        )
    }

    @Test
    fun `an entity the instance does not list is held to the domains the service admits`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "light.turn_on", entity = "cover.nowhere"),
                TapAction.HaAction(service = "cover.stop_cover", entity = "cover.elsewhere"),
                TapAction.HaAction(service = "homeassistant.toggle", entity = "fan.nowhere"),
                TapAction.HaAction(service = "light.turn_on", entity = placeholderFor("gate")),
            ),
        )

        assertEquals(
            listOf(
                GroundingProblem.UnknownHaEntity("cover.nowhere"),
                GroundingProblem.UnknownHaEntity("cover.elsewhere"),
                GroundingProblem.UnknownHaEntity("fan.nowhere"),
                GroundingProblem.HaServiceMisfit(
                    "light.turn_on",
                    "cover.nowhere",
                    listOf("cover.open_cover", "cover.close_cover", "cover.toggle"),
                ),
            ),
            groundingProblems(draft, home),
        )
    }

    @Test
    fun `an invented service for a listed entity nothing fits is named with its domain's services`() {
        val stopOnly = home.copy(haServices = listOf(service("cover.stop_cover", "cover", STOP)))
        val draft = scenario(actions = arrayOf(TapAction.HaAction(service = "cover.halt", entity = "cover.gate")))

        assertEquals(
            listOf(GroundingProblem.UnknownHaService("cover.halt", listOf("cover.stop_cover"))),
            groundingProblems(draft, stopOnly),
        )
    }

    @Test
    fun `fitting services, a group, an entity without features, a placeholder and no entity pass`() {
        val draft = scenario(
            actions = arrayOf(
                TapAction.HaAction(service = "cover.open_cover", entity = "cover.gate"),
                TapAction.HaAction(service = "homeassistant.toggle", entity = "cover.gate"),
                TapAction.HaAction(service = "light.turn_on", entity = "group.living_room"),
                TapAction.HaAction(service = "lock.open", entity = "lock.back_door"),
                TapAction.HaAction(service = "lock.unlock", entity = placeholderFor("front_door")),
                TapAction.HaAction(service = "script.night"),
                TapAction.HaAction(service = placeholderFor("service"), entity = "cover.gate"),
            ),
        )

        assertEquals(emptyList<GroundingProblem>(), groundingProblems(draft, home))
    }

    @Test
    fun `the shape check comes first with the services as well`() {
        val draft = scenario(actions = arrayOf(TapAction.HaAction(service = "open_settings", entity = "cover.gate")))

        assertEquals(listOf(GroundingProblem.MalformedHaService("open_settings")), groundingProblems(draft, home))
    }

    @Test
    fun `the service problems read as one line that names the replacements`() {
        val unknown = GroundingProblem.UnknownHaService("light.blink", listOf("light.toggle", "light.turn_on"))
        val misfit = GroundingProblem.HaServiceMisfit("light.turn_on", "cover.gate", listOf("cover.open_cover"))

        assertEquals(
            "Home Assistant has no service \"light.blink\"; use light.toggle or light.turn_on.",
            unknown.description(),
        )
        assertEquals(
            "Service \"light.turn_on\" cannot act on \"cover.gate\"; use cover.open_cover.",
            misfit.description(),
        )
        assertEquals(
            "Home Assistant has no service \"x.y\".",
            GroundingProblem.UnknownHaService("x.y", emptyList()).description(),
        )
        val stranded = GroundingProblem.HaServiceMisfit("x.y", "sensor.t", emptyList()).description()
        assertTrue(stranded.endsWith("use another entity."), stranded)
        assertEquals(
            "Service \"notify.pixel\" takes no entity; leave the entity out.",
            GroundingProblem.HaServiceTakesNoEntity("notify.pixel", "light.desk").description(),
        )
    }

    @Test
    fun `grounding asks for the services only for a draft that calls one on a reachable instance`() = runTest {
        var reads = 0
        fun source(snapshot: InventorySnapshot) = object : InventorySource {
            override suspend fun snapshot(share: AiChatShare) = snapshot

            override suspend fun haServices(): List<HaService> {
                reads++
                return home.haServices.orEmpty()
            }
        }
        val app = scenario(actions = arrayOf(TapAction.App("com.maps")))
        val placeholder = scenario(actions = arrayOf(TapAction.HaAction(service = placeholderFor("service"))))
        val invented = scenario(actions = arrayOf(TapAction.HaAction(service = "light.blink", entity = "light.desk")))
        val malformed = scenario(actions = arrayOf(TapAction.HaAction(service = "open_settings")))

        InventoryGrounding(source(home)).problems(app)
        InventoryGrounding(source(home)).problems(placeholder)
        val rejected = InventoryGrounding(source(home)).problems(malformed)
        InventoryGrounding(source(home.copy(haEntities = null))).problems(invented)
        assertEquals(0, reads)
        assertEquals(listOf(GroundingProblem.MalformedHaService("open_settings")), rejected)

        val problems = InventoryGrounding(source(home)).problems(invented)

        assertEquals(1, reads)
        assertInstanceOf(GroundingProblem.UnknownHaService::class.java, problems.single())
    }

    @Test
    fun `grounding problems have English one-liners`() {
        val problems = listOf(
            GroundingProblem.UnknownPackage("a"),
            GroundingProblem.UnknownBluetoothDevice("b"),
            GroundingProblem.UnknownHaEntity("c"),
            GroundingProblem.UnknownWidget("d", "e"),
            GroundingProblem.UnknownAppAction("f", "g"),
            GroundingProblem.ServiceDomainMismatch("h.i", "j.k"),
            GroundingProblem.UnknownHaService("l.m", listOf("n.o")),
            GroundingProblem.HaServiceMisfit("p.q", "r.s", listOf("t.u", "v.w", "x.y")),
            GroundingProblem.HaServiceTakesNoEntity("z.a", "b.c"),
        )

        assertTrue(problems.all { it.description().isNotBlank() && '\n' !in it.description() })
    }

    private companion object {
        const val OPEN = 1
        const val CLOSE = 2
        const val STOP = 8
        val LIGHT_SERVICES = listOf("light.toggle", "light.turn_on", "light.turn_off")
    }
}
