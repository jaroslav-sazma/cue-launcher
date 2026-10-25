package app.cue.launcher.data.ha

data class HaStateRead(
    val probe: HaProbe,
    val values: Map<String, String> = emptyMap(),

    val atMillis: Long = 0L,
    val attributes: Map<String, HaIconAttributes> = emptyMap(),
) {

    fun missing(entities: Set<String>): Set<String> = entities - values.keys

    companion object {
        val NONE = HaStateRead(HaProbe.NotConfigured)
    }
}
