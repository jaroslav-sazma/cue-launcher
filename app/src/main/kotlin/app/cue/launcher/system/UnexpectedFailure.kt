package app.cue.launcher.system

import android.util.Log

// The message is left out on purpose: exceptions quote intents, URIs and notification text, so
// only the type and where it was thrown reach the log.
internal fun logUnexpected(tag: String, what: String, failure: Throwable) {
    val frames = failure.stackTrace.take(UNEXPECTED_FRAMES).joinToString(separator = "\n  at ")
    Log.e(tag, "$what: ${failure.javaClass.name}\n  at $frames")
}

private const val UNEXPECTED_FRAMES = 8
