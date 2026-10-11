package app.cue.launcher.feature.drawer

import android.graphics.Rect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.rememberSearchListState
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.system.LauncherAppsRepository

@Composable
fun DrawerResults(
    search: DrawerSearch,
    rows: DrawerRows,
    query: String,
    launcherApps: LauncherAppsRepository,
    drag: DrawerDrag,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    onContact: (ContactAction, Contact, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberSearchListState(search),

        verticalArrangement = Arrangement.Bottom,
        contentPadding = PaddingValues(
            top = Tokens.Drawer.listTopPadding,
            bottom = Tokens.Drawer.listBottomPadding,
        ),
    ) {
        if (search.apps.isNotEmpty()) {
            item(key = SECTION_APPS, contentType = "header") {
                DrawerSectionHeader(text = stringResource(R.string.drawer_section_apps))
            }
            items(
                items = search.apps,
                key = { result -> result.app.listKey },
                contentType = { "app" },
            ) { result ->
                AppRow(
                    app = result.app,
                    meta = result.lastLaunchedAt?.let { lastUsedText(it) },
                    launcherApps = launcherApps,
                    drag = drag,
                    onLaunch = onLaunch,
                    enterTarget = result === search.enterApp,
                )
            }
        }
        if (search.contacts.isNotEmpty()) {
            item(key = SECTION_CONTACTS, contentType = "header") {
                DrawerSectionHeader(text = stringResource(R.string.drawer_section_contacts))
            }
            items(
                items = search.contacts,
                key = { result -> result.contact.id },
                contentType = { "contact" },
            ) { result ->
                ContactRow(
                    contact = result.contact,
                    onOpen = { bounds -> onContact(ContactAction.OPEN, result.contact, bounds) },
                    onCall = { bounds -> onContact(ContactAction.CALL, result.contact, bounds) },
                    onMessage = { bounds -> onContact(ContactAction.MESSAGE, result.contact, bounds) },
                    enterTarget = result === search.enterContact,
                )
            }
        }
        if (rows.ai) {
            item(key = ROW_AI) {
                DrawerExtraRow(
                    glyph = GLYPH_AI,
                    label = stringResource(R.string.drawer_row_ai),
                    query = query,
                    onClick = { bounds -> rows.onAsk(query, bounds) },
                )
            }
        }
    }
}

enum class ContactAction { OPEN, CALL, MESSAGE }

@Composable
fun DrawerNoMatch(rows: DrawerRows, query: String, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    Column(

        modifier = modifier.fillMaxSize().padding(bottom = Tokens.Drawer.listBottomPadding),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = stringResource(R.string.drawer_no_match),
            modifier = Modifier.padding(horizontal = Tokens.Drawer.rowPadding),
            color = colors.textSecondary,
            style = Tokens.Drawer.result.toTextStyle(),
        )
        if (rows.ai) {
            DrawerExtraRow(
                glyph = GLYPH_AI,
                label = stringResource(R.string.drawer_row_ai),
                query = query,
                onClick = { bounds -> rows.onAsk(query, bounds) },
            )
        }
        if (rows.web) {
            DrawerExtraRow(
                glyph = GLYPH_WEB,
                label = stringResource(R.string.drawer_row_web),
                query = query,
                onClick = { bounds -> rows.onWeb(query, bounds) },
            )
        }
    }
}

@Composable
private fun lastUsedText(lastLaunchedAt: Long): String {
    val days = remember(lastLaunchedAt) {
        LastUsed.daysAgo(lastLaunchedAt, System.currentTimeMillis())
    }
    return when (days) {
        LastUsed.TODAY -> stringResource(R.string.drawer_last_used_today)
        LastUsed.YESTERDAY -> stringResource(R.string.drawer_last_used_yesterday)
        else -> stringResource(R.string.drawer_last_used_days, days)
    }
}

private const val SECTION_APPS = "section:apps"
private const val SECTION_CONTACTS = "section:contacts"
private const val ROW_AI = "row:ai"

private const val GLYPH_AI = "creation"
private const val GLYPH_WEB = "web"
