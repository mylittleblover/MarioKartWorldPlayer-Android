# JukeKart

[简体中文](README.md) | [繁體中文](README.zh-Hant.md) | **English** | [日本語](README.ja.md)

**Beta 0.1.6** · Android 8.0 (API 26) and higher

JukeKart is an independent Android soundtrack player for *Mario Kart World*. Import your own `.mkwpack` archive to switch course music stages and variants interactively, play course medleys, listen along world-map routes, or organize favorites and playlists in the Jukebox.

- **Courses & Routes**: Interactive stage switching, course medleys, official Grand Prix / Knockout Tour routes, custom routes, and the Flow Editor for saving custom course arrangements.
- **Jukebox & Library**: Multi-pack merging, search, favorites, playlists, background playback, and system media controls.
- **Playback & Management**: Equalizer and audio effects, speed and pitch controls, A–B loop, scheduled sleep/switch timer, home-screen widget, Quick Settings tile, and JSON/M3U backup.

## Installation & Import

Install the JukeKart Release APK, then import your pack from the first-launch welcome screen or via **Settings → Import .mkwpack**. When optional artwork, maps, or unlock videos are absent, the app falls back to built-in graphics and animations. The launcher and notification icons retain their existing designs.

- **Complete `.mkwpack` Pack Download (MEGA)**: https://mega.nz/file/j1BxBRyI#cJxJEof3uY3JxDSPiIep7WOI1mNyI2h3Vz7yZ3YRdOc

See [Beta Usage Guide](docs/USAGE.en.md), [Pack Format](docs/PACK-FORMAT.md), and [Changelog](CHANGELOG.en.md) for details.

## Building from Source

Prepare JDK 21, Android SDK Platform 35, NDK `27.0.12077973`, and CMake `3.22.1`. Set `JAVA_HOME` and `ANDROID_HOME`, then run at the repository root:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. The debug APK is output to `app/build/outputs/apk/debug/`. See the [Architecture Guide](docs/ARCHITECTURE.en.md) for an overview of modules and the native engine.

Maintainers can create a git-ignored `keystore.properties` locally with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` pointing by absolute path to a keystore outside the repository, then run `./gradlew :app:assembleRelease`. The release APK is output to `app/build/outputs/apk/release/`. Debug builds use the repository's debug keystore.

## Feedback & License

Report issues at [GitHub Issues](https://github.com/mylittleblover/JukeKart-Android/issues) with your app version, device model, Android version, and reproduction steps. JukeKart's original source code and built-in original vector artwork are licensed under [Apache-2.0](LICENSE); third-party components and their respective licenses are listed in [Third-Party Notices](NOTICE-THIRD-PARTY.md).

JukeKart is an independent fan project. Nintendo has no affiliation with it and does not endorse or sponsor it. Game names, trademarks, and game assets remain the property of their respective owners.
