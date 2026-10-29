package app.cue.launcher.system.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

// A connection of its own for one push made while no session is up: hello, the message and bye. What
// the Mac sends meanwhile goes to drained, everything else of a session stays undone.
internal class OneShotPush(
    private val name: String,
    private val log: (String) -> Unit = BridgeLog::event,
    private val drained: suspend (BridgeInbound) -> Unit = {},
) {

    // Each carrier gets the whole cap: the TCP attempt to a Mac on a network that drops the phone's
    // packets uses all of it, which would otherwise leave nothing for Bluetooth. Without a transfer
    // budget the cap covers the whole exchange; with one, only the connect and handshake.
    suspend fun push(
        carrier: Carrier,
        hello: BridgeMessage.Hello,
        message: BridgeMessage,
        transferMillis: Long?,
        connect: suspend () -> BridgeConnection,
    ) {
        val delivered = try {
            if (transferMillis == null) {
                withTimeoutOrNull(ONE_SHOT_TIMEOUT_MS) { deliver(opened(connect), hello, message) }
            } else {
                connectThenTransfer(transferMillis, { opened(connect) }) { deliver(it, hello, message) }
            }
        } catch (failure: IOException) {
            log("$name could not connect over ${carrier.tag}: ${failure.javaClass.simpleName}")
            throw failure
        }
        if (delivered != true) {
            log("$name over ${carrier.tag} ${if (delivered == null) "timed out" else "not delivered"}")
            throw IOException("the Mac did not take the push over ${carrier.tag}")
        }
    }

    private suspend fun opened(connect: suspend () -> BridgeConnection): BridgeConnection = try {
        connect()
    } catch (failure: GeneralSecurityException) {
        throw IOException("the phone's keys cannot be used", failure)
    } catch (failure: ProviderException) {
        throw IOException("the phone's keys cannot be used", failure)
    }

    // Blocking reads do not notice cancellation, so a watcher closes the socket when the caller goes.
    private suspend fun deliver(
        connection: BridgeConnection,
        hello: BridgeMessage.Hello,
        message: BridgeMessage,
    ): Boolean = coroutineScope {
        val watcher = launch(Dispatchers.IO) {
            try {
                awaitCancellation()
            } finally {
                connection.close()
            }
        }
        try {
            withContext(Dispatchers.IO) { converse(connection, hello, message) }
        } finally {
            watcher.cancel()
            connection.close()
        }
    }

    // The bye is followed by reading until the Mac closes: closing first, with something of the Mac's
    // still unread, would reset the connection and could take the message down with it.
    private suspend fun converse(
        connection: BridgeConnection,
        hello: BridgeMessage.Hello,
        message: BridgeMessage,
    ): Boolean = try {
        send(connection, hello)
        when (val opening = sessionOpening(connection.read())) {
            SessionOpening.Established -> {
                send(connection, message)
                send(connection, BridgeMessage.Bye())
                drain(connection)
                true
            }
            is SessionOpening.Refused -> {
                opening.bye?.let { send(connection, it) }
                false
            }
        }
    } catch (failure: IOException) {
        log("$name failed: ${failure.javaClass.simpleName}")
        false
    }

    private fun send(connection: BridgeConnection, message: BridgeMessage) {
        connection.send(message)
        log(BridgeLog.sentLine(name, message))
    }

    private suspend fun drain(connection: BridgeConnection) {
        try {
            connection.setReadTimeout(DRAIN_TIMEOUT_MS)
            do {
                val inbound = connection.read()?.also { drained(it) }
            } while (inbound != null)
        } catch (_: IOException) {
            log("$name closed before the Mac did")
        }
    }

    private companion object {
        const val DRAIN_TIMEOUT_MS = 1_000
    }
}
