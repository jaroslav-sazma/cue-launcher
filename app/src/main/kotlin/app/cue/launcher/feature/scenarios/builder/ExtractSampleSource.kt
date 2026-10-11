package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R
import app.cue.launcher.data.scenario.EXTRACT_MAX_INPUT
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.TonalRow
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.supportingInk
import app.cue.launcher.designsystem.supportingStyle
import app.cue.launcher.engine.PostedNotification
import app.cue.launcher.engine.notificationHaystack
import app.cue.launcher.system.notifications.notificationListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Listing {

    data object Loading : Listing

    data object Unavailable : Listing

    data class Loaded(val notifications: List<PostedNotification>) : Listing
}

@Composable
internal fun SampleSourceBlock(
    packageName: String,
    state: ExtractSampleState,
    access: ExtractAccess,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        when (state.source) {
            SampleSource.NOTIFICATION -> ChosenSampleHeader(onOther = { state.use("", SampleSource.NONE) })
            else -> SampleSourceButtons(
                canPick = packageName.isNotBlank(),
                onPick = {
                    state.use("", SampleSource.PICKING)
                    state.attempt++
                },
                onType = { if (state.source != SampleSource.TYPED) state.use("", SampleSource.TYPED) },
            )
        }
        when (state.source) {
            SampleSource.PICKING -> NotificationPick(
                packageName = packageName,
                access = access,
                attempt = state.attempt,
                onPick = { picked, current ->
                    state.use(notificationHaystack(picked), SampleSource.NOTIFICATION, current)
                },
            )

            SampleSource.TYPED -> TypedSample(text = state.text, onText = state::edit)
            SampleSource.NOTIFICATION -> Note(text = stringResource(R.string.builder_extract_tap))
            SampleSource.NONE -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SampleSourceButtons(canPick: Boolean, onPick: () -> Unit, onType: () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.chipGap),
    ) {
        FilledTonalButton(onClick = onPick, shapes = ButtonDefaults.shapes(), enabled = canPick) {
            Text(text = stringResource(R.string.builder_extract_pick))
        }
        OutlinedButton(onClick = onType, shapes = ButtonDefaults.shapes()) {
            Text(text = stringResource(R.string.builder_extract_type))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChosenSampleHeader(onOther: () -> Unit) {
    TextButton(
        onClick = onOther,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(horizontal = Tokens.Settings.rowMargin),
    ) {
        Text(text = stringResource(R.string.builder_extract_other_sample))
    }
}

@Composable
private fun TypedSample(text: String, onText: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { onText(it.take(EXTRACT_MAX_INPUT)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Settings.surfacePadding),
        label = { Text(text = stringResource(R.string.builder_extract_sample_field)) },
        minLines = SAMPLE_MIN_LINES,
        maxLines = SAMPLE_MAX_LINES,
    )
    Note(text = stringResource(R.string.builder_extract_sample_note))
    if (text.isNotBlank()) Note(text = stringResource(R.string.builder_extract_tap))
}

@Composable
private fun NotificationPick(
    packageName: String,
    access: ExtractAccess,
    attempt: Int,
    onPick: (PostedNotification, List<PostedNotification>) -> Unit,
) {
    val context = LocalContext.current
    val granted = remember(access.tick, context) { notificationListenerEnabled(context) }
    // The sheet already asks for this access under the form; a second request here would only repeat it.
    if (!granted) {
        Note(text = stringResource(R.string.builder_extract_needs_access))
        return
    }
    val listing = rememberListing(packageName, access.tick, attempt)
    when (listing) {
        Listing.Loading -> Unit
        Listing.Unavailable -> Note(text = stringResource(R.string.builder_extract_unavailable))
        is Listing.Loaded -> if (listing.notifications.isEmpty()) {
            Note(text = stringResource(R.string.builder_extract_none_now))
        } else {
            Note(text = stringResource(R.string.builder_extract_pick_one))
            listing.notifications.forEach { notification ->
                NotificationSampleRow(
                    notification = notification,
                    onPick = { onPick(notification, listing.notifications) },
                )
            }
        }
    }
}

@Composable
private fun rememberListing(packageName: String, tick: Int, attempt: Int): Listing {
    val source = LocalActiveNotifications.current
    var listing by remember(source, packageName, tick, attempt) { mutableStateOf<Listing>(Listing.Loading) }
    LaunchedEffect(source, packageName, tick, attempt) {
        // Right after access is granted the listener binds with a delay, so an unbound one is
        // asked again a few times before the pick gives up.
        var found = withContext(Dispatchers.Default) { source.of(packageName) }
        var attempts = 1
        while (found == null && attempts < LISTENER_ATTEMPTS) {
            delay(LISTENER_RETRY_MS)
            found = withContext(Dispatchers.Default) { source.of(packageName) }
            attempts++
        }
        // A listener the system has dropped is asked to bind again, so the next pick can succeed.
        if (found == null) source.reconnect()
        listing = found?.let(Listing::Loaded) ?: Listing.Unavailable
    }
    return listing
}

@Composable
private fun NotificationSampleRow(notification: PostedNotification, onPick: () -> Unit) {
    val title = notification.title ?: notification.text.orEmpty()
    val text = notification.text.takeIf { notification.title != null }
    TonalRow(onClick = onPick) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Settings.rowGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            text?.let {
                Text(
                    text = it,
                    style = supportingStyle,
                    color = supportingInk(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val SAMPLE_MIN_LINES = 3
private const val SAMPLE_MAX_LINES = 6
private const val LISTENER_ATTEMPTS = 5
private const val LISTENER_RETRY_MS = 400L
