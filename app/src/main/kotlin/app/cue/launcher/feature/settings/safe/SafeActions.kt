package app.cue.launcher.feature.settings.safe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.cue.launcher.R
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.system.DocumentStore
import app.cue.launcher.system.launchOrElse
import app.cue.launcher.system.safe.SafeFilesOpened
import app.cue.launcher.system.safe.SafeState
import app.cue.launcher.system.safe.SafeVault
import app.cue.launcher.system.safe.safeAuthority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.nio.CharBuffer

// Every unlock started here opens the files on success, the switch permitting, as a tile's does.
internal class SafeActions(
    private val vault: SafeVault,
    private val context: Context,
    private val scope: CoroutineScope,
    private val flash: FlashState,
) {

    fun unlock() {
        scope.launch { openIfUnlocked(vault.unlock()) }
    }

    // Undispatched, so the array is wiped even if the screen goes before the attempt starts. The
    // platform's Unicode normalization still makes a String of it, which nothing can wipe.
    fun unlockWithPassword(password: CharArray) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                openIfUnlocked(vault.unlockWithPassword(CharBuffer.wrap(password)))
            } finally {
                password.fill(Char.MIN_VALUE)
            }
        }
    }

    fun lock() = vault.lock()

    fun showFiles() {
        reportFiles(vault.openInFiles(context))
    }

    fun configure(uri: Uri, displayName: String) {
        scope.launch {
            vault.configure(uri.toString(), displayName)
            openIfUnlocked(vault.unlock())
        }
    }

    fun forget() {
        scope.launch { vault.forget() }
    }

    fun setLockAfterMinutes(minutes: Int) {
        scope.launch { vault.setLockAfterMinutes(minutes) }
    }

    fun setOpenInFiles(enabled: Boolean) {
        scope.launch { vault.setOpenInFiles(enabled) }
    }

    private suspend fun openIfUnlocked(state: SafeState) {
        if (state is SafeState.Unlocked) reportFiles(vault.openInFilesIfEnabled(context))
    }

    // A vault locked in the meantime says so on the screen itself.
    private fun reportFiles(opened: SafeFilesOpened) {
        if (opened == SafeFilesOpened.NO_FILE_MANAGER) flash.show(R.string.safe_no_file_manager, error = true)
    }
}

// Drive serves a .cuevault as application/octet-stream, so the picker cannot narrow by type. A file
// from the vault's own provider, listed while it is unlocked, would vanish with the lock it causes.
@Composable
internal fun rememberFileChoice(actions: SafeActions, flash: FlashState): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri == null -> Unit
            uri.authority == safeAuthority(context) -> flash.show(R.string.safe_error_not_a_vault, error = true)
            else -> scope.launch {
                keepReadGrant(context, uri)
                actions.configure(uri, DocumentStore.displayName(context, uri) ?: uri.lastPathSegment.orEmpty())
            }
        }
    }
    return remember(picker, flash) {
        { picker.launchOrElse(arrayOf(VAULT_MIME)) { flash.show(R.string.document_picker_missing, error = true) } }
    }
}

// A provider that hands out no persistable grant still lets the vault open until the process ends;
// after that the vault asks for the file again.
private fun keepReadGrant(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (refused: SecurityException) {
        Log.w(TAG, "The vault file's provider gives no lasting grant (${refused.javaClass.simpleName})")
    }
}

private const val VAULT_MIME = "*/*"

private const val TAG = "CueSafe"
