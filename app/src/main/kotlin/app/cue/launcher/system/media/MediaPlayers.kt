package app.cue.launcher.system.media

import android.app.Application
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.session.MediaController
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import app.cue.launcher.system.InstalledApp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

@SingleIn(AppScope::class)
@Inject
class MediaPlayers(
    private val application: Application,
    private val watch: MediaSessionWatch,
) {

    // Another player's callbacks still arrive, as every reader shares one watch; they end here,
    // before any cover is looked at and before anything reaches the widget.
    fun observe(packageName: String): Flow<MediaWidgetRead> = flow {
        val reader = PlayerReader(application, packageName)
        emitAll(
            watch.sessions
                .map { sessions -> sessions?.let { pickedSession(it.filter { s -> s.packageName == packageName }) } }
                .distinctUntilChanged()
                .conflate()
                .map(reader::read),
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    fun sessionActivity(packageName: String): PendingIntent? =
        watch.current(packageName)
            ?.let { controllers -> pickedSession(controllers) { isPlaying(it.playbackState?.state) } }
            ?.sessionActivity
            ?.takeIf { it.isActivity }

    // Players announce themselves through the service a media browser or a Media3 controller binds
    // to. These are only visible because every launchable package already is, see the manifest.
    suspend fun playerPackages(): Set<String> = withContext(Dispatchers.Default) {
        val packages = application.packageManager
        PLAYER_SERVICE_ACTIONS.flatMapTo(mutableSetOf()) { action ->
            packages.queryIntentServices(Intent(action), PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { it.serviceInfo?.packageName }
        }
    }

    private companion object {
        val PLAYER_SERVICE_ACTIONS = listOf(
            "android.media.browse.MediaBrowserService",
            "androidx.media3.session.MediaSessionService",
            "androidx.media3.session.MediaLibraryService",
        )
    }
}

internal fun playerChoices(installed: List<InstalledApp>, wanted: Set<String>): List<InstalledApp> =
    installed
        .filter { it.component.packageName in wanted }
        .distinctBy { it.component.packageName }

private fun pickedSession(sessions: List<PlayerSession>): PickedSession =
    PickedSession(pickedSession(sessions) { isPlaying(it.state?.state) })

private data class PickedSession(val session: PlayerSession?)

// One reader per collector: it remembers the last cover and the controls handed out, so a state
// change alone produces a value equal to the previous one and the widget does not redraw.
private class PlayerReader(application: Application, packageName: String) {

    private val art = ArtCache(application, packageName)
    private var controls: Pair<MediaController, MediaControls>? = null

    fun read(picked: PickedSession?): MediaWidgetRead {
        val session = picked?.session
        val metadata = session?.metadata
        val title = metadata?.let { firstText(TITLE_KEYS, it::getText) }
        return when {
            picked == null -> MediaWidgetRead.NoAccess
            session == null || metadata == null || title == null -> MediaWidgetRead.Idle
            else -> playing(session, metadata, title)
        }
    }

    private fun playing(session: PlayerSession, metadata: MediaMetadata, title: String): MediaWidgetRead {
        val cover = art.of(metadata)
        return MediaWidgetRead.Playing(
            track = NowPlaying(
                title = title,
                artist = firstText(ARTIST_KEYS, metadata::getText),
                art = cover?.bitmap,
                artLuminance = cover?.luminance,
                buttons = mediaButtonsOf(session.state?.state, session.state?.actions ?: 0L),
            ),
            controls = controlsOf(session.controller),
        )
    }

    private fun controlsOf(controller: MediaController): MediaControls =
        controls?.takeIf { it.first === controller }?.second
            ?: TransportControls(controller.transportControls).also { controls = controller to it }

    private companion object {
        val TITLE_KEYS = listOf(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val ARTIST_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        )
    }
}

private class TransportControls(private val controls: MediaController.TransportControls) : MediaControls {
    override fun play() = controls.play()
    override fun pause() = controls.pause()
    override fun previous() = controls.skipToPrevious()
    override fun next() = controls.skipToNext()
}

private class Cover(val bitmap: Bitmap, val luminance: Double)

// The work on a cover is done once per cover: a state change hands back the same metadata, and a
// track that publishes its metadata again is recognised by its fields and, for a bitmap, by its
// pixels — a placeholder and the real cover usually come at the same size.
private class ArtCache(private val application: Application, private val packageName: String) {

    private var metadata: MediaMetadata? = null
    private var key: List<Any?>? = null
    private var source: Bitmap? = null
    private var cover: Cover? = null

    fun of(metadata: MediaMetadata): Cover? {
        if (metadata !== this.metadata) {
            this.metadata = metadata
            refresh(metadata)
        }
        return cover
    }

    private fun refresh(metadata: MediaMetadata) {
        val bitmap = BITMAP_KEYS.firstNotNullOfOrNull(metadata::getBitmap)
        val uri = if (bitmap == null) URI_KEYS.firstNotNullOfOrNull(metadata::getString) else null
        val description = metadata.description
        val identity = listOf(description.mediaId, description.title, description.subtitle, uri)
        val unchanged = identity == key && (bitmap == null || source?.sameAs(bitmap) == true)
        if (unchanged) return
        key = identity
        source = bitmap
        cover = (bitmap ?: uri?.let(::decode))?.let(::coverOf)
    }

    // The cover is read with the launcher's identity, which may reach further than the player's:
    // no file path at all, and a content URI only when the player could read it itself.
    private fun decode(uri: String): Bitmap? {
        val parsed = Uri.parse(uri)
        val allowed = when (parsed.scheme) {
            ContentResolver.SCHEME_CONTENT -> playerMayRead(parsed)
            ContentResolver.SCHEME_ANDROID_RESOURCE -> true
            else -> false
        }
        if (!allowed) return null
        val source = ImageDecoder.createSource(application.contentResolver, parsed)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_COVER_EDGE) decoder.setTargetSampleSize(longest / MAX_COVER_EDGE)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (failure: IOException) {
            // Only the class: the message carries the cover URI, which can name what is playing.
            Log.w(TAG, "A cover of $packageName cannot be read: ${failure.javaClass.simpleName}")
            null
        } catch (denied: SecurityException) {
            Log.w(TAG, "A cover of $packageName is not shared with the launcher: ${denied.javaClass.simpleName}")
            null
        }
    }

    private fun playerMayRead(uri: Uri): Boolean {
        val uid = try {
            application.packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
        } catch (gone: PackageManager.NameNotFoundException) {
            Log.w(TAG, "$packageName is gone", gone)
            return false
        }
        return application.checkUriPermission(uri, NO_PID, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    // A session's bitmap is bounded only by the player that sent it, and one too large for a canvas
    // would bring down the whole home screen on every frame it is drawn.
    private fun coverOf(bitmap: Bitmap): Cover? {
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            bitmap
        }
        val longest = max(readable.width, readable.height)
        val bounded = if (longest > MAX_COVER_EDGE) {
            readable.scale(readable.width * MAX_COVER_EDGE / longest, readable.height * MAX_COVER_EDGE / longest)
        } else {
            readable
        }
        val sample = bounded.scale(LUMINANCE_SAMPLE, LUMINANCE_SAMPLE)
        val pixels = IntArray(LUMINANCE_SAMPLE * LUMINANCE_SAMPLE)
        sample.getPixels(pixels, 0, LUMINANCE_SAMPLE, 0, 0, LUMINANCE_SAMPLE, LUMINANCE_SAMPLE)
        return averageLuminance(pixels)?.let { Cover(bounded, it) }
    }

    private companion object {
        const val LUMINANCE_SAMPLE = 24
        // About the widget's width on a dense screen; past that the cover only costs memory.
        const val MAX_COVER_EDGE = 1024
        const val NO_PID = -1

        val BITMAP_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )
        val URI_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
    }
}

private const val TAG = "MediaPlayers"
