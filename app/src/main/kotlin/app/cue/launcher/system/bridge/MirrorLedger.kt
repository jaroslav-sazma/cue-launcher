package app.cue.launcher.system.bridge

import java.util.concurrent.TimeUnit

// What one session connection has told the Mac: the keys of the last active-set and the posts since,
// by key hash, and the icons it may be asked for. Until the first active-set has gone out nothing is
// news, because that set reflects whatever happened before it.
internal class MirrorLedger(private val clock: () -> Long = ::monotonicMillis) {

    private val mirrored = HashMap<String, String>()

    private val tombstones = HashMap<String, Long>()

    private val icons = HashMap<String, ByteArray>()

    var synced = false
        private set

    fun reset(keys: Collection<String>) {
        mirrored.clear()
        keys.associateByTo(mirrored, ::notificationKeyHash)
        synced = true
    }

    // A post that passes the filters is always news, as an update of the same key is news to the Mac
    // too; one that no longer passes takes back a key the Mac mirrors.
    fun news(key: String, passes: Boolean): PostNews? {
        val keyHash = notificationKeyHash(key)
        return when {
            !synced -> null
            passes -> {
                mirrored[keyHash] = key
                PostNews.Content(keyHash)
            }
            else -> forget(keyHash)?.let(PostNews::Ring)
        }
    }

    // Without a session the content cannot follow, so the news is a doorbell.
    fun posted(key: String, passes: Boolean): BleSignal? = when (val news = news(key, passes)) {
        is PostNews.Content -> BleSignal.NotifPosted(news.keyHash)
        is PostNews.Ring -> news.signal
        null -> null
    }

    fun removed(key: String): BleSignal? = forget(notificationKeyHash(key))

    fun keyOf(keyHash: String): String? = mirrored[keyHash]

    // The removal a dismiss causes is the Mac's own, so it leaves the ledger without ringing.
    fun tombstone(keyHash: String) {
        val now = clock()
        tombstones.values.removeAll { until -> until <= now }
        tombstones[keyHash] = now + TOMBSTONE_MIN_MS
    }

    fun advertise(iconHash: String, png: ByteArray) {
        icons[iconHash] = png
    }

    fun icon(iconHash: String): ByteArray? = icons[iconHash]

    private fun forget(keyHash: String): BleSignal? {
        val buried = tombstones.remove(keyHash)?.let { until -> clock() < until } == true
        val known = mirrored.remove(keyHash) != null
        return if (synced && known && !buried) BleSignal.NotifRemoved(keyHash) else null
    }
}

internal sealed interface PostNews {

    class Content(val keyHash: String) : PostNews

    class Ring(val signal: BleSignal) : PostNews
}

internal fun monotonicMillis(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
