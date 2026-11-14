package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class BenchmarkManagerTest {
    private fun sample(startMs: Long, durationMs: Long = 10, success: Boolean = true, timeout: Boolean = false, pid: Pid = Pid.RPM) =
        RequestSample(pid, startMs * 1_000_000, (startMs + durationMs) * 1_000_000, success, timeout)
    @Test fun statisticsUseActualRequestsIncludingFailedLatencies() {
        val samples = listOf(sample(0, 10), sample(100, 20), sample(200, 30), sample(300, 100, false, true),
            sample(500, pid = Pid.SPEED), sample(600, pid = Pid.COOLANT))
        val s = BenchmarkStatistics.calculate(samples, 0, 1_000_000_000)
        assertEquals(3.0, s.rpmHz, 0.0); assertEquals(1.0, s.speedHz, 0.0); assertEquals(1.0, s.coolantHz, 0.0)
        assertEquals(6.0, s.commandsHz, 0.0); assertEquals(40.0, s.meanMs!!, 0.0)
        assertEquals(25.0, s.medianMs!!, 0.0); assertEquals(100.0, s.p95Ms!!, 0.0)
        assertEquals(100.0, s.maxMs!!, 0.0); assertEquals(100.0, s.latestMs!!, 0.0)
        assertEquals(5, s.success); assertEquals(1, s.failed); assertEquals(1, s.timeouts)
        assertEquals(500.0 / 6, s.successPercent!!, .0001); assertEquals(0.0, s.jitterMs!!, 0.0)
    }
    @Test fun nearestRankP95AndOddMedian() {
        val s = BenchmarkStatistics.calculate((1L..21L).map { sample(it * 100, it) }, 0, 3_000_000_000)
        assertEquals(20.0, s.p95Ms!!, 0.0); assertEquals(11.0, s.medianMs!!, 0.0)
    }
    @Test fun jitterUsesSuccessfulReceptionIntervals() {
        val s = BenchmarkStatistics.calculate(listOf(sample(0), sample(100), sample(200, success = false), sample(400)), 0, 1_000_000_000)
        assertEquals(100.0, s.jitterMs!!, 0.0)
    }
    @Test fun emptyAndZeroTimeHaveNoInventedLatencyOrSuccessRate() {
        val s = BenchmarkStatistics.calculate(emptyList(), 0, 0)
        assertEquals(0.0, s.rpmHz, 0.0); assertNull(s.meanMs); assertNull(s.successPercent); assertNull(s.jitterMs)
    }
    @Test fun rollingWindowExpiresOldReadings() {
        val m = BenchmarkManager(); m.connected(0); m.record(sample(0))
        assertEquals(1, m.snapshot(1_000_000_000).live.success)
        assertEquals(0, m.snapshot(6_000_000_000).live.success)
    }
    @Test fun runEndsAt30SecondsAndExcludesBoundaryCrossingRequests() {
        val m = BenchmarkManager(); m.connected(0); m.start(1_000_000_000, PollingMode.RPM_ONLY, AdaptiveTiming.ATAT2)
        m.record(sample(990, 20)); m.record(sample(1000)); m.record(sample(30_990, 20))
        val v = m.snapshot(32_000_000_000)
        assertFalse(v.running); val r = v.result!!
        assertEquals(30.0, r.stats.seconds, 0.0); assertEquals(1, r.stats.success)
        assertEquals(1.0 / 30, r.stats.rpmHz, .00001); assertEquals(AdaptiveTiming.ATAT2, r.timing)
        assertEquals(PollingMode.RPM_ONLY, r.mode); assertEquals("30秒完了", r.reason)
    }
    @Test fun disconnectStopsRunAndClearsOnlyLiveMetrics() {
        val m = BenchmarkManager(); m.connected(0); m.start(0, PollingMode.BALANCED, AdaptiveTiming.ATAT1)
        m.record(sample(0)); m.record(sample(100, success = false, timeout = true)); m.disconnected(1_000_000_000)
        val v = m.snapshot(2_000_000_000)
        assertFalse(v.running); assertEquals(0, v.live.success); assertNull(v.live.meanMs)
        assertEquals(1, v.result!!.stats.success); assertEquals(1, v.result!!.stats.timeouts)
        assertEquals("通信切断", v.result!!.reason)
    }
    @Test fun manualStopPreservesElapsedTimeAndCsvConditions() {
        val m = BenchmarkManager(); m.start(0, PollingMode.RPM_PRIORITY, AdaptiveTiming.ATAT2)
        m.record(sample(0)); m.stop(2_000_000_000)
        val r = m.snapshot(3_000_000_000).result!!
        assertEquals(2.0, r.stats.seconds, 0.0); assertEquals(.5, r.stats.rpmHz, 0.0)
        assertTrue(r.csv().contains("RPM_PRIORITY")); assertTrue(r.csv().contains("ATAT2"))
        assertEquals(3, r.csv().split("\r\n").size)
    }
    @Test fun doubleStartDoesNotChangeActiveConditions() {
        val m = BenchmarkManager(); m.start(0, PollingMode.RPM_ONLY, AdaptiveTiming.ATAT1)
        m.start(1_000_000_000, PollingMode.BALANCED, AdaptiveTiming.ATAT2); m.stop(2_000_000_000)
        assertEquals(PollingMode.RPM_ONLY, m.snapshot(2_000_000_000).result!!.mode)
    }
}
