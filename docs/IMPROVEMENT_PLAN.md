# Improvement plan (2026-10-04)

What was reported, what was measured, what changed in this build, and what to build next. Numbers are from this PC (Ryzen 7 8845HS, Radeon 780M, 1920x1200 at 60 Hz, Windows 11) unless said otherwise.

## 1. What was reported

1. Videos lag or stutter ("stutter feeling").
2. DRM live channels (WILLOW, JIO TV) do not work, "before it worked".
3. The in-app updater is bad: remove it, send people to GitHub.
4. UI is less smooth than another desktop client (`errorcode26/CS3-desktop-client-unofficial`), which is behind us on extensions and Android-only features.
5. A proper README (Telegram channel and donations first) and a plan for improvements.

## 2. Findings

### 2.1 Video: the player is correct, 60 Hz film judder is what you see

Measured with the packaged app, local 1080p files, windowed and fullscreen: no dropped frames, speed 1.000, A/V sync 0.00001 s, and a perfect 2-3-2-3 refresh rhythm for 24 and 25 fps (`/videostats`: `repeats` equals the ideal for the frame rate). That rhythm *is* the problem on a 60 Hz screen: 24 or 25 fps film can only be shown as alternating 2 and 3 refreshes per frame, which the eye reads as a regular small jump in every pan. A phone with a 90 or 120 Hz panel shows 24 fps as an even 4 or 5 refreshes per frame, so the same film looks smooth there. This laptop panel only offers 60 Hz modes (`EnumDisplaySettings`), so switching the refresh rate (what MPC-HC calls auto refresh rate) is not possible.

Other facts that matter:

- The player draws with libmpv's **software** renderer into a bitmap that Compose shows (libmpv has only OpenGL and software render APIs; no Direct3D one, `render.h`). CPU cost is about 4 ms per frame at 1920x1200 here, which is fine, but it grows with the window's pixel count (a 4K window is 3.6x the work plus two frame copies) and it cannot do display-synchronised interpolation.
- Java's sleeping is precise on JDK 21 (frame pacer sleep overshoot 0.1 ms average), so timer resolution is not the cause.
- The pacer followed the **primary** monitor only: with the window on a second monitor (other refresh or phase) frames were paced against the wrong screen.
- Rare hiccups remain (one 46 ms sleep overshoot or a 92 ms frame gap per about 45 s). Not yet explained; see 5.4.

### 2.2 DRM channels: the player decrypts them, the sources changed

- ClearKey DASH plays at 1.00x in the live lab and in the packaged app: WILLOW FHD (via PlayFy, 1080p25, `d3d11va`) and FANCODE FHD (via LivXow); the HLS channels of the same lab (PlayFy TV+) play at 1.00x too. The DashProxy, key handling (`decryption_key`, `cenc_decryption_key`) and the 2-second-loop fix are intact.
- **WILLOW through LivXow** now returns `403` from the CDN (`aiv-cdn.net`, `x-amz-source: LivePlaybackOrigin`), also to plain `curl`.
- **JIO TV (IND)** (PlayFy plugin): the playlist worker `jiotv-mixed.alpha-circuit.workers.dev/?token=...` answers every request from this PC with `302` to a YouTube music video (decoy), with the plugin's own headers, with other user agents, over IPv4 and IPv6. The app then used the page as a "video address" and mpv sat for 31 s ("Unknown Channel"). The token is the same on every run, so it is not per request.
- The owner says JIO TV works on their phone. The plugin code is the same on the phone, and the desktop's own request (the plugin's OkHttp) gets the same decoy as `curl`, so the difference is **where the request comes from**, not what the app sends. This PC is on **Cloudflare WARP** (`warp=on`, egress `AS13335`), and both refusing sources are behind Cloudflare/AWS edges that commonly refuse VPN and proxy addresses. **Not yet proven**: needs one test with WARP switched off (see section 6).
- Changed: a "video address" that is really a web page now fails at once with a clear message instead of a 31 s stall.

### 2.3 UI smoothness

Frame times of the real UI (a recorder asks for a frame on every refresh; `-Dcloudstream.framestats=1`, `/framestats`), foreground window:

