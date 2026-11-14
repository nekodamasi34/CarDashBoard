package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test

class AdapterDiagnosticsTest {
    private fun classify(raw: String) = AdapterDiagnostics.classify("ATCSM1", raw)
    @Test fun ok() { assertEquals(CommandSupport.SUPPORTED, classify("OK\r>").support) }
    @Test fun unsupported() { assertEquals(CommandSupport.UNSUPPORTED, classify("?\r>").support) }
    @Test fun errorIsUnknownSupport() { assertEquals(ResponseKind.ERROR, classify("CAN ERROR\r>").kind); assertEquals(CommandSupport.ABNORMAL, classify("ERROR>").support) }
    @Test fun timeout() { assertEquals(ResponseKind.TIMEOUT, AdapterDiagnostics.classify("ATCSM1", "", ResponseKind.TIMEOUT).kind) }
    @Test fun empty() { assertEquals(ResponseKind.EMPTY, classify("\r>").kind) }
    @Test fun incomplete() { assertEquals(ResponseKind.INCOMPLETE, classify("OK\r").kind) }
    @Test fun echo() { assertEquals(CommandSupport.SUPPORTED, classify("ATCSM1\r\nOK\r\n>").support) }
    @Test fun multipleLines() { assertEquals(ResponseKind.ERROR, classify("OK\rERROR\r>").kind); assertEquals(CommandSupport.SUPPORTED, AdapterDiagnostics.classify("ATI", "ATI\rELM327 v1.5\r>").support) }
    @Test fun unexpectedIsNotSupported() { assertEquals(CommandSupport.ABNORMAL, classify("garbage>").support) }
    @Test fun informationResponses() {
        assertEquals(CommandSupport.SUPPORTED, AdapterDiagnostics.classify("ATDPN", "A6>").support)
        assertEquals(CommandSupport.ABNORMAL, AdapterDiagnostics.classify("ATDPN", "XX>").support)
        assertEquals(CommandSupport.SUPPORTED, AdapterDiagnostics.classify("ATDP", "AUTO, ISO 15765-4 (CAN 11/500)>").support)
    }
    @Test fun noContinuousOrUnsafeCommandInDiagnostics() {
        assertEquals(8, AdapterDiagnostics.commands.size)
        assertFalse(AdapterDiagnostics.commands.contains("ATMA"))
        assertFalse(AdapterDiagnostics.commands.contains("ATCSM0"))
    }
    @Test fun reportsEscapeCsvAndRemoveBluetoothDetails() {
        val d = AdapterDiagnosticView(adapterName = "private adapter").record(
            AdapterDiagnostics.classify("AT@1", "private adapter\rAA:BB:CC:DD:EE:FF\r\"info\">"))
        assertFalse(d.csv().contains("AA:BB")); assertFalse(d.sharedReport().contains("private adapter"))
        assertTrue(d.csv().contains("\"\"info\"\"")); assertTrue(d.summary().contains("ATMA: 未検証"))
    }
}
