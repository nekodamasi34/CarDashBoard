package dev.aw.cardashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

private val Background = Color(0xFF0C121A)
private val Panel = Color(0xFF192533)
private val Accent = Color(0xFF44E0BB)
private val Muted = Color(0xFFA2B1C0)

class MainActivity : ComponentActivity() {
    private val controller by lazy { ObdController(applicationContext) }
    private var hudFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Panel)
            ) {
                Surface(color = Background) { DashboardScreen(controller, ::setHudFullscreen) }
            }
        }
    }

    private fun setHudFullscreen(enabled: Boolean) {
        hudFullscreen = enabled
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && hudFullscreen) setHudFullscreen(true)
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

@Composable
private fun DashboardScreen(controller: ObdController, setHudFullscreen: (Boolean) -> Unit) {
    val data by controller.state.collectAsState()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) controller.refreshDevices() }

    fun refreshBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            controller.refreshDevices()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) controller.refreshDevices()
    }

    DisposableEffect(page == 4) {
        setHudFullscreen(page == 4)
        onDispose { setHudFullscreen(false) }
    }
    BackHandler(enabled = page == 4) { page = 0 }
    if (page == 4) {
        HudScreen(data, onExit = { page = 0 }, onStart = controller::startCanLive,
            onStop = controller::stopCanLive, onRefresh = ::refreshBluetooth,
            onDemo = controller::startDemo)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RX-8 DASHBOARD", fontWeight = FontWeight.Black, fontSize = 25.sp, color = Color.White)
                Text("VP11  /  ELM327  /  v0.4.3", fontSize = 12.sp, color = Muted)
            }
            Text(if (data.demo) "DEMO" else if (data.connected) "LIVE" else "OFFLINE",
                color = if (data.demo) Color(0xFFFFBE55) else if (data.connected) Accent else Muted,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { page = 0 }) { Text("メーター") }
            OutlinedButton(onClick = { page = 4 }) { Text("HUD") }
            OutlinedButton(onClick = { page = 1 }) { Text("通信ベンチマーク") }
        OutlinedButton(onClick = { page = 2 }) { Text("CANモニター") }
        OutlinedButton(onClick = { page = 3 }) { Text("アダプター診断") }
        }
        if (data.communication == CommunicationMode.CAN) Text("CANデコードは未検証の参考値です", color = Accent)
        if (page == 3) { DiagnosticPane(data, controller) } else if (page == 2) { CanPane(data, controller) } else if (page == 1) {
            BenchmarkPane(data, controller)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ENGINE SPEED", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(data.rpm?.roundToInt()?.toString() ?: "--", fontSize = 64.sp,
                            color = Color.White, fontWeight = FontWeight.Black)
                        Text(" rpm", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    val ratio = ((data.rpm ?: 0f) / 9000f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(13.dp),
                        color = if (ratio > 0.85f) Color(0xFFF36B72) else Accent,
                        trackColor = Color(0xFF304353)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0", color = Muted, fontSize = 11.sp)
                        Text("9,000 rpm", color = Muted, fontSize = 11.sp)
                    }
                    Text(if (data.demo) "デモ更新: 約20 Hz（実測ではありません）" else "取得頻度  " + (data.rpmHz?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "-- Hz"),
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (data.demo) "RPM応答時間: --（デモ）" else
                        "RPM応答時間: " + (data.rpmResponseMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--") +
                        " / 平均: " + (data.rpmAverageMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--"),
                        color = Muted, fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DataCard("SPEED", data.speedKmh?.toString() ?: "--", "km/h", Modifier.weight(1f))
                DataCard("COOLANT", data.coolantC?.toString() ?: "--", "°C", Modifier.weight(1f))
            }

        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("接続", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Text(data.status, color = if (data.connected) Accent else Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (data.demo) controller.disconnect() else controller.startDemo()
                    }) {
                        Text(if (data.demo) "デモ終了" else "デモ開始")
                    }
                    OutlinedButton(onClick = { controller.disconnect() }) { Text("切断") }
                }
                HorizontalDivider(color = Color(0xFF344454))
                Text("ペアリング済みBluetooth機器", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { refreshBluetooth() }) {
                    Text("デバイス一覧を更新・権限を許可")
                }
                if (data.devices.isEmpty()) {
                    Text("まだ一覧がないよ。先にスマホの設定でVP11とペアリングしてね。", color = Muted, fontSize = 13.sp)
                } else {
                    data.devices.forEach { device ->
                        OutlinedButton(
                            onClick = { controller.connect(device.address) },
                            enabled = !data.busy && !data.bluetoothConnected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(device.address, fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("スマホのBluetooth設定を開く") }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("通信ログ（直近35行）", fontWeight = FontWeight.Bold, color = Color.White)
                Text(data.logs.joinToString("\n"), modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
        Text("読取専用：故障コード消去・ECU書換・CAN送信は行わないよ。\n運転中のスマホ操作はしないでね。",
            color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DataCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 35.sp)
                Text(" $unit", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

private fun number(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

@Composable
private fun BenchmarkPane(data: DashboardState, controller: ObdController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportCsv by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("保存先を開けません")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF" + exportCsv) }
                }.fold({ "CSVを保存しました" }, { "CSV保存失敗: ${it.message}" })
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    if (data.communication == CommunicationMode.CAN) { CanPane(data, controller); return }
    val view = data.benchmark
    val editable = !view.running && data.communication == CommunicationMode.OBD
    Text("停車中に条件を選び、走行前に計測を開始してください。", color = Muted)
    Text("取得モード", color = Accent)
    PollingMode.entries.forEach { mode ->
        OutlinedButton(onClick = { controller.selectMode(mode) }, enabled = editable,
            modifier = Modifier.fillMaxWidth()) {
            Text((if (data.mode == mode) "● " else "○ ") + mode.label)
        }
    }
    Text("RPM優先: 車速500ms / 水温2000ms\nバランス: 車速250ms / 水温1000ms\nRPM専用: 010Cのみ（間隔は応答時間の制約を受けます）", color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveTiming.entries.forEach { timing ->
            OutlinedButton(onClick = { controller.selectTiming(timing) }, enabled = editable) {
                Text((if (data.timing == timing) "● " else "") + timing.name)
            }
        }
    }
    Text("適用確認: ${data.appliedTiming?.name ?: "未確認／適用待ち"}", color = Accent)
    data.settingError?.let { Text(it, color = Color(0xFFF36B72)) }
    Text("リアルタイム（直近最大5秒）", color = Accent, fontWeight = FontWeight.Bold)
    StatsText(view.live)
    Text("対象: 010C / 010D / 0105。AT・初期化0100は除外。\n応答時間は失敗も含む。P95はnearest rank、ばらつきは成功RPM受信間隔の母標準偏差。", color = Muted, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { controller.startBenchmark() }, enabled = !view.running && data.connected &&
            !data.demo && data.appliedTiming == data.timing && data.appliedMode == data.mode) { Text("30秒計測開始") }
        OutlinedButton(onClick = { controller.stopBenchmark() }, enabled = view.running) { Text("停止") }
    }
    if (view.running) {
        Text("計測中 ${number(view.measured.seconds)} / 30秒\n平均RPM ${number(view.measured.rpmHz)} Hz", color = Accent)
    }
    view.result?.let { result ->
        Text("測定結果: ${result.reason}\n${result.date}\n${result.mode.label} / ${result.timing.name}\n測定時間 ${number(result.stats.seconds)} 秒", color = Accent)
        StatsText(result.stats)
        OutlinedButton(onClick = {
            exportCsv = result.csv()
            save.launch("CarDashBoard-${result.date.replace(":", "-")}.csv")
        }) { Text("結果をCSV保存") }
    }
    Text("v0.2実測: RPM専用 ATAT1 15.20Hz / ATAT2 16.97Hz、RPM優先 ATAT2 15.27Hz、バランス ATAT2 12.93Hz。各30秒・成功100%・timeout 0。", color = Muted)
}

@Composable
private fun StatsText(s: BenchmarkStats) {
    Text("RPM ${number(s.rpmHz)} Hz / 車速 ${number(s.speedHz)} Hz / 水温 ${number(s.coolantHz)} Hz\n" +
        "OBD2コマンド ${number(s.commandsHz)} 回/秒\n" +
        "RPM応答 最新 ${number(s.latestMs)} / 平均 ${number(s.meanMs)} ms\n" +
        "中央値 ${number(s.medianMs)} / P95 ${number(s.p95Ms)} / 最大 ${number(s.maxMs)} ms\n" +
        "成功 ${s.success} / 失敗 ${s.failed} / 成功率 ${number(s.successPercent)} %\n" +
        "タイムアウト ${s.timeouts} / RPM間隔標準偏差 ${number(s.jitterMs)} ms", color = Color.White,
        fontSize = 14.sp, lineHeight = 23.sp)
}

@Composable
private fun CanPane(data: DashboardState, controller: ObdController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var csv by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("保存先を開けません")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF" + csv) }
                }.fold({ "CSV保存完了" }, { "CSV保存失敗: ${it.message}" })
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    Text("CAN直接監視（実験・VP11実機未確認）", color = Accent)
    var confirmExperiment by remember { mutableStateOf(false) }
    if (confirmExperiment) AlertDialog(onDismissRequest = { confirmExperiment = false },
        title = { Text("CAN実験モード・毎回確認") },
        text = { Text("このアダプターのCANサイレント監視動作は未検証です。ATMAはCAN監視コマンドですが、互換ファームウェアによるACK等の送信有無は保証されません。実車CAN通信へ影響する可能性があります。完全停車状態で短時間の実験を行ってください。") },
        confirmButton = { Button(onClick = { confirmExperiment = false; controller.startCanExperiment() }) { Text("確認して5秒間の実験開始") } },
        dismissButton = { TextButton(onClick = { confirmExperiment = false }) { Text("キャンセル") } })
    Button(onClick = { confirmExperiment = true }, enabled = data.bluetoothConnected && data.diagnostic.ecuVerified &&
        !data.busy && !data.diagnostic.running && data.communication == CommunicationMode.OBD) { Text("CAN実験モード（最大5秒）") }
    Text("停車中・IG ON・エンジン停止で初回確認。長時間測定はバッテリー消耗に注意。", color = Muted)
    Button(onClick = { controller.selectCommunication(CommunicationMode.CAN) },
        enabled = data.bluetoothConnected && data.communication != CommunicationMode.CAN && !data.diagnostic.running) { Text("通常CAN監視・安全条件を確認（未検証機器は保留）") }
    OutlinedButton(onClick = controller::stopCanExperiment,
        enabled = data.communication == CommunicationMode.CAN) { Text("監視停止・通常OBD2へ") }
    Text("選択: ${data.communication} / ${data.status}\n未接続の場合は下の機器一覧から接続してください。", color = Muted)
    val s = data.can.live
    Text("残り監視時間 ${number(if (data.can.running) (5.0 - s.seconds).coerceIn(0.0, 5.0) else 0.0)}秒 / 状態 ${data.status}", color = Accent)
    Text(data.diagnostic.results.filter { it.command in listOf("ATCSM1", "ATCRA201") }.joinToString("\n") { "${it.command}: ${it.support.label} / ${it.kind}" }, color = Muted)
    Text("ATMA: ${data.can.result?.info?.atma ?: if (data.can.running) "監視中（未対応応答なし）" else "未検証"}", color = Muted)
    Text("ID 0x201 / DLC ${data.can.latest?.data?.size ?: "--"}\n" +
        "スマホ到着Hz（監視開始からの平均） ${number(s.hz)}\n" +
        "RPM値変化Hz ${number(s.rpmChangedHz)}\n" +
        "到着間隔 最新 ${number(s.latestMs)} / 平均 ${number(s.meanMs)} ms\n" +
        "最小 ${number(s.minMs)} / 最大 ${number(s.maxMs)} / P95 ${number(s.p95Ms)} ms\n" +
        "標準偏差 ${number(s.stddevMs)} ms\n" +
        "フレーム ${s.frames} / 不正 ${s.invalid} / 通信エラー ${s.errors}", color = Color.White)
    Text("解析: " + if (!data.connected || data.can.values == null) "無効／未受信／期限切れ" else
        "候補（未検証） RPM ${data.can.values.rpm} / 車速 ${number(data.can.values.speedKmh)} km/h", color = Accent)
    Text("参考式: BE[0..1]/4 rpm、(BE[4..5]-10000)/100 km/h\n" +
        "到着周期はCANバス上の送信周期とは異なる場合があります。値変化Hzは同じRPMの繰返しを数えません。\n" +
        "P95は1ms切上げヒストグラム（60秒以上は最終bin）。欠損数はシーケンス番号がないため推定しません。", color = Muted, fontSize = 12.sp)
    Text("実験開始時にRAWフレームHzの計測を自動開始し、監視停止時に確定します。", color = Muted)
    data.can.result?.let { result ->
        Text("${result.date} / ${result.reason}\n${number(result.stats.seconds)}秒 / ${result.stats.frames}枚 / " +
            "${number(result.stats.hz)}Hz\n平均 ${number(result.stats.meanMs)} / P95 ${number(result.stats.p95Ms)} / 最大 ${number(result.stats.maxMs)} ms\n" +
            "不正 ${result.stats.invalid} / エラー ${result.stats.errors}", color = Accent)
        if (result.stats.frames == 0L) Text("受信ゼロ: ${result.info.atma}。ATMA開始後に0枚でも非対応とは断定できません。フィルタ設定、IG ON・エンジン停止時の0x201送信有無、Bluetooth受信・パーサー、その他通信異常を確認してください。", color = Accent)
        OutlinedButton(onClick = { csv = result.csv(); save.launch("CarDashBoard-CAN-v0.3.2-summary.csv") }) { Text("CAN結果CSV保存") }
        OutlinedButton(onClick = { csv = result.rawCsv(); save.launch("CarDashBoard-CAN-v0.3.2-raw.csv") }) { Text("RAW CSV保存（最大10000行）") }
    }
    Text("RAW（単調増加ns、直近35行）\n" + data.can.raw.joinToString("\n"), color = Muted, fontSize = 11.sp)
    data.can.result?.rows?.lastOrNull()?.let { Text("最終RAW: ${it.ns} ${it.raw}", color = Muted, fontSize = 11.sp) }
}
