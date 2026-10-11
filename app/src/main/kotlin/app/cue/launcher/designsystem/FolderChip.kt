package app.cue.launcher.designsystem

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun FolderChip(
    fieldCount: Int,
    plusCount: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    field: @Composable (Int) -> Unit,
) {
    val colors = LocalCueColors.current
    TileSurface(
        modifier = modifier
            .size(Tokens.Size.icon)
            .clip(RoundedCornerShape(Tokens.Radius.icon))
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        radius = Tokens.Radius.icon,
        contentPadding = Tokens.Folder.chipPadding,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Tokens.Folder.fieldGap),
        ) {
            val rows = FOLDER_CHIP_ROWS
            val columns = FOLDER_CHIP_COLUMNS
            repeat(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Folder.fieldGap),
                ) {
                    repeat(columns) { column ->
                        val index = row * columns + column
                        val last = index == rows * columns - 1
                        FolderChipField(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when {
                                plusCount > 0 && last -> Text(
                                    text = "+$plusCount",
                                    color = colors.textPrimary,
                                    style = Tokens.Type.stackPlus.toTextStyle(),
                                )

                                index < fieldCount -> field(index)

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChipField(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TileSurface(
        modifier = modifier,
        level = GlassLevel.THICK,
        radius = Tokens.Folder.fieldRadius,
        contentPadding = 0.dp,
        shadow = null,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private const val FOLDER_CHIP_ROWS = 2
private const val FOLDER_CHIP_COLUMNS = 2
