# CloudStream for Windows

A native Windows desktop app built on the engine of [CloudStream](https://github.com/recloudstream/cloudstream): the same extension
system, search, library and player logic, with a Windows 11 (Fluent) interface instead of the Android one in a window.

> **Pre-release.** Version 0.1.x is an early build. Expect rough edges.

**Found a problem or have an idea?** Report it in the [GitHub issues](../../issues) or in the [Telegram group](https://t.me/+QYV8hdldi_w0MDBl).

## Install

1. Download `CloudStream-<version>-windows-x64.msi` from the [latest release](../../releases).
2. Run it. It installs for your user only (no administrator rights), adds a Start menu entry and a desktop shortcut, and lets you pick the folder.
3. A newer MSI upgrades the installed app in place. The app also looks for new releases by itself, see *Updates*.

Requires Windows 10 or 11, 64-bit. The MSI is not code-signed yet, so Windows SmartScreen may ask for confirmation.

Your settings, extensions and downloads are kept in `%APPDATA%\CloudStream` and survive upgrades and uninstalling.

## Support and bug reports

- [GitHub issues](../../issues): bugs and feature requests. Say what you did, what you expected and what happened, and attach `crash.log` (Settings > About > Crash log) if there is one.
- [Telegram group](https://t.me/+QYV8hdldi_w0MDBl): questions, problems and ideas.

## Extensions and content

The app contains **no extensions and no content**. Like CloudStream, it loads third-party extension repositories (`.cs3` plugins) that you add
yourself (Extensions page). Those repositories and what they provide are not part of this project; you are responsible for what you use them for.

## Updates

A few seconds after start the app asks GitHub for the list of releases of this repository (at most every few hours) and offers a newer version:
*Download and install* fetches the MSI, checks its size and SHA-256, closes the app, upgrades it and starts it again. Settings > About has
*Check for updates* and a switch for the automatic check. Only the public releases list is requested, nothing about you is sent.
Pre-releases are offered while the installed version is a 0.x version. A portable copy only opens the release page.

## Build from source

You need JDK 21 and [Git LFS](https://git-lfs.com) (the bundled libmpv DLL is stored with it: run `git lfs install` before cloning).

```
./gradlew :app:runDev                  # run from source
./gradlew :app:portableDist            # portable folder in dist/CloudStream-Portable (no installer, data in .\data)
./gradlew :app:installerMsi            # installer in dist/release (needs the WiX Toolset 3.x on PATH for jpackage)
./gradlew :app:test
```

Optional keys of the tracker integrations go in `local.properties` (`simkl.id`, `simkl.secret`, `mal.key`, `anilist.key`) or the environment
(`SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET`, `MAL_KEY`, `ANILIST_KEY`); the app builds without them.

| Module | What it is |
| --- | --- |
| `app` | The desktop application: Fluent UI (`desktop/ui`), player (libmpv), update checker, packaging |
| `android` | The Android APIs that CloudStream and its extensions use, implemented for the desktop JVM (same package and signatures) |
| `library` | CloudStream's plugin API and extractors (upstream sources, JVM build) |
| `shared` | CloudStream's shared Compose components and theme (upstream sources, JVM build) |
| `tools` | Developer tools: extension API checker, Ultima sync test servers |
| `docs/PLAN.md` | Architecture, decisions, test notes and the list of open work |

## Releasing

Raise `desktopVersion` in `app/build.gradle.kts`, run `./gradlew :app:installerMsi`, and publish the MSI from `dist/release` as a GitHub release tagged
`v<version>` (mark it *pre-release* while the version starts with 0). The update checker compares that tag with the installed version.

## Licence and credits

GPL-3.0, see [LICENSE](LICENSE). This project reuses source code of CloudStream (GPL-3.0, [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream),
base commit in `UPSTREAM_COMMIT`). Binary components: [mpv](https://mpv.io) / libmpv 0.41 with FFmpeg (GPL), Chromium Embedded Framework through
[JCEF](https://github.com/jcefmaven/jcefmaven) (BSD-style), Conscrypt, Bouncy Castle and the other libraries pulled in by Gradle under their own licences.
