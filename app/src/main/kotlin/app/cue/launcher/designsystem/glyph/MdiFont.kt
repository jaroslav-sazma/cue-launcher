package app.cue.launcher.designsystem.glyph

import android.content.res.AssetManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.cue.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

val MdiFontFamily: FontFamily = FontFamily(Font(R.font.mdi))

val LocalMdiGlyphs: ProvidableCompositionLocal<MdiGlyphs> = compositionLocalOf { MdiGlyphs.EMPTY }

@Composable
fun rememberMdiGlyphs(): MdiGlyphs {
    val assets = LocalContext.current.assets
    val glyphs by produceState(MdiGlyphs.EMPTY, assets) { value = loadMdiGlyphs(assets) }
    return glyphs
}

suspend fun loadMdiGlyphs(assets: AssetManager): MdiGlyphs = withContext(Dispatchers.IO) {
    try {
        assets.open(MDI_INDEX_ASSET).bufferedReader().useLines(::parseMdiGlyphs)
    } catch (failure: IOException) {
        Log.w(TAG, "The glyph index $MDI_INDEX_ASSET cannot be read", failure)
        MdiGlyphs.EMPTY
    }
}

const val MDI_INDEX_ASSET = "mdi_glyphs.txt"

private const val TAG = "MdiGlyphs"
