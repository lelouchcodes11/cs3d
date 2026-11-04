<p align="center">
  <img src="app/src/main/icons/icon.png" width="96" height="96" alt="CloudStream for Windows icon">
</p>

<h1 align="center">CloudStream for Windows</h1>

<p align="center">Movies, series, anime and live TV in a native Windows 10 & 11 desktop app.<br>
Runs your CloudStream extensions on the desktop, with a real <code>mpv</code> player and a sandbox around every extension.</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Download for Windows" src="https://img.shields.io/badge/Download-Windows%2010%20%7C%2011%20(x64)-0078D4?style=for-the-badge&logo=windows11&logoColor=white"></a>
  <a href="https://discord.gg/u82JU5JDM"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
</p>

<p align="center">
  <a href="../../releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/lelouchcodes11/cs3d?include_prereleases&label=release&color=3B7BFF"></a>
  <a href="../../actions/workflows/ci.yml"><img alt="CI" src="https://github.com/lelouchcodes11/cs3d/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-3B7BFF"></a>
</p>

> [!NOTE]
> **A personal project.** I am not a developer. This app was made entirely for my own needs, using vibe coding (building with AI). It is shared as is: expect bugs, glitches and rough edges. Please report issues: I will fix them, but slowly. Pull requests are welcome.

<p align="center">
  <img src="docs/screenshots/home.jpg" width="100%" alt="Home with the StreamPlay extension: a billboard, the next titles and Trending">
</p>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/details.jpg" alt="A title page with artwork, ratings, Up Next and tabs"><br><sub><b>Title pages</b></sub></td>
    <td width="50%"><img src="docs/screenshots/episodes.jpg" alt="An episode list with stills; unseen episodes are blurred to avoid spoilers"><br><sub><b>Episodes</b></sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/explore.jpg" alt="Explore with Global and Local lists, genre and streaming service filters"><br><sub><b>Explore</b></sub></td>
    <td width="50%"><img src="docs/screenshots/extensions.jpg" alt="Extension sources and the Phisher repository with its icons"><br><sub><b>Extensions</b></sub></td>
  </tr>
</table>

## Download

Get the latest version from **[Releases](../../releases/latest)** (Windows 10 or 11, 64-bit):

- **Setup** (`…-setup.exe`, recommended): installs without admin rights and updates itself.
- **MSI** (`….msi`): a standard Windows installer.

Windows SmartScreen may warn on first run because the installer is not signed: click **More info → Run anyway**.

## Features

- **Video player** built on mpv: hardware decoding, HLS/DASH, picture-in-picture, subtitles, optional Anime4K/FSR upscaling.
- **CloudStream extensions** run directly on Windows; also Stremio add-ons and torrents.
- **Explore and title pages** with TMDB and AniList: trailers, cast, reviews, spoiler-free episodes.
- **Live TV** with a seek bar that follows each channel.
- **Sync** with AniList, MyAnimeList and Simkl.
- **Profiles** with an optional PIN lock, 14 themes, 10 languages, a screen dimmer and a low-end PC mode.
- **Extension sandbox:** every extension runs in a locked-down Windows sandbox by default. See [SECURITY.md](SECURITY.md).

## Getting started

1. Open **Extensions → Add repository** and install the extensions you want. Nothing comes pre-installed.
2. Pick a source on Home, or search, then press **Play**.
3. Optional: sign in to AniList, MyAnimeList or Simkl in **Settings → Accounts**.

## Help

- **Video stutters:** set **Upscaling** to Off in the player menu.
- **Window flickers or the app is slow:** try **Settings → Performance**, or start **CloudStream (safe graphics)** from the Start menu.
- **A source does not play:** turn off your VPN or Cloudflare WARP, or use **Reload sources** in the player.
- **Report a bug:** open an [issue](../../issues/new/choose) with **Settings → About → Copy diagnostics**, or ask on [Discord](https://discord.gg/u82JU5JDM).

## Build from source

Needs **JDK 21** and **[Git LFS](https://git-lfs.com)** (run `git lfs install` before cloning).

```powershell
.\gradlew.bat :app:runDev          # run the app
.\gradlew.bat :app:portableDist    # build a portable copy in dist/
```

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CHANGELOG.md](CHANGELOG.md).

## Credits

- **[CloudStream](https://github.com/recloudstream/cloudstream)** (GPL-3.0): the engine and extension format this app is built on. This is an independent desktop port, not an official release.
- **[CS3 Desktop Client](https://github.com/errorcode26/CS3-desktop-client-unofficial)** by errorcode26: UI ideas.
- Built with [mpv](https://mpv.io), [FFmpeg](https://ffmpeg.org), [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), [Anime4K](https://github.com/bloc97/Anime4K), [TorrServer](https://github.com/YouROK/TorrServer) and the [Manrope](https://github.com/sharanda/manrope) font. Data from [TMDB](https://www.themoviedb.org), [AniList](https://anilist.co), [MyAnimeList](https://myanimelist.net), [Simkl](https://simkl.com), [OpenSubtitles](https://www.opensubtitles.com) and [SubDL](https://subdl.com). This product uses the TMDB API but is not endorsed or certified by TMDB.

## Support

Free and ad-free. A star helps, or donate via [Razorpay](https://razorpay.me/@lelouch11) (India) or [Buy Me a Coffee](https://buymeacoffee.com/lelouchcodes11).

## Disclaimer

This app contains **no extensions, add-ons or media**. They are made by third parties and you add them yourself. Torrent streaming shares your IP address with other peers unless you use a VPN.

<p align="center"><sub>Licensed under <a href="LICENSE">GPL-3.0</a>.</sub></p>
