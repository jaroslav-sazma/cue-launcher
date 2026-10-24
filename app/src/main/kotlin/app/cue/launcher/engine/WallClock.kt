package app.cue.launcher.engine

import java.time.ZoneId

fun interface WallClock {

    fun nowMillis(): Long

    // Read on every call: the user can change the zone while the process lives.
    fun zone(): ZoneId = ZoneId.systemDefault()

    companion object {
        val SYSTEM: WallClock = WallClock { System.currentTimeMillis() }
    }
}
