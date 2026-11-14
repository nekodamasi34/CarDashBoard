package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class CanTest {
    private fun frame(t: Long, rpm: Int = 0) = CanFrame(0x201, listOf(rpm / 256, rpm % 256, 0, 0, 0x27, 0x10, 0, 0), t)
    @Test fun headersSpacesDlcAndMultipleFrames() {
        for (raw in listOf("201 8 00 00 00 00 27 10 00 00", "20180000000027100000", "2010000000027100000")) {
            val f = CanFrameParser.parse(raw, 123)!!
            assertEquals(0x201, f.id); assertEquals(8, f.data.size); assertEquals(123L, f.receivedNs)
            assertEquals("00 00 00 00 27 10 00 00", f.hex)
        }
        assertEquals(2, CanFrameParser.parseLines("201 8 0000000027100000\r\n2010000000027100000\r", 1).size)
        assertEquals(0x7e8, CanFrameParser.parse("7E8 03 41 0C", 1)!!.id)
    }
    @Test fun malformedAndDlcMismatchRejected() {
        for (raw in listOf("", " ", "?", "NO DATA", "2018GG00000027100000", "20170000000027100000", "FFF00", "2019000000000000000000")) {
            assertNull(raw, CanFrameParser.parse(raw, 0))
        }
        assertNull(Rx8CanDecoder().decode(CanFrame(0x201, listOf(0, 0), 0)))
    }
    @Test fun decodeCandidatesAreConfigurableAndZeroIsValid() {
        val decoder = Rx8CanDecoder()
        assertEquals(0f, decoder.decode(frame(0))!!.rpm)
        assertEquals(0.0, decoder.decode(frame(0))!!.speedKmh, 0.0)
        assertEquals(1500f, decoder.decode(frame(0, 6000))!!.rpm)
        assertEquals(3000f, Rx8CanDecoder(rpmDivisor = 2.0).decode(frame(0, 6000))!!.rpm)
    }
    @Test fun intervalsP95MissingFramesAndRpmChanges() {
        val m = CanBenchmarkManager(); m.connected(0)
        listOf(0L, 10L, 20L, 50L).forEachIndexed { i, t -> m.record(frame(t * 1_000_000, if (i >= 2) 6000 else 0)) }
        val s = m.snapshot(100_000_000).live
        assertEquals(40.0, s.hz, .0001)
        assertEquals(10.0, s.rpmChangedHz, .0001)
        assertEquals(50.0 / 3, s.meanMs!!, .0001)
        assertEquals(30.0, s.p95Ms!!, .0); assertEquals(30.0, s.maxMs!!, .0)
        assertEquals(10.0, s.minMs!!, .0); assertTrue(s.stddevMs!! > 0)
        // No invented lost-frame count: the gap is included in actual arrival intervals.
    }
    @Test fun thirtySecondsCsvErrorsAndBoundedRaw() {
        val m = CanBenchmarkManager(); m.connected(0); m.start(0)
        for (i in 0..300) m.record(frame(i * 100_000_000L))
        m.invalid(1); m.error(2)
        m.record(frame(31_000_000_000L))
        val view = m.snapshot(32_000_000_000L)
        assertFalse(view.running); assertEquals(35, view.raw.size)
        val r = view.result!!
        assertEquals(30.0, r.stats.seconds, .0); assertEquals(301L, r.stats.frames)
        assertEquals(1L, r.stats.invalid); assertEquals(1L, r.stats.errors)
        assertTrue(r.csv().contains("arrival_hz")); assertTrue(r.csv().contains("\"0x201\""))
        m.disconnected(33_000_000_000L)
        assertNull(m.snapshot(34_000_000_000L).latest)
        assertNotNull(m.snapshot(34_000_000_000L).result)
    }
    @Test fun freshnessTimeoutAndProtocolGuard() {
        assertTrue(CommunicationHealth.fresh(3_000_000_000, 0))
        assertFalse(CommunicationHealth.fresh(3_000_000_001, 0))
        assertFalse(CommunicationHealth.fresh(0, null))
        assertTrue(CommunicationHealth.protocol6("AUTO, ISO 15765-4 (CAN 11/500)", "A6"))
        assertFalse(CommunicationHealth.protocol6("CAN 29/500", "A7"))
        assertFalse(CommunicationHealth.protocol6("?", "6"))
        assertFalse(CommunicationHealth.protocol6("CAN 11/500", "?"))
    }
    @Test fun switchingAndDisconnectClearOldValuesPreserveChoice() {
        val s = DashboardState(communication = CommunicationMode.CAN, connected = true,
            bluetoothConnected = true, rpm = 0f, can = CanView(latest = frame(0), values = Rx8CanValues(0f, 0.0)))
        val reset = s.resetConnection()
        assertEquals(CommunicationMode.CAN, reset.communication)
        assertFalse(reset.connected); assertNull(reset.rpm); assertNull(reset.can.latest); assertNull(reset.can.values)
    }
    @Test fun manualDisconnectNeverRetriesAndCanFailureRequiresExplicitRestart() {
        assertFalse(CommunicationHealth.retry(true, CommunicationMode.OBD, 1))
        assertFalse(CommunicationHealth.retry(true, CommunicationMode.CAN, 1))
        assertFalse(CommunicationHealth.retry(false, CommunicationMode.CAN, 1))
        assertTrue(CommunicationHealth.retry(false, CommunicationMode.OBD, 1))
        assertFalse(CommunicationHealth.retry(false, CommunicationMode.OBD, 3))
    }
}
