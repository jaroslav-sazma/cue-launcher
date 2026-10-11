package app.cue.launcher.engine

fun interface HaEventLog {

    suspend fun record(event: HaEvent, matched: Int)
}

// Incoming codes are cut to HA_CODE_MAX_LENGTH, so a scenario's code has to go through the same cut —
// otherwise a longer one could never match, nor ever be marked as seen.
fun haCodeKey(code: String): String = haEventCode(code).orEmpty().lowercase()
