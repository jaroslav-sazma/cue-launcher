package app.cue.launcher.feature.settings.ha

import android.content.Context
import androidx.compose.foundation.text.input.clearText
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaConfigRepository
import app.cue.launcher.data.ha.HaSignIn
import app.cue.launcher.designsystem.FlashState
import app.cue.launcher.system.ha.HaConnectionTester

// Both credentials go at once, and the session is ended in Home Assistant too: half-removed access is
// worse than none, because the screen would keep claiming there is a connection. When the instance
// cannot be reached the local copy goes anyway, and the user is told where the session still lives.
internal suspend fun forget(
    screen: HaScreenState,
    configs: HaConfigRepository,
    setup: HaSetup,
    flash: FlashState,
) {
    val ended = setup.authFlow.signOut(configs.config())
    configs.clearToken()
    screen.tokenStored = false
    screen.sessionStored = false
    screen.token.clearText()
    screen.probes = emptyMap()
    if (!ended) flash.show(R.string.ha_revoke_failed, error = true)
}

internal suspend fun runProbe(screen: HaScreenState, tester: HaConnectionTester, stored: HaConfig?) {
    screen.testing = true
    screen.probes = tester.test(screen.draft(stored ?: HaConfig()))
    screen.testing = false
}

// The picked instance is remembered straight away: the approval that came with it is what the launcher
// needs to reach a local address at all, and losing it to an unsaved screen would be a silent step
// back to a permission dialog.
internal suspend fun discover(
    screen: HaScreenState,
    configs: HaConfigRepository,
    setup: HaSetup,
    flash: FlashState,
) {
    screen.discovering = true
    val instance = setup.discovery.pick()
    screen.discovering = false
    if (instance == null) {
        flash.show(R.string.ha_discover_none)
        return
    }
    screen.apply(instance)
    configs.saveInstance(uuid = instance.uuid, name = instance.label)
    setup.selector.invalidate()
    flash.show(R.string.ha_discover_found)
}

internal suspend fun signIn(
    screen: HaScreenState,
    configs: HaConfigRepository,
    setup: HaSetup,
    from: Context,
    flash: FlashState,
) {
    screen.signingIn = true
    val previous = configs.config()
    val result = setup.authFlow.signIn(screen.draft(previous), from)
    screen.signingIn = false
    if (result !is HaSignIn.Done) {
        if (result is HaSignIn.Failed) {
            flash.show(R.string.ha_signin_failed, error = true)
        } else {
            flash.show(R.string.ha_signin_cancelled)
        }
        return
    }
    // The address goes in with the session: signing in against an address that then stays unsaved
    // would leave a credential for an instance the launcher does not know about.
    val saved = configs.save(
        urlInternal = screen.urlInternal,
        urlExternal = screen.urlExternal,
        token = "",
        homeNetworks = screen.homeNetworks,
    ) && configs.saveSession(result.tokens.refreshToken, result.clientId)
    if (!saved) {
        flash.show(R.string.ha_save_failed, error = true)
        return
    }
    screen.sessionStored = true
    screen.tokenStored = false
    screen.token.clearText()
    screen.probes = emptyMap()
    screen.markSaved()
    setup.selector.invalidate()
    // The old session is ended only once the new one is stored, so a failed save never leaves the
    // launcher holding a token it has just revoked.
    val ended = previous.refreshToken == result.tokens.refreshToken || setup.authFlow.signOut(previous)
    if (ended) {
        flash.show(R.string.ha_signin_saved)
    } else {
        flash.show(R.string.ha_revoke_failed, error = true)
    }
}
