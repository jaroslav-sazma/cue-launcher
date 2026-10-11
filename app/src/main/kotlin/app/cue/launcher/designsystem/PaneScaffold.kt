package app.cue.launcher.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.cue.launcher.R

// The bar collapses on scroll: a tab title that keeps a two-line block of height on a long list of
// rows costs a row of content on every screen it sits above.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabPaneScaffold(
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    flash: FlashState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val appBar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .nestedScroll(appBar.nestedScrollConnection),
    ) {
        MediumFlexibleTopAppBar(
            title = { Text(text = title) },
            actions = actions,
            scrollBehavior = appBar,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (scrollable) {
                            Modifier
                                .verticalScroll(scroll)
                                .padding(bottom = Tokens.Settings.navBarReserve)
                        } else {
                            Modifier
                        },
                    ),
                content = content,
            )
            flash?.let { FlashHost(state = it, modifier = Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPaneScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    flash: FlashState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // navigationBarsPadding before imePadding: the modifier chain consumes the bar inset first, so
    // with the keyboard up the padding is the keyboard height rather than the sum of both.
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = {
                subtitle?.let { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    GlyphIcon(
                        name = GLYPH_BACK,
                        size = Tokens.Settings.glyph,
                        tint = LocalContentColor.current,
                        contentDescription = stringResource(R.string.builder_back),
                    )
                }
            },
            actions = actions,
        )

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize(), content = content)
            flash?.let { FlashHost(state = it, modifier = Modifier.align(Alignment.BottomCenter)) }
        }
        bottomBar?.invoke()
    }
}

