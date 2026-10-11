package app.cue.launcher.data

import androidx.room.Database
import androidx.room.RoomDatabase
import app.cue.launcher.data.ha.HaCodeSeenEntity
import app.cue.launcher.data.ha.HaEventLogDao
import app.cue.launcher.data.drawer.HiddenAppDao
import app.cue.launcher.data.drawer.HiddenAppEntity
import app.cue.launcher.data.ha.HaEventLogEntity
import app.cue.launcher.data.icons.IconOverrideDao
import app.cue.launcher.data.icons.IconOverrideEntity
import app.cue.launcher.data.launchlog.AppLaunchDao
import app.cue.launcher.data.launchlog.AppLaunchEntity
import app.cue.launcher.data.layout.FolderMemberDao
import app.cue.launcher.data.layout.FolderMemberEntity
import app.cue.launcher.data.layout.LauncherItemEntity
import app.cue.launcher.data.layout.LauncherLayoutDao
import app.cue.launcher.data.scenario.ActivationDao
import app.cue.launcher.data.scenario.ActivationEntity
import app.cue.launcher.data.scenario.ScenarioDao
import app.cue.launcher.data.scenario.ScenarioEntity
import app.cue.launcher.data.widgets.HostedWidgetDao
import app.cue.launcher.data.widgets.HostedWidgetEntity
import app.cue.launcher.data.widgets.PanelWidgetDao
import app.cue.launcher.data.widgets.PanelWidgetEntity

// Room annotations are not kept at runtime, so the version lives here: this is what a test can hold
// the exported schemas to. The history before 17 was squashed, so the first migration a schema
// change adds starts at 17 and an older database has no way up.
const val CUE_DATABASE_VERSION = 17

@Database(
    entities = [
        LauncherItemEntity::class,
        FolderMemberEntity::class,
        AppLaunchEntity::class,
        HostedWidgetEntity::class,
        PanelWidgetEntity::class,
        ScenarioEntity::class,
        ActivationEntity::class,
        HaEventLogEntity::class,
        HaCodeSeenEntity::class,
        IconOverrideEntity::class,
        HiddenAppEntity::class,
    ],
    version = CUE_DATABASE_VERSION,
    exportSchema = true,
)
abstract class CueDatabase : RoomDatabase() {

    abstract fun launcherLayoutDao(): LauncherLayoutDao

    abstract fun folderMemberDao(): FolderMemberDao

    abstract fun appLaunchDao(): AppLaunchDao

    abstract fun hostedWidgetDao(): HostedWidgetDao

    abstract fun panelWidgetDao(): PanelWidgetDao

    abstract fun scenarioDao(): ScenarioDao

    abstract fun activationDao(): ActivationDao

    abstract fun haEventLogDao(): HaEventLogDao

    abstract fun iconOverrideDao(): IconOverrideDao

    abstract fun hiddenAppDao(): HiddenAppDao

    companion object {
        const val NAME = "cue.db"
    }
}
