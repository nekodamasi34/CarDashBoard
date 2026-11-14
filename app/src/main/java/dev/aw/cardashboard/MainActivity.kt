package dev.aw.cardashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Panel)
            ) {
                Surface(color = Background) { DashboardScreen(controller) }
            }
        }
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

@Composable
private fun DashboardScreen(controller: ObdController) {
    val data by controller.state.collectAsState()
    val context = LocalContext.current
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
                Text("VP11  /  ELM327  /  v0.1", fontSize = 12.sp, color = Muted)
            }
            Text(if (data.demo) "DEMO" else if (data.connected) "LIVE" else "OFFLINE",
                color = if (data.demo) Color(0xFFFFBE55) else if (data.connected) Accent else Muted,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

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
                            enabled = !data.busy && !data.connected,
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
