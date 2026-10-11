package app.cue.launcher.feature.home

import androidx.annotation.StringRes
import app.cue.launcher.R

// The order is the on-screen order: the panel sits left of the home screen.
enum class LauncherPage(@StringRes val labelRes: Int) {
    PANEL(R.string.page_panel),
    HOME(R.string.page_home),
    ;

    val index: Int get() = ordinal
}

// 1 is the panel, which is the first page, so the progress counts down as the pager scrolls right.
fun panelProgress(page: Int, pageOffsetFraction: Float, pageCount: Int): Float {
    if (pageCount <= 1) return 0f
    return (1f - (page + pageOffsetFraction) / (pageCount - 1)).coerceIn(0f, 1f)
}

fun panelInView(panelProgress: Float): Boolean = panelProgress > 0f
