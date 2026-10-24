package app.cue.launcher.system

import android.app.Application
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import app.cue.launcher.data.scenario.AppProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class UserProfiles(application: Application) {

    private val userManager: UserManager = application.getSystemService(UserManager::class.java)

    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)

    val own: UserHandle = Process.myUserHandle()

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userOf(userSerial: Long): UserHandle? = userManager.getUserForSerialNumber(userSerial)

    fun isOwn(user: UserHandle): Boolean = user == own

    val ownSerial: Long = serialOf(own)

    fun isReadable(user: UserHandle): Boolean =
        !userManager.isQuietModeEnabled(user) && userManager.isUserUnlocked(user)

    // Told by its type, not by being another profile: some phones show a clone profile to the
    // launcher as well, and an action saved for the work profile must never start there.
    fun isWork(user: UserHandle): Boolean =
        launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED

    // The profiles whose apps an action can name: the launcher's own, and the work profile.
    fun actionUsers(): List<UserHandle> = launcherApps.profiles.filter { isOwn(it) || isWork(it) }

    // null is both the launcher's own profile and one an action cannot name, and an action reads null
    // as its own: only a user of actionUsers() may be asked.
    fun appProfileOf(user: UserHandle): AppProfile? = if (!isOwn(user) && isWork(user)) AppProfile.WORK else null

    fun userFor(profile: AppProfile?): UserHandle? = when (profile) {
        null -> own
        AppProfile.WORK -> launcherApps.profiles.firstOrNull(::isWork)
    }
}
