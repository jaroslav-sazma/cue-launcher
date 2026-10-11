package app.cue.launcher.feature.drawer

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.HomeRoleStatus

// Launched from its own drawer, the default home would only come back to the screen the drawer is
// opened from, so there its icon opens the settings. Everywhere else — another launcher, the store,
// a shortcut on the home screen — and while Cue: is only being tried out, it is launched as usual.
// The profile counts: Cue: in a work profile is another installation.
internal fun opensLauncherSettings(
    component: ComponentRef,
    ownPackage: String,
    ownSerial: Long,
    roleStatus: HomeRoleStatus,
): Boolean = roleStatus == HomeRoleStatus.HELD &&
    component.packageName == ownPackage &&
    component.userSerial == ownSerial
