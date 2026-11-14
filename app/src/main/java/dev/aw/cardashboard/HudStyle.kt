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

/** Canvas coordinates: 5 at twelve o'clock, 0/10 at the lower corners. */
internal object HudDial {
    const val StartDegrees = 140f
    const val SweepDegrees = 260f
    const val RedBandStartRpm = 8000f
    const val RadiusFraction = .478f
    const val NumeralRadius = .765f
    const val NumeralSize = .19f
    const val SpeedLeft = .535f
    const val SpeedTop = .615f
    const val SpeedWidth = .200f
    const val SpeedHeight = .137f
    const val RpmLeft = .272f
    const val RpmTop = .665f
    const val RpmWidth = .240f
    const val RpmHeight = .110f

    fun angle(rpm: Float) = StartDegrees + SweepDegrees * rpm.coerceIn(0f, HUD_MAX_RPM) / HUD_MAX_RPM
}
