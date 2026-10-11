package app.cue.launcher.data.layout

import androidx.room.withTransaction
import app.cue.launcher.data.CueDatabase
import app.cue.launcher.data.scenario.LauncherWidgetType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class LauncherLayoutRepository(
    private val database: CueDatabase,
    private val dao: LauncherLayoutDao,
    private val folderDao: FolderMemberDao,
) {

    fun observeLayout(): Flow<StaticLayout> =
        combine(dao.observeItems(), folderDao.observeMembers(), ::toLayout)

    suspend fun layout(): StaticLayout = toLayout(dao.items(), folderDao.members())

    suspend fun placeShortcut(container: LayoutContainer, position: Int, component: ComponentRef) {
        require(container.isValidPosition(position)) {
            "Position $position is outside container $container (${container.slotCount} slots)"
        }
        clearArea(container, listOf(position))
        dao.upsert(
            LauncherItem.Shortcut(id = 0, container = container, position = position, component = component)
                .toEntity(),
        )
    }

    suspend fun placeLauncherWidget(
        container: LayoutContainer,
        position: Int,
        widget: LauncherWidgetType,
    ) {
        val columns = widget.staticColumns
        val rows = widget.staticRows
        require(container.fitsArea(position, columns, rows)) {
            "Widget $widget ${columns}×$rows does not fit at position $position in container $container"
        }
        clearArea(container, areaPositions(container, position, columns, rows))
        dao.upsert(
            LauncherItem.LauncherWidget(id = 0, container = container, position = position, widget = widget)
                .toEntity(),
        )
    }

    suspend fun placeAppWidget(
        container: LayoutContainer,
        position: Int,
        appWidgetId: Int,
        columnSpan: Int,
        rowSpan: Int,
    ) {
        require(container.fitsArea(position, columnSpan, rowSpan)) {
            "Widget $columnSpan×$rowSpan does not fit at position $position in container $container"
        }
        clearArea(container, areaPositions(container, position, columnSpan, rowSpan))
        dao.upsert(
            LauncherItem.AppWidget(
                id = 0,
                container = container,
                position = position,
                appWidgetId = appWidgetId,
                columnSpan = columnSpan,
                rowSpan = rowSpan,
            ).toEntity(),
        )
    }

    suspend fun moveItem(item: LauncherItem, container: LayoutContainer, position: Int) {
        require(container.fitsArea(position, item.columnSpan, item.rowSpan)) {
            "Item ${item.id} does not fit at position $position in container $container"
        }
        val moved = when (item) {
            is LauncherItem.Shortcut -> item.copy(container = container, position = position)
            is LauncherItem.Folder -> item.copy(container = container, position = position)
            is LauncherItem.LauncherWidget -> item.copy(container = container, position = position)
            is LauncherItem.AppWidget -> item.copy(container = container, position = position)
        }
        clearArea(container, moved.coveredPositions, keep = item.id)
        dao.upsert(moved.toEntity())
    }

    suspend fun createFolder(container: LayoutContainer, position: Int, name: String): Long {
        require(container.isValidPosition(position)) {
            "Position $position is outside container $container (${container.slotCount} slots)"
        }
        clearArea(container, listOf(position))
        return dao.upsert(
            LauncherItem.Folder(
                id = 0,
                container = container,
                position = position,
                name = name,
                items = emptyList(),
            ).toEntity(),
        )
    }

    suspend fun mergeIntoNewFolder(
        target: LauncherItem.Shortcut,
        added: ComponentRef,
        name: String,
        removing: LauncherItem? = null,
    ): Long = database.withTransaction {
        removing?.let { removeItem(it) }

        val folderId = createFolder(target.container, target.position, name)
        writeFolderItems(folderId, folderItemsWith(listOf(target.component), added))
        folderId
    }

    suspend fun renameFolder(folder: LauncherItem.Folder, name: String) {
        dao.upsert(folder.copy(name = name).toEntity())
    }

    suspend fun addToFolder(folderId: Long, component: ComponentRef) {
        val items = folderItems(folderId)
        val updated = folderItemsWith(items, component)
        if (updated.size != items.size) writeFolderItems(folderId, updated)
    }

    suspend fun removeFromFolder(folderId: Long, component: ComponentRef) {
        val items = folderItems(folderId)
        val updated = items.filterNot { it == component }
        if (updated.size != items.size) writeFolderItems(folderId, updated)
    }

    suspend fun moveInFolder(folderId: Long, from: Int, to: Int) {
        val items = folderItems(folderId)
        val updated = folderItemsMoved(items, from, to)
        if (updated !== items) writeFolderItems(folderId, updated)
    }

    suspend fun clearPosition(container: LayoutContainer, position: Int) {
        clearArea(container, listOf(position))
    }

    suspend fun removeItem(item: LauncherItem) {
        if (item is LauncherItem.Folder) folderDao.deleteMembers(item.id)
        dao.deleteById(item.id)
    }

    suspend fun removePackage(packageName: String, userSerial: Long): Int {
        val fromFolders = dropFromFolders { member ->
            member.packageName == packageName && member.userSerial == userSerial
        }
        return dao.deleteByPackage(packageName, userSerial) + fromFolders
    }

    suspend fun storedProfiles(): List<Long> = (dao.profiles() + folderDao.memberProfiles()).distinct()

    suspend fun storedPackages(): List<StoredPackage> =
        dao.items()
            .filter { it.type == LauncherItemType.SHORTCUT.storageKey }
            .map { StoredPackage(it.packageName, it.userSerial) } + folderDao.memberPackages()

    suspend fun removeProfile(userSerial: Long): Int {
        val fromFolders = dropFromFolders { member -> member.userSerial == userSerial }
        return dao.deleteByProfile(userSerial) + fromFolders
    }

    suspend fun removeDeadAppWidgets(liveAppWidgetIds: Set<Int>): Int {
        val dead = layout().allItems()
            .filterIsInstance<LauncherItem.AppWidget>()
            .filterNot { it.appWidgetId in liveAppWidgetIds }
        dead.forEach { widget -> dao.deleteById(widget.id) }
        return dead.size
    }

    suspend fun isEmpty(): Boolean = dao.count() == 0

    suspend fun replaceAll(items: List<LauncherItem>) {
        database.withTransaction {
            folderDao.deleteAllMembers()
            dao.deleteAll()
            for (item in items.filter { it.container.fitsArea(it.position, it.columnSpan, it.rowSpan) }) {
                val id = dao.upsert(item.withId(0).toEntity())
                if (item is LauncherItem.Folder) writeFolderItems(id, item.items)
            }
        }
    }

    private suspend fun folderItems(folderId: Long): List<ComponentRef> =
        folderDao.membersOf(folderId).map(FolderMemberEntity::toComponentRef)

    private suspend fun writeFolderItems(folderId: Long, items: List<ComponentRef>) {
        folderDao.replaceMembers(folderId, folderMemberEntities(folderId, items))
    }

    private suspend fun clearArea(
        container: LayoutContainer,
        positions: List<Int>,
        keep: Long? = null,
    ) {
        dao.itemsIn(container.storageKey)
            .mapNotNull { row -> row.toLauncherItem() }
            .filter { it.id != keep && it.coveredPositions.any { p -> p in positions } }
            .forEach { item -> removeItem(item) }
    }

    private suspend fun dropFromFolders(matches: (FolderMemberEntity) -> Boolean): Int {
        val folders = folderDao.members().filter(matches).map(FolderMemberEntity::folderId).distinct()
        var removed = 0
        for (folderId in folders) {
            val current = folderDao.membersOf(folderId)
            val kept = current.filterNot(matches)
            removed += current.size - kept.size
            writeFolderItems(folderId, kept.map(FolderMemberEntity::toComponentRef))
        }
        return removed
    }

    private fun toLayout(
        rows: List<LauncherItemEntity>,
        members: List<FolderMemberEntity>,
    ): StaticLayout {
        val byFolder = members
            .sortedBy(FolderMemberEntity::position)
            .groupBy(FolderMemberEntity::folderId)
        return staticLayoutOf(
            rows.mapNotNull { row ->
                row.toLauncherItem(
                    members = byFolder[row.id].orEmpty().map(FolderMemberEntity::toComponentRef),
                )
            },
        )
    }
}
