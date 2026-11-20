# v0.4.6 検証記録

2026-11-21 / Windows / Android Studio同梱JBR。

## 作業開始時

- git fetch origin後、master / origin/master / v0.4.5はc100b7e（Release ver0.4.5）。GitHub latestもv0.4.5。
- v0.4.6タグ・Releaseの競合なし。versionName 0.4.6 / versionCode 12を採用。
- 既存の.idea/gradle.xmlの変更は保持し、リリースコミットから除外。
- 従来のvタグ、ver名のRelease、Debug/未署名Release APK配布方式を踏襲。

## 実行結果

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest
```

- 最終実行BUILD SUCCESSFUL（5秒）。89タスク: 3実行、86 UP-TO-DATE。
- 直前の実装後実行もBUILD SUCCESSFUL（28秒）、89タスク: 18実行、71 UP-TO-DATE。
- assembleDebug / assembleRelease / testDebugUnitTest成功。
- lintVitalAnalyzeRelease / lintVitalReportRelease / lintVitalRelease成功（最終実行は解析・レポートUP-TO-DATE）。
- JUnit XML: 15スイート、111件、失敗0、エラー0、スキップ0。
- 実装途中の初回は旧3秒STALE期待値の既存3テストが失敗。新仕様の3秒鮮度・4秒表示確定へ期待値を更新し、最終全件成功。
- 新規CanStabilityTest 12件: ATCAF0のOK・未対応・異常・タイムアウトと部分RAW、各設定途中の切断、ECU未確認時の送信禁止、ATMAより前の設定順序、通常OBD初期化と分離、実車2サンプルの空白有無、不正DLC・不完全フレーム・DATA ERROR・CAN ERROR・BUFFER FULLの拒否、3秒鮮度と4秒表示の分離、猶予解除、次の途絶えの計時、復帰途中の途絶え・重複フレーム・単発フレームの抑止、500ms安定復帰、ERROR/切断/DEMOの独立。
- 時刻はすべて模擬単調時刻で検証。実時間待機なし。
- 既存無制限・5/15/30秒・5秒実験、毎回同意、CANデコード、ベンチマーク・CSV・RAW上限、PID排他、停止CR・プロンプト復帰、Reader退役、再接続ポリシー等のテストも成功。

## コード確認

- 0100成功後のCAN分岐だけでATCAF0を設定。通常OBDはATZから従来どおり初期化しCAF既定値へ復帰。
- CAN設定は各OK必須。失敗は次の設定とATMAへ進まず、診断結果と通信ログを記録。
- ATH1 / ATD1 / ATCRA201を維持。ATCAF0の生バイト形式に既存の11bit ID・任意DLCパーサーを利用。フレーム解析式の変更なし。
- DATA ERROR付き行は部分的にも測定値へ採用しない。致命的エラーはRAW行と原因を通信ログへ保存し、改行なしのプロンプト終端も記録。5秒実験では既存RAW CSVにも不正行として保存。ライブ異常時も通信ログはSocket終了後に残る。
- HUDの復帰観測にはデコード可能な0x201のみ使用。不完全な0x201で最新値を置換しない。
- 鮮度は厳密に3秒未満。HUD表示猶予と独立。500ms復帰・250ms超の間隔で復帰観測リセット。実車周期による調整は未検証。
- 判定器はObdController所有で、画面再描画と独立。既存configChangesにより画面回転で接続再起動なし。
- HUD全体のalpha変更を撤去。通信ERROR・切断では値なし。DEMO経路は独立。
- git diff --check成功（既存.idea変更を除き確認）。

## APK検査

SDK build-tools/36.0.0のaapt dump badgingとapksigner verify --verboseを使用。

| 対象 | 結果 |
|---|---|
| 両APK | dev.aw.cardashboard / versionName 0.4.6 / versionCode 12 |
| Debug | Verifies / v2=true / 署名者1 |
| Release | DOES NOT VERIFY / Missing META-INF/MANIFEST.MF（想定どおり未署名） |

未署名Release APKはそのままインストールできません。利用者側で署名が必要です。署名鍵・パスワードは配布に含めません。

build/distributionsへコピーし、以下のSHA-256を確認。

- CarDashBoard-v0.4.6-debug.apk: `4F1E44A7FDC7160528E38038404296E19472422020E398B01B01C0AB2D4F58BF`
- CarDashBoard-v0.4.6-release-unsigned.apk: `FCB453DB0F3BF62F29C98222364040558F5B3647A7D2A70A25CB32C44F2FAF62`

## 未検証事項

adb devicesでは接続端末1台を確認しましたが、インストール・操作・画面描画試験は行っていません。BluetoothSocket/VP11/RX-8実車通信、ATCAF0の互換性とDATA ERROR解消効果、RAW実出力、受信周期、長時間監視、実際の切断・停止、実画面でのちらつき、回転挙動は未検証です。

VP11のCAN送信・ACK抑止は未検証。検証根拠は空、検証済み連続監視許可はfalse。毎回同意の無制限実験と警告を維持。実車確認手順はRELEASE_NOTES-v0.4.6.mdを参照してください。
