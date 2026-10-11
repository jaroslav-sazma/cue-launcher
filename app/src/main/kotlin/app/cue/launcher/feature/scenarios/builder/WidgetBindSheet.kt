package app.cue.launcher.feature.scenarios.builder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.widget.AvailableWidget
import app.cue.launcher.designsystem.SheetFrame
import app.cue.launcher.designsystem.SheetHeader
import app.cue.launcher.designsystem.SheetNote
import app.cue.launcher.designsystem.SheetConfirmButton
import app.cue.launcher.designsystem.SheetSecondaryButton

@Composable
internal fun WidgetBindSheetContent(
    widget: AvailableWidget,
    onContinue: () -> Unit,
    onPickAnother: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(
        header = {
            SheetHeader(
                title = stringResource(R.string.builder_bind_title, widget.appLabel),
                onClose = onClose,
                onBack = onPickAnother,
                backDescription = R.string.builder_bind_back,
            )
        },
        modifier = modifier,
        footer = {
            SheetConfirmButton(
                label = stringResource(R.string.builder_bind_continue),
                onClick = onContinue,
            )
            SheetSecondaryButton(
                label = stringResource(R.string.builder_bind_another),
                onClick = onPickAnother,
            )
        },
    ) {
        SheetNote(text = stringResource(R.string.builder_bind_system))
        SheetNote(text = stringResource(R.string.builder_bind_battery))
    }
}

// The suggestion names no profile (userSerial 0, the main one), which is tried first.
fun suggestedWidgetIn(available: List<AvailableWidget>, suggested: ComponentRef?): AvailableWidget? {
    if (suggested == null) return null
    val matching = available.filter {
        it.provider.packageName == suggested.packageName && it.provider.className == suggested.className
    }
    return matching.firstOrNull { it.provider.userSerial == suggested.userSerial } ?: matching.firstOrNull()
}
