package app.cue.launcher.feature.scenarios

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.cue.launcher.BuildConfig
import app.cue.launcher.R
import app.cue.launcher.designsystem.ModalSheet
import app.cue.launcher.designsystem.SheetConfirmButton
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetNote
import app.cue.launcher.designsystem.SheetSecondaryButton
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberExpandedSheetState
import app.cue.launcher.designsystem.rememberSheetExit
import app.cue.launcher.system.browserPackage
import app.cue.launcher.system.openWebPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(onLicenses: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val source = remember(context) { installSource(context) }
    // The report goes to the default browser by name: the GitHub app claims github.com links and
    // may not fill the issue form from the query. It is resolved while the sheet is open, because
    // the sheet's own scope ends as the sheet closes.
    val browser by produceState<String?>(initialValue = null, context) {
        value = context.packageManager.browserPackage()
    }
    val sheetState = rememberExpandedSheetState()
    val leave = rememberSheetExit(sheetState, onDismiss)
    ModalSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetFrame(
            header = { SheetHeader(title = stringResource(R.string.app_name), onClose = { leave {} }) },
            footer = {
                SheetConfirmButton(
                    label = stringResource(R.string.about_report_bug),
                    onClick = {
                        val url = bugReportUrl(bugReportFacts(source))
                        val pinned = browser
                        leave { context.openWebPage(url, pinned) }
                    },
                )
                SheetSecondaryButton(
                    label = stringResource(R.string.about_licenses),
                    onClick = { leave(onLicenses) },
                )
            },
        ) {
            Text(
                text = versionLine(source),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Settings.screenPadding),
                style = MaterialTheme.typography.bodyLarge,
            )
            SheetNote(text = stringResource(R.string.about_license))
        }
    }
}

@Composable
private fun versionLine(source: InstallSource): String {
    val name = BuildConfig.VERSION_NAME
    val code = BuildConfig.VERSION_CODE
    val label = installSourceLabel(source)
    return if (label == null) {
        stringResource(R.string.about_version, name, code)
    } else {
        stringResource(R.string.about_version_from, name, code, stringResource(label))
    }
}

private fun installSource(context: Context): InstallSource = installSource(
    installer = context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName,
    debug = BuildConfig.DEBUG,
)

// A debug build's version name already ends in -debug, so the line names no source for it.
@StringRes
private fun installSourceLabel(source: InstallSource): Int? = when (source) {
    InstallSource.GOOGLE_PLAY -> R.string.about_source_play
    InstallSource.GITHUB -> R.string.about_source_github
    InstallSource.DEBUG_BUILD -> null
}

// Settings shows Build.DISPLAY as the build number, and RELEASE_OR_PREVIEW_DISPLAY as the version.
private fun bugReportFacts(source: InstallSource) = BugReportFacts(
    device = deviceName(Build.MANUFACTURER, Build.MODEL),
    android = androidBuild(Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY, Build.DISPLAY),
    version = appVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, source),
)
