package app.cue.launcher.feature.drawer

import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cue.launcher.R
import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.drawer.HiddenAppRepository
import app.cue.launcher.data.launchlog.LaunchLogRepository
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.settings.DrawerSettings
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.designsystem.CutTopCornersShape
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.backPreview
import app.cue.launcher.designsystem.rememberBackPreview
import app.cue.launcher.designsystem.rememberReducedMotion
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.feature.drawer.search.AppSearchIndex
import app.cue.launcher.feature.drawer.search.appSearchIndexOf
import app.cue.launcher.feature.drawer.vault.DrawerVault
import app.cue.launcher.feature.drawer.vault.Vault
import app.cue.launcher.feature.drawer.vault.vaultOf
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.ActionOutcome
import app.cue.launcher.system.ContactsRepository
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.SearchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

@Composable
fun IntentDrawer(
    launcherApps: LauncherAppsRepository,
    launchLog: LaunchLogRepository,
    contacts: ContactsRepository,
    hiddenApps: HiddenAppRepository,
    settings: SettingsRepository,
    actions: ActionExecutor,
    drag: DrawerDrag,
    roleStatus: HomeRoleStatus,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val query = rememberTextFieldState()
    val catalog by drawerCatalog(launcherApps, launchLog, hiddenApps)
    val contactIndex by contactCatalog(contacts, settings)
    val search by drawerSearch(catalog.index, contactIndex, query)
    val stored by settings.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())

    val dismiss: () -> Unit = {
        keyboard?.hide()
        onClose()
    }

    val leave: (Boolean) -> Unit = { done ->
        if (done) dismiss() else Toast.makeText(context, R.string.home_launch_failed, Toast.LENGTH_SHORT).show()
    }
    val launch = drawerLaunch(launcherApps, roleStatus, query, onLaunched = leave) {
        dismiss()
        onOpenSettings()
    }
    val contactAction: (ContactAction, Contact, Rect?) -> Unit = { action, contact, bounds ->
        leave(actions.perform(action, contact, bounds) == ActionOutcome.DONE)
    }
    val rows = drawerRows(
        settings = stored.drawer,
        onAsk = { text, bounds -> leave(actions.askAi(text, bounds) == ActionOutcome.DONE) },
        onWeb = { text, bounds -> leave(actions.searchWeb(text, bounds) == ActionOutcome.DONE) },
    )

    val backPreview = rememberBackPreview(
        onDismiss = dismiss,
        onStart = { keyboard?.hide() },
        onCancel = { keyboard?.show() },
    )
    val reducedMotion = rememberReducedMotion()
    DrawerKeyboard(focusRequester, keyboard, drag)

    DrawerContent(
        query = query,

        hint = stringResource(
            if (stored.drawer.contacts) R.string.drawer_search_hint else R.string.drawer_search_hint_apps,
        ),
        search = search,
        rows = rows,
        vault = catalog.vault,
        focusRequester = focusRequester,
        launcherApps = launcherApps,
        drag = drag,
        onLaunch = launch,
        onContact = contactAction,
        modifier = modifier
            .backPreview(backPreview, reducedMotion)
            .background(colors.glass(GlassLevel.THIN))
            .drawerSwipeDown(dismiss)
            .drawerCloseOnOverscroll(dismiss),
    )
}

@Composable
private fun drawerLaunch(
    launcherApps: LauncherAppsRepository,
    roleStatus: HomeRoleStatus,
    query: TextFieldState,
    onLaunched: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
): (ComponentRef, Rect?) -> Unit {
    val ownPackage = LocalContext.current.packageName
    return { component, sourceBounds ->
        val fromSearch = query.text.isNotEmpty()
        if (opensLauncherSettings(component, ownPackage, launcherApps.profiles.ownSerial, roleStatus)) {
            launcherApps.recordLaunch(component, fromSearch)
            onOpenSettings()
        } else {
            onLaunched(launcherApps.launch(component, sourceBounds, fromSearch))
        }
    }
}

