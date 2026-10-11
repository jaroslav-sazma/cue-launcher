package app.cue.launcher.feature.settings.ha

import androidx.compose.runtime.Stable
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaEventLogRepository
import app.cue.launcher.feature.scenarios.builder.HaActionTester
import app.cue.launcher.feature.scenarios.builder.HaTestResult
import app.cue.launcher.feature.scenarios.builder.haConfirmedTest
import app.cue.launcher.feature.scenarios.builder.haTestResultOf
import app.cue.launcher.system.ActionRoute
import app.cue.launcher.system.actionRouteOf
import app.cue.launcher.system.ha.HaAuthFlow
import app.cue.launcher.system.ha.HaBlueprintUploader
import app.cue.launcher.system.ha.HaConnectionTester
import app.cue.launcher.system.ha.HaDiscovery
import app.cue.launcher.system.ha.HaEndpointSelector
import app.cue.launcher.system.ha.HaGateway
import app.cue.launcher.system.ha.HaStateReader

@Stable
class HaServices(
    val configs: HaConfigRepository,
    val log: HaEventLogRepository,
    val gateway: HaGateway,
    val states: HaStateReader,

    // Everything only the Home Assistant screen needs: setting the connection up and proving it works.
    val setup: HaSetup,
)

@Stable
class HaSetup(
    val tester: HaConnectionTester,
    val selector: HaEndpointSelector,
    val discovery: HaDiscovery,
    val authFlow: HaAuthFlow,
    val blueprint: HaBlueprintUploader,
)

fun haActionTester(gateway: HaGateway): HaActionTester = HaActionTester { action ->
    when (val route = actionRouteOf(action)) {
        is ActionRoute.HaService -> haConfirmedTest(action, gateway::confirm) {
            haTestResultOf(gateway.callService(route.call))
        }

        else -> HaTestResult.Failed(null)
    }
}
