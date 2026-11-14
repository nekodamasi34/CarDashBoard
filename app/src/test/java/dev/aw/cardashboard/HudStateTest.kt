package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class HudStateTest {
    private fun frame(rpmWord: Int = 24000, speedWord: Int = 22345, pedal: Int = 125) =
        CanFrame(0x201, listOf(rpmWord shr 8, rpmWord and 255, 0, 0,
            speedWord shr 8, speedWord and 255, pedal, 0), 1_000_000_000)
    private fun live(f: CanFrame = frame()) = DashboardState(communication = CommunicationMode.CAN,
        bluetoothConnected = true, can = CanView(latest = f, live = CanStats(hz = 42.0)))

    @Test fun decodesAllThreeSignals() {
        val v = Rx8CanDecoder().decode(frame())!!
        assertEquals(6000f, v.rpm, 0f)
        assertEquals(123.45, v.speedKmh, .0001)
        assertEquals(62.5f, v.acceleratorPercent, 0f)
    }
    @Test fun clampsSpeedAndAcceleratorButRetainsActualRpmAboveDial() {
        val v = Rx8CanDecoder().decode(frame(65535, 0, 255))!!
        assertEquals(0.0, v.speedKmh, 0.0)
        assertEquals(100f, v.acceleratorPercent, 0f)
        assertEquals(16383.75f, v.rpm, 0f)
        assertEquals(0f, Rx8CanDecoder().decode(frame(pedal = 0))!!.acceleratorPercent, 0f)
    }
    @Test fun rejectsWrongIdAndTruncatedFrames() {
        assertNull(Rx8CanDecoder().decode(frame().copy(id = 0x202)))
        assertNull(Rx8CanDecoder().decode(frame().copy(data = listOf(0, 0))))
    }
    @Test fun offlineAndWaitingDoNotExposeObdValues() {
        assertEquals(HudStatus.OFFLINE, DashboardState().toHudState(0).status)
        val s = DashboardState(bluetoothConnected = true, connected = true, rpm = 3000f)
        assertEquals("OBD2", s.toHudState(0).source)
        assertEquals(HudStatus.WAITING, s.toHudState(0).status)
        assertNull(s.toHudState(0).values)
    }
    @Test fun zeroRpmIsLiveAndHzIsMeasured() {
        val s = live(frame(0, 10000, 0)).toHudState(2_000_000_000)
        assertEquals(HudStatus.LIVE, s.status)
        assertEquals(0f, s.values!!.rpm, 0f)
        assertEquals(42.0, s.measuredHz!!, 0.0)
        assertEquals(1_000_000_000L, s.lastUpdatedNs)
    }
    @Test fun freshnessBoundaryAndStaleRetention() {
        assertEquals(HudStatus.LIVE, live().toHudState(4_000_000_000).status)
        val stale = live().toHudState(4_000_000_001)
        assertEquals(HudStatus.STALE, stale.status)
        assertNotNull(stale.values)
        assertEquals(HudStatus.STALE, live().toHudState(0).status)
    }
    @Test fun demoIsIsolatedFromCanAndHasNoMeasuredHz() {
        val values = Rx8CanValues(9000f, 100.0, 75f)
        val s = live().copy(demo = true, demoHud = values, demoUpdatedNs = 10).toHudState(20)
        assertEquals(HudStatus.DEMO, s.status)
        assertEquals("DEMO", s.source)
        assertEquals(values, s.values)
        assertNull(s.measuredHz)
    }
    @Test fun errorsAndModeSwitchClearHudValues() {
        assertEquals(HudStatus.ERROR, live().copy(communicationError = "failure").toHudState(0).status)
        assertEquals(HudStatus.ERROR, live().copy(can = CanView(live = CanStats(errors = 1))).toHudState(0).status)
        assertNull(live().copy(communication = CommunicationMode.OBD).toHudState(0).values)
        assertNull(live().resetConnection().toHudState(0).values)
        assertEquals(HudStatus.WAITING, live(frame().copy(data = listOf(0))).toHudState(0).status)
    }
}
