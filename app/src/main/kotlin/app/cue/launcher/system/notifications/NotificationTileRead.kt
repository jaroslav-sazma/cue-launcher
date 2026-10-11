package app.cue.launcher.system.notifications

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.util.Log
import app.cue.launcher.data.scenario.SemanticStyle
import app.cue.launcher.data.scenario.TileProgress
import app.cue.launcher.data.scenario.TileProgressPoint
import app.cue.launcher.data.scenario.TileProgressSegment
import app.cue.launcher.engine.PostedNotification
import android.text.Annotation as TextAnnotation

fun PostedNotification.withTileData(
    context: Context,
    sbn: StatusBarNotification,
): PostedNotification = copy(
    progress = tileProgressOf(context, sbn.notification),
    semantic = semanticStyleOf(sbn.notification),
    reply = notificationReplyOf(sbn.key, sbn.notification),
)

// recoverBuilder creates a context for the posting app, by far the dearest step in reading a post,
// and only a ProgressStyle needs it; the template name in the extras says which style was used.
private fun tileProgressOf(context: Context, notification: Notification): TileProgress? {
    val progressStyle = notification.extras.getString(Notification.EXTRA_TEMPLATE) == PROGRESS_TEMPLATE
    val style = if (progressStyle) recoveredStyle(context, notification) else null
    return (style as? Notification.ProgressStyle)?.toTileProgress() ?: notification.extras.plainProgress()
}

private val PROGRESS_TEMPLATE: String = Notification.ProgressStyle::class.java.name

private fun recoveredStyle(context: Context, notification: Notification): Notification.Style? =
    runCatching { Notification.Builder.recoverBuilder(context, notification).style }
        .onFailure { Log.w(NOTIFICATION_LISTENER_TAG, "The notification style cannot be read", it) }
        .getOrNull()

private fun Notification.ProgressStyle.toTileProgress(): TileProgress = TileProgress(
    value = progress,
    max = progressMax,
    indeterminate = isProgressIndeterminate,
    segments = progressSegments.map { TileProgressSegment(it.length, it.semanticStyle()) },
    points = progressPoints.map { TileProgressPoint(it.position, it.semanticStyle()) },
)

private fun Bundle.plainProgress(): TileProgress? {
    val max = getInt(Notification.EXTRA_PROGRESS_MAX, 0)
    val indeterminate = getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    if (max <= 0 && !indeterminate) return null
    return TileProgress(
        value = getInt(Notification.EXTRA_PROGRESS, 0),
        max = max,
        indeterminate = indeterminate,
    )
}

private fun semanticStyleOf(notification: Notification): SemanticStyle {
    if (SEMANTIC_ANNOTATIONS.isEmpty()) return SemanticStyle.UNSPECIFIED
    val extras = notification.extras
    return SEMANTIC_TEXT_KEYS
        .mapNotNull { extras.getCharSequence(it) }
        .firstNotNullOfOrNull { it.semanticStyle() }
        ?: SemanticStyle.UNSPECIFIED
}

private fun CharSequence.semanticStyle(): SemanticStyle? {
    val spanned = this as? Spanned ?: return null
    return spanned.getSpans(0, spanned.length, TextAnnotation::class.java)
        .firstNotNullOfOrNull { SEMANTIC_ANNOTATIONS[it.key to it.value] }
}

private fun Notification.ProgressStyle.Segment.semanticStyle(): SemanticStyle =
    semanticStyleOf(semanticStyle)

private fun Notification.ProgressStyle.Point.semanticStyle(): SemanticStyle =
    semanticStyleOf(semanticStyle)

private fun semanticStyleOf(code: Int): SemanticStyle = SEMANTIC_STYLE_CODES[code] ?: SemanticStyle.UNSPECIFIED

private val SEMANTIC_STYLE_CODES: Map<Int, SemanticStyle> = mapOf(
    Notification.SEMANTIC_STYLE_INFO to SemanticStyle.INFO,
    Notification.SEMANTIC_STYLE_SAFE to SemanticStyle.SAFE,
    Notification.SEMANTIC_STYLE_CAUTION to SemanticStyle.CAUTION,
    Notification.SEMANTIC_STYLE_DANGER to SemanticStyle.DANGER,
)

private val SEMANTIC_ANNOTATIONS: Map<Pair<String, String>, SemanticStyle> =
    SEMANTIC_STYLE_CODES.entries.associate { (code, style) ->
        val annotation = Notification.createSemanticStyleAnnotation(code)
        (annotation.key to annotation.value) to style
    }

private val SEMANTIC_TEXT_KEYS = listOf(
    Notification.EXTRA_TITLE,
    Notification.EXTRA_TITLE_BIG,
    Notification.EXTRA_TEXT,
    Notification.EXTRA_BIG_TEXT,
)
