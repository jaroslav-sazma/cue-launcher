package app.cue.launcher.system.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import android.widget.RemoteViews
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.UserProfiles
import app.cue.launcher.system.toComponentName
import app.cue.launcher.system.toComponentRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class WidgetCatalog(private val application: Application, private val profiles: UserProfiles) {

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(application)

    private val launcherApps: LauncherApps = application.getSystemService(LauncherApps::class.java)

    private val densityDpi: Int = application.resources.displayMetrics.densityDpi

    private val order = availableWidgetOrder()

    suspend fun availableWidgets(): List<AvailableWidget> = withContext(Dispatchers.Default) {
        launcherApps.profiles
            .flatMap { user -> providersFor(user).map { info -> info to profiles.serialOf(user) } }
            .filter { (info, _) -> isPickable(info) }
            .map { (info, serial) -> info.toAvailableWidget(serial) }
            .sortedWith(order)
    }

    suspend fun preview(widget: AvailableWidget): WidgetPreview? = withContext(Dispatchers.IO) {
        val info = providerInfo(widget.provider) ?: return@withContext null
        val views = generatedPreview(widget.provider)
            ?: info.previewLayout.takeIf { it != 0 }
                ?.let { layout -> RemoteViews(widget.provider.packageName, layout) }
        val image: Drawable? = info.loadPreviewImage(application, densityDpi)
            ?: info.loadIcon(application, densityDpi)
        if (views == null && image == null) null else WidgetPreview(views, image)
    }

    suspend fun labelOf(appWidgetId: Int): String? = withContext(Dispatchers.Default) {
        appWidgetManager.getAppWidgetInfo(appWidgetId)?.loadLabel(application.packageManager)
    }

    private fun generatedPreview(provider: ComponentRef): RemoteViews? {
        val user = profiles.userOf(provider.userSerial) ?: return null
        return runCatching {
            appWidgetManager.getWidgetPreview(
                provider.toComponentName(),
                user,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            )
        }.onFailure { failure ->
            Log.w(TAG, "Could not obtain the preview of $provider", failure)
        }.getOrNull()
    }

    private fun providersFor(user: UserHandle): List<AppWidgetProviderInfo> =
        runCatching { appWidgetManager.getInstalledProvidersForProfile(user) }
            .onFailure { failure -> Log.w(TAG, "Widgets of profile $user cannot be read", failure) }
            .getOrDefault(emptyList())

    private fun providerInfo(provider: ComponentRef): AppWidgetProviderInfo? {
        val user = profiles.userOf(provider.userSerial) ?: return null
        return runCatching {
            appWidgetManager.getInstalledProvidersForPackage(provider.packageName, user)
        }.getOrDefault(emptyList())
            .firstOrNull { info -> info.provider == provider.toComponentName() }
    }

    private fun isPickable(info: AppWidgetProviderInfo): Boolean {
        val homeScreen = info.widgetCategory and
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
        val hidden = info.widgetFeatures and
            AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER != 0
        return homeScreen && !hidden
    }

    private fun AppWidgetProviderInfo.toAvailableWidget(userSerial: Long): AvailableWidget {
        val packageManager = application.packageManager
        return AvailableWidget(
            provider = provider.toComponentRef(userSerial),
            label = loadLabel(packageManager),
            appLabel = activityInfo.applicationInfo.loadLabel(packageManager).toString(),
            needsConfiguration = configure != null,
            size = toSizeSpec(),
        )
    }

    private companion object {
        const val TAG = "WidgetHost"
    }
}

data class WidgetPreview(val remoteViews: RemoteViews?, val image: Drawable?)
