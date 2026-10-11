package app.cue.launcher.feature.scenarios.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EXTRACT_MAX_RULES
import app.cue.launcher.data.scenario.ExtractMatch
import app.cue.launcher.data.scenario.ExtractRule
import app.cue.launcher.data.scenario.extractMatches
import app.cue.launcher.data.scenario.extractProblem
import app.cue.launcher.designsystem.Message
import app.cue.launcher.designsystem.Note
import app.cue.launcher.designsystem.SectionLabel
import app.cue.launcher.designsystem.Tokens
import app.cue.launcher.engine.NOTIFICATION_VALUE_NAMESPACE
import app.cue.launcher.engine.PostedNotification
import app.cue.launcher.engine.notificationHaystack
import app.cue.launcher.engine.notificationMatches
import app.cue.launcher.feature.scenarios.JitRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
internal data class ExtractAccess(val tick: Int, val resolve: (JitRequirement) -> Unit)

internal enum class SampleSource { NONE, PICKING, TYPED, NOTIFICATION }

// The rule a tap produced or found already there, and where the tapped value stood in the sample.
internal class TappedValue(val rule: ExtractRule, val start: Int, val tapped: String)

// The sample lives only here, in memory: it is never written into the scenario, never saved with the
// screen state and never logged — it is someone's notification.
@Stable
internal class ExtractSampleState {
    var source by mutableStateOf(SampleSource.NONE)
    var text by mutableStateOf("")

    // The app's notifications at the moment one of them became the sample; rules are counted against them.
    var current by mutableStateOf<List<PostedNotification>>(emptyList())
    var tapped by mutableStateOf<TappedValue?>(null)

    // Bumped by every "pick from a notification", so a list that failed to load is asked for again.
    var attempt by mutableIntStateOf(0)
    val ruleKeys = RuleKeys()

    fun use(sample: String, source: SampleSource, current: List<PostedNotification> = emptyList()) {
        text = sample
        this.source = source
        this.current = current
        tapped = null
    }

    fun edit(sample: String) {
        text = sample
        tapped = null
    }

    fun forget(rule: ExtractRule) {
        if (tapped?.rule == rule) tapped = null
    }
}

// Keys stay with their card while the cards before it come and go and while its name is typed in,
// so a field never loses focus to a card that only moved.
internal class RuleKeys {
    private var next = 0L
    private val keys = mutableListOf<Long>()

    fun of(count: Int): List<Long> {
        while (keys.size < count) keys += next++
        while (keys.size > count) keys.removeAt(keys.lastIndex)
        return keys.toList()
    }

    fun removed(indices: Collection<Int>) {
        indices.filter { it in keys.indices }.sortedDescending().forEach { keys.removeAt(it) }
    }
}

enum class TokenPull { FREE, PULLED, INSIDE }

// A rule belongs to the token its value starts in; a token further inside the value is only part
// of it, so tapping it pulls a value of its own instead of dropping that rule.
internal fun rulesStartingIn(token: SampleToken, matches: Map<String, ExtractMatch>): Set<String> =
    matches.filterValues { it.start in token.labelStart until token.labelEnd }.keys

internal fun tokenPull(token: SampleToken, matches: Map<String, ExtractMatch>): TokenPull = when {
    rulesStartingIn(token, matches).isNotEmpty() -> TokenPull.PULLED
    matches.values.any { it.start < token.end && token.start < it.end } -> TokenPull.INSIDE
    else -> TokenPull.FREE
}

internal data class RuleReading(val value: String?, val hits: Int?, val of: Int)

internal class NotificationCounts(val hits: Map<ExtractRule, Int>, val of: Int)

// Read by the same evaluator the scenario runs on, so a rule never counts a value it would not deliver.
internal fun notificationCounts(
    condition: Condition.NotificationFrom,
    notifications: List<PostedNotification>,
): NotificationCounts? {
    val matching = notifications.filter { notificationMatches(condition, it) }
        .map { extractMatches(condition.extract, notificationHaystack(it)) }
    if (matching.isEmpty()) return null
    val hits = condition.extract.associateWith { rule -> matching.count { rule.name in it } }
    return NotificationCounts(hits, matching.size)
}

