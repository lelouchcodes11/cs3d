# Changelog

## 1.0.0

The first official release. A lighter, better-looking app with a lot more to find: the installer is about 40 % smaller and it uses much less memory, there are colour themes and your own wallpaper, Explore and richer title pages (TMDB), trailers with sound inside the app, editable profiles, and many player, subtitle and extension fixes. Everything below is new since 0.1.8.

**Highlights**
- **Lighter**: installer 377 MB -> about 184 MB; web pages use the WebView2 that comes with Windows; a leak that made memory swing up to 2 GB while a film played is fixed.
- **Better looking**: colour themes (Midnight is the default), your own wallpaper, a calmer minimal design, Customise Home.
- **Search + Explore**: one Search page with a filter button, recent searches and Explore (trending, in cinemas, top rated, by genre and streaming service).
- **Richer title pages**: logo, score, age rating, where it streams, collections, cast and person pages, trailers that play (with sound) inside the app.
- **Player**: one-click Sources/Tracks menus, audio decoder (SW / HW / HW+), subtitle renderer and fonts, seekable IStreamFlare videos, CNCVerse qualities and subtitles, correct subtitle after a source switch.
- **Profiles you can edit**, a once-a-day reminder to star the project, extension update history and *Update all*.

Install: download the MSI, run it; 0.1.5 - 0.1.8 upgrade in place and keep your data. The Details below list every change.

**Details**

**Smaller and lighter**
- **Web pages now use Microsoft Edge WebView2, which comes with Windows 10 and 11, instead of a copy of Chromium inside the app.** The installer goes from 377 MB to about 213 MB, the installed app from about 595 MB to about 386 MB, and the data folder no longer grows by about 680 MB the first time an extension needs a web page. The old Chromium files (about 560 MB) are removed from the data folder by themselves after the update.
- **Web pages start at once** (in a few hundredths of a second; the old Chromium needed up to two minutes the first time) and **their processes end when no page is in use**: before, about 700 MB of browser processes stayed until the app was closed. Pages an extension opens and forgets are closed after a few minutes and reopen by themselves if the extension uses them again.
- **Less memory for the app itself**: memory it no longer needs goes back to Windows, and the picture cache of the graphics card is smaller (on PCs with integrated graphics it is ordinary RAM). The poster cache on disk is limited to 150 MB.
- The video player library is loaded straight from the install folder instead of a second 116 MB copy in the data folder (the old copy is deleted).
- **The video player library is 3 times smaller** (39 MB instead of 121 MB): a build with only what playback needs (no video encoders, Vulkan, disc formats or scripting). Same player version, same formats, hardware decoding, subtitles and native GPU player. It no longer needs Vulkan to be installed.

