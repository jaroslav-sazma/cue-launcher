package app.cue.launcher.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import app.cue.launcher.R
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LauncherLayoutRepository
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.StaticLayout
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.designsystem.AppIcon
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.feature.home.LauncherWidgetContent
import app.cue.launcher.feature.home.LauncherWidgetFrame
import app.cue.launcher.feature.icons.rememberAppIcon
import app.cue.launcher.system.HomeRoleStatus
import app.cue.launcher.system.LauncherAppsRepository
import app.cue.launcher.system.homeRoleOffered

@Composable
fun OnboardingScreen(
    roleStatus: HomeRoleStatus,
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    onRequestHomeRole: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Tokens.Settings.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_claim),
            modifier = Modifier.padding(top = Tokens.Settings.surfacePadding),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        HomePreview(
            launcherApps = launcherApps,
            layoutRepository = layoutRepository,
            modifier = Modifier.padding(vertical = Tokens.Settings.surfacePadding),
        )
        Text(
            text = stringResource(R.string.onboarding_seed_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
            }
            Spacer(modifier = Modifier.height(Tokens.Settings.surfacePadding))
            OnboardingActions(
                offersRole = homeRoleOffered(roleStatus),
                onRequestHomeRole = onRequestHomeRole,
                onDone = onDone,
                modifier = Modifier.padding(bottom = Tokens.Settings.sectionSpacing),
            )
        }
    }
}

@Composable
private fun OnboardingActions(
    offersRole: Boolean,
    onRequestHomeRole: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    if (offersRole) {
        Button(
            onClick = {
                onRequestHomeRole()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.onboarding_set_default))
        }
    }
    OutlinedButton(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Tokens.Settings.rowGap),
    ) {

        Text(
            text = stringResource(
                if (offersRole) R.string.onboarding_skip else R.string.onboarding_continue,
            ),
        )
    }
    }
}

@Composable
private fun HomePreview(
    launcherApps: LauncherAppsRepository,
    layoutRepository: LauncherLayoutRepository,
    modifier: Modifier = Modifier,
) {
    val layout by remember(layoutRepository) { layoutRepository.observeLayout() }
        .collectAsStateWithLifecycle(initialValue = StaticLayout.EMPTY)
    val dock = remember(layout) {
        layout.slots(LayoutContainer.DOCK).filterIsInstance<LauncherItem.Shortcut>()
    }
    val description = stringResource(R.string.onboarding_preview)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Tokens.Grid.gapBentoStatic),
    ) {
        LauncherWidgetFrame(
            radius = Tokens.Radius.tile4x2,
            modifier = Modifier.fillMaxWidth().height(Tokens.Grid.RowHeight.hero4x2),
        ) {
            LauncherWidgetContent(widget = LauncherWidgetType.CLOCK)
        }
        TileSurface(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.THIN,
            radius = Tokens.Radius.zoneDock,
            contentPadding = Tokens.Padding.zone,
            shadow = Tokens.Elevation.zone,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(Tokens.Size.icon),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dock.forEach { shortcut ->
                    AppIcon(
                        icon = rememberAppIcon(shortcut.component, launcherApps),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
