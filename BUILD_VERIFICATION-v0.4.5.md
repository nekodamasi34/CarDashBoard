# v0.4.5 検証記録

2026-11-15 / Windows / Android Studio同梱JBR。

## 作業前の確認

- master / origin/master / v0.4.4: `044ef169334f022279756c27478471f899e9c537`。git fetch origin後の一致を確認。
- GitHub latest Release: v0.4.4、名称ver0.4.4、同じコミット。v0.4.5タグ・Releaseは開始時に存在せず。
- 元からある`.idea/gradle.xml`の未コミット変更は保持し、コミットから除外。
- 従来のv接頭辞タグ、ver接頭辞Release名、Debug/未署名Release APKの配布方式を踏襲。

## 実行結果

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest
```

- 初回BUILD SUCCESSFUL（52秒）。3秒境界の厳密化後に全3タスクを再実行、BUILD SUCCESSFUL（12秒）。
- assembleDebug / assembleRelease / testDebugUnitTest成功。
- lintVitalAnalyzeRelease / lintVitalReportRelease / lintVitalRelease成功（最終実行には一部UP-TO-DATEあり）。
- JUnit XML: 14スイート、99件、失敗0、エラー0、スキップ0。
- 新規2テスト: 明示的実験同意と検証済み許可の分離・新規開始での同意必須・7日相当で期限なし・停止/二重started拒否、および2万フレームでも監視継続・RAW35件・CSV捕捉なし・WAITING/LIVE/STALE/異常時値無効化。
- 更新テスト: 速度位置の上方4%移動と既存サイズ/支点/目盛り/外周数字の分離、最終受信からちょうど3秒でSTALE。
- 既存5/15/30秒期限、5秒実験、通常OBD、0x201デコーダー、PID排他、停止CRとプロンプト、復帰タイムアウト、Reader退役、CAN再接続禁止などの単体試験は成功。
- Activity onDestroy→closeのコード確認: 監視停止を要求し、既存の復帰手順とReader終了を待機、最大7.5秒でSocketを強制終了。BluetoothSocket統合試験は未実施。
- ログは35件、応答組立8192文字、応答Channel8件、固定60001ビンのヒストグラム。ライブはベンチマーク行を蓄積せず、既存実験CSVは10000件上限。
- HUD開始はイベント内だけ。回転は既存configChangesで処理。再描画・再表示による接続処理なし。
- git diff --checkの末尾空行を修正し再確認。元の.idea変更は対象外。

## APK実出力確認

SDK build-tools/36.0.0のaapt dump badging / apksigner verify --verboseを使用。

| 対象 | 結果 |
|---|---|
| 両APK | applicationId dev.aw.cardashboard / versionName 0.4.5 / versionCode 11 |
| Debug | Verifies / v2=true / 署名者1 |
| Release | DOES NOT VERIFY / Missing META-INF/MANIFEST.MF（想定どおり未署名） |

build/distributionsへコピーし、元APKとのSHA-256一致を確認。

- CarDashBoard-v0.4.5-debug.apk: `6422C91C6830B30E4786CFFD41488C4993FBFE69C8F4A65F0D9C88A4B4A8FBBE`
- CarDashBoard-v0.4.5-release-unsigned.apk: `D51723BC16BCCB9AE803531B5EBBED41F3FC623C9F7796764BD004894DED6F09`

## 実機未検証・制限

adb devicesで接続端末なし。SDKにエミュレーターなし。端末へのインストール、Compose Previewのレンダリング、実画面、Bluetooth/VP11、実車試験は未実施。

縦横・小型・文字倍率2倍、指定6回転数、速度0/1桁/2桁/3桁/欠測、アクセル0/50/100%/欠測のPreview定義を用意。サイズ比・目盛り分離の幾何試験は成功しましたが、文字の実描画、針と速度表示の見え方、画面端・切り欠き・システムバーとの干渉は実機確認が必要です。

無制限の数日試験は時間値を進めたロジック試験であり、長期の実通信試験ではありません。手動停止・切断・通信異常・Activity破棄のSocket操作はコード確認と既存停止部品の単体試験までです。

CAN送信・ACK抑止は未検証。VERIFIED_NO_TRANSMIT_EVIDENCEは空、検証済み連続監視許可はfalse。毎回明示的に同意した未検証の実験用無制限監視のみ追加しました。実機手順はRELEASE_NOTES-v0.4.5.mdを参照。
