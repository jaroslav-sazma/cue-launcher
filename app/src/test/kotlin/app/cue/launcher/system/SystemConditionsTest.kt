package app.cue.launcher.system

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.RingerModeState
import app.cue.launcher.engine.BluetoothState
import app.cue.launcher.engine.HaStates
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateNeeds
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import app.cue.launcher.engine.aspect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemConditionsTest {

    private val started = mutableListOf<StateAspect>()

    private val providers = setOf(
        provider(StateAspect.POWER, SystemState(charging = true)),
        provider(StateAspect.CALENDAR, SystemState(calendarEvent = true)),
        provider(StateAspect.WIFI, SystemState()),
    )

    @Test
    fun `only the asked aspects are started`() = runTest {
        val state = SystemConditions(providers, backgroundScope).measure(StateNeeds(setOf(StateAspect.POWER)))

        assertEquals(listOf(StateAspect.POWER), started)
        assertEquals(true, state.charging)
        assertEquals(null, state.calendarEvent)
    }

    @Test
    fun `asking for nothing starts nothing`() = runTest {
        assertEquals(SystemState(), SystemConditions(providers, backgroundScope).measure(StateNeeds(emptySet())))
        assertEquals(emptyList<StateAspect>(), started)
    }

    @Test
    fun `readings close together share one subscription and a quiet moment ends it`() = runTest {
        val conditions = SystemConditions(providers, backgroundScope)

        conditions.measure(StateNeeds(setOf(StateAspect.POWER)))
        conditions.measure(StateNeeds(setOf(StateAspect.POWER)))
        assertEquals(listOf(StateAspect.POWER), started)

        advanceTimeBy(6_000)
        conditions.measure(StateNeeds(setOf(StateAspect.POWER)))
        assertEquals(listOf(StateAspect.POWER, StateAspect.POWER), started)
    }

    @Test
    fun `a provider is asked anew after a quiet moment, so a permission granted meanwhile counts`() = runTest {
        var granted = false
        val bluetooth = object : StateProvider {
            override val aspect = StateAspect.BLUETOOTH

            override fun observe(): Flow<SystemState> =
                if (granted) flowOf(SystemState(bluetooth = BluetoothState(setOf("AA")))) else flowOf(SystemState())
        }
        val conditions = SystemConditions(setOf(bluetooth), backgroundScope)

        assertEquals(null, conditions.measure(StateNeeds(setOf(StateAspect.BLUETOOTH))).bluetooth)
        granted = true
        advanceTimeBy(6_000)

        val state = conditions.measure(StateNeeds(setOf(StateAspect.BLUETOOTH)))
        assertEquals(BluetoothState(setOf("AA")), state.bluetooth)
    }

    @Test
    fun `a failing provider reads as unknown instead of hanging the event`() = runTest {
        val broken = object : StateProvider {
            override val aspect = StateAspect.CALENDAR

            override fun observe(): Flow<SystemState> = flow { error("provider outage") }
        }

        val conditions = SystemConditions(setOf(broken), backgroundScope)

        assertEquals(SystemState(), conditions.measure(StateNeeds(setOf(StateAspect.CALENDAR))))
    }

    @Test
    fun `following state is not shared and starts only what is asked`() = runTest {
        SystemConditions(providers, backgroundScope).observeState(setOf(StateAspect.WIFI)).first()

        assertEquals(listOf(StateAspect.WIFI), started)
    }

    @Test
    fun `an event reads what a provider offers events, the screen what it follows`() = runTest {
        val remembered = SystemState(ha = HaStates(mapOf("media_player.tv" to "playing")))
        val read = SystemState(ha = HaStates(mapOf("media_player.tv" to "off")))
        val ha = object : StateProvider {
            override val aspect = StateAspect.HA

            override fun observe(): Flow<SystemState> = flowOf(remembered)

            override fun observeForEvent(needs: StateNeeds): Flow<SystemState> = flowOf(read)
        }
        val conditions = SystemConditions(setOf(ha), backgroundScope)

        assertEquals(read, conditions.measure(StateNeeds(setOf(StateAspect.HA))))
        assertEquals(remembered, conditions.observeState(setOf(StateAspect.HA)).first())
    }

    @Test
    fun `an event hands its needs to the provider and shares a reading only with the same needs`() = runTest {
        val handed = mutableListOf<Set<String>>()
        val ha = object : StateProvider {
            override val aspect = StateAspect.HA

            override fun observe(): Flow<SystemState> = flowOf(SystemState())

            override fun observeForEvent(needs: StateNeeds): Flow<SystemState> = flow {
                handed += needs.haEntities
                emit(SystemState(ha = HaStates(needs.haEntities.associateWith { "on" })))
            }
        }
        val conditions = SystemConditions(setOf(ha), backgroundScope)
        val tv = StateNeeds(setOf(StateAspect.HA), haEntities = setOf("media_player.tv"))
        val door = StateNeeds(setOf(StateAspect.HA), haEntities = setOf("binary_sensor.door"))

        conditions.measure(tv)
        conditions.measure(tv)
        val state = conditions.measure(door)

        assertEquals(listOf(setOf("media_player.tv"), setOf("binary_sensor.door")), handed)
        assertEquals(HaStates(mapOf("binary_sensor.door" to "on")), state.ha)
    }

    @Test
    fun `every state condition names the aspect that measures it`() {
        assertEquals(StateAspect.CLOCK, Condition.TimeWindow(from = "08:00", to = "09:00").aspect())
        assertEquals(StateAspect.MEDIA, Condition.MediaPlaying().aspect())
        assertEquals(StateAspect.RINGER, Condition.RingerMode(mode = RingerModeState.SILENT).aspect())
        assertEquals(StateAspect.HA, Condition.HaState(entity = "media_player.tv", states = listOf("on")).aspect())
        assertEquals(StateAspect.MAC, Condition.MacNearby().aspect())
        assertEquals(StateAspect.MAC, Condition.MacLocked().aspect())
    }

    private fun provider(aspect: StateAspect, reading: SystemState) = object : StateProvider {
        override val aspect: StateAspect = aspect

        override fun observe(): Flow<SystemState> = flow {
            started += aspect
            emit(reading)
        }
    }
}
