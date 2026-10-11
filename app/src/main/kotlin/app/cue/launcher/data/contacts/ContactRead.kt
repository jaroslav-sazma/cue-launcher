package app.cue.launcher.data.contacts

data class ContactRead(
    val access: ContactAccess,
    val contacts: List<Contact> = emptyList(),
) {

    companion object {
        val DENIED = ContactRead(ContactAccess.DENIED)
        val OFF = ContactRead(ContactAccess.OFF)
    }
}

enum class ContactAccess {
    GRANTED,
    DENIED,

    OFF,

    UNAVAILABLE,
}
