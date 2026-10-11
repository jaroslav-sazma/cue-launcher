package app.cue.launcher.system.aichat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.cue.launcher.system.AI_ASSISTANT_PACKAGE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class HandoffOutcome { SENT, NO_CHAT_APP, BRIEF_UNWRITABLE }

// The brief goes as an attached file with a short message beside it: the file arrives whole, while a
// chat's text field may cut tens of thousands of characters short.
class AiChatHandoff(private val context: Context) {

    suspend fun send(brief: String): HandoffOutcome = handOff(brief, channel)

    private val channel = object : BriefChannel {

        // RoleManager.getRoleHolders() is a system API. The assistant role keeps its holder's component
        // in this secure setting, which an app may still read; where it cannot, the activity answering
        // ACTION_ASSIST is the holder whenever there is only one.
        override fun assistantPackage(): String? {
            val setting = try {
                Settings.Secure.getString(context.contentResolver, ASSISTANT_SETTING)
            } catch (_: SecurityException) {
                null
            }
            return assistantPackageOf(setting) ?: context.packageManager
                .resolveActivity(Intent(Intent.ACTION_ASSIST), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeUnless { it == RESOLVER_PACKAGE }
        }

        override fun writeBrief(brief: String): String {
            val dir = File(context.cacheDir, AI_CHAT_DIR).apply { mkdirs() }
            val file = File(dir, AI_CHAT_CONTEXT_FILE_NAME).apply { writeText(brief) }
            return FileProvider.getUriForFile(context, aiChatAuthority(context), file).toString()
        }

        override fun accepts(share: BriefShare): Boolean = context.packageManager
            .queryIntentActivities(share.toIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

        override fun start(share: BriefShare): Boolean = try {
            context.startActivity(share.toIntent())
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

        private fun BriefShare.toIntent(): Intent {
            val uri = Uri.parse(stream)
            return Intent(action)
                .setPackage(packageName)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(flags)
                .apply { clipData = ClipData.newUri(context.contentResolver, clipLabel, uri) }
        }
    }
}

internal interface BriefChannel {

    fun assistantPackage(): String?

    // The content: URI of the written file.
    fun writeBrief(brief: String): String

    fun accepts(share: BriefShare): Boolean

    // False when the app turned out not to take it after all.
    fun start(share: BriefShare): Boolean
}

internal data class BriefShare(
    val packageName: String,
    val stream: String,
    val action: String = Intent.ACTION_SEND,
    val type: String = AI_CHAT_MIME,
    val text: String = AI_CHAT_FILE_PROMPT,
    val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
    val clipLabel: String = AI_CHAT_CONTEXT_FILE_NAME,
)

// A full cache or a path the provider does not cover leaves no file to share; the user still gets the
// fallback rather than a crash.
internal suspend fun handOff(
    brief: String,
    channel: BriefChannel,
    io: CoroutineDispatcher = Dispatchers.IO,
): HandoffOutcome {
    val stream = try {
        withContext(io) { channel.writeBrief(brief) }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: return HandoffOutcome.BRIEF_UNWRITABLE
    val sent = chatCandidates(channel.assistantPackage()).asSequence()
        .map { BriefShare(it, stream) }
        .filter(channel::accepts)
        .any(channel::start)
    return if (sent) HandoffOutcome.SENT else HandoffOutcome.NO_CHAT_APP
}

internal fun assistantPackageOf(setting: String?): String? =
    setting?.substringBefore('/')?.trim()?.takeIf(String::isNotEmpty)

// The Google app holds the assistant role on Gemini's behalf, and a text share sent to it lands in its
// own "Saves", not in a chat — so it stands for the Gemini app. An assistant that takes no share falls
// back to Gemini too.
internal fun chatCandidates(assistant: String?): List<String> =
    listOfNotNull(assistant?.takeUnless { it == GOOGLE_APP_PACKAGE }, AI_ASSISTANT_PACKAGE).distinct()

internal fun aiChatAuthority(context: Context): String = "${context.packageName}.aichat"

internal const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

// Markdown is plain text, and text/plain is the one text type every chat app's share filter takes.
internal const val AI_CHAT_MIME = "text/plain"

private const val ASSISTANT_SETTING = "assistant"
private const val RESOLVER_PACKAGE = "android"
private const val AI_CHAT_DIR = "aichat"
