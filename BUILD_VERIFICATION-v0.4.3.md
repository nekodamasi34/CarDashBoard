# v0.4.3 検証記録

2026-11-15。Android Studio同梱JBR、既存プロジェクトを直接編集。

- `gradlew.bat assembleDebug assembleRelease testDebugUnitTest`: BUILD SUCCESSFUL。
- 最終コード変更後にも3タスクを実行、Release Lint Vitalを含め成功。
- JUnit XML合計94件、失敗0、エラー0（従来86件 + 新規8件）。
- 両APKのaapt badgingでdev.aw.cardashboard、versionCode=9、versionName=0.4.3。
- Debugのapksigner verify成功。ReleaseはMissing META-INF/MANIFEST.MFで未署名を確認。
- 配布用APKはbuild/distributionsに指定名でコピー。
- adb接続端末へDebug APKを更新インストールし、DEMOの実描画を確認。数値表示と支点の間隔を調整。画像はGit対象外のbuild/hud-*.png。
- 縦横切替コマンドを試したものの取得画像は縦のままで、横画面実描画の確認済みとはしない。Compose Previewは指定6回転数と縦横の定義を用意。
- 独立Canvas描画の中心と角度変換、速度窓と数字の領域分離、RPMの固定幅セルは単体テストで確認。Android各端末/文字倍率の描画を保証するテストではない。
- CAN開始/停止のセッション状態、上限、取消、PID許可状態、HUD反映は純粋ロジックの単体テスト。実BluetoothSocketの統合試験ではない。
- VP11/実車の受信、監視停止とSocketの実動作、断線時挙動、無送信/ACK抑止、切り欠き・戻るの端末実動作、純正写真との直接比較は未確認。

元から存在した.idea/gradle.xmlの未コミット変更は変更・破棄せず、リリースコミットから除外する。

実機手順と安全上の制限はRELEASE_NOTES-v0.4.3.mdを参照。
