package app.cue.launcher.feature.settings.ha

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaEndpoint
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HA_EVENT_SECRET_MIN_LENGTH
import app.cue.launcher.data.ha.HaProbe
import app.cue.launcher.data.ha.HaSecretError
import app.cue.launcher.data.ha.HaUrlError
import app.cue.launcher.designsystem.relativeTimeText

@Composable
internal fun haEndpointName(kind: HaEndpointKind): String = stringResource(
    when (kind) {
        HaEndpointKind.HOME -> R.string.ha_endpoint_home
        HaEndpointKind.AWAY -> R.string.ha_endpoint_away
    },
)

@Composable
internal fun haProbeText(probe: HaProbe?, running: Boolean): String? = when {
    running -> stringResource(R.string.ha_test_running)
    probe == null -> null
    probe is HaProbe.Ok -> stringResource(R.string.ha_test_ok, haEntitiesText(probe.entities), probe.millis)
    probe is HaProbe.Http && probe.status == HTTP_UNAUTHORIZED -> stringResource(R.string.ha_test_unauthorized)
    probe is HaProbe.Http -> stringResource(R.string.ha_test_http, probe.status)
    probe is HaProbe.Network && probe.detail != null ->
        stringResource(R.string.ha_test_network_detail, probe.detail)

    probe is HaProbe.Network -> stringResource(R.string.ha_test_network)
    probe is HaProbe.Timeout -> stringResource(R.string.ha_test_timeout)
    probe is HaProbe.Blocked -> stringResource(R.string.ha_test_blocked)
    probe is HaProbe.LocalNetworkDenied -> stringResource(R.string.ha_test_local_network)
    else -> stringResource(R.string.ha_test_not_configured)
}

@Composable
internal fun haEntitiesText(count: Int): String =
    pluralStringResource(R.plurals.ha_entities, count, count)

@Composable
internal fun haUrlErrorText(error: HaUrlError): String = stringResource(haUrlErrorRes(error))

@StringRes
internal fun haUrlErrorRes(error: HaUrlError): Int = when (error) {
    HaUrlError.SCHEME -> R.string.ha_url_error_scheme
    HaUrlError.HOST -> R.string.ha_url_error_host
    HaUrlError.INSECURE_EXTERNAL -> R.string.ha_url_error_insecure
    HaUrlError.INSECURE_HOME -> R.string.ha_url_error_insecure_home
}

@Composable
internal fun haSecretErrorText(error: HaSecretError): String = when (error) {
    HaSecretError.SHORT ->
        stringResource(R.string.ha_secret_error_short, HA_EVENT_SECRET_MIN_LENGTH)

    HaSecretError.SEPARATOR -> stringResource(R.string.ha_secret_error_separator)
    HaSecretError.CHARACTERS -> stringResource(R.string.ha_secret_error_characters)
    HaSecretError.DIGITS -> stringResource(R.string.ha_secret_error_digits)
}

@Composable
internal fun haNowUsingText(endpoint: HaEndpoint?): String = when (endpoint) {
    null -> stringResource(R.string.ha_now_using_none)
    else -> stringResource(R.string.ha_now_using, haEndpointName(endpoint.kind), endpoint.url)
}

@Composable
internal fun haPairingCodeDetail(code: HaPairingCode): String {
    val scenarios = pluralStringResource(R.plurals.ha_code_scenarios, code.scenarios, code.scenarios)
    val last = code.lastSeenAt?.let { stringResource(R.string.ha_code_last, relativeTimeText(it)) }
        ?: stringResource(R.string.ha_code_never)
    return stringResource(R.string.ha_code_detail, scenarios, last)
}

private const val HTTP_UNAUTHORIZED = 401
