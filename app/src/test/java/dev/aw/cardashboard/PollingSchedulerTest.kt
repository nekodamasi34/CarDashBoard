package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class PollingSchedulerTest {
    @Test fun rpmOnlyNeverRequestsOtherPids() {
        val s = PollingScheduler()
        repeat(1000) { assertEquals(Pid.RPM, s.next(it * 100_000_000L, PollingMode.RPM_ONLY)) }
    }
    @Test fun priorityUsesMonotonicDeadlinesAndInterleavesRpm() {
        val s = PollingScheduler(); val mode = PollingMode.RPM_PRIORITY
        assertEquals(Pid.RPM, s.next(0, mode))
        assertEquals(Pid.SPEED, s.next(10_000_000, mode))
        assertEquals(Pid.RPM, s.next(20_000_000, mode))
        assertEquals(Pid.COOLANT, s.next(30_000_000, mode))
        repeat(10) { assertEquals(Pid.RPM, s.next(40_000_000, mode)) }
        assertEquals(Pid.SPEED, s.next(510_000_000, mode))
        assertEquals(Pid.RPM, s.next(520_000_000, mode))
        assertEquals(Pid.RPM, s.next(1_000_000_000, mode))
    }
    @Test fun balancedSpeedDueAfter250ms() {
        val s = PollingScheduler(); val m = PollingMode.BALANCED
        s.next(0, m); s.next(0, m); s.next(0, m); s.next(0, m); s.next(0, m)
        assertEquals(Pid.RPM, s.next(249_000_000, m))
        assertEquals(Pid.SPEED, s.next(250_000_000, m))
    }
    @Test fun modeSwitchResetsDeadlinesWithoutCatchUpBurst() {
        val s = PollingScheduler()
        s.next(0, PollingMode.RPM_PRIORITY); s.next(1, PollingMode.RPM_PRIORITY)
        assertEquals(Pid.RPM, s.next(2, PollingMode.RPM_ONLY))
        assertEquals(Pid.RPM, s.next(3, PollingMode.BALANCED))
        assertEquals(Pid.SPEED, s.next(4, PollingMode.BALANCED))
        assertEquals(Pid.RPM, s.next(10_000_000_000, PollingMode.BALANCED))
        assertEquals(Pid.SPEED, s.next(10_000_000_001, PollingMode.BALANCED))
        assertEquals(Pid.RPM, s.next(10_000_000_002, PollingMode.BALANCED))
        assertEquals(Pid.COOLANT, s.next(10_000_000_003, PollingMode.BALANCED))
    }
    @Test fun coolantPeriodUsesSelectedMode() {
        for (m in listOf(PollingMode.RPM_PRIORITY, PollingMode.BALANCED)) {
            val s = PollingScheduler()
            s.next(0, m); s.next(0, m); s.next(0, m)
            assertEquals(Pid.COOLANT, s.next(0, m)); s.next(0, m)
            s.next(m.coolantNs, m); s.next(m.coolantNs, m)
            assertEquals(Pid.COOLANT, s.next(m.coolantNs, m))
        }
    }
}
