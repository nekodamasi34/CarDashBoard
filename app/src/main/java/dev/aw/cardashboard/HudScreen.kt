package dev.aw.cardashboard

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.*

private val HudWhite = Color(0xFFF3EEE5)
private val HudOrange = Color(0xFFFFA052)
private val HudRed = Color(0xFFFF4545)
private val HudMuted = Color(0xFF8C949F)

@Composable
fun HudScreen(data: DashboardState) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(100) } }
    val state = data.toHudState(now)
    val active = state.status == HudStatus.LIVE || state.status == HudStatus.DEMO
    val visible = state.values
    Column(Modifier.fillMaxWidth().background(Color(0xFF07090D), RoundedCornerShape(24.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ROTARY / HUD", color = HudWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${state.source} · v0.4", color = HudMuted, fontSize = 12.sp)
        }
        Text(state.status.label, color = when (state.status) {
            HudStatus.LIVE -> HudOrange
            HudStatus.ERROR -> HudRed
            else -> HudMuted
        }, fontSize = 13.sp)
        Column(Modifier.alpha(if (active) 1f else .42f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TachometerGauge(visible?.rpm)
            SpeedHud(visible?.speedKmh)
            AcceleratorHud(visible?.acceleratorPercent)
        }
        Text(if (state.lastUpdatedNs == null) "最終受信: --" else
            String.format(Locale.US, "最終更新: %.1f秒前 · %s", (state.ageNs ?: 0) / 1e9,
                if (state.source == "DEMO") "デモ生成値" else "受信時刻 ${state.lastUpdatedNs} ns (起動後)"),
            color = HudMuted, fontSize = 10.sp)
        Text(if (state.measuredHz == null) "CAN 0x201 / 受信Hz: --" else
            String.format(Locale.US, "CAN 0x201 / 実測受信: %.1f Hz", state.measuredHz),
            color = HudMuted, fontSize = 11.sp)
        Text("針・バーの補間は表示専用 / CAN換算は実車未検証", color = HudMuted, fontSize = 10.sp)
        if (state.status == HudStatus.WAITING || state.status == HudStatus.OFFLINE)
            Text("接続後、CANモニターで実験を開始してください。デモは下の接続欄から開始できます。",
                color = HudMuted, fontSize = 12.sp)
        if (state.status == HudStatus.ERROR) Text(data.communicationError ?: "CAN受信エラー。通信ログを確認してください。", color = HudRed, fontSize = 12.sp)
    }
}

@Composable
fun TachometerGauge(rpm: Float?) {
    val needle by animateFloatAsState((rpm ?: 0f).coerceIn(0f, HUD_MAX_RPM), tween(140), label = "rpm needle")
    val hot = rpm != null && rpm >= HUD_REDLINE_RPM
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .455f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            fun point(degrees: Float, r: Float): androidx.compose.ui.geometry.Offset {
                val a = Math.toRadians(degrees.toDouble())
                return center + androidx.compose.ui.geometry.Offset(cos(a).toFloat() * r, sin(a).toFloat() * r)
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFF20252B), Color(0xFF090B10)), center, radius), radius, center)
            drawCircle(Color(0xFF353B44), radius, center, style = Stroke(2.dp.toPx()))
            val arcRadius = radius * .91f
            val topLeft = center - androidx.compose.ui.geometry.Offset(arcRadius, arcRadius)
            val arcSize = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2)
            drawArc(Color(0xFF454A51), 135f, 270f, false, topLeft, arcSize, style = Stroke(3.dp.toPx()))
            val redStart = 135f + 270f * HUD_REDLINE_RPM / HUD_MAX_RPM
            drawArc(HudRed, redStart, 405f - redStart, false, topLeft, arcSize, style = Stroke(5.dp.toPx()))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                textSize = radius * .135f
            }
            for (tick in 0..50) {
                val value = tick * 200f
                val angle = 135f + 270f * value / HUD_MAX_RPM
                val major = tick % 5 == 0
                val color = if (value >= HUD_REDLINE_RPM) HudRed else HudWhite
                drawLine(color, point(angle, radius * .88f), point(angle, radius * if (major) .76f else .83f),
                    if (major) 2.dp.toPx() else 1.dp.toPx())
                if (major) {
                    paint.color = color.toArgb()
                    val p = point(angle, radius * .63f)
                    drawContext.canvas.nativeCanvas.drawText((tick / 5).toString(), p.x, p.y - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
            val angle = 135f + 270f * needle / HUD_MAX_RPM
            drawLine(HudRed.copy(alpha = .16f), point(angle, -radius * .14f), point(angle, radius * .73f), 10.dp.toPx(), StrokeCap.Round)
            drawLine(HudOrange, point(angle, -radius * .14f), point(angle, radius * .73f), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(Color(0xFF3F454E), radius * .085f, center)
            drawCircle(HudWhite, radius * .035f, center)
        }
        Text("×1000", Modifier.align(Alignment.TopCenter).padding(top = 88.dp), color = HudMuted, fontSize = 11.sp)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rpm?.roundToInt()?.toString() ?: "--", color = if (hot) HudRed else HudWhite,
                fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("rpm", color = HudMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun SpeedHud(speedKmh: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(speedKmh?.coerceAtLeast(0.0)?.roundToInt()?.toString() ?: "--", color = HudWhite,
            fontWeight = FontWeight.Black, fontSize = 68.sp)
        Text(" km/h", Modifier.padding(bottom = 14.dp), color = HudMuted, fontSize = 17.sp)
    }
}

@Composable
fun AcceleratorHud(percent: Float?) {
    val fraction by animateFloatAsState((percent ?: 0f).coerceIn(0f, 100f) / 100f, tween(120), label = "accelerator bar")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ACCELERATOR", color = HudMuted, fontSize = 12.sp)
            Text(percent?.let { "${it.coerceIn(0f, 100f).roundToInt()} %" } ?: "-- %", color = HudWhite, fontSize = 16.sp)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if ((percent ?: 0f) > 0f) HudOrange else HudMuted, trackColor = Color(0xFF262B33))
    }
}
