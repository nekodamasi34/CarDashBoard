# CarDashBoard v0.3.2

「CAN直接監視の実機実験・Bluetooth通信安定化」。既存プロジェクトのv0.3.1から更新。applicationIdはdev.aw.cardashboard、versionCode=5、versionName=0.3.2。

## v0.3.1からの変更点

- 通常CAN監視と分離した、初期無効・毎回確認のCAN実験モードを追加。
- ATCSM1=OKは受付成功として記録し、物理的サイレント動作の証明とは区別。単独の ? は明確な未対応として記録し、明示同意と他の条件が揃う場合のみ続行。ERROR・タイムアウト・切断では中止。
- CAN 11bit/500kbps、ATSP6、ATH1、ATD1、ATCRA201成功を確認し、ATMAでCAN ID 0x201を直接監視。フィルタなしのフォールバックなし。
- 通信処理の単調時計で5秒自動停止。手動停止、エラー停止、単一文字送信と > 復帰確認、Socket廃棄・通常OBD2復旧。ATMA自動再実行なし。
- CANベンチマークでRAW受信Hz・フレーム数・平均/最短/最長/P95/標準偏差・不正行・通信エラー・測定時間を計測。RPM値変化Hzとは別表示。
- SAFでサマリーCSVと最大10000行のRAW CSVを保存。受信ゼロの原因候補を案内し、0枚だけで非対応と判定しない。
- BluetoothはSocket終了後のReader終了待ち、旧操作終了待ち、セッション競合防止、モード切替時600ms待機、再接続バックオフと最大3試行、接続時間・例外ログを改善。
- 通常OBD2、3ポーリングモード、ATAT設定、RPM/車速/水温、従来ベンチマーク・CSV、CAN解析、アダプター診断、デモとダークテーマを維持。

## 安全性と未検証事項

このアダプターのCANサイレント監視動作は未検証です。ATMAはCAN監視コマンドですが、互換ファームウェアによるACK等の送信有無は保証されません。実車CAN通信へ影響する可能性があります。完全停車状態で短時間の実験を行ってください。

ECU書込み、故障コード消去、任意CAN送信、ECU制御、サイレント無効化は実装しません。AT設定の受付と物理的Listen Onlyは別です。実験許可は保存せず毎回消費します。

VP11実機でのATMA、0x201受信Hz、ACK抑制、停止・再接続、RX-8デコード式は未検証です。初回は停車・IG ON・エンジン停止で最大5秒。RPMが0でもRAW Hzで評価してください。5秒期限はAndroidスケジューラによる遅延があり、ハードリアルタイム保証ではありません。

## APKインストール

- 実機用: `CarDashBoard-v0.3.2-debug.apk` をAndroid端末へダウンロードし、使用ブラウザ/ファイルアプリの「不明なアプリのインストール」を必要に応じて許可してインストール。既存Debug署名を使用。既存版と署名が異なる場合は上書きできません。
- `CarDashBoard-v0.3.2-release-unsigned.apk` は未署名のビルド成果物で、そのまま通常インストールできません。新規署名鍵の作成・公開や署名設定の変更は行っていません。
- Android 12/API 31以上。Bluetooth接続権限を許可し、VP11を端末設定でペアリングして接続します。

## ソフトウェア検証とハッシュ

assembleDebug / assembleRelease / testDebugUnitTest成功。JUnit 78件、失敗0、エラー0（既存57件＋追加21件）。物理Bluetooth Socket・実車の確認は含まれません。

| APK | SHA-256 |
| --- | --- |
| Debug | `fee8ced28e243391f3d59fd85b93ae1fa2ce4d001ca3f273755d2e3ebb2be047` |
| Release unsigned | `f736fee3a2a02b98d60b96e8c6d5428e4966536c8c500e7118a15bb8fe2432c3` |

詳細手順: [CAN実験資料](https://github.com/nekodamasi34/CarDashBoard/blob/v0.3.2/CAN-EXPERIMENT-v0.3.2.md)。[ビルド検証記録](https://github.com/nekodamasi34/CarDashBoard/blob/v0.3.2/BUILD_VERIFICATION-v0.3.2.md)。
