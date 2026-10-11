package app.cue.launcher.data.layout

const val FOLDER_CHIP_FIELDS: Int = 4

fun folderPreviewCount(itemCount: Int, fields: Int = FOLDER_CHIP_FIELDS): Int = when {
    itemCount <= fields -> itemCount
    else -> fields - 1
}

fun folderPlusCount(itemCount: Int, fields: Int = FOLDER_CHIP_FIELDS): Int =
    (itemCount - folderPreviewCount(itemCount, fields)).coerceAtLeast(0)

fun <T> folderItemsMoved(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    val moved = items.toMutableList()
    moved.add(to, moved.removeAt(from))
    return moved
}

fun folderItemsWith(items: List<ComponentRef>, component: ComponentRef): List<ComponentRef> =
    if (component in items) items else items + component
