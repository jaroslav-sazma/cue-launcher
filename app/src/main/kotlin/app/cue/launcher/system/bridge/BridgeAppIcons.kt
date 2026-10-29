package app.cue.launcher.system.bridge

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.ByteArrayOutputStream

internal class AppIcon(val hash: String, val png: ByteArray)

internal class AppFace(val label: String, val icon: AppIcon?)

// The Mac caches icons by hash and replaces a mirror whenever its hash changes, so the same app has
// to give the same bytes on every read: the icon is rendered once per package and app version and
// kept. An app this launcher cannot see shows under its package name, without an icon.
@SingleIn(AppScope::class)
@Inject
class BridgeAppIcons(private val application: Application) {

    private class Cached(val updatedAt: Long, val face: AppFace)

    private val cache = object : LinkedHashMap<String, Cached>(MAX_CACHED, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > MAX_CACHED
    }

    @Synchronized
    internal fun face(packageName: String): AppFace {
        val info = try {
            application.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val cached = cache[packageName]?.takeIf { it.updatedAt == info?.lastUpdateTime }?.face
        return when {
            info == null -> AppFace(packageName, null)
            cached != null -> cached
            else -> faceOf(packageName, info).also { cache[packageName] = Cached(info.lastUpdateTime, it) }
        }
    }

    private fun faceOf(packageName: String, info: PackageInfo): AppFace {
        val manager = application.packageManager
        val app = info.applicationInfo ?: return AppFace(packageName, null)
        val label = app.loadLabel(manager).toString().ifBlank { packageName }
        val png = renderPng(manager.getApplicationIcon(app).toBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888))
        return AppFace(label, AppIcon(sha256(png).toHexString(), png))
    }

    private fun renderPng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        out.toByteArray()
    }

    private companion object {
        const val ICON_PX = 192
        const val PNG_QUALITY = 100
        const val MAX_CACHED = 64
        const val LOAD_FACTOR = 0.75f
    }
}
