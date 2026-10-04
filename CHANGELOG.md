# Changelog

## 0.1.5 (pre-release)

Subtitles that actually load, a calmer and smoother player, OpenSubtitles working again, and a safer installer.

**Installer and updates**
- An update could delete your settings, accounts and extensions when they were kept in the install folder (a `data` folder next to `CloudStream.exe`). The new installer moves that folder out of the way during the update and puts it back, and later versions no longer delete the install folder at all. **If you are on 0.1.3 and the `data` folder next to `CloudStream.exe` matters to you, copy it somewhere safe before installing this update.** Version 0.1.4 had this problem and was withdrawn.
- The app now stops Chromium's helper processes when it closes (one of them could stay alive for a day and make the installer fail with "another application has exclusive access to chrome_debug.log"), and the installer stops such leftovers.

**Subtitles**
- A small indicator at the top right says what is going on: loading, on, or could not be loaded. It also shows while an online subtitle is being downloaded.
- Subtitles from StreamPlay and other extensions now start quickly. The app used to download every subtitle a source listed (often 180) before the one you picked; now it fetches only the one you pick, and skips dead ones by itself.
- Every kind of subtitle (including ASS/SSA files and tracks inside the video) now follows your subtitle style: same font, size, colours and position.
- The default drop shadow was never actually drawn. It is now, and a saved "Outline" is switched to the shadow once (change it back in Settings → Subtitles if you prefer).
- Subtitles no longer move up when the player controls show.

**OpenSubtitles**
- Clicking a result in "Search subtitles online" did nothing: the download was cancelled as the window closed. Fixed.
- In some countries (India) OpenSubtitles' main server is blocked for searching as well; the app now uses OpenSubtitles' older server for search and download when that happens.
- Results show up as each site answers, so one slow site no longer holds the list back. Searching again while a search is running now starts the new search.

**Player**
- Smoother 24 fps films: frames are lined up with your screen's refresh, so motion no longer turns uneven for a few seconds at a time (it looked like slow motion).
- Less stutter while the controls show and hide (the subtitles no longer slide up and down).
- If a source you picked does not work, the sources after it are tried first, and the one that was playing before comes back last.

## 0.1.3 (pre-release)

A big update: new look, a better player and search, account syncing, and fixes for live TV.

**New look**
- A redesigned Home, Search, Library, Extensions and Settings. *Continue watching* is now a row of posters with a progress bar and "S1 · E3 · 40 min left".
- Make it yours in Settings → Appearance: menu position (top, left, right, bottom), roundness of corners, poster size, spacing, background and interface size.
- One calm start-up screen that waits until the app is ready, instead of several loaders.
- One window title bar. When the window is maximized it hides and comes back when you touch the top edge.

**Search**
- Results show up as each extension answers. Extensions that are still searching show a placeholder, and the ones with no results are hidden.
- A calmer search page; the recent searches no longer jump around.

**Player**
- New controls: elapsed time, the bar and the length on one line; **Sources** and **Tracks** buttons at the top right; the source and picture size under the title.
- Subtitles move up while the controls are visible. Default subtitle size is 17 with a shadow.
- A source that was switched to after a failed one now starts playing by itself instead of waiting paused.
- Open any link in **VLC** or your browser (Settings → Player → Preferred video player).

**Live TV**
- Fixed live channels drifting out of sync or going into slow motion after you skip ahead (for example WILLOW). "Go live" brings you back to the live picture.

**Accounts and syncing**
- AniList, MyAnimeList and Simkl need an API client of your own: Settings → Accounts & security → **Sign-in keys** explains the three steps and shows the redirect address to use. After "Authorise" the browser now returns to the app by itself.
- Title pages have a **Tracking** card (status, score, episodes) and the episodes you watch are reported to the services you signed in to.
- Fixed the FebBox cookies not being saved after signing in (StreamPlay and CineStream).

**Updates**
- The app now also looks for a new version every few hours while it stays open (not during a video), not only at start.

**Removed**
- Torrent support and the settings that did nothing on Windows.

Known issues: some live channels may still stutter every few seconds on certain PCs (we could not reproduce it yet: tell us on Telegram if you see it). Sign-in to the tracker services is new in this build and has had little testing with real accounts.

## 0.1.1 (pre-release)

- Fixed live DASH channels (for example JIO TV) repeating the last two seconds after a while: the channel's manifest lags behind its segments, which made the player fetch and play the same segment several times in a row. Every segment is played once now.

## 0.1.0 (pre-release)

First public build of CloudStream for Windows: a native Windows 11 (Fluent) app on the CloudStream 4.8.0 engine.

- Browse, search and open titles through third-party extension repositories you add yourself (the app ships without extensions or content); library, downloads page, accounts, settings.
- Player on libmpv: HLS and DASH (including ClearKey protected live channels), audio and video track menus, subtitle search (OpenSubtitles and others) and subtitle style (font, size, colours, outline, background, position, saved for all videos).
- Live DASH channels with many audio languages (for example JIO TV in the CNC Verse repository) play and switch audio without freezing.
- Installer for the current user (no administrator rights), upgrades in place; portable folder build for developers.
- Update checker: looks for new releases of this repository (Settings > About), installs them with one click, can be switched off.

Known limits: the installer is not code-signed (SmartScreen may warn); Windows 10/11 64-bit only.
