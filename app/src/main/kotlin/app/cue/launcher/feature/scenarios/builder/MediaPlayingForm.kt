package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.LocalMediaFeed
import app.cue.launcher.system.InstalledApp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPlayingForm(
    condition: Condition.MediaPlaying,
    onChange: (Condition.MediaPlaying) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed = LocalMediaFeed.current
    val players by produceState<List<InstalledApp>?>(initialValue = null, feed) {
        value = feed.players(include = condition.packages.toSet())
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_media_which))
        val rows = players?.let { loaded -> mediaAppRows(loaded, condition.packages) } ?: return@Column
        if (rows.isEmpty()) {
            Note(text = stringResource(R.string.builder_media_no_players))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Settings.rowMargin),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                rows.forEachIndexed { index, row ->
                    val checked = row.packageName in condition.packages
                    SegmentedListItem(
                        checked = checked,
                        onCheckedChange = { on ->
                            val packages = toggledPackages(condition.packages, row.packageName, on)
                            onChange(condition.copy(packages = packages))
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        supportingContent = if (row.installed) {
                            null
                        } else {
                            { Text(text = stringResource(R.string.builder_media_not_installed)) }
                        },
                        content = { Text(text = row.label) },
                    )
                }
            }
        }
        Note(text = stringResource(R.string.builder_media_any))
    }
}

internal data class MediaAppRow(val packageName: String, val label: String, val installed: Boolean)

// A ticked app that is not installed — imported, or removed since — still gets its row: hidden, it
// would leave a condition that never holds and that nothing on screen explains or lets you undo.
internal fun mediaAppRows(players: List<InstalledApp>, ticked: List<String>): List<MediaAppRow> {
    val offered = players.map { MediaAppRow(it.component.packageName, it.label, installed = true) }
    val known = offered.mapTo(mutableSetOf(), MediaAppRow::packageName)
    return offered + ticked.filterNot(known::contains).map { MediaAppRow(it, it, installed = false) }
}

internal fun toggledPackages(packages: List<String>, packageName: String, on: Boolean): List<String> =
    if (on) (packages + packageName).distinct() else packages - packageName
