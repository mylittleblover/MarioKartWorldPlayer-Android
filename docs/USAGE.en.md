# JukeKart Beta Usage Guide

[简体中文](USAGE.md) | [繁體中文](USAGE.zh-Hant.md) | **English** | [日本語](USAGE.ja.md)

## Installation & Importing Music

1. Install the JukeKart `Beta 0.1.6` Release APK on Android 8.0 or higher. Your device may prompt you to allow installation from this source.
2. Download the complete [`.mkwpack` archive (MEGA)](https://mega.nz/file/j1BxBRyI#cJxJEof3uY3JxDSPiIep7WOI1mNyI2h3Vz7yZ3YRdOc) (or prepare your own compatible `.mkwpack`) and save it somewhere accessible to the Android system file picker. The app imports pack contents into internal storage, so keep sufficient free space available. See [Pack Format](PACK-FORMAT.md) for the archive specification.
3. On first launch, you can grant notification permission and select a `.mkwpack` directly from the welcome screen, or open **Settings → Import .mkwpack** at any time to choose a file and wait for verification. Once complete, open **Import details** to review playable tracks. Multiple packs can be imported sequentially and merge automatically into your library.
4. If an import is interrupted, choose **Resume import** in Settings. If the system file access grant has expired, re-select the exact same `.mkwpack` file when prompted.

If an Alpha build signed with the older debug/test key is already installed, the Release APK with the same package name cannot install directly over it. Export an in-app backup and keep your original `.mkwpack` files first, then uninstall the old build. Uninstalling clears the app's internal storage.

## Playback & Editing

| Feature | How to use |
|---|---|
| Course console | Pick any course with imported audio from Home. The console strictly follows MKW v1.8.0 transition rules and displays available stages, endings, Final Lap toggles, region switches, and alternate-version controls. |
| Medleys & Routes | Select from 121 course medleys, official Grand Prix / Knockout Tour routes, or Free Roam routes on the course or map screen. Pure Free Roam routes support both **Route Rules** and **In-Game Free Roam Library** track pools. |
| Custom routes | Build routes on the world map along the 202 verified MKW v1.8.0 directed connections, choose road music rules or the in-game Free Roam pool, then save or play. |
| Free Roam Radio | Listen directly to the 13 official in-game `SoundJukeBoxPlaylist` slot tables across 7 modes (Auto Clock, Solo Day/Night, Full In-Game Free Roam Pool, Free Roam Exclusives, Online Day/Night) with 9-track history dedup and 1.5 s inter-track breathing gap. |
| Flow Editor | Open the Flow Editor on supported courses to arrange stages and transition modes, save reusable presets, export/import flow JSON, or render lossless WAV / FLAC audio offline. |
| Track Markers & Staff Credits | Bookmark timestamps on any track for instant seeking; during *Staff Credits* (including automatic RMS-envelope offset calibration for the Nintendo Music Special edition), the progress bar dynamically sweeps through 18 course theme colors and a flowing Rainbow Road gradient. |
| Jukebox, Themes & Special Cup | Search globally, manage favorites/playlists, switch among 7 character themes in the Settings theme sheet, check GitHub updates in-app, and unlock the Special Cup & Rainbow Road via playback progress or the unlock quiz. |
| Background controls | Leave the app while playing and control playback via system media controls, the home-screen widget, or the Quick Settings tile. Grant notification permission when prompted on Android 13+. |

Course covers, map artwork, and unlock videos can be supplied by a pack. When absent, the app displays built-in covers, map graphics, or a landscape fallback animation. Missing audio must be supplied by importing a pack containing those assets.

## Backup & Restore

Open **Settings → Backup & Export** to save a JSON backup outside the app. When restoring, choose **Merge with current library** or **Overwrite completely**. Overwriting replaces your current playlists, favorites, presets, routes, play statistics, and preferences with the backup; audio files must be re-imported from your original `.mkwpack` archives. You can also export playlists individually as M3U.

## Reporting Issues

When reporting playback, import, or display issues, include the JukeKart version, device model, Android version, track name or pack version, and reproduction steps. If an issue occurs during background playback, note whether you were using the notification, Quick Settings tile, or home-screen widget. [Submit a GitHub Issue](https://github.com/mylittleblover/JukeKart-Android/issues)
