# CarDashBoard v0.3.2

Mazda RX-8（2005年式 前期 Type S 6MT）向けAndroid/Kotlin/Jetpack Composeアプリ。
Bluetooth Classic SPP接続のELM327互換アダプターを使用します。
Application ID: `dev.aw.cardashboard` / versionCode: `5`。

通常OBD2のRPM・車速・水温、3ポーリングモード、ATAT1/2、30秒ベンチマーク、CSV、通信ログ、デモを維持しています。
CAN ID 0x201のRAW解析、受信周期・Hz計算とCANベンチマークの処理も維持しています。換算式は未検証の参考値です。

## アダプター診断

停車中に通常OBD2で接続し、「アダプター診断」→「診断開始」。
PID取得を停止し、Bluetooth再接続・ELM初期化後、ATI / AT@1 / ATDP / ATDPN / ATH1 / ATD1 / ATCRA201 / ATCSM1を順番に実行します。
コマンド別の対応状況、応答種別、終端を含むRAWを表示します。停止は現在のコマンド完了または最大3秒のタイムアウト後です。
終端未受信・切断時は次の診断コマンドを送信せずSocketを破棄します。終了・停止・失敗後は新しいSocketとELM初期化で通常OBD2へ復帰します。
切断ボタンは自動再接続を禁止します。

診断のコピー・CSVにはBluetoothアドレスとアプリで表示した機器名を含めません。RAWにはアダプター自身の識別情報が含まれ得るので共有前に内容を確認してください。

## CAN監視

ATCSM1の `?` はそのコマンドの未対応であり、CAN監視全体の非対応とは判定しません。
ERROR、タイムアウト、空・不完全応答などは対応状況不明の応答異常です。
通常CAN監視はサイレント動作の信頼済み根拠がないため保留します。
別の「CAN実験モード」は毎回警告を確認して開始し、ATCSM1がOKまたは単独の ? でも、CANプロトコル・設定・ATCRA201成功・ECU確認・通信排他が揃えばATMAを最大5秒間だけ試せます。
OKは物理的Listen Onlyの証明ではありません。フィルタなしへの自動切替、ATMA自動再実行はありません。
手動停止・5秒自動停止後は単一文字とプロンプト復帰を確認し、Socket/Readerを終了して通常OBD2へ戻します。
RAW受信HzとRPM変化Hzを分け、サマリー・RAWをSAF CSVへ保存します。
実車手順と未確認事項は [CAN実験資料](CAN-EXPERIMENT-v0.3.2.md) を参照してください。

## ビルド・テスト

Android StudioのJDKとAndroid SDK 36.1を使用します。

```powershell
./gradlew.bat assembleDebug assembleRelease testDebugUnitTest
```

単体テストには既存の30秒ベンチマークに加え、5秒実験期限・許可条件・RAW上限・CSV・停止プロトコル・Reader終了/キャンセル・再接続ポリシーのテストを含みます。
テストデータはアプリの実車測定値へ注入しません。Bluetooth Socket操作・実車挙動は単体テストでは検証できません。

## 変更履歴

- v0.3.2: 毎回同意のCAN実験モード、ATMA最大5秒監視、RAW CSV、停止・Bluetooth復旧の安定化。
- v0.3.1: VP11互換性診断、RAW/CSV/コピー、ATCSM1未対応と通信異常の区別、未検証ATMAの抑止、失敗後の通常OBD2への復帰。
- v0.3: CAN直接受信・0x201解析、CANベンチマーク、通信高速化検証。
- v0.2: 3種類のポーリング、ATAT1/2、OBD2ベンチマーク。

インストール方法・未検証事項は [Release Notes](RELEASE_NOTES-v0.3.2.md) を参照してください。
