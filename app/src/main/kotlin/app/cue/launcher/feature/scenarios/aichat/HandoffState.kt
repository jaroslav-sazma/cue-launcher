package app.cue.launcher.feature.scenarios.aichat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.system.aichat.HandoffOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Nothing is read or sent until the user taps send: outcome stays null until then, and sending is true
// while the brief is read and handed over; a preview only reads it. Only the outcome is saved: the
// brief is too big for saved state and is read again when it is needed after a process death. What
// the phone offers is taken again on every start. onSent must not depend on the screen staying
// started — the chat app stops it right after the send.
@Suppress("LongParameterList")
@Stable
internal class HandoffState(
    private val readBrief: suspend (AiChatShare, AiChatOffer) -> String,
    private val sendBrief: suspend (String) -> HandoffOutcome,
    private val clear: () -> Unit,
    private val scope: CoroutineScope,
    outcome: HandoffOutcome? = null,
    share: AiChatShare = AiChatShare.ALL,
    offer: AiChatOffer = AiChatOffer.ALL,
    private val persistShare: (AiChatShare) -> Unit = {},
    private val onSent: () -> Unit = {},
) {
    var outcome by mutableStateOf(outcome)
        private set

    var share by mutableStateOf(share)
        private set

    var offer by mutableStateOf(offer)
        private set

    var sending by mutableStateOf(false)
        private set

    var brief by mutableStateOf<String?>(null)
        private set

    var previewing by mutableStateOf(false)
        private set

    private var job: Job? = null

    private val fallback: Boolean get() = outcome != null && outcome != HandoffOutcome.SENT

    // A brief read while the phone offered something else no longer matches it.
    fun start(available: AiChatOffer) {
        if (available != offer) {
            offer = available
            brief = null
        }
        if ((fallback || previewing) && brief == null) read(andSend = false)
    }

    // The phone can change while the screen is away, so a held brief would show it stale in a preview;
    // only the fallback keeps its brief, as the one Save writes.
    fun stop() {
        job?.cancel()
        job = null
        sending = false
        if (!fallback) brief = null
        clear()
    }

    fun send() {
        sending = true
        read(andSend = true)
    }

    // A brief already read no longer matches the choice; the fallback reads it again for saving.
    fun choose(chosen: AiChatShare) {
        if (sending || chosen == share) return
        share = chosen
        persistShare(chosen)
        job?.cancel()
        brief = null
        if (fallback) read(andSend = false)
    }

    // Read exactly as a send reads it, and never handed over.
    fun preview() {
        if (sending) return
        previewing = true
        if (brief == null && job?.isActive != true) read(andSend = false)
    }

    fun closePreview() {
        previewing = false
    }

    // A save result can arrive in a process that has not read the brief yet.
    suspend fun briefForSaving(): String = brief ?: readBrief(share, offer).also { brief = it }

    private fun read(andSend: Boolean) {
        val chosen = share
        val available = offer
        job?.cancel()
        job = scope.launch {
            val text = readBrief(chosen, available)
            brief = text
            if (andSend) {
                val result = sendBrief(text)
                outcome = result
                sending = false
                if (result == HandoffOutcome.SENT) onSent()
            }
        }
    }

    companion object {
        fun saver(create: (HandoffOutcome?) -> HandoffState): Saver<HandoffState, Any> = listSaver(
            save = { listOf(it.outcome?.name.orEmpty()) },
            restore = { saved -> create(saved[0].takeIf(String::isNotEmpty)?.let(HandoffOutcome::valueOf)) },
        )
    }
}
