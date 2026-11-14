package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class CanExperimentTest {
    private fun allowed(silent: String, filter: String = "OK>", consent: Boolean = true, protocol: Boolean = true): Boolean {
        val m = CanMonitorSession(); m.setup()
        return m.authorize(protocol, AdapterDiagnostics.classify("ATCSM1", silent).support,
            AdapterDiagnostics.classify("ATCRA201", filter).support, false, consent)
    }
    @Test fun silentOkExperimentalOnly() { assertTrue(allowed("OK>")); assertFalse(allowed("OK>", consent = false)) }
    @Test fun silentUnsupportedExplicitConsentOnly() { assertTrue(allowed("?>")); assertFalse(allowed("?>", consent = false)) }
    @Test fun silentFaultAborts() { for (s in listOf("ERROR>", "", "OK", "?\rERROR>", "?\rOK>")) assertFalse(allowed(s)) }
    @Test fun filterUnsupportedAborts() { assertFalse(allowed("?>", "?>")); assertTrue(allowed("?>", "OK>")) }
    @Test fun protocolMismatchAborts() { assertFalse(allowed("OK>", protocol = false)) }
    @Test fun monotonicFiveSecondsNoFrameOrUiDependency() {
        val e = CanExperiment(100)
        assertFalse(e.expired(5_000_000_099)); assertTrue(e.expired(5_000_000_100))
        assertEquals(0.0, e.remaining(9_000_000_000), 0.0)
    }
    @Test fun atmaUnsupportedAndEcho() {
        assertEquals("ATMA未対応 (?)", MonitorLine.error("?", false))
        assertTrue(MonitorLine.ignored("ATMA", false)); assertNull(CanFrameParser.parse("ATMA", 1))
    }
    @Test fun individualMonitorFailures() {
        assertEquals("BUFFER FULL", MonitorLine.error("2018FFFF BUFFER FULL", false))
        assertEquals("CAN ERROR", MonitorLine.error("CAN ERROR", false))
        assertNotNull(MonitorLine.error("STOPPED", false))
        assertNull(MonitorLine.error("STOPPED", true)); assertTrue(MonitorLine.ignored("STOPPED", true))
    }
    @Test fun rawArrivalMetricsAndCsv() {
        val m = CanBenchmarkManager(); m.connected(0); m.start(0, CanExperiment.DURATION_NS)
        for (t in listOf(0L, 10_000_000L, 40_000_000L)) {
            val raw = "201 8 0000000027100000"; val f = CanFrameParser.parse(raw, t)!!
            m.rawRow(t, raw, f); m.record(f)
        }
        m.rawRow(50, "bad,\"line", null); m.invalid(50); m.error(60)
        m.stop(5_000_000_000, "5秒自動停止")
        val r = m.snapshot(8_000_000_000).result!!
        assertEquals(5.0, r.stats.seconds, 0.0); assertEquals(.6, r.stats.hz, .0001)
        assertEquals(20.0, r.stats.meanMs!!, .0001); assertEquals(30.0, r.stats.p95Ms!!, .0001)
        assertEquals(0.0, r.stats.rpmChangedHz, .0); assertEquals(1L, r.stats.errors)
        assertTrue(r.csv().contains("ATCSM1")); assertTrue(r.rawCsv().contains("bad,\"\"line"))
    }
    @Test fun zeroFramesAndManualStop() {
        val m = CanBenchmarkManager(); m.connected(0); m.start(0, CanExperiment.DURATION_NS)
        m.stop(2_000_000_000, "手動停止"); val r = m.snapshot(3_000_000_000).result!!
        assertEquals(0L, r.stats.frames); assertEquals(0.0, r.stats.hz, .0)
        assertNull(r.stats.p95Ms); assertEquals("手動停止", r.reason); assertEquals(2.0, r.stats.seconds, .0)
    }
    @Test fun rawBoundAndDeadline() {
        val m = CanBenchmarkManager(); m.connected(0); m.start(0, CanExperiment.DURATION_NS)
        repeat(10003) { m.rawRow(it.toLong(), "bad", null) }
        m.rawRow(5_000_000_001, "late", null); m.stop(5_000_000_000)
        val r = m.snapshot(6_000_000_000).result!!
        assertEquals(10000, r.rows.size); assertEquals(3L, r.dropped)
    }
}
