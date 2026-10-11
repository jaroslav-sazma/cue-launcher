package app.cue.launcher.engine

fun interface WallClock {

    fun nowMillis(): Long

    companion object {
        val SYSTEM: WallClock = WallClock { System.currentTimeMillis() }
    }
}
