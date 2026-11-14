CarDashBoard ver0.3「CAN直接受信・通信高速化検証」。

- 通常OBD2の3取得モード・ATAT1/2・デモ・ログ・30秒測定・CSVを維持。
- 実験用CAN直接受信を追加。CAN ID 0x201のRAW・DLC・受信時刻、RPM/車速デコード候補を表示。
- CANモニター画面、スマホ到着Hz、受信間隔（最新・平均・最小・最大・P95・標準偏差）、フレーム数・不正数・通信エラー数を追加。
- CAN専用30秒ベンチマークとCSV保存、通常OBD2との排他的な切替に対応。
- PID連続失敗、データ期限切れ、CAN受信停止時のLIVE解除・復旧処理を改善。
- versionCode 3 / versionName 0.3。アプリIDは dev.aw.cardashboard のまま。

CAN監視方式:
自動プロトコル選択と0100応答後にATDP/ATDPNで11bit/500kbpsを確認。確認できない場合はATSP6を適用せず中止します。
ATSP6→ATE0→ATL0→ATS0→ATH1→ATD1→ATCSM1→ATCRA201→ATMA。
設定に未対応応答があれば中止。監視停止はCRを1文字だけ送り、最大3秒で「>」を確認後、Socketを破棄して再初期化します。
監視失敗時もSocketを破棄し、通常OBD2を選択して接続し直せます。手動切断では自動再接続しません。

実機で未確認:
**CAN直接監視はVP11実機・日本仕様2005年式RX-8で動作確認していません。**
VP11のATD1/ATCSM1/ATCRA201/ATMA対応、実車プロトコル、監視停止・再接続、実機CSV保存操作、フレーム換算式は未検証です。
ATCSM1を必須にしていますが、互換ELM実装の完全なListen Only動作は保証できません。
ECU書込・故障コード消去・任意CANフレーム送信・車両制御は実装していません。
RPMはBE byte[0..1]/4、車速は(BE byte[4..5]-10000)/100の参考候補です。
0rpm/0km/hだけでは正しさを検証できません。停車中のアイドリングで標準OBD2と交互に照合してください。

測定上の注意:
CAN到着周期と車両CANバス上の送信周期は異なります。Bluetoothでまとめて届くことがあります。
フレーム到着HzとRPM値変化Hzを区別します。CAN受信間隔はOBD2リクエスト応答時間とは別指標です。
P95は1ms切上げヒストグラム（60秒以上は最終bin）、RAW履歴は35行に制限。
CAN高速化は未確認です。v0.2の30秒実測（RPM専用ATAT1 15.20Hz、ATAT2 16.97Hz、RPM優先ATAT2 15.27Hz、バランスATAT2 12.93Hz、各成功100%/timeout 0）と比較してください。

初回確認:
1. 停車中、IG ON・エンジン停止でVP11とBluetoothペアリング。
2. 通常OBD2でECU応答と30秒CSVを確認。
3. CANモニターでCAN選択・監視開始。ログのプロトコル・設定応答を確認。
4. ID 0x201/DLC 8のRAWと受信周期を確認し、CAN 30秒計測・CSV保存。
5. 監視停止・通常OBD2への復帰、手動切断後に再接続しないことを確認。
6. 換算候補は後から停車アイドリングのOBD2値と比較。走行中は操作しないでください。長時間のIG ONによるバッテリー消耗に注意。

添付APK:
- CarDashBoard-v0.3-debug.apk: デバッグ署名済み。**実機インストール用はこちら**。Androidにダウンロードして開き、必要に応じて配布元のインストールを許可してください。
- CarDashBoard-v0.3-release-unsigned.apk: 未署名Releaseビルド。そのままでは通常インストールできず、別途署名が必要です。
- 新しい署名鍵は作成していません。既存アプリと署名が一致しない場合は更新できません。

仕様参照:
- ELM327メーカー仕様: https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf （MA、CRA、CSM、DPN、D1）
- RX-8解析: https://github.com/Antipixel/RX8-Dash
- RX-8実測解析: https://blog.hiroaki.jp/blog/2010/02/22/canusb-rx-8/

ソフトウェア検証: Debug/Releaseビルド成功、ユニットテスト35件成功（既存27件＋追加8件）。Socket上の切替・監視停止・Bluetooth切断はVP11実機での確認が必要です。
