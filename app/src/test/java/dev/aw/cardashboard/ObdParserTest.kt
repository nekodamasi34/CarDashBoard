package dev.aw.cardashboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdParserTest {
    @Test
    fun rpm_acceptsSpacesEchoAndCanHeaders() {
        assertEquals(1726f, ObdParser.rpm("010C\r7E8 04 41 0C 1A F8\r>"))
        assertEquals(1726f, ObdParser.rpm("410c1af8"))
    }

    @Test
    fun speedAndCoolant_decodeValues() {
        assertEquals(100, ObdParser.speedKmh("41 0D 64"))
        assertEquals(83, ObdParser.coolantC("41 05 7B"))
        assertEquals(-40, ObdParser.coolantC("41 05 00"))
    }

    @Test
    fun missingIncompleteAndWrongPidResponses_returnNull() {
        assertNull(ObdParser.rpm("NO DATA\r>"))
        assertNull(ObdParser.rpm("41 0C 1A"))
        assertNull(ObdParser.speedKmh("41 05 7B"))
        assertNull(ObdParser.coolantC(""))
    }

    @Test
    fun supportedPids_readsFourBytePayload() {
        assertArrayEquals(
            intArrayOf(0xBE, 0x3F, 0xA8, 0x13),
            ObdParser.payload("41 00 BE 3F A8 13", "00", 4)
        )
    }

    @Test
    fun adapterErrorsAndEchoOnly_doNotProduceReadings() {
        for (raw in listOf("", " \r\n>", "NO DATA", "SEARCHING...", "STOPPED",
            "UNABLE TO CONNECT", "BUS ERROR", "CAN ERROR", "?", "010C\r>")) {
            assertNull(raw, ObdParser.rpm(raw))
            assertNull(raw, ObdParser.speedKmh(raw))
            assertNull(raw, ObdParser.coolantC(raw))
            assertNull(raw, ObdParser.payload(raw, "00", 4))
        }
    }

    @Test
    fun malformedHexAndSplitFrames_areNotRepaired() {
        for (raw in listOf("41 0C 1G F8", "41 0C 1A FZ8", "41 0C 1A\rF8",
            "41 0C 1A\r41 0D F8", "41 0C 1AF", "41 0C 1A F8 00",
            "garbage410C1AF8", "410C1AF8garbage")) {
            assertNull(raw, ObdParser.rpm(raw))
        }
        assertNull(ObdParser.speedKmh("41 0D GG"))
        assertNull(ObdParser.coolantC("41 05 7"))
    }

    @Test
    fun searchingEchoAndMultipleEcus_selectCompleteMatchingFrame() {
        assertEquals(1726f, ObdParser.rpm("010C\rSEARCHING...\r41 0C 1A F8\r>"))
        assertEquals(1726f, ObdParser.rpm("7E8 04 41 0C 1A\r7E9 04 41 0C 1A F8\r>"))
        assertEquals(1726f, ObdParser.rpm("18DAF110 04 41 0C 1A F8"))
        assertEquals(100, ObdParser.speedKmh("010D\r41 05 7B\r41 0D 64\r>"))
        assertEquals(83, ObdParser.coolantC("0105\rSEARCHING...\r41 05 7B\r>"))
    }

    @Test
    fun boundaryValues_decodeCorrectly() {
        assertEquals(0f, ObdParser.rpm("41 0C 00 00"))
        assertEquals(16383.75f, ObdParser.rpm("41 0C FF FF"))
        assertEquals(0, ObdParser.speedKmh("41 0D 00"))
        assertEquals(255, ObdParser.speedKmh("41 0D FF"))
        assertEquals(215, ObdParser.coolantC("41 05 FF"))
    }

    @Test
    fun invalidHeadersAndTerminalErrors_rejectEvenWithMatchingBytes() {
        assertNull(ObdParser.rpm("7E8 03 41 0C 1A F8"))
        assertNull(ObdParser.rpm("FFF 04 41 0C 1A F8"))
        assertNull(ObdParser.rpm("FFFFFFFF 04 41 0C 1A F8"))
        assertNull(ObdParser.rpm("41 0C 1A F8\rSTOPPED\r>"))
        assertNull(ObdParser.payload("41 00 BE 3F A8 13\rUNABLE TO CONNECT", "00", 4))
    }

    @Test
    fun failureReasons_preserveAdapterCause() {
        for (reason in listOf("NO DATA", "STOPPED", "UNABLE TO CONNECT", "CAN ERROR")) {
            assertEquals(reason, ObdParser.failureReason("010C\r$reason\r>"))
        }
        assertEquals("空応答", ObdParser.failureReason("\r>"))
    }
}
