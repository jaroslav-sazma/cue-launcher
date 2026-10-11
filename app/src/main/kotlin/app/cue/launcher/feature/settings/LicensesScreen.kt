package app.cue.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.designsystem.DetailPaneScaffold
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle

@Composable
internal fun LicensesScreen(
    onOpen: (LicenseNotice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailPaneScaffold(
        title = stringResource(R.string.licenses_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    modifier = Modifier.padding(
                        horizontal = Tokens.Settings.screenPadding,
                        vertical = Tokens.Settings.textSpacing,
                    ),
                    style = supportingStyle,
                    color = supportingInk(),
                )
            }
            items(items = LicenseNotice.entries, key = { it.name }) { notice ->
                SettingsLinkRow(
                    label = stringResource(notice.title),
                    detail = stringResource(notice.covers),
                    onClick = { onOpen(notice) },
                )
            }
        }
    }
}

// The name comes from a saved back stack, which can outlive a notice renamed in a later build.
@Composable
internal fun LicenseTextScreen(name: String, onBack: () -> Unit, onUnknown: () -> Unit) {
    val notice = LicenseNotice.entries.firstOrNull { it.name == name }
    if (notice != null) {
        LicenseTextScreen(notice = notice, onBack = onBack)
    } else {
        LaunchedEffect(name) { onUnknown() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LicenseTextScreen(
    notice: LicenseNotice,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assets = LocalContext.current.assets
    val paragraphs by produceState<LicenseText>(LicenseText.Loading, notice, assets) {
        value = readLicenseNotice(assets, notice)?.let(LicenseText::Read) ?: LicenseText.Missing
    }
    DetailPaneScaffold(
        title = stringResource(notice.title),
        subtitle = stringResource(notice.covers),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val text = paragraphs) {
            LicenseText.Loading -> LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(Tokens.Settings.screenPadding),
            )
            LicenseText.Missing -> Text(
                text = stringResource(R.string.licenses_missing),
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                style = supportingStyle,
                color = supportingInk(),
            )
            is LicenseText.Read -> SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = Tokens.Settings.screenPadding,
                        vertical = Tokens.Settings.textSpacing,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap),
                ) {
                    items(items = text.paragraphs) { paragraph ->
                        Text(text = paragraph, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private sealed interface LicenseText {
    data object Loading : LicenseText
    data object Missing : LicenseText
    data class Read(val paragraphs: List<String>) : LicenseText
}
