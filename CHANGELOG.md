# Changelog

## 0.1.8 (pre-release)

Live TV playlists that did not play now do, AnimePahe's home page and Cloudflare window work, and no more frozen window while Cloudflare is being solved.

**Updating from 0.1.7:** the app tells you about this version a few seconds after it starts. Choose *Open download page*, download the installer and run it over the old one: your settings, accounts and extensions are kept (this upgrade was checked on a real install).

**Extensions**
- **Extensions that declare classes inside a function work again.** The converter that turns an extension (dex) into something this app can load dropped the "declared inside this function" mark of such classes, so Kotlin reflection (used by the JSON reading of many extensions) failed with "Unresolved class". The home page of **AnimePahe** (Phisher repository) was blank because of it; other extensions may have been affected in the same way. Every installed extension is converted once more on the first start after the update.

**Cloudflare**
- **The window no longer freezes while Cloudflare is being solved.** The dialog that extensions such as AnimePahe open for the "Just a moment" check asks the browser for its cookies every second on the screen's own thread, and while the browser was busy with the challenge page each ask held the whole window (up to 0.7 s each, so it seemed stuck). After a slow answer the app now does not wait for a few seconds.

**Player**
- Live DASH channels whose address sends the player on to another server now play. Many playlist channels (for example the DRMlive playlist in the M3U Playlist Player of CNCVerse) answer with a redirect to a CDN that adds a token; the app asked that CDN's host for the segments with the *first* host's path, got 404 for every one and gave up with "unrecognized file format". It now takes the segments from where the manifest really came from, passes the token of the redirect on with them, and keeps the cookies the CDN hands out (as a browser does). The "Activate Playlist" entry of such playlists is a real 10 to 13 second clip behind the same kind of redirect, so it could never play and the playlist stayed inactive; it plays now.
- HLS channels whose playlist address redirects to another server (for example the Sony channels of the tsiptv playlist) play now: the addresses inside the playlist were resolved against the first server and answered 403.
- Live channels whose segments are numbered in hex (`$Number%08x$`, used by Sling-based channels such as Willow) play now: ffmpeg only knows decimal numbers, so the numbers are translated by the app, and the CDNs named by the manifest's absolute BaseURLs are reached through it.

**Known**
- A Cloudflare check that needs you to click "Verify you are human" still needs that click. AnimePahe's own window shows the check inside the app so you can click it (it clears by itself in about 10 seconds most of the time). Extensions that use the app's built-in Cloudflare handler (no window) can still fail on such sites after waiting about a minute; a window for those is planned.
- Some playlist channels still do not play: entries the source itself has taken down or blocks (403/404, expired links), Widevine-protected channels (no decryption module on a PC), and playlist entries whose keys the CNC "M3U Playlist Player" extension does not pass on (for example the `jtv.tsiptv.xyz` entries and the Jio TV+ entries of premiumplugx).

## 0.1.7 (pre-release)

Restoring a backup now works (and brings your extensions back), smoother video with an optional GPU player, and updates that point you to GitHub instead of installing behind your back.

**If you are on 0.1.6 or older** the app offers this update itself: choose *Install update* (a portable copy: download it from the release page instead). From 0.1.7 on the app only tells you about a new version and opens its download page.

**Player**
- **Smooth motion** (Settings → Appearance, on by default): 24 and 25 fps films no longer judder on a 60 Hz screen. The frames around each screen refresh are blended, like mpv's interpolation, so pans and scrolling credits glide.
- **Native GPU player (beta)** (Settings → Appearance, off by default, applies to the next video): mpv draws the video with its own GPU renderer in a window of its own, timed to your screen's refresh (blended frames, display-synchronised audio clock), and the controls float above it. Smoothest motion and the least CPU. If it cannot start it falls back to the standard player by itself.
- Very large windows (4K and bigger) are drawn a little smaller and enlarged by the graphics card, so playback stays smooth.
- The frame pacing follows the monitor the window is on (it used to follow the main monitor only, wrong on a second screen with another refresh rate), and follows the window when you move it.
- A source that answers with a web page instead of a video now fails at once with a clear message, instead of waiting 30 seconds.

