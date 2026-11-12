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
}
