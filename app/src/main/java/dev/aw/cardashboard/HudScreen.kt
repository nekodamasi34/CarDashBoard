package dev.aw.cardashboard

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

private val HudWhite = Color(0xFFF0E9DA)
private val HudAmber = Color(0xFFE79549)
private val HudRed = Color(0xFFE84B35)
private val HudMuted = Color(0xFF8C867B)

/** Dedicated instrument surface. Telemetry and freshness remain independent of animation. */
@Composable
fun HudScreen(data: DashboardState, onExit: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(100) } }
    val state = data.toHudState(now)
    val active = state.status == HudStatus.LIVE || state.status == HudStatus.DEMO
    Column(Modifier.fillMaxSize().background(Color(0xFF030303))) {
        // Insets protect controls from cutouts; the instrument background reaches every edge.
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ 戻る", color = HudMuted, fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Text(state.status.name, color = when (state.status) {
                HudStatus.ERROR -> HudRed
                HudStatus.STALE, HudStatus.DEMO -> HudAmber
                HudStatus.LIVE -> HudWhite
                else -> HudMuted
            }, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(if (state.status == HudStatus.DEMO) " / SIM" else " / CAN", color = HudMuted,
                fontSize = 10.sp, modifier = Modifier.padding(end = 20.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            val landscape = maxWidth > maxHeight
            val gaugeSize = if (landscape) minOf(maxHeight, maxWidth * .68f)
                else minOf(maxWidth, maxHeight * .72f)
            if (landscape) {
                Row(Modifier.fillMaxSize().alpha(if (active) 1f else .38f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    TachometerGauge(state.values?.rpm, Modifier.size(gaugeSize))
                    Column(Modifier.widthIn(max = 260.dp).weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SpeedHud(state.values?.speedKmh)
                        AcceleratorHud(state.values?.acceleratorPercent)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().alpha(if (active) 1f else .38f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    TachometerGauge(state.values?.rpm, Modifier.size(gaugeSize))
                    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpeedHud(state.values?.speedKmh)
                        AcceleratorHud(state.values?.acceleratorPercent)
                    }
                }
            }
        }
    }
}

@Composable
fun TachometerGauge(rpm: Float?, modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
    val needle by animateFloatAsState((rpm ?: 0f).coerceIn(0f, HUD_MAX_RPM), tween(140), label = "rpm needle")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val dialWidth = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .475f
            val center = Offset(size.width / 2, size.height / 2)
            fun point(degrees: Float, r: Float): Offset {
                val a = Math.toRadians(degrees.toDouble())
                return center + Offset(cos(a).toFloat() * r, sin(a).toFloat() * r)
            }
            // Recessed black dial and subdued twin bezel, rather than a progress ring.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF171715), Color(0xFF050505)), center, radius), radius, center)
            drawCircle(Color(0xFF383731), radius, center, style = Stroke(radius * .012f))
            drawCircle(Color(0xFF161613), radius * .976f, center, style = Stroke(radius * .018f))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = radius * .17f
            }
            // 250 degree sweep; 100 rpm graduations and heavy 1000 rpm indices.
            for (tick in 0..100) {
                val value = tick * 100f
                val angle = 145f + 250f * value / HUD_MAX_RPM
                val major = tick % 10 == 0
                val half = tick % 5 == 0
                val color = if (value >= HUD_REDLINE_RPM) HudRed else HudWhite
                drawLine(color.copy(alpha = if (major || half) 1f else .7f),
                    point(angle, radius * .925f),
                    point(angle, radius * if (major) .77f else if (half) .82f else .87f),
                    radius * if (major) .022f else .009f)
                if (major) {
                    paint.color = color.toArgb()
                    val p = point(angle, radius * .64f)
                    drawContext.canvas.nativeCanvas.drawText((tick / 10).toString(), p.x,
                        p.y - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
            // Segmented redline band sits behind the red graduations.
            val arcRadius = radius * .955f
            drawArc(HudRed.copy(alpha = .75f), 145f + 250f * .85f, 250f * .15f, false,
                center - Offset(arcRadius, arcRadius), Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(radius * .025f))
            paint.apply { color = HudWhite.copy(alpha = .65f).toArgb(); textSize = radius * .072f }
            drawContext.canvas.nativeCanvas.drawText("×1000 r/min", center.x,
                center.y - radius * .32f, paint)
            val angle = 145f + 250f * needle / HUD_MAX_RPM
            val path = Path().apply {
                moveTo(point(angle, radius * .86f).x, point(angle, radius * .86f).y)
                val left = point(angle + 90, radius * .022f)
                val tail = point(angle, -radius * .19f)
                val right = point(angle - 90, radius * .022f)
                lineTo(left.x, left.y); lineTo(tail.x, tail.y); lineTo(right.x, right.y); close()
            }
            drawLine(HudRed.copy(alpha = .12f), point(angle, -radius * .16f), point(angle, radius * .86f), radius * .055f)
            drawPath(path, HudRed)
            drawLine(HudAmber, center, point(angle, radius * .82f), radius * .006f)
            drawCircle(Color(0xFF393630), radius * .105f, center)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF282723), Color(0xFF10100F)), center, radius * .09f), radius * .09f, center)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = dialWidth * .11f),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rpm?.roundToInt()?.toString()?.padStart(5, ' ') ?: "   --",
                color = if ((rpm ?: 0f) >= HUD_REDLINE_RPM) HudRed else HudWhite,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = (dialWidth.value * .085f).sp, maxLines = 1)
            Text("RPM", color = HudMuted, fontSize = (dialWidth.value * .025f).sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun SpeedHud(speedKmh: Double?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val speedFontSize = minOf(64f, maxWidth.value * .30f).sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(speedKmh?.coerceAtLeast(0.0)?.roundToInt()?.toString()?.padStart(3, ' ') ?: " --",
                color = HudWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = speedFontSize, maxLines = 1)
            Text("km/h", color = HudMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun AcceleratorHud(percent: Float?) {
    val fraction by animateFloatAsState((percent ?: 0f).coerceIn(0f, 100f) / 100f, tween(120), label = "accelerator bar")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ACCEL", color = HudMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(percent?.let { "${it.coerceIn(0f, 100f).roundToInt().toString().padStart(3, ' ')} %" } ?: " -- %",
                color = HudWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val gap = 2.dp.toPx()
            val width = (size.width - gap * 19) / 20
            repeat(20) { index ->
                drawRect(if (fraction * 20 > index) HudAmber else Color(0xFF292722),
                    Offset(index * (width + gap), 0f), Size(width, size.height))
            }
        }
    }
}
