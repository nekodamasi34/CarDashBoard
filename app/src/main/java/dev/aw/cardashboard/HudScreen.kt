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
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
fun HudScreen(data: DashboardState, onExit: () -> Unit = {},
    onStart: (String, Int?) -> Unit = { _, _ -> }, onStop: () -> Unit = {},
    onRefresh: () -> Unit = {}, onDemo: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) {
        while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(100) }
    }
    var setup by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(5) }
    var device by remember { mutableStateOf<String?>(null) }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("CANライブ検証") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("VP11の無送信・ACK抑止は未確認です。ATCSM1の応答やATMA受信だけでは証明できません。停車中に短時間から検証してください。上限で停止し、自動再開しません。")
                Row { CanLiveSafety.validationSeconds.forEach { duration ->
                    TextButton(onClick = { seconds = duration }) { Text(if (seconds == duration) "[$duration 秒]" else "$duration 秒") }
                } }
                data.devices.forEach { d -> TextButton(onClick = { device = d.address }) {
                    Text((if (device == d.address) "✓ " else "") + d.name + "\n" + d.address)
                } }
                TextButton(onClick = onRefresh) { Text("Bluetooth機器を更新") }
            }
        }, confirmButton = { TextButton(enabled = device != null, onClick = {
            setup = false; device?.let { onStart(it, seconds) }
        }) { Text("確認して監視開始") } },
        dismissButton = { TextButton(onClick = { setup = false }) { Text("キャンセル") } })
    HudInstruments(data.toHudState(now), onExit, controls = {
        TextButton(enabled = !data.busy && !data.canLiveRequested && !data.can.running,
            onClick = { onRefresh(); setup = true }) { Text("CAN開始", color = HudColors.White) }
        TextButton(onClick = onStop) { Text("停止", color = HudColors.White) }
        TextButton(enabled = !data.busy && !data.canLiveRequested && !data.can.running,
            onClick = onDemo) { Text("DEMO", color = HudColors.Muted) }
    }, detail = data.status)
}

@Composable
private fun HudInstruments(state: HudState, onExit: () -> Unit = {},
    controls: @Composable () -> Unit = {}, detail: String = "") {
    val active = state.status == HudStatus.LIVE || state.status == HudStatus.DEMO
    Column(Modifier.fillMaxSize().background(HudColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            // Full touch target, with only a small, subdued chevron drawn inside.
            TextButton(onClick = onExit, modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)) {
                Text("‹", color = HudColors.Muted, fontSize = 25.sp,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "HUDを閉じる" })
            }
            controls()
            Spacer(Modifier.width(12.dp))
            Text(state.status.name, color = when (state.status) {
                HudStatus.ERROR -> HudColors.Needle
                HudStatus.STALE, HudStatus.DEMO -> HudColors.Warning
                HudStatus.LIVE -> HudColors.White
                else -> HudColors.Muted
            }, fontSize = 10.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(end = 20.dp))
        }
        Text(detail, color = HudColors.Muted, fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // A separate bottom instrument owns its space in both orientations.
            val dialSize = minOf(maxWidth * .96f, (maxHeight - 86.dp).coerceAtLeast(0.dp))
            val acceleratorWidth = minOf(maxWidth * .88f, 480.dp)
            Column(Modifier.fillMaxSize().alpha(if (active) 1f else .42f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(dialSize)) { PodSilhouettes(dialSize) }
                    TachometerGauge(state.values?.rpm, state.values?.speedKmh, Modifier.size(dialSize))
                }
                AcceleratorHud(state.values?.acceleratorPercent,
                    Modifier.padding(top = 12.dp, bottom = 20.dp).width(acceleratorWidth))
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
        TachometerFace(needle, Modifier.fillMaxSize())
        Column(Modifier.offset(x = dialWidth * HudDial.RpmLeft, y = dialWidth * HudDial.RpmTop)
            .size(dialWidth * HudDial.RpmWidth, dialWidth * HudDial.RpmHeight),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val rpmText = rpm?.roundToInt()?.toString() ?: "--"
            // Draw the needle first, then legible glyphs with a thin shadow, never a masking panel.
            HudDigits(rpmText, rpmText.length, dialWidth * .040f,
                if (HudDial.rpmWarning(rpm ?: 0f)) HudColors.Needle else HudColors.White, "rpm")
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
        HudDial.digitCells(value, slots).forEach { char ->
            Text(char.toString(), modifier = Modifier.width(digitSize * .90f),
                color = color, fontFamily = HudNumberFont, fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                style = TextStyle(fontFeatureSettings = "tnum",
                    shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun AcceleratorHud(percent: Float?, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState((percent ?: 0f).coerceIn(0f, 100f) / 100f,
        tween(120), label = "accelerator bar")
    val labelSize = with(LocalDensity.current) { 13.5.dp.toSp() }
    val unitSize = with(LocalDensity.current) { 15.dp.toSp() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ACCEL", color = HudColors.Muted, fontSize = labelSize, fontFamily = HudNumberFont,
                lineHeight = labelSize * 1.2f, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            HudDigits(percent?.coerceIn(0f, 100f)?.roundToInt()?.toString() ?: "--",
                3, 19.5.dp, HudColors.White, "%")
            Text(" %", color = HudColors.Muted, fontSize = unitSize, fontFamily = HudNumberFont, lineHeight = unitSize * 1.2f)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRect(Color(0xFF29282A))
            drawRect(HudColors.White.copy(alpha = .65f), size = Size(size.width * fraction, size.height))
        }
    }
}

@Preview(name = "HUD portrait DEMO", widthDp = 393, heightDp = 851, showBackground = true)
@Preview(name = "HUD landscape DEMO", widthDp = 851, heightDp = 393, showBackground = true)
@Preview(name = "HUD compact landscape large text", widthDp = 568, heightDp = 320, fontScale = 2f)
@Preview(name = "HUD compact portrait large text", widthDp = 320, heightDp = 480, fontScale = 2f)
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

@Preview(name = "0 rpm", widthDp = 393, heightDp = 700)
@Composable private fun Rpm0Preview() = RpmPreview(0f)
@Preview(name = "1000 rpm", widthDp = 393, heightDp = 700)
@Composable private fun Rpm1000Preview() = RpmPreview(1000f)
@Preview(name = "5000 rpm", widthDp = 700, heightDp = 393)
@Composable private fun Rpm5000Preview() = RpmPreview(5000f)
@Preview(name = "8000 rpm", widthDp = 393, heightDp = 700)
@Composable private fun Rpm8000Preview() = RpmPreview(8000f)
@Preview(name = "8500 rpm", widthDp = 393, heightDp = 700)
@Composable private fun Rpm8500Preview() = RpmPreview(8500f)
@Preview(name = "10000 rpm", widthDp = 700, heightDp = 393)
@Composable private fun Rpm10000Preview() = RpmPreview(10000f)
@Composable private fun RpmPreview(rpm: Float) =
    HudInstruments(HudState("DEMO", HudStatus.DEMO, Rx8CanValues(rpm, 0.0, 0f)))
