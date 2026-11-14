package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class CanMonitorSessionTest {
    private val supported = CommandSupport.SUPPORTED
    @Test fun supportedSilentRequiresTrustedEvidence() {
        val m = CanMonitorSession(); m.setup()
        assertTrue(m.authorize(true, supported, supported, true)); assertEquals(MonitorPhase.MONITORING, m.phase)
    }
    @Test fun unsupportedSilentBlocksUnverifiedAdapter() {
        val m = CanMonitorSession(); m.setup()
        assertFalse(m.authorize(true, CommandSupport.UNSUPPORTED, supported, false)); assertEquals(MonitorPhase.BLOCKED, m.phase)
    }
    @Test fun evenOkDoesNotProveCloneHardwareIsSilent() {
        val m = CanMonitorSession(); m.setup(); assertFalse(m.authorize(true, supported, supported, false))
    }
    @Test fun futureBenchVerifiedCompatibility() {
        val m = CanMonitorSession(); m.setup(); assertTrue(m.authorize(true, CommandSupport.UNSUPPORTED, supported, true))
    }
    @Test fun unsupportedFilterNeverFallsBackToUnfilteredMonitor() {
        val m = CanMonitorSession(); m.setup(); assertFalse(m.authorize(true, supported, CommandSupport.UNSUPPORTED, true))
    }
    @Test fun communicationFaultCannotAuthorize() {
        val m = CanMonitorSession(); m.setup(); assertFalse(m.authorize(true, CommandSupport.ABNORMAL, supported, true))
    }
    @Test fun protocolMustBeConfirmed() {
        val m = CanMonitorSession(); m.setup(); assertFalse(m.authorize(false, supported, supported, true))
    }
    @Test fun stopReconnectAndObdRestore() {
        val m = CanMonitorSession(); m.setup(); m.authorize(true, supported, supported, true)
        m.stop(); assertEquals(MonitorPhase.STOPPING, m.phase); assertFalse(m.permitsPid)
        m.reconnect(); assertFalse(m.permitsPid); m.restored(); assertTrue(m.permitsPid)
    }
    @Test fun bluetoothDisconnectInvalidatesSession() {
        val m = CanMonitorSession(); m.setup(); m.disconnected(); assertFalse(m.permitsPid)
        assertEquals(MonitorPhase.DISCONNECTED, m.phase)
    }
    @Test(expected = IllegalStateException::class) fun concurrentSetupRejected() {
        val m = CanMonitorSession(); m.setup(); m.setup()
    }
}
