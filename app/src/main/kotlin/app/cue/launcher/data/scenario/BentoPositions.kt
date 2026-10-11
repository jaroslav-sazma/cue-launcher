package app.cue.launcher.data.scenario

private const val HERO_SLOTS = 1
private const val MEDIUM_SLOTS = 2
private const val SMALL_SLOTS = 4

fun bentoSlotCount(size: TileSize): Int = when (size) {
    TileSize.HERO_4X2 -> HERO_SLOTS
    TileSize.GRID_2X2 -> MEDIUM_SLOTS
    TileSize.GRID_1X1 -> SMALL_SLOTS
    TileSize.OVERLAY -> 0
}

// The last slot of a class is the overflow stack and has to stay reachable, so nothing may be pinned
// onto it. The hero owns nothing else, which is why it cannot be pinned at all.
fun pinnablePositions(size: TileSize): IntRange = 0 until bentoSlotCount(size) - 1

fun canPin(size: TileSize): Boolean = !pinnablePositions(size).isEmpty()

fun pinFits(size: TileSize, position: Int?): Boolean =
    position == null || position in pinnablePositions(size)
