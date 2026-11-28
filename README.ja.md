# JukeKart

[简体中文](README.md) | [繁體中文](README.zh-Hant.md) | [English](README.en.md) | **日本語**

**Beta 0.1.6** · Android 8.0（API 26）以降

JukeKart は『マリオカート ワールド』のサウンドトラック向け独立 Android プレイヤーです。ユーザー自身の `.mkwpack` パックをインポートすることで、コース楽曲のパートやバージョンを切り替えたり、コースメドレーやワールドマップ上のルート連動再生を楽しんだり、ジュークボックスでお気に入りやプレイリストを管理したりできます。

- **コースとルート**：インタラクティブなパート切替、コースメドレー、公式ルート、カスタムルートに対応。フローエディターでカスタム構成を保存できます。
- **ジュークボックスとライブラリ**：複数パックの統合、検索、お気に入り、プレイリスト、バックグラウンド再生、システムメディアコントロールに対応。
- **再生と管理**：イコライザー、再生速度・ピッチ調整、A–B リピート、タイマー停止、ホーム画面ウィジェット、クイック設定タイル、データバックアップを備えています。

## インストールとインポート

JukeKart の Release APK をインストールし、初回起動時のウェルカム画面またはアプリ内の **設定 → .mkwpack をインポート** からパックを読み込んでください。任意のカバー画像・マップ・解放演出動画が含まれていない場合は、内蔵のグラフィックとアニメーションが使用されます。アプリアイコンおよび通知アイコンは従来デザインを引き継ぎます。

- **完全版 `.mkwpack` パックのダウンロード（MEGA）**：https://mega.nz/file/j1BxBRyI#cJxJEof3uY3JxDSPiIep7WOI1mNyI2h3Vz7yZ3YRdOc

詳しくは [Beta 利用ガイド](docs/USAGE.ja.md)、[パック形式仕様](docs/PACK-FORMAT.ja.md)、[変更履歴](CHANGELOG.ja.md) をご覧ください。

## ソースからのビルド

JDK 21、Android SDK Platform 35、NDK `27.0.12077973`、CMake `3.22.1` を用意し、`JAVA_HOME` と `ANDROID_HOME` を設定した上でリポジトリのルートで以下を実行します：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 環境では `./gradlew` を `.\gradlew.bat` に置き換えてください。Debug APK は `app/build/outputs/apk/debug/` に出力されます。プロジェクト構成とネイティブエンジンの詳細は[アーキテクチャ解説](docs/ARCHITECTURE.ja.md)を参照してください。

メンテナーが Release ビルドを行う場合は、Git 管理対象外の `keystore.properties` をローカルに作成し、リポジトリ外のキーストアへの絶対パスを指定して `storeFile`、`storePassword`、`keyAlias`、`keyPassword` を設定した上で `./gradlew :app:assembleRelease` を実行してください。Release APK は `app/build/outputs/apk/release/` に出力されます。Debug APK にはリポジトリ付属のテスト署名が使われます。

## フィードバックとライセンス

不具合や要望は [GitHub Issues](https://github.com/mylittleblover/JukeKart-Android/issues) へ、アプリバージョン・端末モデル・Android バージョン・再現手順を添えてお送りください。JukeKart のオリジナルソースコードおよび内蔵オリジナルベクターアートワークは [Apache-2.0](LICENSE) でライセンスされています。サードパーティ製コンポーネントとそれぞれのライセンスは[サードパーティライセンス通知](NOTICE-THIRD-PARTY.ja.md)をご覧ください。

JukeKart は非公式のファン制作プロジェクトであり、任天堂とは一切関係なく、公式の許諾・推奨・後援を受けたものではありません。ゲーム名称・商標・ゲーム素材の権利はそれぞれの権利者に帰属します。
