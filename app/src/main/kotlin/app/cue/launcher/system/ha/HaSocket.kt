package app.cue.launcher.system.ha

import android.util.Log
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaLiveListener
import app.cue.launcher.data.ha.HaLiveState
import app.cue.launcher.data.ha.haLiveStep
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// One socket follows every entity a visible tile refers to, so values change with the instance instead
// of with the next poll. Home Assistant sends the initial set once and then only differences, which is
// why this is cheaper than the REST read it replaces while the launcher is on screen.
@SingleIn(AppScope::class)
@Inject
class HaSocket(
    private val gateway: HaGateway,
    private val client: HaClient,
    private val version: HaInstanceVersion,
    private val configs: HaConfigRepository,
) {

    // True only when an event actually arrived: the caller polls over REST otherwise, and a channel
    // that authenticates and then dies must not be able to stop it from doing so.
    suspend fun consume(entities: Set<String>, onStates: HaLiveListener): Boolean {
        if (entities.isEmpty()) return false
        return gateway.live { endpoint, token ->
            client.socket(endpoint) { session -> follow(session, token, entities, onStates) }
        } ?: false
    }

    private suspend fun follow(
        session: WebSocketSession,
        token: String,
        entities: Set<String>,
        onStates: HaLiveListener,
    ): Boolean {
        var state = HaLiveState()
        for (frame in session.incoming) {
            val message = textOf(frame)?.let(::objectOf) ?: continue
            val step = haLiveStep(state, message, entities, token)
            if (step.state.authorized && !state.authorized) announce(step.state.version)
            state = step.state
            step.send?.let { session.send(Frame.Text(it)) }
            if (step.emit) onStates(state.values, state.attributes)
            if (step.refused) {
                Log.w(TAG, "Live channel refused the token")
                return false
            }
        }
        return state.streamed
    }

    private suspend fun announce(announced: String?) {
        val instance = configs.iconInstance.first()
        if (announced != null && instance != null) version.saw(instance, announced)
    }

    private fun textOf(frame: Frame): String? = (frame as? Frame.Text)?.readText()

    private fun objectOf(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private companion object {

        val json = Json { ignoreUnknownKeys = true }

        const val TAG = "HaSocket"
    }
}
