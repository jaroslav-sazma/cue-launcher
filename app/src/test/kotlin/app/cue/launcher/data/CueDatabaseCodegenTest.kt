package app.cue.launcher.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CueDatabaseCodegenTest {

    @Test
    fun `Room generated the database and DAO implementation`() {
        val database = Class.forName("app.cue.launcher.data.CueDatabase_Impl")
        assertTrue(CueDatabase::class.java.isAssignableFrom(database))

        Class.forName("app.cue.launcher.data.layout.LauncherLayoutDao_Impl")
        Class.forName("app.cue.launcher.data.layout.FolderMemberDao_Impl")
        Class.forName("app.cue.launcher.data.launchlog.AppLaunchDao_Impl")
        Class.forName("app.cue.launcher.data.widgets.HostedWidgetDao_Impl")
        Class.forName("app.cue.launcher.data.scenario.ScenarioDao_Impl")
        Class.forName("app.cue.launcher.data.scenario.ActivationDao_Impl")
        Class.forName("app.cue.launcher.data.ha.HaEventLogDao_Impl")
        Class.forName("app.cue.launcher.data.icons.IconOverrideDao_Impl")
    }

    // Every step from the squashed base up has to be there, or an install one version behind has
    // nothing to migrate through and Room refuses to open the database at all. Derived from the
    // version so that bumping it without adding the migration fails here rather than on a device.
    @Test
    fun `Room generated an automatic migration for every version step`() {
        (SQUASHED_VERSION until CUE_DATABASE_VERSION).forEach { from ->
            Class.forName("app.cue.launcher.data.CueDatabase_AutoMigration_${from}_${from + 1}_Impl")
        }
    }

    // A schema that changed without the version changing with it is the other half of the same trap:
    // Room compares the hash it was compiled with against the one in the database and refuses to open
    // it. The exported file of every version from the base up has to exist, and — this part only a
    // reviewer can see — one that is already committed is never edited again.
    @Test
    fun `every version has an exported schema`() {
        (SQUASHED_VERSION..CUE_DATABASE_VERSION).forEach { exported ->
            val schema = File(SCHEMAS, "$exported.json")
            assertTrue(schema.isFile) { "the schema of version $exported is not in the repository" }
        }
    }

    private companion object {

        // Release installs sit on this version, so it stays the floor every later migration starts at.
        const val SQUASHED_VERSION = 17

        val SCHEMAS: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/schemas/app.cue.launcher.data.CueDatabase") }
            .first(File::isDirectory)
    }
}