| Situation | p50 | p95 | p99 | worst |
| --- | --- | --- | --- | --- |
| steady, scrolling Home, Library, Extensions, Settings | 16.7 ms | 18 ms | 19-23 ms | |
| **first open** of Home / Library / Extensions / Settings | | | | **93 / 239 / 145 / 348 ms** |
| scrolling Settings > Player | 16.7 ms | 18 ms | 34 ms | 65 ms |

So scrolling and animation are at the refresh rate; the unsmooth moments are the **first visit of a screen** (100 to 350 ms freezes: class loading and JIT of Compose code that CDS only partly removes) and playback start. This is what a user notices as "not smooth" next to a small app that has less to compose.

### 2.4 The reference client (errorcode26/CS3-desktop-client-unofficial)

About 107k lines of Kotlin plus a 1.5k-line C++ bridge, Compose, developer-only ("not for end users": manual libmpv download, MSVC/MinGW build for the bridge).

| | This app | Reference client |
| --- | --- | --- |
| Video | libmpv software render into Compose | **native mpv window with its own GPU renderer** (`vo=gpu`, `gpu-api=d3d11`, `hwdec=auto-safe`), optional `video-sync=display-resample` + `interpolation=yes` + `tscale=oversample`, Anime4K shaders |
| Player UI | Compose, drawn over the picture | HTML/JS in a transparent WebView2 layer over the mpv window; a C++ bridge (`player_bridge.cpp`) composes the Win32 windows and handles seek/volume without the JVM |
| Extensions | the real CloudStream engine, wide Android compatibility (what you called "extension menus" and Android-only features) | own plugin runtime (dex2jar + ASM) with stubs; fewer features work |
| Features we lack | | Stremio add-ons, metadata pipeline, Discord rich presence, profiles, watch history screen, stream priorities, keyboard shortcut settings, diagnostics page, Anime4K, TorrServer |
| Distribution | MSI installer, portable folder | clone and build |

Their smoothness comes from the **video path**, not from the UI toolkit. Their idea worth taking is the native GPU video window; their WebView2 and C++ bridge are not needed (see 3).

## 3. Prototype: can we get a native GPU video window with our Compose controls?

`app/src/main/kotlin/com/lagradost/desktop/tools/EmbedProto.kt`, `./gradlew :app:runEmbedProto -Pargs="<video> [shotDir]"`.

| Try | Result |
| --- | --- |
| mpv `wid` into an AWT `Canvas`, `vo=gpu`, `gpu-api=d3d11`, `hwdec=d3d11va`, `video-sync=display-resample`, `interpolation=yes`, `tscale=oversample` | **works**: picture visible, `display-fps=60`, `vsync-ratio` 2.36-2.40 for 25 fps, 0 decoder drops |
| Compose `SwingPanel` with `compose.interop.blending=true` | Compose controls show, but the native video is **not** (blending copies the Swing paint, not a child HWND): grey panel |
| `SwingPanel` without blending | the canvas covers the Compose controls (airspace) |
| **a transparent undecorated `DialogWindow` owned by the main window, kept over the canvas** | **works**: video plus semi-transparent Compose controls above it, no C++ and no WebView2 |

So the reference client's result is reachable inside our Compose app with JNA only. Open items: input and focus handling across the two windows, fullscreen, picture in picture, resize/move sync without lag, minimise/restore, multi-monitor DPI.

## 4. Changed in this build (uncommitted)

- **Updater removed**: no network check, no dialog, no start-up wait. Settings > About and Updates & backup have "Get the newest version", which opens the GitHub releases page; Telegram and donate links added. `desktop/update/AppUpdater.kt` deleted, `desktop/AppInfo.kt` holds the links and version.
- **Smooth motion** (Settings > Appearance, on by default): keeps the newest frames with their mpv target times and, in the refresh during which the next frame is due, draws the two weighted by overlap, like mpv's `oversample` interpolation. Drawn on every refresh only while the video's cadence needs it (24/25/50 fps on 60 Hz); 30 and 60 fps are untouched. Measured: 60.0 draws/s, about 20 of them mixed for 25 fps, CPU the same, GPU +5 % points of an iGPU.
- **Large windows**: a view over 3.7 Mpix (above 2560x1440) is rendered smaller and enlarged by the graphics card with a Catmull-Rom filter (`-Dcloudstream.rendercap=<pixels>`, 0 = off).
- **Frame pacer follows the monitor the window is on** (and restarts when the window moves), logs "watching the refresh of \\.\DISPLAYn".
- **A web page as a video address fails fast.**
- Dev tools: `/framestats`, `FrameStats`, `/look?smooth=`, pacer sleep-overshoot in the 5 s log, `EmbedProto`.
- README rebuilt (banner, screenshots, Telegram and donate first, FAQ).