**Look**
- **A friendly reminder once a day**: when the app has been open a few seconds, a small window asks for a star on GitHub or a donation (never on the first day, never over the player). It comes once every day; *Later* simply closes it.
- **Edit your profiles**: the profile button at the top right opens your profiles, each with *Edit* (name, one of the pictures or a picture of your own). A new profile opens the editor at once.
- **Search page**: the big search box has a *Filter* button next to it (the kinds of title) instead of a row of chips; the filters show above the results after a search. The search box in the top bar is hidden on this page (it only repeated the big one). The recent searches are one row with arrows that appear only while the pointer is over it.
- **Customise Home**: the pencil next to the reload button on Home shows every row of the provider with a switch and up/down arrows to hide and reorder them (remembered per provider).
- **Extension update history** (Settings > Updates & backup): which extensions were updated and when, from the automatic check and from Update Plugins.
- Trailers and plain links no longer appear in History.
- **Colour themes instead of grey**: *Midnight* (violet, the new default), *Crimson*, *Ocean*, *Matcha*, *Sunset*, *Dracula*, *Nord*, *Cinema gold* and the old *Classic*. A theme tints the pages, cards and menus, sets the accent colour, brightens the secondary text and puts two soft glows of its colours in the corners of every page (switchable). Selected filters and tabs are filled with the accent. Settings > Appearance > Theme.
- **Your own wallpaper**: pick a picture (Settings > Appearance > Theme > Wallpaper) and it shows behind every page, dimmed as much as you like.
- **History page**: *Continue watching* has a *See all* that opens everything you started, with resume, open, remove and *Clear history*.
- **Home can be calmer**: switches for the big banner and the Continue watching row (Settings > Appearance).
- **Update all** on the Extensions page, next to *Add repository*.
- **Messages from the engine are visible now**: *Update Plugins* in Settings did work, but its messages ("Checking…", "All your extensions are up to date", "Updated 3") were drawn nowhere, so it looked dead. They show as toasts now, like restore and download messages.
- **Explore lives on the Search page**: under the search box and the row of your recent searches (they scroll sideways now) you find a rotating banner, then trending (with big rank numbers), in cinemas, coming soon, top rated and airing lists for films and series from TMDB, and trending, airing, top rated and upcoming anime from AniList. Filter by **genre** and by **streaming service** (Netflix, Prime Video, Disney+, Hotstar, Apple TV+, Max, Hulu, Crunchyroll and more, for your country). A poster opens a search for that title in your extensions (right-click: *Search extensions*).
- **Title pages know more** (data from TMDB): the title logo and backdrop when the extension sends none, the tagline, TMDB score and votes, age rating for your country, length, seasons and episodes, who directed or created it, studios and networks, **where it streams** (service logos), the **collection** it belongs to, a **trailer** button when the extension has none, a **Gallery** of backdrops and **Reviews**. *Cast & crew* shows real photos for the whole cast; a click opens the **person's page** (photo, age, biography, known for, full filmography). *More like this* is filled from TMDB when the extension gives no recommendations.
- Posters from these lists open a search for the title in your extensions, so they work with every extension you have.
- **Trailers play inside the app** (the trailer button used to open the browser; it still does when a trailer's video cannot be taken out of its page). When an extension sends none, TMDB's official trailer is used, and *Settings > Player > Show trailers* turns both off.
- **Trailers have sound**: YouTube sends its picture and its sound as separate files, and only the picture was played. The player now opens the sound file beside the video (the best one), and trailers start at 1080p at most, H.264 first. The same applies to any other source that names its sound separately.
- **A search that found only one or two sources is no longer remembered for 20 minutes** as "all there is": the next *Play* looks again (and shows what it found before at once).
- **Studios and networks are links**: click *Netflix* or a studio on a title page to see everything it made. Director, creator and writer names open the person's page. The ⋯ menu has *Open on IMDb* and *Open on TMDB*.
- **The Home banner gets TMDB artwork** (title logo and a wide backdrop) for titles the extension sent a small poster only.
- **Settings > Appearance > Title information**: a switch for everything TMDB (off: the app never contacts it) and the **country** that decides the age rating and the streaming services.
- **Player menus apply with one click**: Sources and Tracks no longer need a choice plus *Apply*. A click switches the source, subtitle, audio or video track or the audio decoder at once, the current choice has a check mark, the lists open at the current choice, and a click outside or Esc closes the menu. The video keeps playing so a change can be judged straight away.
- **A click on the video pauses at once** (it used to wait about a third of a second to rule out a double click). A double click switches fullscreen and leaves playback as it was.
- Filter chips (Search) are quiet when selected instead of solid white.
- **A calmer, minimal design**: no more gradients, glows or coloured shadows. Settings has plain monochrome icons with a thin accent bar on the selected page instead of coloured tiles; the selected page in the top bar, tabs and filter chips are neutral instead of accent-filled; the main button on artwork is plain white; the slow zoom on the home and details artwork and the blurred artwork behind every page are gone (the artwork backdrop can still be picked in Settings > Appearance > Backdrop); posters and episodes only lift a little under the pointer.
- The *App Language* box was empty until a language had been picked; it now shows English.

**Subtitles**
- **Subtitle renderer setting** (Settings > Player > Subtitles, and the gear in the player's Sources menu): *Subtitle's own* (default) lets styled subtitles (ASS tracks, SRT files with colours or positions) keep their own fonts, colours and positions, and plain ones use your style; *Universal* draws every text subtitle in your style. (Picture subtitles and subtitles burned into the video cannot be restyled.)
- **Wrong subtitles after a source switch fixed**: subtitles inside a video were remembered by their track number, so after switching source (or an automatic switch when one failed) the same number could be another language or a signs-only track. They now belong to their own video and the same language is picked again in the new one.
- **Fewer out-of-sync subtitles**: when a video has its own subtitles in your language, they are picked before online files (online files are often made for another release and run early or late). An online file that was picked first is replaced once the video's own track shows up, unless you chose it yourself.
- **The subtitle pop-up no longer comes back again and again**: while a source kept collecting links, every batch re-selected the same subtitle and flashed "Loading subtitles / Subtitles on".

**Player**
- **Much less memory while a film plays**: with *Smooth motion* on (the default), every picture of a 24 or 25 fps video stayed in memory until Windows' garbage collection came around, so the app swung between about 1.1 and 1.9 GB (peaks of 2 GB) every few seconds while playing. The pictures are released at once now: playback stays at about 1 GB, the peak went down by more than half.
- **IStreamFlare videos can be seeked now** (Phisher repository). Their OK.ru links are DASH manifests of the "on demand" kind (one file per quality with an index for seeking); the desktop player read them as one endless piece and could not jump. Such videos now play the best quality for your screen with its sound as separate files, and seeking works like on Android.
- Sources that need an unusual browser name (IStreamFlare's has a Cyrillic letter in it) got "HTTP 400" through the app's playlist server, which silently left the name out; it is now sent as it is.
- **Audio decoder (SW / HW / HW+)**: in the player under *Tracks > Decoder* (applies at once, the video keeps playing) or in Settings > Player > Audio. SW: the app decodes the sound (speakers, headphones). HW: Dolby Digital and DTS are decoded by your AV receiver or TV (HDMI or optical). HW+: also Dolby Digital Plus, TrueHD and DTS-HD (HDMI receivers). When the device refuses a format the app decodes it itself.
- The *Tracks* button is always there and lists every track, also when a video has only one.
- **More IStreamFlare videos can be seeked**: besides the "on demand" manifests fixed earlier, its other OK.ru manifests (numbered segments) froze after a seek. They now play as HLS, open faster (3 s instead of 14 s) and seek at once. Applies to every recorded DASH stream without DRM.
- **CNCVerse (Netflix, Prime Video, Hotstar)**: all qualities are listed under *Tracks* (1080p / 720p / 480p; before only the highest showed) and switch in place; **subtitles show** (they are one file for the whole film, which the player never read: they are now added as subtitle files and picked automatically); videos **open about twice as fast** (the first pieces of all 20+ audio languages are fetched at once instead of one after the other).
- **IStreamFlare "Mandaadi" (and other OK.ru videos) no longer close the app**: the OK.ru server's certificate is revoked; the new player library refused it (Android never checks this) and then crashed while giving up. It now accepts it like Android does, and a playlist that does not open is reported instead of crashing.
- **Start over**: a film that was started has a *Start over* button next to *Resume*; every episode has a ⋯ menu (the same as right click) with *Start from beginning*.
- **Kisskh posters show** (The Heirs and others): its image proxy always sends AVIF, which Windows' image decoder here cannot read; the original image behind the proxy is loaded instead.
- The loading circle shows right after a seek (before, it only came once the buffer had run empty, often seconds later).
- A source that takes over after another failed no longer stays paused when the end of the failed one arrives late.
- **Source priority works on the PC**: Settings > Player > Source priority lists every source (it was empty), and the player's *Sources* dialog has a priority button (like Android's player menu) with the sources of the video; the list is sorted again when it closes. The profiles are *Stream* and *Download* (no Wi-Fi / mobile data on a PC).
- **The title bar is always plain dark**: with the *Ambient* backdrop the artwork colours of the page spilled into the title bar of a window that is not maximized (a colour fade behind minimize / close).

**Subtitles**
- **More fonts**: besides the fonts that come with the app, the Font list now offers fonts of Windows (Arial, Calibri, Georgia, Segoe UI, Tahoma and more, the ones your PC has) and *Font file…* for a font of your own (TTF, OTF, TTC).
- A font chosen in Settings or in the player's subtitle settings is used for every video, also the next ones.
- **Google Sans** never showed (the app drew the default font instead); it does now, also bold and italic.
- A font file of your own only worked the first time: choosing another one later fell back to the default font. Every font file works now, also when it is changed while a video plays.
- **Fonts you add stay in the Font list for good** (shown as "your font", in Settings and in the player): each one is kept in the data folder, so you can switch between them any time without picking the file again.

**Cloudflare**
- **AnimePahe's Cloudflare check now passes by itself in about 10 seconds** with WebView2 (it failed with the old built-in Chromium). Cookies from web pages are kept by the app itself, so reading them no longer starts a browser; they are carried over between runs.

**Known**
- Cookies of the old built-in browser are not carried over: sites behind Cloudflare may run their check once more.
- On a PC without WebView2 (rare: it ships with Windows 11 and comes with Edge updates on Windows 10) the app falls back to Chromium and downloads it (about 150 MB) the first time a page is needed.

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
