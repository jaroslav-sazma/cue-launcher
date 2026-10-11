package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef

internal class IconCache<T>(private val maxSize: Int) {

    private val entries = LinkedHashMap<ComponentRef, T>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    val size: Int @Synchronized get() = entries.size

    @Synchronized
    fun get(key: ComponentRef): T? = entries[key]

    @Synchronized
    fun put(key: ComponentRef, value: T) {
        entries[key] = value
        while (entries.size > maxSize) {
            val eldest = entries.keys.first()
            entries.remove(eldest)
        }
    }

    @Synchronized
    fun invalidatePackage(packageName: String, userSerial: Long) {
        entries.keys.removeAll { it.packageName == packageName && it.userSerial == userSerial }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private companion object {
        const val INITIAL_CAPACITY = 0
        const val LOAD_FACTOR = 0.75f
    }
}
