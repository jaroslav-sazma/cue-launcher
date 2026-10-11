package app.cue.launcher.data.aichat

import kotlinx.serialization.Serializable

// What the user lets the brief for an AI chat carry. The apps with their screens and verified links,
// and the widgets always go: no scenario can be written without them. A part kept back is not even
// read; the apps' capabilities come from their shortcuts and go with them.
@Serializable
data class AiChatShare(
    val bluetooth: Boolean = true,
    val homeAssistant: Boolean = true,
    val shortcuts: Boolean = true,
) {
    fun within(offer: AiChatOffer): AiChatShare = copy(
        bluetooth = bluetooth && offer.bluetooth,
        homeAssistant = homeAssistant && offer.homeAssistant,
    )

    companion object {
        val ALL = AiChatShare()
    }
}

// What the phone has to offer right now: Bluetooth once its permission is granted, Home Assistant once
// it is set up. A part it lacks is neither offered nor read, and the brief says it is unavailable rather
// than kept back; the user's choice for it stays stored for when it comes.
data class AiChatOffer(val bluetooth: Boolean, val homeAssistant: Boolean) {
    companion object {
        val ALL = AiChatOffer(bluetooth = true, homeAssistant = true)
    }
}