Not changed, on purpose: sources that refuse the connection (JIO TV and LivXow's WILLOW), see 2.2.

## 5. Plan

Order is by value for the next release.

### 5.1 Now: confirm the two source problems (hours)
- JIO TV with WARP off (section 6). If it works: add a README/FAQ line (done) and an in-app hint when a source answers with a web page or 403 while a VPN adapter is up (`Get-NetAdapter` shows `CloudflareWARP`).
- If it does not work with WARP off: log the exact plugin request on the desktop, compare it with the phone's (a proxy on the phone), and look at what differs (headers, TLS, DNS).

### 5.2 Release 0.1.7 (days)
Ship this build; announce on Telegram. Test on a second PC (a 4K or 125-150 % scaling screen, a dual-monitor setup, an Intel iGPU laptop): the render cap, the monitor-aware pacer and smooth motion were only run on one 1920x1200 screen.

### 5.3 Native GPU player, opt-in "Player engine: Native (beta)" (1-2 weeks)
Productise `EmbedProto`: a `NativeVideoSurface` (canvas HWND in the player screen) + a transparent overlay window that hosts the existing `PlayerChrome`; mpv with `wid`, `gpu-next`/d3d11, `display-resample` + `interpolation=yes` + `tscale=oversample`; the existing subtitle style, tracks, DASH/HLS proxies and ClearKey options stay (they are mpv options). Keep the current engine as the default and the automatic fallback when the native one fails to initialise. Gains: mpv-quality motion on 60 Hz, GPU scaling and HDR tone mapping, Anime4K shaders, much lower CPU at 4K. Acceptance: no frame drops over 10 min at 1080p24/60 and 4K30; fullscreen, PiP, move/resize/minimise, two monitors with different refresh rates, Win+Arrow snap, alt-tab.

### 5.4 Smoothness of the UI (about a week)
- Warm the screens: compose Library, Extensions and Settings once, off screen, while the start-up loader is still up (bounded, no side effects), so the first visit is not a 100-350 ms freeze. Target: no frame over 50 ms when opening any screen (`/framestats`).
- Explain the rare 46-92 ms hiccups: run with `-Xlog:gc` and the pacer's overshoot log during a 10-minute film; if G1 pauses are the cause try `-XX:MaxGCPauseMillis=10` or ZGC in `CloudStream.cfg`.
- Keep animations off the video (already a rule) and measure each new effect with `/framestats` before it ships.

### 5.4b Diagnostics (days)
Today there is no log from a user's PC. Add a rolling `logs/app.log` and a "Copy diagnostics" button in About (version, Windows, GPU, refresh rate, mpv `vo`/`hwdec`, last errors, sources that failed with their HTTP codes), so a report on Telegram comes with facts.

### 5.5 Features worth taking from the reference client (pick by what users ask for)
Discord rich presence (small), watch history screen (small), stream priorities (medium), keyboard shortcut settings (small), Stremio add-on support (large), metadata pipeline for richer details pages (medium), profiles (we have profile pictures only), Anime4K (needs 5.3).

### 5.6 Distribution and trust
Code-sign the MSI (SmartScreen), publish to winget, keep the portable zip, add a CI build that runs `:app:test` and builds the MSI on a tag. The updater stays removed: Telegram announces, GitHub serves.

## 6. What is needed from the owner
1. Turn **Cloudflare WARP off** for a minute and try JIO TV (IND) and LivXow's WILLOW again; say what happens. (If WARP is needed for the ISP's blocks, a split-tunnel exclusion for `*.workers.dev` can be tried.)
2. Say which videos stutter for you or the reviewers: film or live TV, which source, window or fullscreen, and what screen (resolution, refresh rate, one or two monitors). Smooth motion targets 24/25 fps film on 60 Hz.
3. Decide whether the native GPU player (5.3) should go ahead; it is the biggest piece.

