package dev.aw.cardashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun DiagnosticPane(data: DashboardState, controller: ObdController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var csv by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("保存先を開けません")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF" + csv) }
                }.fold({ "診断CSV保存完了" }, { "保存失敗: ${it.message}" })
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val d = data.diagnostic
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("アダプター診断", style = MaterialTheme.typography.titleLarge)
            Text("アダプター: ${d.adapterName}\nBluetooth: ${if (data.bluetoothConnected) "接続中" else "未接続"}\nECU通信: ${if (d.ecuVerified) "応答確認済み" else "未確認"}")
            Text("停車中に実行してください。PID取得を停止して診断し、終了後はBluetooth再接続とELM初期化を行います。ATMAは診断に含めません。")
            Button(onClick = controller::startDiagnostics, enabled = data.bluetoothConnected && !data.busy && !d.running && data.communication == CommunicationMode.OBD) { Text("診断開始") }
            OutlinedButton(onClick = controller::stopDiagnostics, enabled = d.running) { Text("診断停止・OBD2へ復帰") }
            Text(d.summary())
            d.results.forEach { result ->
                Text("${result.command} / ${result.support.label} / ${result.kind ?: "未実施"}")
                Text("RAW: " + result.raw.replace("\r", "\\r").replace("\n", "\\n").ifEmpty { "（未取得）" })
            }
            Text("ATCSM1未対応だけではCAN監視全体の非対応とは判定しません。通常監視は保留します。CAN実験モードでは毎回の明示同意と全設定の成功を条件に最大5秒間のATMAを試せます。")
            OutlinedButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Adapter diagnostics", d.sharedReport()))
            }) { Text("診断結果をコピー（機器名・アドレスを除外）") }
            OutlinedButton(onClick = { csv = d.csv(); save.launch("CarDashBoard-v0.3.2-diagnostics.csv") }) { Text("診断ログCSV保存") }
        }
    }
}
