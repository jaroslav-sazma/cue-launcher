package app.cue.launcher.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

@Composable
fun AppIcon(
    icon: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Size.icon,
) {
    val sized = modifier.size(size)
    if (icon == null) {
        Spacer(modifier = sized)
        return
    }
    val flat = LocalCueColors.current.reducedTransparency || LocalTintedIcons.current
    val shadow = Tokens.Elevation.icon.takeUnless { flat }
    val radius = Tokens.Radius.icon * (size / Tokens.Size.icon)
    Image(
        bitmap = icon,
        contentDescription = contentDescription,
        modifier = if (shadow == null) sized else sized.cueShadow(RoundedCornerShape(radius), shadow),
    )
}
