# v0.4.1 ソフトウェア検証

検証日: 2026-11-15。既存ローカルプロジェクトを直接編集。Android Studio同梱JBR / Android SDK build-tools 36.0.0。

`./gradlew.bat assembleDebug assembleRelease testDebugUnitTest`: **BUILD SUCCESSFUL**。Release Lintも成功。
初回はComposeの暗黙レシーバー参照によるコンパイルエラーを修正し、同じ3タスクを再実行して成功。
JUnit XML: **86件、失敗0、エラー0**。

既存HudStateTest 8件でOFFLINE / WAITING / LIVE / DEMO / STALE / ERROR、RPM 0、3秒境界、STALE値保持、デモ分離、CANデコード、OBD値混入防止、エラー時の値消去を確認。
CAN受信・制限・デコード・状態判定ロジックの変更なし。

ソース確認: HUD分岐は通常画面のスクロール・ヘッダー・接続欄より前で専用画面を返す。DisposableEffectによりHUD表示時のみWindowInsetsControllerCompatでsystemBarsをhide、離脱・破棄時はshowと通常レイアウトを復元。BackHandlerと画面内の戻るボタンを実装。数値はCompose Monospace / Canvas MONOSPACE。
これはソース確認であり、端末上のシステムバー・レイアウト・描画検証ではありません。

両APKのaapt dump badgingでapplicationId=dev.aw.cardashboard、versionCode=7、versionName=0.4.1を確認。
Debug APKのapksigner verifyは成功。Release APKはMissing META-INF/MANIFEST.MFで署名なしを確認。
配布用APKはbuild/distributions/（Git対象外）に指定名でコピー。

adb devicesに端末なし。SDK emulator実行ファイルなし。Android端末での没入表示・復帰、縦横・文字サイズ・カットアウト、DEMOの視覚品質、Bluetooth・実車信号換算は未確認。
実機確認・インストール手順はRELEASE_NOTES-v0.4.1.mdに記載。
