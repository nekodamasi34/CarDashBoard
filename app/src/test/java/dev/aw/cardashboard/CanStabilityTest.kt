package dev.aw.cardashboard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CanStabilityTest {
    @Test fun normalObdRestoresDefaultsWithoutCanSettings() {
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"), ObdInitialization.commands)
        assertFalse(ObdInitialization.commands.contains("ATCAF0"))
        assertFalse(ObdInitialization.commands.contains("ATMA"))
        assertFalse(AdapterDiagnostics.commands.contains("ATCAF0"))
    }
    @Test fun setupCompletesBeforeMonitorAndRequiresEcu() = runBlocking {
        val sent = mutableListOf<String>()
        val results = mutableListOf<CommandDiagnostic>()
        CanMonitorSetup.apply(true, { sent.add(it); "OK\r" }, { results.add(it) })
        sent.add("ATMA")
        assertEquals(listOf("ATSP6", "ATE0", "ATL0", "ATS0", "ATH1", "ATCAF0", "ATD1", "ATMA"), sent)
        assertEquals(ResponseKind.OK, results.first { it.command == "ATCAF0" }.kind)
        sent.clear()
        try { CanMonitorSetup.apply(false, { sent.add(it); "OK" }, {}); fail() }
        catch (_: IllegalStateException) { }
        assertTrue(sent.isEmpty())
    }
    @Test fun unsupportedOrAbnormalCafNeverStartsMonitor() = runBlocking {
        for (bad in listOf("?", "ERROR", "", "garbage", "OK\rERROR", "OK\r?")) {
            val sent = mutableListOf<String>()
            val results = mutableListOf<CommandDiagnostic>()
            try {
                CanMonitorSetup.apply(true, { sent.add(it); if (it == "ATCAF0") bad else "OK" }, { results.add(it) })
                sent.add("ATMA"); fail(bad)
            } catch (_: IllegalStateException) { }
            assertEquals("ATCAF0", sent.last())
            assertNotEquals(ResponseKind.OK, results.last().kind)
        }
    }
    @Test fun disconnectDuringEachSettingStopsSequenceAndRecordsFailure() = runBlocking {
        CanMonitorSetup.commands.forEach { target ->
            val sent = mutableListOf<String>()
            val results = mutableListOf<CommandDiagnostic>()
            try {
                CanMonitorSetup.apply(true, { sent.add(it); if (it == target) throw IOException("Bluetooth切断"); "OK" }, { results.add(it) })
                fail()
            } catch (_: IOException) { }
            assertEquals(target, sent.last())
            assertEquals(ResponseKind.DISCONNECTED, results.last().kind)
        }
    }
    @Test fun cafTimeoutPreservesPartialRawAndStopsSetup() = runBlocking {
        val results = mutableListOf<CommandDiagnostic>()
        try {
            CanMonitorSetup.apply(true, { if (it == "ATCAF0") throw IOException("応答タイムアウト"); "OK" },
                { results.add(it) }, { "ATCAF0\rO" })
            fail()
        } catch (_: IOException) { }
        assertEquals("ATCAF0", results.last().command)
        assertEquals(ResponseKind.TIMEOUT, results.last().kind)
        assertEquals("ATCAF0\rO", results.last().raw)
    }
    @Test fun realCarSamplesWithAndWithoutSpaces() {
        val samples = listOf("0CEBFFFF271000FF" to (826.75f to 0.0), "0F8CFFFF2E8500FF" to (995f to 19.09))
        samples.forEach { (bytes, expected) ->
            for (raw in listOf("2018$bytes", "201 8 " + bytes.chunked(2).joinToString(" "))) {
                val f = CanFrameParser.parse(raw, 100)!!
                assertEquals(8, f.data.size)
                val v = Rx8CanDecoder().decode(f)!!
                assertEquals(expected.first, v.rpm, 0f)
                assertEquals(expected.second, v.speedKmh, .00001)
                assertEquals(0f, v.acceleratorPercent, 0f)
            }
        }
    }
    @Test fun corruptAndErrorLinesNeverProduceValues() {
        for (raw in listOf("20170CEBFFFF271000FF", "20180CEBFF", "20190CEBFFFF271000FF",
            "20180CEBFFFF271000FF<DATA ERROR", "20180F8CFFFF2E8500FF<DATA ERROR", "DATA ERROR", "CAN ERROR", "BUFFER FULL")) {
            assertNull(raw, CanFrameParser.parse(raw, 0))
            if ("ERROR" in raw || "FULL" in raw) assertNotNull(raw, MonitorLine.error(raw, false))
        }
        assertEquals("CAN ERROR", MonitorLine.error("20180CEBFFFF271000FF<CAN ERROR", false))
        assertEquals("BUFFER FULL", MonitorLine.error("20180CEBFFFF271000FF BUFFER FULL", false))
        assertNull(MonitorLine.error("20180CEBFFFF271000FF", false))
    }
    private fun state(time: Long, stale: Boolean? = null) = DashboardState(
        communication = CommunicationMode.CAN, bluetoothConnected = true, hudDisplayStale = stale,
        can = CanView(latest = CanFrameParser.parse("20180CEBFFFF271000FF", time)))
    @Test fun freshnessIsSeparateFromDisplayGrace() {
        val s = state(0)
        assertTrue(s.toHudState(2_999_999_999).dataFresh)
        val candidate = s.toHudState(3_000_000_000)
        assertFalse(candidate.dataFresh); assertTrue(candidate.staleCandidate)
        assertEquals(HudStatus.LIVE, candidate.status)
        assertEquals(HudStatus.LIVE, s.toHudState(3_999_999_999).status)
        assertEquals(HudStatus.STALE, s.toHudState(4_000_000_000).status)
    }
    @Test fun graceRecoveryAndNextGapRestartFromNewFrame() {
        val r = HudReception()
        assertFalse(r.received(0)); assertFalse(r.tick(3_500_000_000))
        assertFalse(r.received(3_900_000_000))
        assertFalse(r.tick(7_899_999_999)); assertTrue(r.tick(7_900_000_000))
    }
    @Test fun recoveryRequiresNewFramesAcrossHalfSecondAndResetsOnGap() {
        val r = HudReception(); r.received(0)
        assertTrue(r.tick(4_000_000_000))
        assertTrue(r.received(4_100_000_000))
        assertTrue(r.tick(4_600_000_000)) // time alone cannot recover
        assertTrue(r.received(4_600_000_000))
        assertTrue(r.received(4_850_000_000))
        assertTrue(r.received(4_850_000_000)) // duplicate cannot advance recovery
        assertFalse(r.received(5_100_000_000))
        assertFalse(r.tick(9_099_999_999)); assertTrue(r.tick(9_100_000_000))
    }
    @Test fun intermittentSingleFramesNeverFlickerBackToLive() {
        val r = HudReception(); r.received(0); r.tick(4_000_000_000)
        repeat(100) { assertTrue(r.received(5_000_000_000 + it * 300_000_000L)) }
        r.reset(); assertFalse(r.received(40_000_000_000))
    }
    @Test fun fatalErrorDisconnectAndDemoBypassDebounce() {
        val s = state(0, true)
        assertEquals(HudStatus.STALE, s.toHudState(1).status)
        assertEquals(HudStatus.ERROR, s.copy(communicationError = "CAN ERROR").toHudState(1).status)
        assertNull(s.copy(communicationError = "CAN ERROR").toHudState(1).values)
        assertEquals(HudStatus.OFFLINE, s.copy(bluetoothConnected = false).toHudState(1).status)
        assertNull(s.copy(bluetoothConnected = false).toHudState(1).values)
        assertEquals(HudStatus.DEMO, s.copy(demo = true).toHudState(1).status)
    }
}
