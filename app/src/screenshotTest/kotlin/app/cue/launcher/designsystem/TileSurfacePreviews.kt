package app.cue.launcher.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest

class TileSurfacePreviews {

    @PreviewTest
    @Preview(name = "tile-light-wallpaper", widthDp = 200, heightDp = 160)
    @Composable
    fun tileSurfaceOverLightWallpaper() {
        Frame(wallpaperLuminance = 0.85, backdrop = Color(0xFFE8E0D5))
    }

    @PreviewTest
    @Preview(name = "tile-dark-wallpaper", widthDp = 200, heightDp = 160)
    @Composable
    fun tileSurfaceOverDarkWallpaper() {
        Frame(wallpaperLuminance = 0.08, backdrop = Color(0xFF13181C))
    }

    @Composable
    private fun Frame(wallpaperLuminance: Double, backdrop: Color) {
        CueTheme(wallpaperLuminance = wallpaperLuminance, isDark = wallpaperLuminance < 0.5) {
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 160.dp)
                    .background(backdrop),
            ) {
                TileSurface(
                    modifier = Modifier
                        .padding(Tokens.Grid.gutter)
                        .size(width = 140.dp, height = 100.dp),
                ) {
                    Text(text = "Cue:", color = LocalCueColors.current.textPrimary)
                }
            }
        }
    }
}
