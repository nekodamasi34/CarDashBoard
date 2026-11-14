package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardStateTest {
    @Test fun disconnectClearsTelemetryAndAcknowledgements() {
        val old = DashboardState(connected = true, bluetoothConnected = true, rpm = 1500f,
            speedKmh = 10, coolantC = 85, rpmHz = 10f, rpmResponseMs = 90f, rpmAverageMs = 95f,
            appliedMode = PollingMode.BALANCED, appliedTiming = AdaptiveTiming.ATAT2,
            mode = PollingMode.BALANCED, timing = AdaptiveTiming.ATAT2)
        val reset = old.resetConnection()
        assertFalse(reset.connected); assertFalse(reset.bluetoothConnected); assertFalse(reset.busy)
        assertNull(reset.rpm); assertNull(reset.speedKmh); assertNull(reset.coolantC); assertNull(reset.rpmHz)
        assertNull(reset.rpmResponseMs); assertNull(reset.rpmAverageMs); assertNull(reset.appliedTiming); assertNull(reset.appliedMode)
        assertEquals(old.mode, reset.mode); assertEquals(old.timing, reset.timing)
    }
}
