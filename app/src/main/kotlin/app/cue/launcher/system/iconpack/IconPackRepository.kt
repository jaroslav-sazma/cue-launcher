package app.cue.launcher.system.iconpack

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.settings.SettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class IconPackRepository(
    private val application: Application,
    private val settings: SettingsRepository,
) {

    private val packageManager: PackageManager = application.packageManager

    private var selected: String? = null

    private var loaded: LoadedPack? = null

    private val _revision = MutableStateFlow(0)

    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.settings
                .map { it.appearance.iconPack }
                .distinctUntilChanged()
                .collect(::select)
        }
    }

    fun icon(component: ComponentRef, densityDpi: Int): Drawable? {
        val pack = activePack() ?: return null
        return pack.filter.drawableFor(component.packageName, component.className)
            ?.let { pack.drawable(it, densityDpi) }
    }

    suspend fun firstMapped(candidates: List<ComponentRef>): ComponentRef? =
        withContext(Dispatchers.Default) {
            val pack = activePack() ?: return@withContext null
            candidates.firstOrNull { pack.filter.drawableFor(it.packageName, it.className) != null }
        }

    val installedPacks: Flow<List<IconPack>> = packageChanges()
        .map { queryPacks() }
        .flowOn(Dispatchers.Default)
        .conflate()

    private fun queryPacks(): List<IconPack> =
        PACK_INTENTS
            .flatMap { packageManager.queryIntentActivities(Intent(it), RESOLVE_FLAGS) }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPack(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }

    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        application.registerReceiver(receiver, PACKAGE_CHANGES, Context.RECEIVER_NOT_EXPORTED)
        send(Unit)
        awaitClose { application.unregisterReceiver(receiver) }
    }

    @Synchronized
    fun invalidatePackage(packageName: String): Boolean {
        val active = packageName == selected
        if (active) {
            loaded = null
            _revision.value += 1
        }
        return active
    }

    private val selectedOnce = CompletableDeferred<Unit>()

    suspend fun awaitSelected() = selectedOnce.await()

    @Synchronized
    private fun select(packageName: String?) {
        if (packageName != selected) {
            selected = packageName
            loaded = null
            _revision.value += 1
        }
        selectedOnce.complete(Unit)
    }

    @Synchronized
    private fun activePack(): LoadedPack? {
        val packageName = selected ?: return null
        return loaded?.takeIf { it.packageName == packageName }
            ?: load(packageName).also { loaded = it }
    }

    private fun load(packageName: String): LoadedPack {
        val resources = packResources(packageName)
        val filter = if (resources == null) AppFilter.EMPTY else readAppFilter(packageName, resources)
        Log.i(TAG, "Icon pack $packageName: ${filter.size} mappings")
        return LoadedPack(packageName, resources, filter)
    }

    private fun packResources(packageName: String): Resources? = try {
        packageManager.getResourcesForApplication(packageName)
    } catch (missing: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Icon pack $packageName is not installed", missing)
        null
    }

    private fun readAppFilter(packageName: String, resources: Resources): AppFilter = try {
        val xmlId = resources.getIdentifier(APP_FILTER, "xml", packageName)
        if (xmlId != 0) {
            resources.getXml(xmlId).use { appFilterOf(it.items()) }
        } else {
            resources.assets.open("$APP_FILTER.xml").use { stream ->
                appFilterOf(Xml.newPullParser().apply { setInput(stream, null) }.items())
            }
        }
    } catch (broken: XmlPullParserException) {
        Log.w(TAG, "appfilter.xml in $packageName cannot be read", broken)
        AppFilter.EMPTY
    } catch (unreadable: IOException) {
        Log.w(TAG, "appfilter.xml is missing in $packageName", unreadable)
        AppFilter.EMPTY
    } catch (missing: Resources.NotFoundException) {
        Log.w(TAG, "appfilter.xml is missing in $packageName", missing)
        AppFilter.EMPTY
    }

    private class LoadedPack(
        val packageName: String,
        private val resources: Resources?,
        val filter: AppFilter,
    ) {

        fun drawable(name: String, densityDpi: Int): Drawable? {
            val resources = resources ?: return null
            val id = resources.getIdentifier(name, "drawable", packageName)
            return if (id == 0) null else drawableForDensity(resources, id, densityDpi)
        }

        private fun drawableForDensity(resources: Resources, id: Int, densityDpi: Int): Drawable? =
            try {
                resources.getDrawableForDensity(id, densityDpi, null)
            } catch (missing: Resources.NotFoundException) {
                Log.w(TAG, "Drawable $id is missing in $packageName", missing)
                null
            }
    }

    private companion object {
        const val TAG = "IconPacks"
        const val APP_FILTER = "appfilter"

        val RESOLVE_FLAGS: PackageManager.ResolveInfoFlags = PackageManager.ResolveInfoFlags.of(0)

        // Without the package data scheme the filter matches nothing and no broadcast ever arrives.
        val PACKAGE_CHANGES = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        val PACK_INTENTS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "app.lawnchair.icons.THEMED_ICON",
        )
    }
}

data class IconPack(val packageName: String, val label: String)

private fun XmlPullParser.items(): Sequence<Pair<String?, String?>> = sequence {
    var event = eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && name == "item") {
            yield(getAttributeValue(null, "component") to getAttributeValue(null, "drawable"))
        }
        event = next()
    }
}
