package app.cue.launcher.system

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val AI_ASSISTANT_PACKAGE: String = "com.google.android.apps.bard"

const val WEB_SEARCH_URL: String = "https://www.google.com/search?q="

fun webSearchUrl(query: String): String =
    WEB_SEARCH_URL + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
