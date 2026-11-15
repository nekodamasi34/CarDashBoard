package dev.aw.cardashboard

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HudDialTest {
    @Test fun enlargedSpeedWindowContainsDigitsAndAvoidsPivotAndTicks() {
        assertEquals(.300f, HudDial.SpeedWidth, .001f)
        assertEquals(.04f, .610f - HudDial.SpeedTop, .001f)
        // Three fixed cells plus both horizontal paddings fit the window.
        assertTrue(3 * .320f * .90f + 2 * .055f < 1f)
        assertTrue(.320f * 1.2f + .112f * 1.2f < HudDial.SpeedHeight / HudDial.SpeedWidth)
        assertTrue(HudDial.SpeedTop > HudDial.PivotY + HudDial.RadiusFraction * .108f)
        for (tick in 0..100) {
            val a = Math.toRadians(HudDial.angle(tick * 100f).toDouble())
            val x = .5 + cos(a) * HudDial.RadiusFraction * .832f
            val y = .5 + sin(a) * HudDial.RadiusFraction * .832f
            assertFalse("tick $tick overlaps speed window", x > HudDial.SpeedLeft &&
                x < HudDial.SpeedLeft + HudDial.SpeedWidth && y > HudDial.SpeedTop &&
                y < HudDial.SpeedTop + HudDial.SpeedHeight)
        }
    }
    @Test fun oemAnglesAndRedline() {
        listOf(0f to 90f, 5000f to 201f, 8000f to 282f, 8500f to 295.5f,
            10000f to 335f, -100f to 90f, 12000f to 335f).forEach { (rpm, angle) ->
            assertEquals(angle, HudDial.angle(rpm), .001f)
        }
        assertEquals(8500f, HudDial.RedBandStartRpm, 0f)
        assertFalse(HudDial.rpmWarning(8000f))
        assertTrue(HudDial.rpmWarning(8500f))
    }
    @Test fun pivotAndRpmPositionAndFixedCells() {
        assertEquals(.5f, HudDial.PivotX, 0f)
        assertEquals(.5f, HudDial.PivotY, 0f)
        assertEquals(HudDial.PivotX, HudDial.RpmLeft + HudDial.RpmWidth / 2, .001f)
        val unitY = .5f - HudDial.RadiusFraction * HudDial.UnitBaselineRadius
        assertTrue(HudDial.RpmTop > unitY)
        assertTrue(HudDial.RpmTop + HudDial.RpmHeight < .51f)
        assertEquals("10000", HudDial.digitCells("10000", 5))
        assertEquals("    0", HudDial.digitCells("0", 5))
        assertEquals("   --", HudDial.digitCells("--", 5))
    }
    @Test fun speedWindowAvoidsAllScaleNumerals() {
        for (rpm in 0..10) {
            val a = Math.toRadians(HudDial.angle(rpm * 1000f).toDouble())
            val x = .5 + cos(a) * HudDial.RadiusFraction * HudDial.NumeralRadius
            val y = .5 + sin(a) * HudDial.RadiusFraction * HudDial.NumeralRadius
            val halfText = HudDial.RadiusFraction * HudDial.NumeralSize * .65
            val overlaps = x + halfText > HudDial.SpeedLeft && x - halfText < HudDial.SpeedLeft + HudDial.SpeedWidth &&
                y + halfText > HudDial.SpeedTop && y - halfText < HudDial.SpeedTop + HudDial.SpeedHeight
            assertFalse("numeral $rpm overlaps speed window", overlaps)
        }
    }
}
