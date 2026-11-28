# JukeKart

[简体中文](README.md) | **繁體中文** | [English](README.en.md) | [日本語](README.ja.md)

**Beta 0.1.6** · Android 8.0（API 26）以上

JukeKart 是專為《瑪利歐賽車 世界》配樂打造的獨立 Android 播放器。匯入自己的 `.mkwpack` 後，即可切換賽道音樂的階段與版本、播放賽道組曲、沿世界地圖路線聆聽道路音樂，或透過點唱機管理收藏與播放清單。

- **賽道與路線**：支援互動式階段切換、組曲、官方路線與自訂路線；流程編排器可儲存自訂賽道編排。
- **點唱機與曲庫**：支援多曲包合併、搜尋、收藏、播放清單、背景播放與媒體控制。
- **播放與管理**：提供等化器、速度與音高調節、A–B 循環、定時停止、桌面小工具、快速設定資訊方塊與資料備份。

## 安裝與匯入

安裝 JukeKart 的 Release APK，並在首次啟動歡迎頁或於應用程式內前往 **設定 → 匯入 .mkwpack** 匯入資源包。缺少選用的封面、地圖或解鎖演出影片時，應用程式會使用內建圖形與動畫。應用程式圖示與通知圖示沿用現有樣式。

- **完整 `.mkwpack` 資源包下載（MEGA）**：https://mega.nz/file/j1BxBRyI#cJxJEof3uY3JxDSPiIep7WOI1mNyI2h3Vz7yZ3YRdOc

詳見 [Beta 使用說明](docs/USAGE.zh-Hant.md)、[曲包格式](docs/PACK-FORMAT.zh-Hant.md) 與 [版本變更](CHANGELOG.zh-Hant.md)。

## 從原始碼建置

請準備 JDK 21、Android SDK Platform 35、NDK `27.0.12077973` 與 CMake `3.22.1`。設定 `JAVA_HOME` 和 `ANDROID_HOME` 後，於專案根目錄執行：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

在 Windows 上請將 `./gradlew` 改為 `.\gradlew.bat`。Debug APK 位於 `app/build/outputs/apk/debug/`。專案架構與模組說明詳見[架構文件](docs/ARCHITECTURE.zh-Hant.md)。

維護者可於本機建立受 Git 忽略的 `keystore.properties`，設定 `storeFile`、`storePassword`、`keyAlias` 與 `keyPassword`，並以絕對路徑指向專案庫外的金鑰庫後執行 `./gradlew :app:assembleRelease`。Release APK 位於 `app/build/outputs/apk/release/`。Debug APK 使用專案庫內建的測試簽章。

## 回報與授權

請至 [GitHub Issues](https://github.com/mylittleblover/JukeKart-Android/issues) 回報問題，並附上應用程式版本、手機型號、Android 版本與重現步驟。JukeKart 的原創原始碼與內建原創向量圖形採用 [Apache-2.0](LICENSE) 授權，第三方元件及其各自授權條款詳見[第三方授權條款](NOTICE-THIRD-PARTY.zh-Hant.md)。

JukeKart 為獨立的愛好者專案，與 Nintendo 無關，亦未獲得任天堂官方認可或贊助。相關名稱、商標與遊戲素材均歸各自權利人所有。