@Composable
internal fun NotificationExtractSection(
    condition: Condition.NotificationFrom,
    access: ExtractAccess,
    onChange: (Condition.NotificationFrom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(condition.packageName) { ExtractSampleState() }
    val rules = condition.extract
    val setRules = { changed: List<ExtractRule> -> onChange(condition.copy(extract = changed)) }
    val sample = state.text.takeIf { it.isNotBlank() && state.source != SampleSource.PICKING }
    val matches = remember(rules, sample) { sample?.let { extractMatches(rules, it) }.orEmpty() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Scenarios.rowGap),
    ) {
        SectionLabel(title = stringResource(R.string.builder_notification_extract_section))
        Note(text = stringResource(R.string.builder_extract_intro))
        SampleSourceBlock(packageName = condition.packageName, state = state, access = access)
        if (sample != null) {
            SampleTokenFlow(
                sample = sample,
                matches = matches,
                full = rules.size >= EXTRACT_MAX_RULES,
                onPull = { token ->
                    val inferred = inferExtractRule(sample, token, rules)
                    state.tapped = TappedValue(inferred.rule, token.start, inferred.tapped)
                    if (inferred.added) setRules(rules + inferred.rule)
                },
                onRelease = { names ->
                    val released = rules.indices.filter { rules[it].name in names }
                    state.ruleKeys.removed(released)
                    setRules(rules.filterIndexed { index, _ -> index !in released })
                },
            )
        }
        state.tapped?.takeIf { it.rule in rules }?.let { tapped -> MismatchMessage(tapped, matches) }
        ExtractRuleList(condition = condition, state = state, matches = matches, onRules = setRules)
    }
}

@Composable
private fun ExtractRuleList(
    condition: Condition.NotificationFrom,
    state: ExtractSampleState,
    matches: Map<String, ExtractMatch>,
    onRules: (List<ExtractRule>) -> Unit,
) {
    val rules = condition.extract
    val problems = rules.mapIndexed { index, rule -> extractProblem(rule, rules.subList(0, index)) }
    val counts = rememberNotificationCounts(condition, state)
    val sampled = state.text.isNotBlank() && state.source != SampleSource.PICKING
    val keys = state.ruleKeys.of(rules.size)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Settings.cardGap)) {
        rules.forEachIndexed { index, rule ->
            key(keys[index]) {
                ExtractRuleCard(
                    rule = rule,
                    problem = problems[index],
                    // The runtime skips a refused rule, and a duplicate name would show the other rule's value.
                    reading = RuleReading(
                        value = matches[rule.name]?.value,
                        hits = counts?.hits?.get(rule),
                        of = counts?.of ?: 0,
                    ).takeIf { sampled && problems[index] == null },
                    onChange = { changed ->
                        state.forget(rule)
                        onRules(rules.toMutableList().also { it[index] = changed })
                    },
                    onRemove = {
                        state.forget(rule)
                        state.ruleKeys.removed(listOf(index))
                        onRules(rules.filterIndexed { at, _ -> at != index })
                    },
                )
            }
        }
    }
    val fields = rules.filterIndexed { index, _ -> problems[index] == null }
        .joinToString { rule -> "{$NOTIFICATION_VALUE_NAMESPACE:${rule.name}}" }
    if (fields.isNotEmpty()) {
        Note(text = stringResource(R.string.builder_notification_extract_fields, fields))
    }
}

// Every notification of the app is walked by every rule, up to the input cap each; that is too much
// to redo on the main thread on each keystroke in a rule.
@Composable
private fun rememberNotificationCounts(
    condition: Condition.NotificationFrom,
    state: ExtractSampleState,
): NotificationCounts? {
    val counted = state.current.takeIf { state.source == SampleSource.NOTIFICATION }.orEmpty()
    val packageName = condition.packageName
    val contains = condition.contains
    val rules = condition.extract
    val counts by produceState<NotificationCounts?>(null, packageName, contains, rules, counted) {
        value = if (counted.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                notificationCounts(Condition.NotificationFrom(packageName, contains, rules), counted)
            }
        }
    }
    return counts
}

// Read from the current matches, so an edit to the sample or to another rule shows at once.
@Composable
private fun MismatchMessage(tapped: TappedValue, matches: Map<String, ExtractMatch>) {
    val match = matches[tapped.rule.name]
    if (readsTapped(tapped.rule, match, tapped.start, tapped.tapped)) return
    Message(
        text = if (match == null) {
            stringResource(R.string.builder_extract_mismatch_none, tapped.tapped)
        } else {
            stringResource(R.string.builder_extract_mismatch, tapped.tapped, match.value)
        },
    )
}
