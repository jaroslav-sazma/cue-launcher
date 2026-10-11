package app.cue.launcher.system

import android.app.Application
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class UserProfiles(application: Application) {

    private val userManager: UserManager = application.getSystemService(UserManager::class.java)

    val own: UserHandle = Process.myUserHandle()

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userOf(userSerial: Long): UserHandle? = userManager.getUserForSerialNumber(userSerial)

    fun isOwn(user: UserHandle): Boolean = user == own

    val ownSerial: Long get() = serialOf(own)

    fun isReadable(user: UserHandle): Boolean =
        !userManager.isQuietModeEnabled(user) && userManager.isUserUnlocked(user)
}