@Composable
private fun DrawerContent(
    query: TextFieldState,
    hint: String,
    search: DrawerSearch,
    rows: DrawerRows,
    vault: Vault,
    focusRequester: FocusRequester,
    launcherApps: LauncherAppsRepository,
    drag: DrawerDrag,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    onContact: (ContactAction, Contact, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {

        var barHeightPx by remember { mutableIntStateOf(0) }
        val barHeight = with(LocalDensity.current) { barHeightPx.toDp() }

        DrawerList(
            search = search,
            rows = rows,
            query = query.text.toString(),
            vault = vault,
            launcherApps = launcherApps,
            drag = drag,
            onLaunch = onLaunch,
            onContact = onContact,
            bottomInset = (barHeight - Tokens.Drawer.barOverlap).coerceAtLeast(0.dp),
        )
        DrawerBar(
            query = query,
            hint = hint,
            focusRequester = focusRequester,

            onSearch = {
                search.enterApp?.let { onLaunch(it.app.component, null) }
                search.enterContact?.let { onContact(ContactAction.OPEN, it.contact, null) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeightPx = it.height },
        )
    }
}

private class DrawerCatalog(val index: AppSearchIndex, val vault: Vault) {
    companion object {
        val EMPTY = DrawerCatalog(AppSearchIndex.EMPTY, Vault.EMPTY)
    }
}

@Composable
private fun drawerCatalog(
    launcherApps: LauncherAppsRepository,
    launchLog: LaunchLogRepository,
    hiddenApps: HiddenAppRepository,
): State<DrawerCatalog> = remember(launcherApps, launchLog, hiddenApps) {
    combine(
        launcherApps.observeSearchableApps(),
        launchLog.observeLaunchStats(),
        hiddenApps.observeHidden(),
    ) { apps, stats, hidden ->
        val visible = visibleApps(apps, hidden)
        DrawerCatalog(
            index = appSearchIndexOf(visible, stats),
            vault = vaultOf(visible.map(SearchableApp::app)),
        )
    }.flowOn(Dispatchers.Default)
}.collectAsStateWithLifecycle(initialValue = DrawerCatalog.EMPTY)

@Stable
class DrawerRows(
    val ai: Boolean,
    val web: Boolean,
    val onAsk: (String, Rect?) -> Unit,
    val onWeb: (String, Rect?) -> Unit,
)

@Composable
private fun drawerRows(
    settings: DrawerSettings,
    onAsk: (String, Rect?) -> Unit,
    onWeb: (String, Rect?) -> Unit,
): DrawerRows = remember(settings.aiRow, settings.webRow, onAsk, onWeb) {
    DrawerRows(ai = settings.aiRow, web = settings.webRow, onAsk = onAsk, onWeb = onWeb)
}

@Composable
private fun DrawerList(
    search: DrawerSearch,
    rows: DrawerRows,
    query: String,
    vault: Vault,
    launcherApps: LauncherAppsRepository,
    drag: DrawerDrag,
    onLaunch: (ComponentRef, Rect?) -> Unit,
    onContact: (ContactAction, Contact, Rect?) -> Unit,
    bottomInset: Dp,
) {
    val modifier = Modifier
        .fillMaxSize()
        .padding(bottom = bottomInset)
        .clip(
            RoundedCornerShape(
                bottomStart = Tokens.Radius.drawerContentCorner,
                bottomEnd = Tokens.Radius.drawerContentCorner,
            ),
        )
        .windowInsetsPadding(WindowInsets.statusBars)
    val inset = modifier.padding(horizontal = Tokens.Drawer.barPadding)
    when {
        !search.isEmpty -> DrawerResults(
            search = search,
            rows = rows,
            query = query,
            launcherApps = launcherApps,
            drag = drag,
            onLaunch = onLaunch,
            onContact = onContact,
            modifier = inset,
        )

        query.isNotEmpty() -> DrawerNoMatch(rows = rows, query = query, modifier = inset)

        else -> DrawerVault(
            vault = vault,
            launcherApps = launcherApps,
            drag = drag,
            onLaunch = onLaunch,
            modifier = modifier,
        )
    }
}

@Composable
private fun DrawerBar(
    query: TextFieldState,
    hint: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    val shape = CutTopCornersShape(Tokens.Radius.drawerContentCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.glass(GlassLevel.REGULAR))
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(
                start = Tokens.Drawer.barPadding,
                end = Tokens.Drawer.barPadding,

                top = Tokens.Radius.drawerContentCorner + Tokens.Drawer.barTopPadding,
                bottom = Tokens.Drawer.barTopPadding,
            ),
    ) {

        SearchField(
            query = query,
            hint = hint,
            focusRequester = focusRequester,
            onSearch = onSearch,
        )
    }
}

@Composable
private fun SearchField(
    query: TextFieldState,
    hint: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCueColors.current
    BasicTextField(
        state = query,
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.drawerSearchField)
            .focusRequester(focusRequester),
        textStyle = Tokens.Drawer.query.toTextStyle(color = colors.textPrimary),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),

        onKeyboardAction = KeyboardActionHandler { onSearch() },
        decorator = { field ->
            TileSurface(
                modifier = Modifier.fillMaxSize(),
                level = GlassLevel.THICK,
                radius = Tokens.Radius.searchbar,
                contentPadding = Tokens.Drawer.barPadding,
            ) {
                SearchFieldRow(query = query, hint = hint, field = field)
            }
        },
    )
}

@Composable
private fun SearchFieldRow(query: TextFieldState, hint: String, field: @Composable () -> Unit) {
    val colors = LocalCueColors.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Tokens.Drawer.fieldPadding - Tokens.Drawer.barPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(name = "magnify", size = Tokens.Drawer.glyph, tint = colors.textSecondary)
        Spacer(modifier = Modifier.width(Tokens.Drawer.fieldGap))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.text.isEmpty()) {
                Text(
                    text = hint,
                    color = colors.textSecondary,
                    style = Tokens.Drawer.query.toTextStyle(),
                )
            }
            field()
        }
        if (query.text.isNotEmpty()) {
            ClearQueryButton(onClear = { query.clearText() })
        }
    }
}

@Composable
private fun ClearQueryButton(onClear: () -> Unit) {
    val colors = LocalCueColors.current
    Box(
        modifier = Modifier
            .size(Tokens.Drawer.touchTarget)
            .clip(RoundedCornerShape(Tokens.Drawer.touchTarget / 2))
            .clickable(onClickLabel = stringResource(R.string.drawer_clear), onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(name = "close", size = Tokens.Drawer.clearGlyph, tint = colors.textSecondary)
    }
}
