# JukeKart

**简体中文** | [繁體中文](README.zh-Hant.md) | [English](README.en.md) | [日本語](README.ja.md)

**Beta 0.1.6** · Android 8.0（API 26）及以上

JukeKart 是面向《马力欧卡丁车 世界》配乐的独立 Android 播放器。导入自己的 `.mkwpack` 后，可以切换赛道音乐的阶段和版本，播放赛道组曲，沿世界地图路线听音乐，或用点唱机管理收藏和播放列表。

- **赛道与路线**支持交互式阶段切换、组曲、官方路线和自定义路线。流程编排器可以保存自定义赛道编排。
- **点唱机与曲库**支持多曲包合并、搜索、收藏、播放列表、后台播放和媒体控制。
- **播放与管理**提供均衡器、速度与音高调节、A–B 循环、定时停止、桌面小组件、快捷设置磁贴和数据备份。

## 安装与导入

安装 JukeKart 的 Release APK，然后在首次启动欢迎页或应用内打开 **设置 → 导入 .mkwpack** 导入资源包。缺少可选的封面、地图或演出视频时，应用会使用自带图形和动画。应用图标及通知图标沿用现有样式。

- **完整 `.mkwpack` 资源包下载（MEGA）**：https://mega.nz/file/j1BxBRyI#cJxJEof3uY3JxDSPiIep7WOI1mNyI2h3Vz7yZ3YRdOc

详见 [Beta 使用说明](docs/USAGE.md)、[曲包格式](docs/PACK-FORMAT.zh-Hans.md)和[版本变更](CHANGELOG.md)。

## 从源码构建

准备 JDK 21、Android SDK Platform 35、NDK `27.0.12077973` 和 CMake `3.22.1`。设置 `JAVA_HOME` 和 `ANDROID_HOME`，然后在仓库根目录运行：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 上将 `./gradlew` 换成 `.\gradlew.bat`。Debug APK 位于 `app/build/outputs/apk/debug/`。项目架构与核心模块说明详见[架构文档](docs/ARCHITECTURE.md)，赛道配乐与路线无缝衔接原理详见[《马力欧卡丁车 世界》音乐衔接机制详解](docs/MUSIC-TRANSITIONS.md)。

维护者可在本地创建被 Git 忽略的 `keystore.properties`，配置 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。使用绝对路径指向仓库外的密钥库，再运行 `./gradlew :app:assembleRelease`。Release APK 位于 `app/build/outputs/apk/release/`。Debug APK 使用仓库提供的测试签名。

## 反馈与许可

在 [GitHub Issues](https://github.com/mylittleblover/JukeKart-Android/issues) 提交问题，请附上应用版本、手机型号、Android 版本和复现步骤。JukeKart 的原创源代码与内置原创矢量图形采用 [Apache-2.0](LICENSE) 许可，第三方组件及其各自许可详见[第三方开源许可声明](NOTICE-THIRD-PARTY.zh-Hans.md)。

JukeKart 是独立的爱好者项目，与 Nintendo 无关联，亦未经 Nintendo 认可或赞助。相关名称、商标和游戏素材归各自权利人所有。
