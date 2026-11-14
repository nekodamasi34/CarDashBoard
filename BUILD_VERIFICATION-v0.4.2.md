# v0.4.2 ソフトウェア検証

検証日: 2026-11-15。既存Android Studioプロジェクトを直接編集。
Android Studio同梱JBR / Android SDK build-tools 36.0.0。

## ビルド・単体テスト

`./gradlew.bat assembleDebug assembleRelease testDebugUnitTest`: **BUILD SUCCESSFUL**。Release Lint Vitalも成功。
Orbitron字幅と行高の調整後、同じ3タスクを再実行して成功。
JUnit XML: **86件、失敗0、エラー0**。

既存HudStateTest 8件でCANの3値デコード、OFFLINE / WAITING / LIVE / DEMO / STALE / ERROR、RPM 0、3秒境界、STALE値保持、デモ分離、OBD値混入防止、エラー時の値消去を確認。CANデコード・受信・状態判定ロジックの変更なし。
既存の診断、CAN実験、停止プロトコル、Bluetooth復旧、ベンチマーク、パーサー、ポーリング等のテストもすべて成功。

## フォントとレイアウトのソース確認

- Google FontsのOrbitron可変TTFを同梱し、SIL OFL全文もassetsへ同梱。HUD専用FontFamilyの参照はHUDの数値描画のみ。通常UIのMaterialThemeへは適用しない。
- Java AWTで同梱TTFを読み込み、Family=Orbitronを確認。数字0〜9の送り幅は0.391〜0.834em、ハイフンは0.517em。0.90emの固定セルに収まることを確認。これはフォントデータの確認で、Androidの実描画検証ではない。
- RPMは5セル、速度は3セル、アクセルは3セル。欠測は--、速度0は0。最大CAN値のRPM16384/速度555も予約セルに収まる。アナログ針のみ0〜10000rpmへ制限。
- 速度窓をタコ内右下の相対座標で配置し、縦横で同じ構成。アナログ10の数字と窓が重ならないよう窓幅を抑制。RPM/速度のフォントサイズと行高を計器寸法に連動させ、通常UIの本文行高の継承を防止。
- タコサイズは利用可能な幅と高さの小さい方から計算し、下部アクセル用の領域を確保。左右のポッドは数値・ラベル・目盛りがない装飾。
- HUDは通常画面のヘッダー等より前で専用画面を返す。DisposableEffectでsystemBarsをhide、離脱/破棄時はshowと通常レイアウトを復元。BackHandlerと48dpのタッチ領域を持つ戻る操作を維持。
- rememberSaveableでページを保持し、フォーカス復帰時はHUDの没入表示を再適用。接続・デモ状態のActivityをまたぐ保持は今回変更していない。

上記はソース・フォントメトリクスの確認であり、実際の端末レイアウトやシステムバーの表示試験ではない。

## APK

両APKのaapt dump badgingでapplicationId=dev.aw.cardashboard、versionCode=8、versionName=0.4.2を確認。
Debugのapksigner verify成功。ReleaseはMissing META-INF/MANIFEST.MF（未署名）を確認。
両APKのZIP内容に同梱TTFとバイト単位で一致するOrbitronリソース、およびassets/licenses/Orbitron-OFL.txtが含まれることを確認（Releaseではリソースパスが最適化される）。
配布用はGit対象外のbuild/distributions/へ指定名でコピー。

## 未確認事項

adb devicesに端末なし。SDKにsystem imageはあるがemulator実行ファイルはない。
Androidでの実画面・Orbitronの視認性、縦横・文字倍率・カットアウト・針と文字の関係、フルスクリーンからの復帰、Bluetooth/VP11・実車換算は未確認。
縦横DEMO・コンパクト/文字倍率1.5のSTALE・ERRORのCompose Previewを用意したが、実描画は未検証。
実機確認・インストール方法はRELEASE_NOTES-v0.4.2.mdに記載。
