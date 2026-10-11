package app.cue.launcher.feature.drawer

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.contacts.contactInitials
import app.cue.launcher.designsystem.GlassLevel
import app.cue.launcher.designsystem.GlyphIcon
import app.cue.launcher.designsystem.LocalCueColors
import app.cue.launcher.designsystem.TileSurface
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.designsystem.toTextStyle
import app.cue.launcher.system.toSourceBounds

@Composable
internal fun ContactRow(
    contact: Contact,
    onOpen: (Rect?) -> Unit,
    onCall: (Rect?) -> Unit,
    onMessage: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    enterTarget: Boolean = false,
) {
    var avatarBounds by remember(contact.id) { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Tokens.Size.drawerRow)
            .enterTargetSurface(enterTarget)
            .clickable(
                onClickLabel = stringResource(R.string.drawer_contact_open),
                onClick = { onOpen(avatarBounds) },
            )
            .padding(horizontal = Tokens.Drawer.rowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Drawer.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            displayName = contact.displayName,
            modifier = Modifier.onGloballyPositioned {
                avatarBounds = it.boundsInWindow().toSourceBounds()
            },
        )

        DrawerRowText(
            label = contact.displayName,
            meta = contact.phoneNumber,
            modifier = Modifier.weight(1f),
        )
        if (enterTarget) EnterHint()
        ContactActions(contact = contact, onCall = onCall, onMessage = onMessage)
    }
}

@Composable
private fun ContactAvatar(displayName: String, modifier: Modifier = Modifier) {
    val colors = LocalCueColors.current
    val initials = remember(displayName) { contactInitials(displayName) }
    TileSurface(
        modifier = modifier.size(Tokens.Size.drawerResultIcon),
        level = GlassLevel.THICK,
        radius = Tokens.Radius.drawerContactAvatar,
        contentPadding = Tokens.Padding.chip,

        shadow = null,
    ) {
        if (initials.isEmpty()) {
            GlyphIcon(
                name = "account",
                modifier = Modifier.align(Alignment.Center),
                size = Tokens.Drawer.actionGlyph,
                tint = colors.textPrimary,
            )
        } else {
            Text(
                text = initials,
                modifier = Modifier.align(Alignment.Center),
                color = colors.textPrimary,
                style = Tokens.Drawer.initials.toTextStyle(),
            )
        }
    }
}

@Composable
private fun ContactActions(
    contact: Contact,
    onCall: (Rect?) -> Unit,
    onMessage: (Rect?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Drawer.actionGap)) {
        ContactAction(
            glyph = "phone",
            label = stringResource(R.string.drawer_contact_call, contact.displayName),
            onClick = onCall,
        )
        ContactAction(
            glyph = "message-text",
            label = stringResource(R.string.drawer_contact_message, contact.displayName),
            onClick = onMessage,
        )
    }
}

@Composable
private fun ContactAction(glyph: String, label: String, onClick: (Rect?) -> Unit) {
    val colors = LocalCueColors.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val shape = RoundedCornerShape(Tokens.Radius.drawerInlineAction)
    TileSurface(
        modifier = Modifier
            .size(Tokens.Size.drawerInlineAction)

            .clip(shape)
            .clickable(onClickLabel = label, onClick = { onClick(bounds) })
            .onGloballyPositioned { bounds = it.boundsInWindow().toSourceBounds() },
        level = GlassLevel.THICK,
        radius = Tokens.Radius.drawerInlineAction,
        contentPadding = Tokens.Padding.chip,
        shadow = null,
    ) {
        Box(modifier = Modifier.align(Alignment.Center)) {
            GlyphIcon(
                name = glyph,
                size = Tokens.Drawer.actionGlyph,
                tint = colors.textPrimary,
                contentDescription = label,
            )
        }
    }
}