**Look**
- **Dark is the default theme** (it was "Use system setting"). Settings → Appearance → App theme still offers Light and Use system setting; only installs that never chose are affected.
- **Bottom dock**: the page now runs the whole height and scrolls *under* the floating dock (before, it ended 78 px above the bottom with a flat dark band across the whole width, cutting the posters off). Its heavy drop shadow is replaced by a soft one.
- **New setting** Settings → Appearance → Navigation position → *Hide the dock while scrolling* (shown with the bottom dock, **off by default**): the dock slides away when you scroll a page down and comes back when you scroll up or move the pointer to the bottom edge.

**Start-up**
- The app's strings and saved lists are prepared in the background before the first page needs them, and the pages are warmed up only after the extensions have finished loading and the window is calm (a first version of this added freezes while the extensions were still loading).
- The video library (libmpv) is not preloaded when the PC has less than 1.5 GB of free memory; it loads when you first play something.

**Subtitles**
- **Subtitle Cat** (subtitlecat.com) is a new source in *Search subtitles online*: no account, many languages. Only releases whose name matches the title (and episode) are offered.

**Speed**
- Opening Library, Extensions, Settings, Downloads and Search for the first time no longer freezes the window for a moment (they are prepared quietly a few seconds after the start).

**Backup and restore**
- **Restoring a backup now takes effect.** The restored settings (theme, look, player) and lists (history, bookmarks, search history) were written to disk but the open windows kept showing the old values until the next start, and changing any Appearance setting afterwards wrote the old values back. After picking the file the app now says "Backup restored", restarts by itself and comes back with the restored data.
- **Restoring also reinstalls your extensions.** A backup now lists the installed extensions (name and download address); a restore downloads them again from your restored repositories (4 at a time), then restarts so they load. It says how many were installed and leaves the rest to Extensions → install. Backups made by an older version (and by the Android app) have no list, so for those only the repositories come back, as before.
- A copy of the current data is taken just before a restore replaces it (`data/backups/prefs-<time>`).
- **Messages that never appeared now do**: "Backup saved as CS3_Backup_….txt in <folder>" after Back up data, and the reason when a restore fails (before, a failed restore showed nothing at all).

**Help**
- Settings → About → **Copy diagnostics** puts versions, your PC, screens, VPN adapters, the player's output and the latest errors on the clipboard for a bug report (passwords and tokens are masked), and **Log file** opens `logs/app.log` (the last two runs).

**Updates**
- **The in-app updater is gone, the check at start stays.** The app no longer downloads or installs anything. A few seconds after the window is up (never holding the start back, at most every 6 hours) it asks GitHub for the list of releases; when there is a newer one, a dialog says so with the release notes and **Open download page** (also *Later* and *Skip this version*). Download the installer on that page and run it. Settings → About → *Check for updates at start* turns this off, *Check now* (there and in Updates & backup) asks on demand, *Get the newest version* opens the releases page. New versions are also announced on Telegram.

**About**
- New cards for the Telegram channel (t.me/cs3d_official) and for supporting the project (razorpay.me/@lelouch11).

**Known**
- Some live sources (for example JIO TV (IND), or WILLOW from LivXow) refuse VPN and Cloudflare WARP connections and answer with a different page or an error; if one works on your phone but not here, turn the VPN off and try again.

## 0.1.6 (pre-release)

Sign-in fixes for the subtitle services.

**Accounts**
- SubDL: a wrong e-mail or password now shows what SubDL says ("Email or password is not valid") instead of a long internal error.
- SubDL accounts made with "Sign in with Google" have no password and could not be used. Leave the e-mail empty and paste your API key (subdl.com, Panel, API) in the password field instead.
- OpenSubtitles: the sign-in tells you to use your user name (not your e-mail), and says clearly when OpenSubtitles' login server is blocked by your internet provider.
- Subtitle search no longer fails when your OpenSubtitles sign-in has run out and the login server cannot be reached: it carries on without the account.

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
