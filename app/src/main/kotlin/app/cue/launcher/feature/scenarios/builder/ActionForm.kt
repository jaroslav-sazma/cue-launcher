package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.ha.HA_ID_PATTERN
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaService
import app.cue.launcher.data.ha.HaStateRead
import app.cue.launcher.data.ha.haEntityIdOrNull
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.system.ActionRoute
import app.cue.launcher.system.ConfirmationResult
import app.cue.launcher.system.actionRouteOf
import app.cue.launcher.system.confirmationRequired
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class ActionFormError {

    URI_EMPTY,

    URI_UNUSABLE,

    HA_SERVICE_EMPTY,

    HA_SERVICE_FORMAT,

    HA_DATA,

    HA_STATE_ENTITY_FORMAT,
}

fun uriActionError(uri: String): ActionFormError? {
    val trimmed = uri.trim()
    if (trimmed.isEmpty()) return ActionFormError.URI_EMPTY

    return if (actionRouteOf(TapAction.DeepLink(trimmed)) is ActionRoute.Malformed) {
        ActionFormError.URI_UNUSABLE
    } else {
        null
    }
}

fun uriActionOf(uri: String): TapAction.DeepLink? =
    if (uriActionError(uri) == null) TapAction.DeepLink(uri.trim()) else null

fun haActionError(service: String, data: String, stateEntity: String = ""): ActionFormError? {
    val trimmed = service.trim()
    return when {
        trimmed.isEmpty() -> ActionFormError.HA_SERVICE_EMPTY
        !HA_ID_PATTERN.matches(trimmed) -> ActionFormError.HA_SERVICE_FORMAT
        haDataOf(data) == null -> ActionFormError.HA_DATA
        stateEntity.isNotBlank() && haEntityIdOrNull(stateEntity) == null -> ActionFormError.HA_STATE_ENTITY_FORMAT
        else -> null
    }
}

fun haActionOf(
    entity: String,
    service: String,
    data: String,
    confirm: Boolean = false,
    stateEntity: String = "",
): TapAction.HaAction? {
    if (haActionError(service, data, stateEntity) != null) return null
    return TapAction.HaAction(
        service = service.trim(),
        entity = entity.trim().ifEmpty { null },
        data = haDataOf(data) ?: JsonObject(emptyMap()),
        confirm = confirm,
        stateEntity = haEntityIdOrNull(stateEntity),
    )
}

private fun haDataOf(data: String): JsonObject? {
    val trimmed = data.trim()
    if (trimmed.isEmpty()) return JsonObject(emptyMap())
    return runCatching { actionJson.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
}

sealed interface HaTestResult {

    data class Ok(val millis: Long) : HaTestResult

    data class Failed(val detail: String?) : HaTestResult

    // The call went out but Home Assistant took longer than the launcher waits. A service call runs to
    // completion before it answers, so this is not a failure.
    data object Sent : HaTestResult

    data object NotConfigured : HaTestResult

    // The confirmation prompt was backed out of, so nothing was sent — and nothing is wrong either.
    data object Dismissed : HaTestResult

    data object ConfirmationUnavailable : HaTestResult
}

// The Test button sends the very call a tap sends, so an action that asks for confirmation passes the
// same prompt first and is refused the same way without one: the editor is not a way around the gate.
suspend fun haConfirmedTest(
    action: TapAction.HaAction,
    confirm: suspend () -> ConfirmationResult,
    call: suspend () -> HaTestResult,
): HaTestResult = when {
    !confirmationRequired(listOf(action)) -> call()
    else -> when (confirm()) {
        ConfirmationResult.GRANTED -> call()
        ConfirmationResult.DISMISSED -> HaTestResult.Dismissed
        ConfirmationResult.UNAVAILABLE -> HaTestResult.ConfirmationUnavailable
    }
}

fun haTestResultOf(probe: HaProbe): HaTestResult = when (probe) {
    is HaProbe.Ok -> HaTestResult.Ok(probe.millis)
    is HaProbe.Http -> HaTestResult.Failed("HTTP ${probe.status}")
    is HaProbe.Network -> HaTestResult.Failed(probe.detail)
    HaProbe.Timeout -> HaTestResult.Sent

    // Neither is a problem with the action being edited: both are answered on the connection screen,
    // and NotConfigured is what offers the way there.
    HaProbe.Blocked, HaProbe.LocalNetworkDenied, HaProbe.NotConfigured -> HaTestResult.NotConfigured
}

fun interface HaActionTester {

    suspend fun test(action: TapAction.HaAction): HaTestResult

    companion object {
        val NOT_CONFIGURED = HaActionTester { HaTestResult.NotConfigured }
    }
}

fun interface HaStateProbe {

    suspend fun read(entities: Set<String>): HaStateRead?

    companion object {
        val NONE = HaStateProbe { null }
    }
}

internal data class HaEntityRead(val entity: String, val state: String?, val probe: HaProbe)

fun interface HaEntityInventory {

    suspend fun entities(): List<HaEntity>

    companion object {
        val NONE = HaEntityInventory { emptyList() }
    }
}

class HaServiceInventory(
    val list: suspend () -> List<HaService>,
    val names: suspend (language: String, domains: Set<String>) -> Map<String, String>,
) {
    companion object {
        val NONE = HaServiceInventory(list = { emptyList() }, names = { _, _ -> emptyMap() })
    }
}

class HaAccess(
    val tester: HaActionTester = HaActionTester.NOT_CONFIGURED,
    val states: HaStateProbe = HaStateProbe.NONE,
    val entities: HaEntityInventory = HaEntityInventory.NONE,
    val services: HaServiceInventory = HaServiceInventory.NONE,

    val onConfigure: (() -> Unit)? = null,
) {
    companion object {
        val NOT_CONFIGURED = HaAccess()
    }
}

enum class HaTestState { NOT_RUN, RUNNING, OK, SENT, FAILED, NOT_CONFIGURED, CONFIRMATION_UNAVAILABLE }

fun haTestStateOf(result: HaTestResult): HaTestState = when (result) {
    is HaTestResult.Ok -> HaTestState.OK
    is HaTestResult.Failed -> HaTestState.FAILED
    HaTestResult.Sent -> HaTestState.SENT
    HaTestResult.NotConfigured -> HaTestState.NOT_CONFIGURED
    HaTestResult.Dismissed -> HaTestState.NOT_RUN
    HaTestResult.ConfirmationUnavailable -> HaTestState.CONFIRMATION_UNAVAILABLE
}

private val actionJson = Json { ignoreUnknownKeys = true }
