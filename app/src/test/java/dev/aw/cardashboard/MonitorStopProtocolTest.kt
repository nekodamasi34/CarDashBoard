package dev.aw.cardashboard

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MonitorStopProtocolTest {
    @Test fun singleCharacterThenPromptBeforeRestoration() = runBlocking {
        val events = mutableListOf<String>()
        MonitorStopProtocol.finish(false, write = { assertArrayEquals(byteArrayOf(13), it); events.add("stop") },
            prompt = { events.add("prompt") })
        val m = CanMonitorSession(); m.setup()
        m.authorize(true, CommandSupport.UNSUPPORTED, CommandSupport.SUPPORTED, false, true)
        m.stop(); m.reconnect(); m.restored(); events.add("obd")
        assertEquals(listOf("stop", "prompt", "obd"), events); assertTrue(m.permitsPid)
    }
    @Test fun unsupportedAlreadyReturnedPromptDoesNotSendStopAsCommand() = runBlocking {
        MonitorStopProtocol.finish(true, write = { fail("unexpected write") }, prompt = { fail("unexpected wait") })
    }
    @Test fun stopTimeoutRequiresSocketRetirement() = runBlocking {
        var retired = false
        try {
            MonitorStopProtocol.finish(false, 10, write = { }, prompt = { awaitCancellation() })
            fail("timeout expected")
        } catch (_: TimeoutCancellationException) {
            BluetoothRecovery.retire(null) { retired = true }
        }
        assertTrue(retired)
    }
    @Test fun disconnectedPromptFailsWithoutAnotherCommand() = runBlocking {
        var writes = 0
        try {
            MonitorStopProtocol.finish(false, write = { writes++ }, prompt = { throw java.io.IOException("disconnected") })
            fail("disconnect expected")
        } catch (_: java.io.IOException) { }
        assertEquals(1, writes)
    }
}
