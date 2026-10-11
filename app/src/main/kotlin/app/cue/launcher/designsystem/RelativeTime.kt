package app.cue.launcher.designsystem

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// "5 minutes ago", "yesterday 19:02" — the platform phrasing, so it follows the system language.
@Composable
fun relativeTimeText(millis: Long): String {
    val context = LocalContext.current
    return DateUtils.getRelativeDateTimeString(
        context,
        millis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.WEEK_IN_MILLIS,
        0,
    ).toString()
}
