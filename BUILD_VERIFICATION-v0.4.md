# v0.4 ソフトウェア検証

検証日: 2026-11-15。既存ローカルプロジェクトを直接編集。Android Studio同梱JBR / Android SDK build-tools 36.0.0。

`./gradlew.bat assembleDebug assembleRelease testDebugUnitTest --console=plain`: **BUILD SUCCESSFUL**。
ReleaseのLintタスクも成功。初回は開発中のソース更新と同時に実行したLintのKotlin FIR内部例外で失敗。ソース更新完了後に同じコマンドを再実行し成功しました（Lint無効化なし）。
最終ログ: `build/v0.4-build.log`（Git対象外）。

JUnit XML: **86件、失敗0、エラー0**。既存78件とHudStateTestの追加8件。

追加検証: RPM・車速・アクセル換算、負の車速とアクセル上限・下限、ダイヤル上限を超える実測RPMの保持、異なるID・短いフレームの拒否、未接続、OBD値の混入防止、RPM 0のLIVE判定、実測Hz、3秒境界、STALE時の値保持、DEMO分離、通信エラー、モード切替・切断時の無効化。

APKのaapt dump badgingでapplicationId=`dev.aw.cardashboard`、versionCode=`6`、versionName=`0.4`を両方確認。
Debug APKのapksigner verifyは成功。Release APKは署名なし（Missing META-INF/MANIFEST.MF）を確認。

| 配布APK | bytes |
| --- | ---: |
| CarDashBoard-v0.4-debug.apk | 30494130 |
| CarDashBoard-v0.4-release-unsigned.apk | 21754808 |

出力は `app/build/outputs/apk/`、配布名のコピーは `build/distributions/`（Git対象外）。

adb devicesに端末なし。Android実機UI・描画の視認性・Bluetooth・VP11・RX-8の信号換算・実測受信Hz・実際の通信エラー遷移は未検証。実機チェック手順は [Release Notes](RELEASE_NOTES-v0.4.md) を参照。
既存CAN実験の毎回同意・最大5秒制限を維持しています。
