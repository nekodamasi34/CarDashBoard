# v0.3.2 ソフトウェア検証

検証日: 2026-11-15。既存ローカルプロジェクトを直接編集。Android Studio同梱JBR、Android SDK build-tools 36.0.0を使用。

`./gradlew.bat assembleDebug assembleRelease testDebugUnitTest --console=plain`: **BUILD SUCCESSFUL**。
最終ログ: ローカル `build/v0.3.2-build.log`。
JUnit XML: **78件、失敗0、エラー0**。既存57件を維持し21件追加。

追加テスト:

- CanExperimentTest (11): ATCSM1 OK/?/異常と混在応答、フィルタ、明示許可、プロトコル、5秒単調期限、ATMA/エコー、BUFFER FULL/CAN ERROR/STOPPED、RAW計測・CSV、0枚/手動停止、RAW上限。
- BluetoothRecoveryTest (6): バックオフ、セッション拒否、手動切断と再試行上限、Socket終了→Reader終了の順序、close失敗時のjoin、Coroutineキャンセル時の終了。
- MonitorStopProtocolTest (4): 単一CR→プロンプト→OBD復帰、終了済みプロンプト、停止タイムアウト時の廃棄、切断時に後続コマンドを送らないこと。

Android BluetoothSocketそのもの、12秒タイムアウトの実時間挙動、Android実機UI、VP11/RX-8は実機未検証。再接続テストはソフトウェアの復旧ポリシーと疑似Reader/close関数を対象とし、無線接続成功を保証しない。

## APK

実出力:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

配布用コピー: `build/distributions/`（Git対象外）。aapt dump badgingで両方ともapplicationId=`dev.aw.cardashboard`、versionCode=`5`、versionName=`0.3.2`を確認。存在・非ゼロサイズを確認。

apksigner verify: Debug成功、既存Android Debug署名のv2。Releaseは署名なし（Missing META-INF/MANIFEST.MF）を確認。署名設定変更・鍵作成なし。

| 配布APK | bytes | SHA-256 |
| --- | ---: | --- |
| CarDashBoard-v0.3.2-debug.apk | 30378130 | `fee8ced28e243391f3d59fd85b93ae1fa2ce4d001ca3f273755d2e3ebb2be047` |
| CarDashBoard-v0.3.2-release-unsigned.apk | 21722040 | `f736fee3a2a02b98d60b96e8c6d5428e4966536c8c500e7118a15bb8fe2432c3` |

実車用にはDebug APKを案内する。未署名Release APKは通常インストールできない。

## 実車確認待ち

ATMAの対応、0x201の送信条件とHz、フィルタ効果、ACK等の内部送信有無、5秒停止の実時間とプロンプト復帰、再接続時間と改善効果、通常OBD2への復帰、RX-8 RPM/車速式は未確認。
手順と安全条件は [CAN実験資料](CAN-EXPERIMENT-v0.3.2.md) を参照。
