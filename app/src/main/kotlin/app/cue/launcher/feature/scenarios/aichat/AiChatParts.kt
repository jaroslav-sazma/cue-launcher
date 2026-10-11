package app.cue.launcher.feature.scenarios.aichat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.cue.launcher.data.aichat.CachedInventory
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventorySource
import app.cue.launcher.designsystem.MediumFilledButton
import app.cue.launcher.designsystem.Tokens

@Immutable
class AiChatEntries(
    val onAiChat: () -> Unit,
    val onPaste: () -> Unit,
    val pending: Boolean = false,
    val onDismissPending: () -> Unit = {},
)

val LocalAiChatInventory: ProvidableCompositionLocal<CachedInventory> =
    staticCompositionLocalOf { CachedInventory(InventorySource { InventorySnapshot.EMPTY }) }

internal class BarButton(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiChatBottomBar(action: BarButton) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .padding(FloatingToolbarDefaults.ScreenOffset)
            .fillMaxWidth(),
    ) {
        MediumFilledButton(
            label = action.label,
            onClick = action.onClick,
            modifier = Modifier.weight(1f),
            enabled = action.enabled,
        )
    }
}

@Composable
internal fun ColumnScope.AiChatBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(bottom = Tokens.Settings.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiChatWaiting(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.surfacePadding),
        ) {
            ContainedLoadingIndicator()
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = Tokens.Settings.screenPadding),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// No flash after copying: the system shows its own clipboard confirmation.
internal fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
}

// Read only on the user's tap, never on the way in: the system tells the user every time an app reads
// the clipboard.
internal fun clipboardText(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    return (0 until clip.itemCount).firstNotNullOfOrNull { index ->
        clip.getItemAt(index).coerceToText(context)?.toString()?.takeIf(String::isNotBlank)
    }
}