## 7. Status after the first implementation round (2026-10-04, later)

| Plan item | State |
| --- | --- |
| **White flicker** (reported after the first build) | Fixed. Cause: mpv's `bgr0` output leaves the 4th byte 0, Skia's shader path read it as alpha 0, so every blended refresh was **added** to the frame (about +40 brightness, a flash on every mixed refresh, ~20 per second). Found with a screen-brightness spike detector on real content (232 spikes in 10 s, 0 with "no second frame"); fixed by asking mpv for `bgra` (alpha 255): 0 spikes in all modes. The earlier check of smooth motion had used the app's offscreen screenshot, which does not show it: always check a moving real picture on the real screen. |
| 5.4 hiccups | **Found and fixed.** `-Xlog:gc` showed a **full GC with all threads stopped every 30 s** (`Pause Full (System.gc())`, 24-65 ms): upstream's `Editor.apply()` calls `System.gc()` and the player saves the playback position every 30 s. The call is removed; the packaged JVM also runs `-XX:+ExplicitGCInvokesConcurrent` for any library that asks. Young collections are 1-6 ms. |
| 5.4 first-open freezes | **Fixed.** `ScreenWarmup` composes Library, Extensions, Settings, Downloads and Search off screen, one every 1.4 s starting 2.5 s after the start. Worst first-open frame: Library 239 -> 34 ms, Extensions 145 -> 38 ms, Settings 348 -> 68 ms (Home 19 ms); settings pages scroll at 16.7 ms with no frame over 20 ms. |
| 5.4b diagnostics | **Done.** `logs/app.log` (two files of 3 MB, written by one low-priority thread), Settings > About: **Copy diagnostics** (versions, Windows, graphics card, screens with refresh rates, VPN-like adapters, mpv output/decoder/drops, latest warnings and errors; secrets masked) and **Log file**. |
| 5.3 native GPU player | **Built, opt-in** (Settings > Appearance > "Native GPU player (beta)", off by default, applies to the next video): mpv `vo=gpu` / Direct3D 11 / `d3d11va` / `display-resample` + `interpolation` + `tscale=oversample` in a canvas of the page; the controls are a transparent owned `DialogWindow` over it that follows the canvas (move, resize, full screen, minimise), takes the pointer (a nearly invisible layer, because fully transparent pixels let the pointer through) and never the keyboard focus unless a dialog with text is open; dialogs and messages are drawn in that window. Falls back to the standard player when there is no window handle or mpv gives no picture within 6 s. Checked with real mouse input on a test window: click toggles pause, double click = full screen, the Sources dialog opens over the video, resize and full screen keep the video and controls together; mpv reports `current-vo=gpu`, `hwdec-current=d3d11va`, `display-fps=60`, 0 dropped frames. **Not tested**: real keyboard input (keys are handled by the main window, which keeps the focus because the controls window never takes it; the dev `/key` hook works), two monitors, HDR, a second video on the same page, PiP. |
| 5.5 features | **Subtitle Cat** (requested), **keyboard shortcuts window** (F1 in the player, Settings > About), **Anime4K** (needs the native player; two M-size shaders, MIT, headers kept). Not done: Discord rich presence (needs a Discord application id that the owner has to create), watch history screen, stream priorities, Stremio add-ons, profiles. |
| 5.6 distribution | Not done: code signing needs a certificate; winget needs a published release. |

Subtitle Cat (`SubtitleCat.kt`): the site's search is loose and returns unrelated uploads (a search for "Big Buck Bunny" returned adult titles), so a release is only offered when its name has every word of the title and, for an episode, its `SxxEyy`; pages with only a server-side "Translate" button are skipped (no file). Checked live: "Vikram" -> 6 releases, "Inception" -> 6, "Big Buck Bunny" -> none; the applied file is accepted by the player.

## 8. Start-up and UI: measurements, what changed, suggestions (2026-10-04, evening)

