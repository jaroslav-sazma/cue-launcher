package app.cue.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cue.launcher.R
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.system.InstalledApp
import kotlinx.coroutines.launch

@Composable
internal fun MediaPlayerPicker(access: WidgetSettingsAccess, onPicked: () -> Unit) {
    val feed = LocalMediaFeed.current
    val scope = rememberCoroutineScope()
    val stored by access.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val players by produceState<List<InstalledApp>?>(initialValue = null, feed) { value = feed.players() }
    val shown = players
    val chosen = stored?.mediaWidget?.app
    when {
        shown == null || chosen == null -> Unit
        shown.isEmpty() -> Text(
            text = stringResource(R.string.widget_settings_media_player_none),
            color = LocalCueColors.current.textSecondary,
            style = Tokens.Type.tileBody.toTextStyle(),
        )

        else -> PlayerList(
            players = shown,
            chosen = chosen,
            // Back only once the write is through: leaving first would cancel it with the page.
            onPick = { packageName ->
                scope.launch {
                    access.settings.setMediaWidgetApp(packageName)
                    onPicked()
                }
            },
        )
    }
}

@Composable
private fun PlayerList(players: List<InstalledApp>, chosen: String, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(max = PLAYER_LIST_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Tokens.WidgetSettings.rowGap),
    ) {
        players.forEach { player ->
            val packageName = player.component.packageName
            WidgetSettingRow(
                label = player.label,
                detail = null,
                glyph = if (packageName == chosen) GLYPH_SELECTED else GLYPH_UNSELECTED,
                trailingGlyph = null,
                onClick = { onPick(packageName) },
            )
        }
    }
}

private val PLAYER_LIST_MAX_HEIGHT = Tokens.Grid.RowHeight.static * 4

private const val GLYPH_SELECTED = "radiobox-marked"
private const val GLYPH_UNSELECTED = "radiobox-blank"
