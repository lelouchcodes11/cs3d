# CloudStream for Windows

A native Windows app for movies, series, anime and live TV. It runs on the [CloudStream](https://github.com/recloudstream/cloudstream) engine, so it works with the same extensions, but it looks and feels like a real Windows 11 app.

<p align="center">
  <a href="https://t.me/+QYV8hdldi_w0MDBl"><b>💬 Join the Telegram channel</b></a><br>
  <sub>News, new versions, help and feedback: this is where everything happens.</sub>
</p>

> **Pre-release.** The app is new and still gets updates often. If something is wrong, tell us on Telegram.

## Download

1. Open the [Releases](../../releases) page and download the newest `CloudStream-…-windows-x64.msi`.
2. Run it. It installs just for you (no administrator rights).
3. Open **CloudStream** from the Start menu.

Windows 10 or 11, 64-bit. Windows SmartScreen may ask for confirmation because the installer is not code-signed yet: choose *More info, Run anyway*.

New versions are announced inside the app and install with one click. Your settings and downloads stay when you update.

**Already have 0.1.0 or 0.1.1?** If the app does not offer the update, download the newest MSI from [Releases](../../releases) once and run it: it upgrades the installed app in place.

## First steps

1. **Add an extension repository** (Extensions page). The app comes with none, exactly like CloudStream.
2. Search or browse, open a title and press **Play**.
3. *Optional:* sign in to AniList, MyAnimeList or Simkl (Settings → Accounts & security) to sync your list and progress.

## What you get

- Home, search, library and downloads in a clean Windows 11 style, with your own look: menu position, corners, poster size.
- A player built on mpv: live TV, subtitles, audio and video tracks, picture in picture.
- Open any link in **VLC** or your browser instead, if you prefer.
- Progress and lists synced with AniList, MyAnimeList and Simkl.

## Help

- **Telegram:** [t.me/+QYV8hdldi_w0MDBl](https://t.me/+QYV8hdldi_w0MDBl) for questions, problems and ideas.
- **GitHub:** [issues](../../issues) for bug reports. Say what you did and what happened, and add the crash log if there is one (Settings → About).

## About content

The app contains **no extensions and no content**. Extensions are made by other people and added by you; you are responsible for what you use them for.

<details>
<summary>For developers</summary>

You need JDK 21 and [Git LFS](https://git-lfs.com) (run `git lfs install` before cloning; the bundled mpv library is stored with it).

```
./gradlew :app:runDev           # run from source
./gradlew :app:portableDist     # portable folder in dist/CloudStream-Portable
./gradlew :app:installerMsi     # installer in dist/release (needs the WiX Toolset 3.x on PATH)
./gradlew :app:test
```

AniList, MyAnimeList and Simkl need an API client of your own: register one on each service and paste its ID in Settings → Accounts & security → Sign-in keys (the dialog shows the redirect address to use). Builds can also take them from `local.properties` (`anilist.key`, `mal.key`, `simkl.id`, `simkl.secret`) or the environment.

| Folder | What it is |
| --- | --- |
| `app` | The desktop app: interface, player, updates, packaging |
| `android` | The Android APIs that CloudStream and its extensions use, built for the desktop JVM |
| `library`, `shared` | CloudStream's plugin API, extractors and shared UI (upstream sources) |
| `tools` | Developer tools |
| `docs/PLAN.md` | Architecture, decisions, test notes |

Licensed under GPL-3.0, like CloudStream.

</details>