**Measured** (packaged app, the owner's real extension set on a copy of the data, window kept on top; `-Dcloudstream.framestats=1`, `/framestats?s=<n>&worst=<k>` lists the longest frames with their times):
- The window shows after about 1.7 s (native splash before that). The next 10 to 15 s still have 40-80 frames over 50 ms (p99 80-160 ms) and one or two stalls of **2.3-2.6 s** (about 8-10 s after the launch); afterwards the UI is steady at 16.7 ms.
- What the window thread did in that time (start-up sampler + watchdog): reading the Android resource XML files on first use (0.6 s), Jackson's first deserialiser setup (0.5 s, **2.7 s** when it overlapped with plugin loading), Direct3D device creation (0.8 s, unavoidable), and Chromium (JCEF): it starts when an extension first makes a WebView (here the Cloudflare helper of a Home provider, 2 s after launch), runs its start and its message pump **on the window thread** (about 20 % of the thread's time while it is alive: `CefApp.N_DoMessageLoopWork` + the Swing timer and `AccessController` calls that come with it).
- **Memory**: this PC had 0.7 GB free of 13.8 GB (memory compression 1.5 GB, 2 GB in the page file) while the app ran. The app's working set was 0.98 GB (JVM heap 150 MB, JVM non-heap 135 MB, so about 700 MB is native: Skia/Direct3D caches, in-process Chromium, libmpv). With that little free memory Windows compresses and pages, and a freeze of a second or more with an idle-looking thread is the typical sign. Closing other programs shortens the start-up hang more than any code change here.
- `-Dcloudstream.jank=250` (watchdog threshold) inflates the freeze list: its stack dump (`Thread.getAllStackTraces`, 170+ threads) delays its own next heartbeat. Trust `/framestats`.

**Changed**: Warmups (strings and saved lists prepared off the window thread: 0.2 s and 0.06 s of work that used to land on it), screen warm-up only after the extensions finished loading + 3 s + 60 calm frames (the first version ran during the load and added freezes), libmpv preload skipped under 1.5 GB free, Chromium pre-start tried (`-Dcloudstream.jcefearly=true`) and **measured worse** (frames over 50 ms: 66 vs 32), so it stays lazy, the owner's other findings below.

**Suggestions, in order of expected effect**
1. **Footprint**: find the 700 MB of native memory (VMMap, `-XX:NativeMemoryTracking=summary`), cap Skia's resource cache and Coil's memory cache, decode posters at the size they are drawn, and look at what the in-process Chromium holds. Target: under 500 MB at idle.
2. **Chromium off the window thread**: an out-of-process helper for WebView work (Cloudflare, logins), or a headless solver for the Cloudflare cookie that does not need a browser, so the common session never starts it.
3. **Plugin registration storms**: each plugin that finishes loading changes provider lists the pages read; coalesce those updates (200 ms) so 30 plugins cause a handful of recompositions, not 30.
4. **Show last session's Home at once** (rows and posters from disk) and refresh in the background, so the first seconds are not an empty page that fills in and jumps.
5. **Keep the window thread for drawing**: decode images, parse JSON and read preferences on workers (the `Warmups` pattern); a budget check in a script (`/framestats` after start-up: p99 under 33 ms, no frame over 100 ms) so a regression shows at once.
6. **JVM**: a smaller young generation and `-XX:G1PeriodicGCInterval` to give memory back when idle; AppCDS is already used.
7. **Code**: `-Xlambdas=class` and CDS are in; check Compose stability reports for pages that recompose on every scroll, avoid `blur` on large areas, and keep animations off the player.

**Bottom dock** (resolved after the owner's screenshot): the "vertical opaque stretched thing" was the page itself: `ContentArea` padded the page by 78 dp above the dock, so the posters were cut at a hard line and the page layer's flat colour filled the band below, full width, with the dock floating on it (the band was exactly 78 dp x scale high). Now the page runs the full height and scrolls under the dock; scrolling pages add `LocalDockInset` (78 dp, provided by `ContentArea`, 0 for any other navigation) to their end padding (Home, Details, Downloads, Settings, `PosterGrid`, Extensions, Search). Hide-on-scroll (nested scroll, 48 px of travel to hide, 24 px to show, pointer at the bottom edge shows it, a new page shows it) is an option, `Appearance.dockAutoHide`, **off by default**, switch in Settings → Appearance (visible with the bottom dock). Dev server: `/look?dockhide=true|false`.
