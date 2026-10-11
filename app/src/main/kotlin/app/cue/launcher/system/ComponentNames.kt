package app.cue.launcher.system

import android.content.ComponentName
import app.cue.launcher.data.layout.ComponentRef

fun ComponentName.toComponentRef(userSerial: Long): ComponentRef =
    ComponentRef(packageName = packageName, className = className, userSerial = userSerial)

fun ComponentRef.toComponentName(): ComponentName =
    ComponentName(packageName, className)

val COMPONENT_REF_ORDER: Comparator<ComponentRef> =
    compareBy<ComponentRef> { it.packageName }.thenBy { it.className }.thenBy { it.userSerial }
