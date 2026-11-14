package dev.aw.cardashboard

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Receive time drives freshness; animation never changes telemetry or measured Hz. */
@Composable
fun HudScreen(data: DashboardState, onExit: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) {
        while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(100) }
    }
    HudInstruments(data.toHudState(now), onExit)
}

@Composable
private fun HudInstruments(state: HudState, onExit: () -> Unit = {}) {
    val active = state.status == HudStatus.LIVE || state.status == HudStatus.DEMO
    Column(Modifier.fillMaxSize().background(HudColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            // Full touch target, with only a small, subdued chevron drawn inside.
            TextButton(onClick = onExit, modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)) {
                Text("‹", color = HudColors.Muted, fontSize = 25.sp,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "HUDを閉じる" })
            }
            Spacer(Modifier.weight(1f))
            Text(state.status.name, color = when (state.status) {
                HudStatus.ERROR -> HudColors.Needle
                HudStatus.STALE, HudStatus.DEMO -> HudColors.Warning
                HudStatus.LIVE -> HudColors.White
                else -> HudColors.Muted
            }, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(end = 20.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Reserve a footer before choosing a square that fits either orientation.
            val dialSize = minOf(maxWidth * .96f, (maxHeight - 62.dp).coerceAtLeast(0.dp))
            Column(Modifier.fillMaxSize().alpha(if (active) 1f else .42f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().height(dialSize), contentAlignment = Alignment.Center) {
                    PodSilhouettes(dialSize)
                    TachometerGauge(state.values?.rpm, state.values?.speedKmh, Modifier.size(dialSize))
                }
                AcceleratorHud(state.values?.acceleratorPercent,
                    Modifier.padding(top = 12.dp, bottom = 16.dp).width(minOf(dialSize * .68f, 340.dp)))
            }
        }
    }
}

/** Empty housings only: no invented fuel/water readings or dummy graduations. */
@Composable
private fun PodSilhouettes(dialSize: Dp) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val radius = dialSize.toPx() * .34f
        val middle = size.width / 2f
        val spread = dialSize.toPx() * .67f
        for (x in listOf(middle - spread, middle + spread)) {
            val center = Offset(x, size.height * .55f)
            drawCircle(Color(0xFF060607), radius, center)
            drawCircle(Color(0xFF252629), radius, center, style = Stroke(2.dp.toPx()))
            drawCircle(Color(0xFF0E0F11), radius * .94f, center, style = Stroke(4.dp.toPx()))
        }
    }
}

@Composable
fun TachometerGauge(rpm: Float?, speedKmh: Double? = null,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
    val needle by animateFloatAsState((rpm ?: 0f).coerceIn(0f, HUD_MAX_RPM), tween(140), label = "rpm needle")
    BoxWithConstraints(modifier) {
        val dialWidth = maxWidth
        val rpmLabelSize = with(LocalDensity.current) { (dialWidth * .024f).toSp() }
        TachometerFace(needle, Modifier.fillMaxSize())
        Column(Modifier.offset(x = dialWidth * HudDial.RpmLeft, y = dialWidth * HudDial.RpmTop)
            .size(dialWidth * HudDial.RpmWidth, dialWidth * HudDial.RpmHeight),
            horizontalAlignment = Alignment.CenterHorizontally) {
            HudDigits(rpm?.roundToInt()?.toString() ?: "--", 5, dialWidth * .049f,
                if ((rpm ?: 0f) >= HUD_REDLINE_RPM) HudColors.Needle else HudColors.White, "rpm")
            Text("RPM", color = HudColors.Muted, fontSize = rpmLabelSize,
                lineHeight = rpmLabelSize * 1.2f, letterSpacing = 1.sp)
        }
        SpeedHud(speedKmh, Modifier.offset(x = dialWidth * HudDial.SpeedLeft, y = dialWidth * HudDial.SpeedTop)
            .size(dialWidth * HudDial.SpeedWidth, dialWidth * HudDial.SpeedHeight))
    }
}

/** Recessed digital window in the lower right of the tachometer. */
@Composable
fun SpeedHud(speedKmh: Double?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(Color(0xFF0E0E0D))
        .border(.6.dp, Color(0xFF35332E))) {
        val windowWidth = maxWidth
        val unitSize = with(LocalDensity.current) { (windowWidth * .112f).toSp() }
        Column(Modifier.fillMaxSize().padding(horizontal = windowWidth * .055f),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            HudDigits(speedKmh?.coerceAtLeast(0.0)?.roundToInt()?.toString() ?: "--",
                3, windowWidth * .320f, HudColors.White, "km/h")
            Text("km/h", color = HudColors.Muted, fontSize = unitSize, lineHeight = unitSize * 1.2f)
        }
    }
}

/** Equal-width cells stabilize -- and spaces even if a font lacks the tnum feature. */
@Composable
private fun HudDigits(value: String, slots: Int, digitSize: Dp, color: Color, unit: String) {
    val fontSize = with(LocalDensity.current) { digitSize.toSp() }
    Row(Modifier.clearAndSetSemantics { contentDescription = "$value $unit" },
        verticalAlignment = Alignment.CenterVertically) {
        value.padStart(slots, ' ').forEach { char ->
            Text(char.toString(), modifier = Modifier.width(digitSize * .90f),
                color = color, fontFamily = HudNumberFont, fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                style = TextStyle(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun AcceleratorHud(percent: Float?, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState((percent ?: 0f).coerceIn(0f, 100f) / 100f,
        tween(120), label = "accelerator bar")
    val labelSize = with(LocalDensity.current) { 9.dp.toSp() }
    val unitSize = with(LocalDensity.current) { 10.dp.toSp() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ACCEL", color = HudColors.Muted, fontSize = labelSize,
                lineHeight = labelSize * 1.2f, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            HudDigits(percent?.coerceIn(0f, 100f)?.roundToInt()?.toString() ?: "--",
                3, 13.dp, HudColors.White, "%")
            Text(" %", color = HudColors.Muted, fontSize = unitSize, lineHeight = unitSize * 1.2f)
        }
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            drawRect(Color(0xFF29282A))
            drawRect(HudColors.White.copy(alpha = .65f), size = Size(size.width * fraction, size.height))
        }
    }
}

@Preview(name = "HUD portrait DEMO", widthDp = 393, heightDp = 851, showBackground = true)
@Preview(name = "HUD landscape DEMO", widthDp = 851, heightDp = 393, showBackground = true)
@Composable
private fun HudDemoPreview() {
    HudInstruments(HudState("DEMO", HudStatus.DEMO, Rx8CanValues(7250f, 86.0, 32f)))
}

@Preview(name = "HUD compact STALE", widthDp = 320, heightDp = 480, fontScale = 1.5f)
@Composable
private fun HudStalePreview() {
    HudInstruments(HudState("CAN", HudStatus.STALE, Rx8CanValues(8500f, 123.0, 100f)))
}

@Preview(name = "HUD ERROR", widthDp = 393, heightDp = 851)
@Composable
private fun HudErrorPreview() { HudInstruments(HudState("CAN", HudStatus.ERROR)) }
