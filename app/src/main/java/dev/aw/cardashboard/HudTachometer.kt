package dev.aw.cardashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun TachometerFace(rpm: Float, modifier: Modifier) {
    val lettering = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            // Analog scale retains understated OEM-like lettering, not digital numerals.
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
    }
    Canvas(modifier) {
        val radius = size.minDimension * HudDial.RadiusFraction
        val center = Offset(size.width * HudDial.PivotX, size.height * HudDial.PivotY)
        drawBezel(center, radius)
        drawGraduations(center, radius, lettering)
        drawNeedle(center, radius, HudDial.angle(rpm))
    }
}

private fun radialPoint(center: Offset, angle: Float, radius: Float): Offset {
    val radians = Math.toRadians(angle.toDouble())
    return center + Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
}

private fun DrawScope.drawBezel(center: Offset, radius: Float) {
    // Metal lip, inner shadow, then the black recessed dial face.
    drawCircle(Color.Black, radius * 1.04f, center)
    drawCircle(Brush.linearGradient(listOf(Color(0xFF5C5D60), Color(0xFF18191C), Color(0xFF393A3E)),
        center - Offset(radius, radius), center + Offset(radius, radius)), radius, center)
    drawCircle(Color(0xFF0B0B0D), radius * .966f, center)
    drawCircle(HudColors.Bezel.copy(alpha = .55f), radius * .952f, center, style = Stroke(radius * .006f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF111113), HudColors.Face, Color.Black),
        center, radius * .94f), radius * .938f, center)
    drawCircle(Color(0xFF252527), radius * .923f, center, style = Stroke(radius * .008f))
}

private fun DrawScope.drawGraduations(center: Offset, radius: Float, paint: Paint) {
    fun point(angle: Float, r: Float) = radialPoint(center, angle, radius * r)
    val bandRadius = radius * .886f
    val redStart = HudDial.angle(HudDial.RedBandStartRpm)
    drawArc(HudColors.RedBand, redStart, HudDial.angle(HUD_MAX_RPM) - redStart,
        false, center - Offset(bandRadius, bandRadius), Size(bandRadius * 2, bandRadius * 2),
        style = Stroke(radius * .045f))
    for (tick in 0..100) {
        val angle = HudDial.angle(tick * 100f)
        val major = tick % 10 == 0
        val half = tick % 5 == 0
        if (major || half) {
            drawLine(HudColors.White, point(angle, .908f), point(angle, if (major) .832f else .861f),
                radius * if (major) .013f else .007f)
        } else {
            drawCircle(HudColors.White.copy(alpha = .9f), radius * .0055f, point(angle, .881f))
        }
        if (major) {
            paint.color = HudColors.White.toArgb()
            paint.textSize = radius * HudDial.NumeralSize
            val p = point(angle, HudDial.NumeralRadius)
            drawContext.canvas.nativeCanvas.drawText((tick / 10).toString(), p.x,
                p.y - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
    paint.color = HudColors.White.copy(alpha = .8f).toArgb()
    paint.textSize = radius * .073f
    drawContext.canvas.nativeCanvas.drawText("x1000r/min", center.x, center.y - radius * HudDial.UnitBaselineRadius, paint)
}

private fun DrawScope.drawNeedle(center: Offset, radius: Float, angle: Float) {
    fun point(a: Float, r: Float) = radialPoint(center, a, radius * r)
    val tip = point(angle, .844f)
    val tail = point(angle, -.18f)
    val left = point(angle + 90, .015f)
    val right = point(angle - 90, .015f)
    val needle = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(tail.x, tail.y)
        lineTo(right.x, right.y)
        close()
    }
    drawLine(Color.Black.copy(alpha = .6f), tail + Offset(radius * .018f, radius * .018f),
        tip + Offset(radius * .018f, radius * .018f), radius * .025f)
    drawPath(needle, HudColors.Needle)
    drawLine(HudColors.NeedleHighlight, point(angle, .10f), point(angle, .824f), radius * .004f)
    drawCircle(Color.Black, radius * .108f, center)
    drawCircle(Color(0xFF4A4847), radius * .095f, center)
    drawCircle(Brush.linearGradient(listOf(Color(0xFF303033), Color(0xFF111113)),
        center - Offset(0f, radius * .08f), center + Offset(0f, radius * .08f)), radius * .083f, center)
}
