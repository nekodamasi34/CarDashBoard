# v0.3.1 ソフトウェア検証

検証日: 2026-11-15。現在のローカルプロジェクトを使用。

`./gradlew.bat assembleDebug assembleRelease testDebugUnitTest --console=plain`: BUILD SUCCESSFUL。
JUnit XML集計: 57件、失敗0、エラー0（既存35件・追加22件）。
診断応答分類12件・CAN安全判定/状態遷移10件を追加。
既存のPID解析、RPM更新Hz、CANフレーム、CAN受信Hz、30秒測定/CSV、データ鮮度のテストをすべて維持。
物理Bluetooth Socketの競合・キャンセル・再接続、実車CAN ACKは実機検証が必要です。

Android SDK build-tools 36.0.0のaaptで両APKを確認:

- applicationId: `dev.aw.cardashboard`
- versionCode: `4`
- versionName: `0.3.1`
- Debug: debuggable、apksigner verify成功、既存Android Debug証明書、APK Signature Scheme v2。
- Release: debuggableなし、apksigner verifyで署名なし（Missing META-INF/MANIFEST.MF）。新規署名なし。

| APK | サイズ（bytes） | SHA-256 |
| --- | ---: | --- |
| CarDashBoard-v0.3.1-debug.apk | 30293274 | `61ec958627dfd52c39f5daae87aaa010b76de39335ed92d66d305f34138bcd7f` |
| CarDashBoard-v0.3.1-release-unsigned.apk | 21689272 | `1bd8064dbd1ac9a3c5b9928e8a6f63b2b22b0bdef707e285a4411afce7e24c74` |

配布用コピーはローカルの `build/distributions/` に保存（Git対象外）。
VP11実機・RX-8での新たな実測は行っていません。未検証項目は互換性資料とRelease Notesに記載しています。
