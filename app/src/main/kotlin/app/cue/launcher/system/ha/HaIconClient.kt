package app.cue.launcher.system.ha

import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaIcons
import app.cue.launcher.data.ha.haIconsStep
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

// Home Assistant hands out its default icons over the WebSocket API only, the same way the frontend
// gets them.
@SingleIn(AppScope::class)
@Inject
class HaIconClient(private val http: HaHttp, private val client: HaClient) {

    suspend fun icons(endpoint: HaEndpoint, token: String, domains: Set<String>): HaIcons? =
        withTimeoutOrNull(ICONS_TIMEOUT_MILLIS) {
            client.socket(endpoint, purpose = "Icons") { session -> icons(session, token, domains) }
        }

    private suspend fun icons(session: WebSocketSession, token: String, domains: Set<String>): HaIcons? {
        var version = ""
        for (frame in session.incoming) {
            val step = (frame as? Frame.Text)?.let { text ->
                withContext(Dispatchers.Default) {
                    runCatching { http.json.parseToJsonElement(text.readText()) as? JsonObject }.getOrNull()
                        ?.let { haIconsStep(it, token, domains) }
                }
            } ?: continue
            step.version?.let { version = it }
            step.send?.let { session.send(Frame.Text(it)) }
            if (step.done) return step.icons?.let { HaIcons(version, it) }
        }
        return null
    }

    private companion object {

        const val ICONS_TIMEOUT_MILLIS = HaHttp.REQUEST_TIMEOUT_MILLIS
    }
}
