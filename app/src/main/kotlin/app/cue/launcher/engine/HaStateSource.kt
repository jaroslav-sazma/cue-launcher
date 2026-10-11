package app.cue.launcher.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface HaStateSource {

    fun observeStates(entities: Set<String>): Flow<Map<String, String>>

    // A condition's states. The first value is whatever was read last, however long ago, so a tile the
    // condition holds up does not drop out on every return to the home screen and come back once the
    // read lands; from the read on, an entity it did not return is unknown.
    fun observeGate(entities: Set<String>): Flow<Map<String, String>>

    // The same for an event, which waits a bounded time for the read instead of trusting an old value.
    fun observeGateForEvent(entities: Set<String>): Flow<Map<String, String>>

    fun refresh()

    fun nudge()

    companion object {

        val NONE: HaStateSource = object : HaStateSource {
            override fun observeStates(entities: Set<String>): Flow<Map<String, String>> =
                flowOf(emptyMap())

            override fun observeGate(entities: Set<String>): Flow<Map<String, String>> = flowOf(emptyMap())

            override fun observeGateForEvent(entities: Set<String>): Flow<Map<String, String>> =
                flowOf(emptyMap())

            override fun refresh() = Unit

            override fun nudge() = Unit
        }
    }
}
