package app.cue.launcher.feature.home

import app.cue.launcher.data.layout.folderItemsMoved

data class FolderCellBounds(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

fun folderDropIndex(
    cells: Collection<FolderCellBounds>,
    x: Float,
    y: Float,
    itemCount: Int,
): Int? = cells.firstOrNull { it.index < itemCount && it.contains(x, y) }?.index

fun folderPreviewOrder(itemCount: Int, dragged: Int?, target: Int?): List<Int> =
    folderItemsMoved((0 until itemCount).toList(), dragged ?: NO_INDEX, target ?: NO_INDEX)

data class FolderMove(val from: Int, val to: Int)

private const val NO_INDEX = -1
