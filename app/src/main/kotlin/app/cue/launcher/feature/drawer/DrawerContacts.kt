package app.cue.launcher.feature.drawer

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.contacts.ContactRead
import app.cue.launcher.data.settings.SettingsRepository
import app.cue.launcher.feature.drawer.search.ContactSearchIndex
import app.cue.launcher.feature.drawer.search.contactSearchIndexOf
import app.cue.launcher.system.ActionExecutor
import app.cue.launcher.system.ActionOutcome
import app.cue.launcher.system.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun contactCatalog(
    contacts: ContactsRepository,
    settings: SettingsRepository,
): State<ContactSearchIndex> = remember(contacts, settings) {
    settings.settings
        .map { it.drawer.contacts }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) contacts.observeContacts() else flowOf(ContactRead.OFF)
        }
        .map { contactSearchIndexOf(it.contacts) }
        .flowOn(Dispatchers.Default)
}.collectAsStateWithLifecycle(initialValue = ContactSearchIndex.EMPTY)

internal fun ActionExecutor.perform(action: ContactAction, contact: Contact, bounds: Rect?): ActionOutcome =
    when (action) {
        ContactAction.OPEN -> openContact(contact.lookupUri, bounds)
        ContactAction.CALL -> dialContact(contact.phoneNumber, bounds)
        ContactAction.MESSAGE -> messageContact(contact.phoneNumber, bounds)
    }
