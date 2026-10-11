package app.cue.launcher.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class HomeRoleStatus {

    HELD,

    REQUESTABLE,

    UNAVAILABLE,
}

fun homeRoleStatus(isRoleAvailable: Boolean, isRoleHeld: Boolean): HomeRoleStatus = when {
    isRoleHeld -> HomeRoleStatus.HELD
    isRoleAvailable -> HomeRoleStatus.REQUESTABLE
    else -> HomeRoleStatus.UNAVAILABLE
}

fun homeRoleOffered(status: HomeRoleStatus): Boolean = status != HomeRoleStatus.HELD

object HomeRole {

    fun status(context: Context): HomeRoleStatus {
        val roleManager = context.getSystemService(RoleManager::class.java)
            ?: return HomeRoleStatus.UNAVAILABLE
        return homeRoleStatus(
            isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_HOME),
            isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_HOME),
        )
    }

    fun requestIntent(context: Context): Intent {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }
}
