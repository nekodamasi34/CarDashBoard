# v0.4.4 検証記録

2026-11-15。Windows / Android Studio同梱JBR。

## 作業前

- master / origin/master: `73273bbbf37428bd4773a26d14100cc94927a09e`（Release ver0.4.3）。fetchで確認。
- 最新公開Release: v0.4.3 / title ver0.4.3。タグはv接頭辞、配布名はCarDashBoard-vVERSION-debug.apkと-release-unsigned.apk。
- 元からある`.idea/gradle.xml`の未コミット変更は保持しコミット対象外。
- compileSdk 36.1 / targetSdk 36 / minSdk 31 / Java 11。独自Release署名設定なし、鍵・パスワード追加なし。

## 実行と結果

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest
```

- 初回はComposeの暗黙receiverによるmaxWidth参照エラー。外側で幅を計算する修正後、全3タスクを再実行してBUILD SUCCESSFUL（53秒）。
- `lintVitalAnalyzeRelease / lintVitalReportRelease / lintVitalRelease`成功。
- 外周数字の実測文字幅・高さから目盛りへの半径方向の余白を確保する最終調整後にも全3タスクを再実行し成功（20秒）。以下のAPK確認は最終出力で実施。
- JUnit XML 14スイート、97件、失敗0、エラー0、スキップ0。
- 追加3件: 期限なし内部時間管理（開始前・数日経過・二重開始拒否）、5/15/30秒期限と停止、拡大速度窓の文字収容・支点/全目盛りとの分離。
- 既存CAN安全条件・停止プロトコル・Reader終了/取消・再接続ポリシー・HUD状態・デコーダー・通常OBDなどの単体テスト成功。
- `git diff --check`で空白エラーなし（GitのLF→CRLF警告のみ）。

SDK build-tools/36.0.0の以下で実出力を検査:

```powershell
aapt.exe dump badging app/build/outputs/apk/debug/app-debug.apk
aapt.exe dump badging app/build/outputs/apk/release/app-release-unsigned.apk
apksigner.bat verify --verbose app/build/outputs/apk/debug/app-debug.apk
apksigner.bat verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk
```

- 両APKともapplicationId=dev.aw.cardashboard、versionCode=10、versionName=0.4.4。
- Debug: Verifies / APK Signature Scheme v2=true / 署名者1。
- Release: DOES NOT VERIFY / Missing META-INF/MANIFEST.MF。未署名として配布。
- build/distributions/CarDashBoard-v0.4.4-debug.apk と CarDashBoard-v0.4.4-release-unsigned.apkへコピーし、元出力とのSHA-256一致を確認。

## 実機未検証・制限

`adb devices`で接続端末なし。端末へのインストール・実描画・エミュレーター描画・VP11通信・実車試験は本版では未実施。

縦横、小型画面、文字倍率2倍、指定6回転数のCompose Preview定義あり。幾何テストは成功したが、フォントの実描画、0〜10000rpm全域の針とRPM文字の見え方、システムバー/切り欠きとの実際の干渉は端末確認が必要。

CAN監視の開始停止・断線・排他・プロンプト復帰・Socket終了は既存構造のコード確認と純粋ロジック試験。BluetoothSocketを含む統合試験の成功を意味しない。無送信/ACK抑止は未確認でCanLiveSafetyの無制限拒否を維持。RELEASE_NOTES-v0.4.4.mdの実機手順を参照。
