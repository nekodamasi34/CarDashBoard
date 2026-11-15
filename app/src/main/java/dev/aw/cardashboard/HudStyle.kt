package dev.aw.cardashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Local to the HUD: never installed in MaterialTheme's typography. */
internal val HudNumberFont = FontFamily(Font(R.font.orbitron, FontWeight.Normal))

internal object HudColors {
    val Background = Color(0xFF020203)
    val Face = Color(0xFF070708)
    val White = Color(0xFFF1EEE7)
    val Muted = Color(0xFF99968F)
    val Needle = Color(0xFFED5136)
    val NeedleHighlight = Color(0xFFFFA16A)
    val RedBand = Color(0xFFAC322C)
    val Warning = Color(0xFFC48C59)
    val Bezel = Color(0xFF55565A)
}

/** Canvas angles clockwise from right. Every scale component uses this interpolation. */
internal object HudDial {
    private val angles = floatArrayOf(90f, 110f, 132f, 154f, 177f, 201f, 227f, 254f, 282f, 309f, 335f)
    const val RedBandStartRpm = HUD_REDLINE_RPM
    const val RadiusFraction = .478f
    const val NumeralRadius = .765f
    const val NumeralSize = .15f
    const val SpeedLeft = .550f
    const val SpeedTop = .570f
    const val SpeedWidth = .300f
    const val SpeedHeight = .205f
    const val RpmLeft = .380f
    const val RpmTop = .394f
    const val RpmWidth = .240f
    const val RpmHeight = .050f

    const val UnitBaselineRadius = .34f
    const val PivotX = .5f
    const val PivotY = .5f
    fun angle(rpm: Float): Float {
        val step = rpm.coerceIn(0f, HUD_MAX_RPM) / 1000f
        val index = step.toInt().coerceAtMost(9)
        return angles[index] + (angles[index + 1] - angles[index]) * (step - index)
    }
    fun rpmWarning(rpm: Float) = rpm >= HUD_REDLINE_RPM
    fun digitCells(value: String, slots: Int) = value.padStart(slots, ' ')
}
