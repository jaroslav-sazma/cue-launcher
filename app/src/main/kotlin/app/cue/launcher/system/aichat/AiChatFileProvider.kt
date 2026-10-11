package app.cue.launcher.system.aichat

import android.net.Uri
import androidx.core.content.FileProvider

// Serves the one brief file, and only to the app a send granted it to. By its extension alone it would
// be text/markdown, a type Gemini does not accept.
class AiChatFileProvider : FileProvider() {

    override fun getType(uri: Uri): String = AI_CHAT_MIME
}
