package app.cue.launcher.engine

const val MEDIA_GRACE_MILLIS: Long = 60_000L

enum class MediaPlayback {

    PLAYING,

    PAUSED,

    IDLE,

    UNKNOWN,
}

data class MediaSessions(
    val playback: MediaPlayback = MediaPlayback.UNKNOWN,

    val pausedForMillis: Long? = null,
)

// The apps that count as playing right now, a pause still within its grace period included.
data class MediaActivity(val playing: Set<String> = emptySet()) {

    fun isPlaying(packages: List<String>): Boolean =
        if (packages.isEmpty()) playing.isNotEmpty() else packages.any(playing::contains)
}

fun mediaActivityOf(sessions: Map<String, MediaSessions>): MediaActivity =
    MediaActivity(sessions.filterValues { mediaPlayingWithGrace(it) == true }.keys)

fun mediaGraceWaitMillis(sessions: Map<String, MediaSessions>): Long? =
    sessions.values.mapNotNull(::mediaGraceWaitMillis).minOrNull()

fun Map<String, MediaSessions>.agedBy(millis: Long): Map<String, MediaSessions> =
    mapValues { (_, sessions) -> sessions.agedBy(millis) }

fun MediaSessions.agedBy(millis: Long): MediaSessions =
    copy(pausedForMillis = pausedForMillis?.plus(millis))

fun mediaPlayingWithGrace(sessions: MediaSessions): Boolean? = when (sessions.playback) {
    MediaPlayback.PLAYING -> true
    MediaPlayback.PAUSED -> mediaGraceWaitMillis(sessions) != null
    MediaPlayback.IDLE -> false
    MediaPlayback.UNKNOWN -> null
}

fun mediaGraceWaitMillis(sessions: MediaSessions): Long? = sessions.pausedForMillis
    ?.takeIf { sessions.playback == MediaPlayback.PAUSED }
    ?.let { paused -> (MEDIA_GRACE_MILLIS - paused).takeIf { it > 0 } }
