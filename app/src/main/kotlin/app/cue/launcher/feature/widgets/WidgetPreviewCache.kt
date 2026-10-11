package app.cue.launcher.feature.widgets

internal class WidgetPreviewCache<T>(private val maxSize: Int = MAX_ENTRIES) {

    private val entries = LinkedHashMap<String, Entry<T>>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    val size: Int @Synchronized get() = entries.size

    @Synchronized
    fun peek(key: String): T? = entries[key]?.value

    suspend fun getOrLoad(key: String, load: suspend () -> T?): T? {
        cached(key)?.let { return it.value }
        val loaded = load()
        put(key, loaded)
        return loaded
    }

    @Synchronized
    private fun cached(key: String): Entry<T>? = entries[key]

    @Synchronized
    private fun put(key: String, value: T?) {
        entries[key] = Entry(value)
        while (entries.size > maxSize) {
            entries.remove(entries.keys.first())
        }
    }

    private class Entry<T>(val value: T?)

    private companion object {

        const val MAX_ENTRIES = 48
        const val INITIAL_CAPACITY = 0
        const val LOAD_FACTOR = 0.75f
    }
}
