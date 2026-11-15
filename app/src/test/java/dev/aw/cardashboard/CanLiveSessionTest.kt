package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class CanLiveSessionTest {
    @Test fun continuousTimingHasNoDeadlineButDoesNotAuthorizeLiveSession() {
        val lifetime = CanReceiveLifetime(null)
        assertFalse(lifetime.receiving(0))
        lifetime.start(100)
        listOf(100L, 30_000_000_100L, 86_400_000_000_100L, 604_800_000_000_100L).forEach {
            assertTrue(lifetime.receiving(it))
            assertFalse(lifetime.expired(it))
        }
        assertThrows(IllegalStateException::class.java) { lifetime.start(200) }
        assertFalse(CanLiveSafety.permits(null))
    }
    @Test fun allValidationDeadlinesAndStopBeforeStart() {
        CanLiveSafety.validationSeconds.forEach { seconds ->
            val live = CanLiveSession(seconds)
            assertFalse(live.receiving(0))
            live.started(100)
            val end = 100 + seconds * 1_000_000_000L
            assertTrue(live.receiving(end - 1))
            assertFalse(live.receiving(end))
            live.stop()
            assertFalse(live.receiving(100))
        }
    }
    @Test fun unlimitedAndUnapprovedDurationsAreBlocked() {
        assertFalse(CanLiveSafety.continuousAllowed)
        listOf(null, 0, -1, 60).forEach { seconds ->
            assertFalse(CanLiveSafety.permits(seconds))
            assertThrows(IllegalArgumentException::class.java) { CanLiveSession(seconds) }
        }
    }
    @Test fun explicitStartDeadlineAndStopAreIndependentOfBenchmark() {
        val live = CanLiveSession(15)
        live.started(100)
        assertTrue(live.receiving(5_000_000_100))
        assertTrue(CanExperiment(100).expired(5_000_000_100))
        assertFalse(live.receiving(15_000_000_100))
        live.stop()
        assertFalse(live.requested)
        assertFalse(live.receiving(100))
        assertThrows(IllegalStateException::class.java) { live.started(200) }
    }
    @Test fun cancelledSetupCannotStart() {
        val live = CanLiveSession(5)
        live.stop()
        assertThrows(IllegalStateException::class.java) { live.started(0) }
    }
    @Test fun liveFramesUpdateHudWithoutBenchmarkCapture() {
        val manager = CanBenchmarkManager()
        manager.connected(0)
        val frame = CanFrame(0x201, listOf(0x5D, 0xC0, 0, 0, 0x4E, 0x20, 100, 0), 1)
        manager.record(frame)
        val view = manager.snapshot(1_000_000_000)
        assertFalse(view.running)
        assertNull(view.result)
        val state = DashboardState(communication = CommunicationMode.CAN, bluetoothConnected = true, can = view)
        val hud = state.toHudState(100)
        assertEquals(HudStatus.LIVE, hud.status)
        assertEquals(6000f, hud.values!!.rpm, 0f)
        assertEquals(100.0, hud.values.speedKmh, 0.0)
        assertEquals(50f, hud.values.acceleratorPercent, 0f)
        assertEquals(HudStatus.STALE, state.toHudState(4_000_000_000).status)
        assertNull(state.copy(communicationError = "CAN ERROR").toHudState(100).values)
        manager.disconnected(200)
        assertNull(manager.snapshot(200).latest)
        assertEquals(HudStatus.OFFLINE, state.resetConnection().toHudState(200).status)
    }
    @Test fun pidIsBlockedThroughoutMonitorAndStop() {
        val session = CanMonitorSession()
        session.reconnect(); session.restored(); session.setup()
        assertFalse(session.permitsPid)
        assertTrue(session.authorize(true, CommandSupport.UNSUPPORTED, CommandSupport.SUPPORTED, false, true))
        assertFalse(session.permitsPid)
        session.stop()
        assertFalse(session.permitsPid)
        session.disconnected()
        assertFalse(session.permitsPid)
    }
}
