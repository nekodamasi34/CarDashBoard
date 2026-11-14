package dev.aw.cardashboard

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BluetoothRecoveryTest {
    @Test fun reconnectBackoffBounded() {
        assertEquals(1000L, BluetoothRecovery.waitMs(1)); assertEquals(2000L, BluetoothRecovery.waitMs(2))
        assertEquals(3000L, BluetoothRecovery.waitMs(99))
    }
    @Test fun sessionRaceRejectsStaleReader() {
        assertFalse(BluetoothRecovery.accepts(2, 1)); assertTrue(BluetoothRecovery.accepts(2, 2))
    }
    @Test fun manualDisconnectAndTimeoutBoundRetries() {
        assertFalse(CommunicationHealth.retry(true, CommunicationMode.OBD, 1))
        assertTrue(CommunicationHealth.retry(false, CommunicationMode.OBD, 1))
        assertFalse(CommunicationHealth.retry(false, CommunicationMode.OBD, 3))
        assertFalse(CommunicationHealth.retry(false, CommunicationMode.CAN, 1))
    }
    @Test fun closePrecedesReaderJoin() = runBlocking {
        val events = mutableListOf<String>()
        val reader = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { events.add("reader ended") } }
        BluetoothRecovery.retire(reader) { events.add("socket closed") }
        assertTrue(reader.isCompleted); assertEquals(listOf("socket closed", "reader ended"), events)
    }
    @Test fun closeFailureStillJoins() = runBlocking {
        val reader = launch { awaitCancellation() }
        BluetoothRecovery.retire(reader) { error("socket close error") }
        assertTrue(reader.isCompleted)
    }
    @Test fun cancellationStillClosesAndJoins() = runBlocking {
        var closed = false
        val reader = launch { awaitCancellation() }
        val owner = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { BluetoothRecovery.retire(reader) { closed = true } }
        }
        owner.cancelAndJoin(); assertTrue(closed); assertTrue(reader.isCompleted)
    }
}
