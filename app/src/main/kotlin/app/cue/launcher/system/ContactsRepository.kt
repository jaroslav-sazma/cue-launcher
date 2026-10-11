package app.cue.launcher.system

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import app.cue.launcher.data.contacts.Contact
import app.cue.launcher.data.contacts.ContactAccess
import app.cue.launcher.data.contacts.ContactRead
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
class ContactsRepository(private val application: Application) {

    fun observeContacts(): Flow<ContactRead> = flow {
        if (!contactsGranted(application)) {
            emit(ContactRead.DENIED)
            return@flow
        }
        emitAll(contactChanges().map { read() })
    }.flowOn(Dispatchers.IO)

    suspend fun read(): ContactRead = withContext(Dispatchers.IO) { query() }

    private fun query(): ContactRead {
        val cursor = try {
            application.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                PROJECTION,
                null,
                null,
                null,
            )
        } catch (_: SecurityException) {

            return ContactRead.DENIED
        }

        return cursor?.use { ContactRead(ContactAccess.GRANTED, it.readContacts()) }
            ?: ContactRead(ContactAccess.UNAVAILABLE)
    }

    private fun Cursor.readContacts(): List<Contact> {
        val byContact = LinkedHashMap<Long, Contact>()
        while (moveToNext()) {
            val contact = contactOrNull() ?: continue
            val known = byContact[contact.id]
            if (known == null || getInt(COLUMN_SUPER_PRIMARY) != 0) byContact[contact.id] = contact
        }
        return byContact.values.toList()
    }

    private fun Cursor.contactOrNull(): Contact? {
        val name = text(COLUMN_DISPLAY_NAME)
        val number = text(COLUMN_NUMBER)
        if (name == null || number == null) return null
        val id = getLong(COLUMN_CONTACT_ID)
        return Contact(
            id = id,
            lookupUri = lookupUri(id, text(COLUMN_LOOKUP_KEY)).toString(),
            displayName = name,
            phoneNumber = number,
            starred = getInt(COLUMN_STARRED) != 0,
        )
    }

    private fun lookupUri(id: Long, lookupKey: String?): Uri = if (lookupKey == null) {
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
    } else {
        ContactsContract.Contacts.getLookupUri(id, lookupKey)
    }

    private fun contactChanges(): Flow<Unit> =
        application.contentResolver.contentChanges(ContactsContract.AUTHORITY_URI)

    private companion object {

        val PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )

        const val COLUMN_CONTACT_ID = 0
        const val COLUMN_LOOKUP_KEY = 1
        const val COLUMN_DISPLAY_NAME = 2
        const val COLUMN_NUMBER = 3
        const val COLUMN_SUPER_PRIMARY = 4
        const val COLUMN_STARRED = 5
    }
}

const val READ_CONTACTS_PERMISSION: String = Manifest.permission.READ_CONTACTS

fun contactsGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
