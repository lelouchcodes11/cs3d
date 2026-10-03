# CloudStream Desktop — Plan and Handoff

This is the only doc. Read it fully before changing code. At the end of every session update section 6
("Status") and section 5 ("Work packages": tick items, add what you learned). Keep it short: replace stale
text, don't append logs.

Last updated: 2026-10-02 (session 6: see section 14).

---

## 0. Course correction (read first)

**What went wrong.** The earlier mission was "the upstream Android app running unchanged on an emulated
Android". It was achieved: every screen was the upstream phone UI drawn by an Android View emulator. The
result was an Android app in a window — bottom-nav icons squeezed into a side strip, a phone search bar
with a mic icon, a phone card carousel, Material dialogs, touch-first behaviour. **That is not what the
user asked for.** The user wants a Windows app: native-feeling, customised and polished for Windows.

**New mission.** A native Windows application — Windows 11 Fluent design, desktop-first layouts,
mouse + keyboard first — written in Compose Desktop, running on the upstream **engine** (providers,
extensions, data store, sync, repositories, ViewModels, player core), **not** on the upstream **UI**.

**What stays from the old work** (it is the engine, and it works):
- `:library` plugin API + extractors; extensions (Phisher, CNCVerse) load and run via dex→jar.
- `:android` compat layer: engine code and extensions call Android APIs (Context, SharedPreferences,
  Resources, Log, ...). It stays.
- Upstream ViewModels and logic in `app/.../cloudstream3` (HomeViewModel, SearchViewModel,
  ResultViewModel2, LibraryViewModel, DownloadViewModel, PlayerGeneratorViewModel, PluginsViewModel, ...).
  They only depend on `ViewModel` + `LiveData`, not on Views. The new UI binds to them.
- libmpv player core (`MpvPlayer : IPlayer`), single-instance IPC, window geometry, URL protocols,
  dev server, extension harness.

**What changed.**
- The Android View renderer (`android/.../desktop/runtime/ui`) is **demoted**: it only hosts
  extension-provided screens (plugin settings, plugin dialogs) and engine-raised Android dialogs.
  Normal use never shows an Android view.
- Upstream Fragments/Activities/XML layouts (`MainActivity`, `HomeFragment`, `ResultFragmentPhone`,
  `GeneratorPlayer`, `Settings*Fragment`, ...) are **off the runtime path** (they still compile; WP12
  removes them once nothing needs them). `-Dcloudstream.ui=legacy` still starts the old UI for comparison.
- The decision of 2026-09-27 ("run upstream Fragments verbatim on the compat View system") is
  **reversed**. Do not extend the View renderer to make more upstream screens work.

## 1. Mission and non-negotiables

1. It looks and behaves like a Windows 11 app (section 2). If a screen could be mistaken for a phone
   screenshot, it is wrong.
2. Every feature of the Android app remains reachable: extensions (Phisher + CNCVerse repos must work),
   search, details, player, library/bookmarks, downloads, sync accounts, backups, settings, deep links.
   Extensions' own settings screens keep working.
3. Mouse, keyboard and wheel are first-class; touch is not a design input. No bottom sheets, FABs,
   pull-to-refresh, swipe gestures, ripple, mic buttons, bottom snackbars, centred phone-width columns.
4. Windows integration is part of the product: OS theme + accent colour, remembered window geometry,
   snap/maximise, taskbar progress, media keys, toast notifications, installer, URL protocols.
5. Fidelity of *behaviour* (data, sources, resume positions, sync) comes from reusing the engine;
   fidelity of *UI* to Android is not a goal.
6. Verify at runtime with screenshots, not by compiling (section 7). Earlier "Complete" claims were
   compile-only.
7. Don't test downloads unless asked. Keep sessions lean: few re-reads, large single writes, terse output.

## 2. Design spec — "Fluent for CloudStream"

Reference apps: Windows 11 Settings, Microsoft Store, Files, Movies & TV, Jellyfin Media Player. Not Material 3.

### 2.1 Tokens (`ui/fluent/Tokens.kt`)
- **Theme**: follows Windows (`AppsUseLightTheme`, polled every 3 s) with an in-app override
  (System / Light / Dark, Settings → Appearance). **Accent**: the Windows accent palette
  (`HKCU\...\Explorer\Accent\AccentPalette`, Light3..Dark3), override swatches in Settings (palette derived
  by lerp), fallback `#0078D4`. Dark theme uses accent *Light2* for fills (black text on accent), light
  theme *Dark1* (white text), as WinUI does.
- **Colours** (dark): backdrop `#202020`, nav pane `#1C1C1C`, page layer `#272727`, card = white 5 %,
  flyout `#2C2C2C`, strokes white 8 %, text white / 77 % / 53 % / 36 %. Light mirrors WinUI.
  Media surfaces (hero, details backdrop, player) always use dark scrims.
- **Type**: Segoe UI from `C:\Windows\Fonts` (Light/SemiLight/Regular/SemiBold/Bold/Italic files; the
  variable `SegUIVar.ttf` is not used because Compose doesn't select its axes by weight). Caption 12,
  Body 14, BodyLarge 18, Subtitle 20, Title 28, TitleLarge 40, Display 68.
- **Icons**: Segoe Fluent Icons (`SegoeIcons.ttf`, fallback `segmdl2.ttf`) glyphs in `Icons.kt`. The
  dev route `/nav?route=icons&q=E700` draws a glyph chart so code points are verified, not guessed
  (several guesses were wrong: always check there).
- **Shape/spacing/motion**: controls 4 dp, cards/flyouts/dialogs 8 dp; 4/8/12/16/24/32 spacing; 83 ms
  hover, 167 ms fades, 250 ms page transitions; no ripples. Focus ring (2 px) only after keyboard input.

### 2.2 Controls (`ui/fluent/`)
`Controls.kt`: Button (standard/accent/subtle), IconButton, Tooltip, ToggleSwitch, CheckBox, TextBox,
ProgressBar/Ring, Slider, Chip, Badge, `FText`, `Icon`, hover/focus/right-click/double-click modifiers.
`Flyouts.kt`: popup positioning, MenuFlyout, ContextMenuArea, ComboBox (keyboard ↑↓ Enter), ContentDialog
host (`Overlays.show/message/onEscape`). `Nav.kt`: NavigationPane, TabBar. `Media.kt`: PosterCard (hover
lift + play, quality/score/progress overlays), PosterSkeleton + `shimmer()`, Shelf (hover chevrons), SectionHeader.
`MaterialBridge.kt`: Material 3 theme derived from the Fluent tokens for the Material code that remains
(engine dialogs, PIN dialog, custom preference widgets).

### 2.3 Window and shell
- **Integrated title bar** (`platform/WinChrome.kt`): the AWT window procedure is subclassed (like FlatLaf):
  `WM_NCCALCSIZE` removes the caption, `WM_NCHITTEST` reports caption buttons (Snap Layouts on maximize),
  the top resize edge and the drag band (top 40 dp minus controls marked `Modifier.noWindowDrag(key)`).
  Compose draws min/max/close (`ui/shell/Caption.kt`, 46×32 dp, close turns red); pages leave `captionInset`
  (138 dp) at the right of the top row. Falls back to the native frame if hooking fails or with
  `-Dcloudstream.titlebar=native`. Plus DWM dark mode, rounded corners (`WinTheme.styleWindow`). Mica is not used
  (Compose paints an opaque surface).
- **NavigationView**: ≥ 1008 dp open (240 dp), 640–1007 dp compact rail (48 dp, tooltips), < 640 dp
  hidden behind a hamburger in the top bar. Back + hamburger on top; Home, Search, Library, Downloads;
  footer: Extensions, Settings.
- **Top bar** over the page (transparent over full-bleed headers until the page scrolls): page title,
  centred global search (Ctrl+K / Ctrl+E) with type-ahead suggestions and recent searches, profile avatar
  (opens the profile picker). Toasts are Fluent info bars at the top right.
- Shortcuts: Alt+← / mouse button 4 = back, F11 fullscreen, Esc closes the top dialog.

## 3. Screens (all built; ✔ = verified in the running app on 2026-10-01)

| Screen | What it is | Engine binding |
|---|---|---|
| **Home** ✔ | Rotating hero (backdrop/logo, Play, More info), provider ComboBox + reload, Continue watching (progress, context menu), Bookmarks by status chips, provider shelves with lazy paging + "See all", shimmer skeletons, error/empty states | `HomeViewModel` |
| **Search** ✔ | Grouped-by-provider shelves or merged grid, type chips, provider picker dialog, recent searches page, type-ahead flyout | `SearchViewModel`, `DataStoreHelper.searchPreference*` |
| **Details** ✔ | Backdrop header, poster, badges, meta, synopsis, Play/Resume (series falls back to episode 1), library status menu, favourite, subscribe, trailer, more menu; episodes with season/dub/range/sort ComboBoxes, thumbnails, progress, context menu; cast; recommendations; engine popups as dialogs; "Finding sources…" card | `ResultViewModel2`, `SyncViewModel` |
| **Player** ✔ | mpv frame under auto-hiding Fluent controls: seek bar (hover time, buffer, skip markers), play/pause, ±10 s, prev/next, volume (+wheel), speed, subtitles (list, delay, add file, online search), audio, sources/quality, picture size, episodes panel, fullscreen; loading/failure overlays; skip intro; next-episode button; keyboard shortcuts; source fallback; progress saved; autoplay next | `PlayerGeneratorViewModel`, `MpvPlayer` |
| **Library** ✔ | Tabs per list (Watching, Completed, ..., Favorites, Subscribed) with counts, sort, search, sync provider switch, poster grid | `LibraryViewModel` |
| **Downloads** (built, not tested by request) | Storage bar, titles, episode folders, play, pause/resume/delete menu | `DownloadViewModel` |
| **Extensions** ✔ | Repositories list (add, remove, copy) + plugin list of the selected repo or all installed: filter, install/uninstall/install-all, plugin settings gear | `ExtensionsViewModel`, `PluginsViewModel`, `RepositoryManager` |
| **Settings** ✔ | Windows 11 Settings layout: category rail + cards. General, Player, Appearance (+ Windows theme/accent), Updates & backup, Accounts & security, About, search across all. Renders the upstream `Settings*Screen.getPreferences()` DSL (`FluentPreferenceList`) minus Android-only items | upstream preference keys |
| **Setup wizard** ✔ | First run, full window: app language → add a repository + install extensions → media types / provider languages → done | `ExtensionsViewModel`, prefs |
| **Profiles** (built) | Dialog: choose / add / remove profile, shown at startup when there are several | `AccountViewModel` |

Dialogs: Fluent `ContentDialog` for everything new. Engine-raised Android `AlertDialog`s (title, message, buttons, plain/single/multi-choice lists) are shown
as Fluent dialogs through `FluentAlerts`; only dialogs with custom views or adapters stay in the
Material-themed `DialogHost`. Extension-provided screens render in the legacy host.

## 4. Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ NATIVE UI            app/.../desktop/ui/{fluent,shell,screens}                │
├──────────────────────────────────────────────────────────────────────────────┤
│ CORE BRIDGES         app/.../desktop/core/Core.kt                             │
│  Navigator + Route + Entry(VmScope) · AppVms · observeAsState · ioTask        │
│  NativeUi hooks (engine → native pages)                                       │
├──────────────────────────────────────────────────────────────────────────────┤
│ UPSTREAM ENGINE      app/.../cloudstream3 (ViewModels, APIRepository, DataStore,
│  PluginManager, sync, subtitles, downloader) + :library + MpvPlayer           │
├──────────────────────────────────────────────────────────────────────────────┤
│ :android compat      Android API surface for the engine and extensions        │
│  (+ View renderer, only for extension screens and engine Android dialogs)     │
└──────────────────────────────────────────────────────────────────────────────┘
```

Files (all under `app/src/main/kotlin/com/lagradost/desktop/`):

| Path | Contents |
|---|---|
| `Main.kt`, `NativeApp.kt` | entry; `NativeWindow` (Window + Fluent theme + shell + legacy overlays + startup wizard/profile picker), `NativeKeys`, `NativeLinks` (deep links) |
| `DesktopBootstrap.kt` | `startEngine()` = application + a *never created* `MainActivity` as the engine's Context + the non-UI half of `MainActivity.onCreate` (`onCreate(act, loadPlugins, activityLifecycle=false)`) |
| `core/Core.kt` | `Route`, `Entry`, `Navigator` (back stack ≤ 40, `go/goTab/back/reset/search/openDetails`), `VmScope`/`AppVms`/`appVm`, `observeAsState`, `ioTask`, `NativeUi` + `nativeNavigate`/`nativeBack` |
| `platform/WinTheme.kt`, `WinChrome.kt`, `WinMedia.kt` | registry theme/accent + DWM styling; integrated title bar (JNA window procedure); keep-awake (`SetThreadExecutionState`) and global media keys (play/pause, next, previous, stop) while the player is open |
| `ui/fluent/*` | design system (section 2) |
| `ui/shell/` | `AppShell` (pane, top bar, page host with saved state per page, toasts), `GlobalSearch`, `Accounts` (profile picker), `Caption` (caption buttons, `noWindowDrag`) |
| `ui/FluentRequests.kt` | the engine's selection / text / message / PIN requests (`DesktopDialogs`) shown as Fluent ContentDialogs |
| `ui/FluentAlerts.kt` | simple Android `AlertDialog`s from engine code (title, message, buttons, choice lists) shown as Fluent ContentDialogs; hooked in `DesktopUiHost.showDialog` |
| `ui/screens/*` | one folder per screen; `settings/FluentPreferences.kt` renders the preference DSL; `IconGallery.kt` dev chart |
| `ui/AppRoot.kt` | legacy: `AppRoot` (old UI), `LegacyOverlays` (engine dialogs/extension activities above the native UI), `LegacyContent` (Material host) |

**Startup.** `main()` → crash handler → `DesktopBootstrap.initApplication()` → image loader →
`startEngine()` + `NativeUi.active = true` → deep link → single-instance IPC → `application { NativeWindow() }`.
First frame then: setup wizard if `HAS_DONE_SETUP` is unset, else the profile picker when there are
several profiles and "skip account selection" is off.

**Engine hooks** (each marked `// desktop:`; keep them minimal): `AppContextUtils.loadResult`,
`loadSearchResult` → Details route; `UIHelper.navigate` → player/tab routes via `nativeNavigate`;
`UIHelper.popCurrentPage` → `Navigator.back`; `ResultViewModel2` `ACTION_PLAY_EPISODE_IN_PLAYER` →
`NativeUi.openPlayer`; `GeneratorPlayer.generators` made public; `:shared` internals made public
(`Preference` helpers, `LocalSharedInfiniteTransition`); `MpvSurfaceView.setRenderSize`, public `frame`.

**Player.** `PlayerScreen` creates a `PlayerSession` (per route entry): `CS3IPlayer` (mpv) +
`PlayerGeneratorViewModel`. It observes `loadingLinks/currentLinks/currentSubtitles/currentStamps`, starts the
first usable source in quality order (`startPlayer/loadLink` as `GeneratorPlayer`), falls back to the next
source on `ErrorEvent`, saves position through `DataStoreHelper.setViewPosAndResume`, syncs episode progress,
preloads the next episode, autoplays the next episode at the end. Video = `MpvSurfaceView` frames drawn
by Compose. Volume/mute/resize go straight to mpv properties.

**Dev tooling.** `./gradlew :app:runDev -PdataDir=<dir> -PdevPort=8765`, then (screenshot pixels, scale 1.5):
`/screenshot`, `/click?x=&y=[&button=right&count=2]`, `/scroll?x=&y=&amount=`, `/key?name=`, `/type?text=`,
`/state` (shows the native back stack), `/nav?route=home|search&q=…|library|downloads|extensions|settings&q=<page>|icons&q=<hex>|back`,
`/resize?w=&h=` (dp), `/placement?v=floating|maximized`, `/hit?x=&y=` (what the title bar reports for a window pixel), `/log?lines=`, `/plugins`, `/quit`. `/find`, `/viewtree`, `/layout`, `/navigate`
only work on the legacy path.

## 5. Work packages

Mark `[x]` only when verified in the running app with a screenshot.

- **WP0 — Plan and decision.** [x] This document.
- **WP1 — Foundation.** [x] tokens, OS theme/accent, Segoe fonts/icons, DWM dark title bar; [x] controls;
  [x] core bridges + engine start without Android UI; [x] shell; [x] native UI default (`legacy` flag keeps
  the old path). [ ] Fluent overlay scrollbars; [ ] `devTag` endpoints (`/ui`, `/click?tag=`); [ ] light theme
  pass (colours defined, not visually checked).
- **WP2 — Home + Search.** [x] both, see section 3. [ ] arrow-key navigation inside shelves/grids (Tab works),
  [ ] context menu on search cards.
- **WP3 — Details + Player.** [x] **M1 reached (2026-10-01): Search → Details → Play, movie and series,
  episode panel, native.** [ ] Details: sync (AniList/MAL) status/score card, cast photos, in-app trailer,
  "play with…" external menu; [ ] Player: subtitle style, audio/video track memory, resume prompt, mini
  player, taskbar progress; online subtitle search and "add file" are built but not yet exercised (they need
  an OpenSubtitles/SubDL login/network); keyboard focus handling when menus close needs a longer test.
- **WP4 — Library, Downloads.** [x] Library. [x] Downloads built (no testing by request).
- **WP5 — Extensions.** [x] page: repos add/remove/copy, install/uninstall/install-all, filter, plugin
  settings gear. [ ] update-all button + outdated badges, per-plugin "Test", language/type filter chips,
  drag-and-drop of `.cs3`, `cloudstreamrepo://` deep link → Fluent confirm dialog (currently the engine's
  Android dialog).
- **WP6 — Settings.** [x] all five upstream pages + About + search + Windows theme/accent. [ ] verify every
  option persists across a restart; [ ] the preference callbacks that call `activity.navigate(...)` /
  file pickers (download path, backup location, restore) need native replacements (SAF is not available).
- **WP7 — Accounts, Setup, Sync.** [x] first-run wizard (verified on an empty data dir), [x] profile picker
  built. [ ] verify profile switch/add with PIN (Windows Hello), [ ] AniList/MAL/Simkl login via browser +
  deep link, [ ] backup export/restore with native file dialogs.
- **WP8 — Dialog migration.** [x] Material theme derived from Fluent tokens (`MaterialBridge`). [x] the
  engine's `DesktopDialogs` requests (selection lists, text input, message, PIN) are Fluent ContentDialogs
  (`FluentRequests.kt`; selection/PIN dialogs written to mirror the old Material ones, PIN not exercised).
  [x] simple Android `AlertDialog`s built by engine code (safe-mode notice verified; title, message, buttons,
  plain/single/multi-choice lists) are Fluent ContentDialogs: `FluentAlerts.kt` reads `AlertController.simpleModel()`
  and forwards clicks, hooked in `DesktopUiHost.showDialog`. [ ] dialogs with custom views/adapters (e.g. "Clone
  site") still use `AndroidDialogHost` (Material-themed); [ ] snackbars → info bars; extension dialogs keep
  their own views.
- **WP9 — Windows integration.** [x] display + system stay awake while playing, global media keys,
  window title = what is playing (`WinMedia`, code verified to run, keys not pressed in tests). [ ] taskbar
  progress, SMTC overlay, jump list, toast notifications, drag-and-drop, DPI sweep (100/150/200 %), high
  contrast, UI Automation names.
- **WP10 — Window chrome.** [x] integrated title bar with native drag/resize/Snap Layouts hit-testing and
  Fluent caption buttons (verified maximized and restored; `/hit` and `/placement` dev endpoints). [ ] test
  Snap Layouts flyout by hand, dragging, double-click maximize, window drag from the player, multi-monitor
  DPI changes; [ ] fullscreen hides the buttons (code path exists, not checked); Mica was dropped.
- **WP11 — Packaging.** [x] `./gradlew :app:createDistributable` builds a 440 MB app image
  (`app/build/compose/binaries/main/app/CloudStream/CloudStream.exe`, bundled JRE, JCEF, libmpv); launched
  with a fresh data dir it shows the wizard; runtime modules now include `jdk.crypto.mscapi` (Windows trust
  store: 165 certs merged) and `jdk.httpserver`. [ ] `packageMsi`/`packageExe` need WiX (not installed here);
  [ ] URL protocol registration, TorrServer inclusion, play a video from the packaged app, clean-PC test.
- **WP12 — Retire legacy.** [ ] remove Android fragment/activity UI + XML layouts from the build, keep only
  the extension view host; [ ] extension harness still ≥ 94/163; [ ] final screenshot sweep at 900/1281/1920 px
  and 100/150 % DPI.

## 6. Status (2026-10-01, end of session 1)

Built and verified in the running native app (screenshots, dev server): Home (hero, shelves, See all, continue
watching, scroll position restored on back), Search, Details (movie Batman Begins; series Breaking Bad with
seasons and episodes), Player (HLS 1080p movie and series episode; speed/subtitle menus, episodes panel),
Library, Extensions (Phisher + CNC repos; install and uninstall of a plugin), Settings (every page, light and dark
theme, accent swatches), Setup wizard (empty data dir, also from the packaged .exe), Fluent engine dialogs,
scrollbars, compact (800–1000 dp) and wide layouts, the integrated title bar (maximized and restored), and the
packaged app image. Engine unchanged: harness baseline 94/163 (not re-run this session).

Known problems / observations:
- Engine-built Android `AlertDialog`s with custom views (e.g. "Clone site") are still Material pop-ups, only themed (WP8); multi/single-choice `FluentAlerts` lists were not exercised, only the message dialog.
- First stream start of an episode can take ~25 s before the first frame (slow provider); the loading overlay
  stays up meanwhile.
- Provider "type" labels are unreliable (Netflix mirror says Anime for everything), so poster cards show
  no type line except in the merged "All results" search grid.
- `MainActivity.afterPluginsLoadedEvent.invoke(true)` is how home refreshes after setup/repo removal; if a page
  does not refresh after installing extensions, check that event first. The Extensions stats line
  ("N installed / M available") only refreshes when the page is reopened.
- Not exercised yet: PIN dialogs, profile add/remove, online subtitle search, "add subtitle file", media keys,
  sync (AniList/MAL) controls, deep links, backup/restore, update check.

Scope decision (session 2): the extra features listed above under "Not exercised yet" and in WP5-WP12 (sync controls, taskbar progress,
toasts, SMTC, jump list, MSI, URL protocols, retiring the legacy UI, ...) are parked, not wanted now. Do them only when asked.

## 7. Rules

1. **Verify at runtime.** Build, launch, screenshot, look. Compile success proves nothing.
2. Never edit `reference/` (read-only upstream clone, commit in `UPSTREAM_COMMIT`). There is no git repo:
   copy a file to the scratchpad before replacing it wholesale.
3. Engine code is not rewritten for UI reasons. Small `// desktop: <reason>` edits are allowed when a
   ViewModel needs a hook; otherwise adapt in `core/`.
4. Compat classes (`:android`) keep exact Android FQCN/signatures (extensions are compiled against
   android.jar). Don't grow the View renderer for new screens.
5. UI is Compose only, in `desktop/ui`. Foundation/ui primitives only; no Material widgets in new code.
   Colours/type/shape only from `FluentTheme` — no hard-coded colours except media scrims.
6. No phone idioms (section 1.3). Check each screen at 3 widths and keyboard-only (Tab, Enter/Space,
   Esc, arrows in lists/menus).
7. Compose 1.12 notes: `Modifier.composed` is gone (write `@Composable fun Modifier.x()`); `Key.Home` is
   deprecated (`Key.MoveHome`); never put `\u` escapes through the Write tool (it turns them into literal
   glyphs) — use `\x5cu` in a perl replacement or edit constants with sed; `ioSafe` is an extension on `T`
   (use `ioTask` in plain functions); lists inside `Overlays.Dialog` bodies need `remember` for state.
8. User preferences: window remembers size/position; the player works like a desktop player; no downloads
   testing; short status messages.

## 8. Environment and dev loop

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (`java` on PATH is Java 8). Always
  `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"`.
- No python (Store alias). Scripting = bash/perl, Java single-file, Edit/Write.
- Compile: `./gradlew :app:compileKotlin -q`.
- Relaunch script (kill old via `/quit`, wait, run, poll `/state`):
  ```bash
  curl -s -m 3 http://127.0.0.1:8765/quit; sleep 4
  cd "<repo>"; ./gradlew :app:runDev -PdataDir="<dataDir>" -PdevPort=8765 --console=plain > run.log 2>&1 &
  until curl -s -m 2 http://127.0.0.1:8765/state >/dev/null; do sleep 3; done
  ```
  A populated data dir lives in an earlier session's scratchpad (`.../d6a297c4-.../scratchpad/data`).
  If a previous instance crashed, delete `<dataDir>/files/last_error` and `crash.log` or the engine shows
  "Safe mode on" and loads no extensions. "Forwarded arguments" + exit = an old instance holds IPC port
  52525: kill the java process listening on 8765.
- Fresh-user test: run with an empty `-PdataDir` (shows the wizard).
- Extension harness: `./gradlew :app:runHarness -Pargs="<dataDir> all full"`; baseline 94/163.
- Machine: Windows 11 (26300), dark mode, accent `#0078D4`, scale 1.5. Don't take full-desktop captures
  (they capture the user's screen): use `/screenshot`.

## 9. Known issues carried over

- Plugin-provided screens depend on the View renderer: `StaggeredGridLayoutManager` equal-height rows,
  `TextView` ignores `includeFontPadding`, Aniworld settings log a bare AWT exception (screen works).
- libcef has crashed once on the UI thread during search (JCEF startup); cookie reads no longer wait on it.
- Site-side provider failures (anime empty links, IPTV "Invalid file header", PlayZTV base64, DoFlix TLS)
  are out of scope unless the harness regresses.

## 10. Session 2 log (2026-10-01)

- **Extension settings button (gear on an installed extension) did nothing.** Cause: extensions open their settings as
  `DialogFragment`s on `activity.supportFragmentManager`; the native UI never starts the engine `MainActivity`, so its
  `FragmentManager` stayed in CREATED and the dialog was never shown. Fix: `DesktopBootstrap.startEngine()` calls
  `FragmentActivity.startFragmentHost()` (new, `android` module). Verified with CNC Verse ("Select studios to show on
  home"): opens, Save works. The sheet is still an Android View dialog from the extension (Material-themed host).
- **"Restart" in an extension closed the app and never reopened it.** Extensions do
  `startActivity(Intent.makeRestartActivityTask(...))` then kill their process; on desktop nothing relaunched.
  Fix: `platform/AppRestart.kt`. `DesktopUiHost.startActivity` treats a MAIN/LAUNCHER intent as a restart request, a
  shutdown hook starts a new instance (packaged: the launcher exe, `jpackage.app-path`; dev: same java command line)
  with env `CLOUDSTREAM_RESTART_WAIT_PID`, and `main()` waits for the old process to be gone before taking the
  single-instance lock, data dir and dev port. If the extension does not end the process itself the app exits after
  1.5 s. Verified in dev (CNC Verse "Restart Required" -> Yes -> new window, same data) and from the packaged exe
  (`/restart` dev endpoint reproduces what extensions do).
- **Fluent dialogs above engine dialogs.** `DialogLayer` moved from `AppShell` to `NativeWindow` (above
  `LegacyOverlays`) so the restart prompt shown while an extension sheet is open is not hidden behind it.
- **Portable build.** `./gradlew :app:portableDist` -> `dist/CloudStream-Portable/CloudStream.exe` (467 MB, bundled JRE,
  no install). `portable.txt` (or a `data` folder) next to the exe makes `AndroidRuntime.defaultDataDir()` use
  `<exe folder>\data`; without it the app uses `%APPDATA%\CloudStream`. Verified: fresh start shows the setup wizard,
  creates `data\`, restart relaunches. Delete `data\` to reset; the folder can be moved or copied as a whole.
- **Gradle daemon.** `./gradlew --stop` after builds (the daemon and Kotlin daemon stay resident otherwise, ~4 GB).
  The portable exe needs no Gradle.

## 11. Session 3 log (2026-10-01): window buttons, fullscreen, captions, player menus

User report: min/max/close did nothing, no way out of fullscreen, captions unreliable (listed but not shown, or
shown but not listed), and the player should have Android's two-button layout. All fixed and checked with real
input (Win32 `mouse_event` / `keybd_event` scripts, not the dev server's synthetic events, which hid the bugs).

- **Caption buttons / title bar** (`platform/WinChrome.kt`). Cause: Compose draws into a child window
  (`SunAwtCanvas`) covering the whole client area; Windows hit-tests the deepest child first, so the frame never saw
  the pointer. Now the canvas is subclassed too: over the caption buttons, drag band and top resize edge it answers
  `HTTRANSPARENT`, so the frame's `WM_NCHITTEST` (HTMIN/HTMAX/HTCLOSE/HTCAPTION/HTTOP) decides. Also: hit testing is
  relative to the client area (the window rect holds ~10 px invisible borders, buttons were offset by that);
  clicks on the buttons are done explicitly (`WM_NCLBUTTONDOWN/UP` -> `WM_SYSCOMMAND`, the default handling does not
  work with a custom caption); pressed look; `TrackMouseEvent` so hover clears when leaving. Verified with real
  mouse: maximize, restore, minimize, drag the band, double-click the band, Snap hover reports HTMAXBUTTON.
- **Fullscreen**. AWT's exclusive full screen replaced by a borderless topmost window over the monitor
  (`WinChrome.setFullscreen`, `WM_NCCALCSIZE` returns the whole window); leaving restores bounds (and maximized).
  `WinChrome.fullscreen` is now Compose state, so the (dead) caption buttons are no longer drawn in fullscreen.
  Esc and F11 are handled window-wide (`NativeKeys`), double-click on the video and the button also exit; the
  player takes the keyboard focus back on click, after fullscreen changes and when dialogs close. Window geometry is
  not saved while fullscreen. Verified with real keys (F, Esc, F11 path) and double-click.
- **Captions** (`player/MpvPlayer.kt`). Causes: selecting a subtitle only set mpv's `slang` (nothing switched);
  external subtitles were `sub-add`ed on the UI thread before the file was open, again on every update (duplicates);
  `file:/C:/...` URLs failed in mpv; embedded HLS/MKV tracks were never listed. Now mpv's track list is the truth:
  `sid=no` at load, external subtitles are added once the file is open (`FILE_LOADED`, on a worker thread, `file:` URIs
  become paths, failures are remembered), embedded tracks are published as `SubtitleData` (origin EMBEDDED, url = mpv
  track id) via `EmbeddedSubtitlesFetchedEvent`, selection sets `sid` (no reload), the current subtitle is read back
  from `sid`. `sid/aid/vid` are observed so menus refresh. Verified: embedded English shows, None -> `sid=no`,
  English -> `sid=1`, external .srt added and drawn. Dev endpoints: `/tracks`, `/addsub?file=&name=&lang=`.
  Stacked captions (2026-10-02, Hotstar via CNC Verse: embedded HLS WebVTT): every seek made mpv feed the cached cues to libass again, so
  one line was drawn 3, 5, 9 times (one copy per seek; a fresh load without seeking showed single lines). Fix: `sub-clear-on-seek=yes`.
  Verified with 12 seeks over one stretch, embedded and an external .srt.
- **Player menus like Android** (`ui/screens/player/PlayerDialogs.kt`). Two buttons replace Subtitles/Audio/Sources:
  *Sources and subtitles* = dialog with Sources list on the left and Subtitles on the right (None, grouped names,
  option list when a name has several files, Add subtitle file, Search online, Add first online result, gear =
  subtitle delay and size); *Audio and video tracks* = Video tracks left, Audio tracks right (a side is hidden when
  it has fewer than two tracks). Apply commits, Cancel leaves everything; the video pauses while open. Verified:
  Hindi audio -> `aid=2`; None / English subtitles.
- Not done on purpose (Android has them, not needed now): quality-profile button, subtitle encoding, full subtitle
  style editor (font/colours).
- Dev-harness caveat: `/click` right after `/move` sometimes lands on the video (controls still hidden) and only
  pauses; real mouse clicks open the dialogs every time (3/3).

## 12. Session 4 log (2026-10-01): player polish, hangs, sources

- **Fullscreen frame**: `WinChrome.setFullscreen` also strips `WS_CAPTION|WS_THICKFRAME` (restored on exit);
  `WinTheme.styleWindow(..., fullscreen)` sets square corners and no border colour (`DWMWA_COLOR_NONE`) while fullscreen.
- **HUD for keyboard actions** (`PlayerScreen.HudOverlay`, `PlayerSession.hud`): macOS-style bubble with spring-in /
  fade-out for volume, seek (+/- s), speed, mute and play/pause. Volume steps go to the next multiple of 5 and boost to
  200 % (bar turns orange above 100 %); mouse wheel uses the same steps.
- **Player layout**: source name (and resolution) under the title; the episodes panel starts below the window caption
  buttons (its close button no longer sits under the app's close button).
- **Remembered choices**: audio language is stored (`desktop_player_audio_lang`) and selected in every file that has
  it (once per file, compared by normalised language); subtitle language already persisted (`SUBTITLE_AUTO_SELECT_KEY`).
- **Endless buffering**: mpv gets `network-timeout=15`, reconnect options for HLS segments, bigger read-ahead; the
  session watches stalls (no position/buffer change for 25 s while buffering, 35 s while starting): retries once, then
  moves to the next source, else shows the failure screen.
- **Exit hang**: `MpvPlayer.release()` detaches at once and destroys the core on a daemon thread (`mpv_terminate_destroy`
  could block on a stuck read and froze the window). `HangWatchdog` writes all thread stacks to `<data>/logs/hang-*.txt`
  when the UI thread does not answer for 8 s.
- **"Empty app"**: the uncaught-exception handler wrote `files/last_error` for any thread at any time, which turns on
  safe mode (no extensions) at the next start. Now only for crashes within 60 s of the start (a bad extension at load).
- **Taskbar logo**: multi-size `icon.ico` (16..256) for the exe, `AppIcon` sets the window icons from `app-icon.png`
  and an explicit AppUserModelID (`CloudStream.Desktop`).
- **Subtitles**: external subtitles are added by a pool of 4 workers (a dead subtitle server no longer blocks all
  others), the wanted one first; per-file generation guards stale results.
- **Search**: `SearchViewModel` retries a failed provider up to twice (not for "not implemented"), failures are logged
  as `SearchVM`. (Disney/Marvel/Pixar/Star Wars of CNC Verse are "merged with Hotstar" and always say not implemented.)
- **Reanime (and similar)**: the extension's playlists come through `enc-dec.app/api/parse-flixcloud?url=...` and name
  the AES key relatively (`key.bin`), which only exists next to the *real* playlist; segments are `.png/.webp`. mpv
  could not decrypt, saw pictures, "no audio or video data played". `player/HlsProxy.kt` (loopback server) is used
  automatically when an HLS link fails once: playlists get absolute addresses, keys are looked up next to the real
  playlist too. Verified: Re:ANIME episodes play with 44 external subtitles.
- **Auto update of extensions**: already on (`DesktopBootstrap.loadPlugins`: `auto_update_plugins_key`, default true,
  every start; "Plugin update done!" in the log), plus "auto download" mode from settings.
- **Read-ahead one minute** (mpv `cache-secs=60`, `demuxer-readahead-secs=60`, 150 MiB, 32 MiB behind): the default `cache-secs` is unlimited, which preloaded ~10 minutes. Measured `demuxer-cache-duration` = 60 s. Links through `enc-dec.app/api/parse-*` go through the repair proxy directly.
- Harness fixed (`ExtensionHarness` passes `activityLifecycle = false`); `./gradlew :app:runHarness -Pargs="<dir> phisher full reanime"`.

## 13. Session 5 log (2026-10-01): top bar, scoped search, crashes, cleanup

- **Never delete `dist/CloudStream-Portable/data`** (it is the user's real data: repositories, installed extensions, settings).
  `portableDist` leaves it alone. Test builds run on that same folder: `scratchpad/relaunch2.sh` = `runDev -PdataDir=<portable data>`
  with `-Dcloudstream.ipcport=52999` (so a running portable exe is not hijacked). Back up `shared_prefs` before long test runs.
- **Top bar**: the profile moved to the bottom of the left pane (`AccountButton`); the Home page shows the extension
  selector (`shell/ProviderSelector.kt`) in its place. Layout is a Row (search shrinks, never overlaps the selector).
- **Scoped search**: on Home the top search box searches only the selected extension (`Route.Search(only = name)`, result page
  says "Only in X" with a "Search all extensions" button); everywhere else, and the Search page of the left bar, search all.
- **"App becomes empty / does not respond"** (found): a `LazyRow`/`LazyGrid` with two items with the same key throws while
  measuring (`Key "..." was already used`, seen with MovieBox lists) and the window stays blank. `Shelf` and `PosterGrid`
  now show each key once.
- **HUD** is a small pill at the top of the picture. Images use bicubic filtering, extension icons are requested at 128 px.
- **Cleanup** (moved to `scratchpad/backup/removed`, project compiles): `app/src/pending`, the legacy window mode
  (`-Dcloudstream.ui=legacy`, `AppRoot()`), and 39 unused Android fragment/adapter/activity files (Download/Library/Result
  fragments, Settings*2, Setup fragments, ...). Kept because layouts/bindings or the engine still name them: `TestView`,
  `PieFetchButton`, `DownloadButton`; two constants moved to `DownloadConstants.kt` / `SetupConstants.kt`. Android layouts and
  resources stay (extensions and the view bindings use them).
- **Extension survey** (`runHarness ... full`, now with a playability probe; `live` mode scans every live event): of 78 Phisher
  extensions tested, 43 gave links; the rest are site-side (dead domains `UnknownHostException`, login required, Cloudflare,
  changed JSON) and not fixable here. Live: PlayFy/Cricify/SKTech are ClearKey DASH or plain HLS and play (checked beIN
  Sports 2); failures are events whose address answers 403/404 (token expired or match not started) - the player moves to the
  next link. Toonstream plays (Abyass), Kisskh search works (50 results for "squid game").

## 14. Session 6 log (2026-10-02): player behaviour, sidebar, PiP, startup speed

- **Back from the player looped** (found): a Details page opened with play/resume (Continue watching, Home "Play") started the
  player again every time it was shown, so Back from the player re-opened it (the "previous server" loop). `Entry.autoStartDone`
  makes the auto start happen once.
- **Resume**: the saved position is now an mpv option of the file that opens next (`start`, `MpvPlayer.loadPlayer`); the old `seek`
  right after `loadfile` was lost while a slow HLS playlist was still opening, so every resume began at 0:00. Another source of the
  same episode continues at the position the video was at (`PlayerSession.resumeMs`), not at 0:00. Reload after a stall keeps the
  intended start. `loadLinksPrev` went to the next episode (`+= 1`), fixed.
- **Status while opening**: mpv reported "playing" at START_FILE, which cleared the loading overlay at once (a source change looked
  like a frozen video for seconds). `updateStatus` now says buffering until the file is open. A source the user picked that does not
  open/play puts the previous working source back (`PlayerSession.backToFallback`); a source that never opened skips the retry.
- **Slow starts**: ffmpeg opens the playlists of an HLS master one after the other. A service master with ~25 subtitle playlists took
  11 s, Re:ANIME's wrapper 8 s. All HLS now goes through `HlsProxy` (falls back to the plain address once if that fails): the proxy
  fetches every variant/audio/subtitle playlist of a master at the same time (8 threads, video first) and keeps them for the player
  (VOD playlists stay cached, live ones are fetched again). Measured: Prime Video HLS 11.7 s -> 3.2 s to open, Re:ANIME 8.4 -> 5.0 s
  (the rest is the wrapper service). `[CC]`-style characters in playlist addresses are percent-encoded. mpv log level: `-Dcloudstream.mpvlog=v`;
  `MpvPlayer` logs `timing: file opened / first frame` after each `loadfile`. The user agent of the previous link no longer stays set.
  Not fixable here: origins that need 5-15 s to answer (Abyass tunnels), and sources whose segments ffmpeg cannot probe (Re:ANIME HD-2).
- **Controls**: hide 2 s after the last mouse move unless the pointer is on them (measured bounds of the top/bottom bars) or a menu /
  the episode list is open; keyboard shortcuts show the HUD pill only, never the controls (also when paused). Shortcuts are handled at
  window level (`PlayerKeys` via `NativeKeys`), so they work whatever has the focus (a focused button that left with the controls used
  to leave the player without key input until play/pause was clicked) plus a focus-restoring loop. Flyout menus count themselves
  with a `DisposableEffect` (no leaked "menu open" state). Real arrow keys need the extended-key flag in test scripts (`realkey.ps1`).
- **Android parity**: the audio/video tracks button only exists with more than one video or audio track (like `playerTracksBtt`);
  "Play now (n)" keeps collecting sources in the background (`PlayerGeneratorViewModel.isLoadingLinks`), the sources dialog lists new
  ones as they arrive with a spinner; keep-screen-on for the whole time the player is open (own thread for `SetThreadExecutionState`).
- **PiP** (`WinChrome.setPip`, key `I` or the button): small borderless always-on-top window (420 dp wide, video aspect, bottom right
  or where it was last), 28 dp strip at the top drags it (back-to-app, close), centre play/pause + progress on hover, Esc / double click
  returns; geometry is not saved while in PiP; leaving the player leaves PiP.
- **Sidebar**: always the 48 dp icon rail; it opens over the page (names, shadow) while the pointer is on it and closes 250 ms after
  it leaves (`AppShell.HoverNavigationPane`); no hamburger, the page never moves.

## 15. Session 7 log (2026-10-02): Ultima (Phisher repo) and Android sync

Ultima = the "all in one home" extension of the Phisher repo; its **Data Sync** keeps bookmarks, resume watching, search history,
settings and the extension/repository list in a Firebase Realtime Database (`<url>sync/<key>/...`, default
`cloudstream-ultima-sync-default-rtdb.firebaseio.com`, or the user's own with "Use Custom Firebase Database"), with the same sync
key on every device. The plugin (no source in the repo any more; decompiled with Vineflower from the converted jar) does everything
itself through the engine's `DataStore` / `SharedPreferences`, so it already ran on the desktop engine. Verified with a mock
database (`tools/ultima-sync`, see its README): install from the repo, settings screens (sections, sync, credentials) render and take
real keyboard input and Ctrl+V; desktop pushes extensions / resume watching / settings / search history; an "Android" update
(newer positions, a new title, a bookmark, an extension list with Android file paths plus one more plugin) is pulled live over SSE:
Home "Continue watching" and Library refresh without a restart, the missing plugin is downloaded, converted and loaded (StreamPlay,
~17 s). Ultima's own home page works as a provider once sections are chosen.

What had to be added on the desktop side:
- **Activity lifecycle for the native UI** (`DesktopLifecycle`, `ComponentActivity.desktopSetResumed`, `Application.onLifecycleCallbackRegistered`):
  the engine activity never ran onResume, so `registerActivityLifecycleCallbacks` callbacks were never called and the plugin's
  `reload()` (it only refreshes the app while the activity is RESUMED) did nothing. Now the engine activity is RESUMED from the
  start, callbacks get `onActivityResumed` when the window gains the focus (at most every 15 s) / `onActivityPaused` when it
  loses it, and a plugin that registers one later (loads while the app is open) gets created/started/resumed after 2.5 s.
  This is what makes Ultima pull on return to the app.
- **Esc / Back close Android dialogs** (`DesktopUiHost.closeTopAndroidDialog`, used by `NativeKeys` and `Navigator.back`): extension
  settings sheets could only be closed by clicking outside, and the sync sheet is not cancelable that way.
- **`PrefsBackup`**: copies of `data/shared_prefs` into `data/backups/prefs-<time>` at start and on window focus after 30 min
  (12 kept). Reason: Ultima mirrors the cloud copy of a category onto the local store and removes local keys of that category that
  the cloud copy lacks ("Removing deleted local key" in the log). Linking a PC that has its own history replaces it by what the
  phone pushed. Restore = copy the folder's .xml files back over `data/shared_prefs` with the app closed.
Unchanged behaviour of the plugin worth knowing: pushes are debounced (a few seconds after the last change, so playback progress
reaches the phone when the video stops); settings sync also carries plugin preference files and `desktop_player_*` keys (harmless).
Not tested against the real Firebase (HTTPS/SSE use the same OkHttp client as everything else) and not against the user's real Android payload.


## 16. Session 8 log (2026-10-02): extensions on the desktop engine, StreamPlay and CineStream

Goal: make extensions work by fixing the runtime, not by patching single extensions. Method: compare what the Android app does
(ExoPlayer + `app.baseClient`, system trust store, DNS setting) with what the desktop does, find the differences, and test with
real links instead of guessing.

**Upstream.** `recloudstream/cloudstream` pulled again (d77c856, `UPSTREAM_COMMIT`): since cf72fc7 only the account picker UI
changed (`AccountAdapter`, `AccountHelper`, `HomeParentItemAdapter` + layouts, applied). The library / extension API is unchanged.
Static check of every plugin of the official, Phisher, Megix (CineStream) and CNC repos (130 plugins, `tools/Scanner.java` +
`tools/ApiChecker.java`): no missing class, method or field against the desktop engine.

**Ultima sync "does not sync".** Not a desktop bug: the default database of the plugin (`cloudstream-ultima-sync-default-rtdb.firebaseio.com`)
no longer exists (HTTP 404 for every key). Sync works only with "Use Custom Firebase Database" + the user's own Firebase URL (the same one
the Android app uses) and the same sync key. `net/FirebaseHint` shows a toast once per 5 minutes when a `*.firebaseio.com` request answers 404.
(The "DataStoreHelper account reflection error" Ultima logs at start is its own and identical on Android.)

**Runtime differences found and fixed**
- *DNS over HTTPS deadlock* (`DohProviders.addGenericDns`): the resolver's asynchronous lookups shared the dispatcher of the main client;
  a burst of requests (an extension asking dozens of sites) filled all 64 slots with calls waiting for a lookup that could not start, so
  everything hung with Settings -> DNS != default. The resolver has its own dispatcher now.
- *Player DNS* (`net/NetProxy`): mpv resolved with the system resolver, so hosts a filtering DNS blocks played on Android and not here. mpv now
  goes through a loopback HTTP proxy (`http-proxy`; CONNECT tunnels, TLS stays between mpv and the server) that resolves with the app's DNS.
  `-Dcloudstream.noproxy=true` switches it off.
- *User agent*: mpv sent "libmpv"; ExoPlayer sends the app's `USER_AGENT` unless the link has one. Same now.
- *Addresses* (`net/UrlFix`): extensions hand out raw file names (spaces, `[ ]`); OkHttp encodes them, libcurl/mpv refused them ("loading failed").
- *HlsProxy* fetches playlists and keys with the app client (DNS, UA, trust) instead of `java.net.http`.
- *Certificate chains* (`net/AiaTrust`): sites that send leaf + intermediate but not the cross-signed link to a known root (Let's Encrypt's 2026
  "Root YE" hierarchy, e.g. DoFlix) failed with "Trust anchor not found" although browsers/curl open them. A failed validation is retried once with
  the missing issuers fetched from the certificate's "CA Issuers" address; the result must still chain to a root of the trust store (merged
  JVM + Windows roots). Expired / self-signed / untrusted-root certificates are still refused (checked against badssl.com).
- *Browsers started in turns* (`BrowserGate` in `JcefWebViewEngine`): a search over many providers started dozens of Chromium browsers at once
  (WebViewResolver, Cloudflare dialogs); all of them starved (DevTools calls timed out, the Cloudflare dialog stayed black). At most 5 start at a
  time; one that waits 20 s starts anyway so an extension that never destroys its WebView cannot block the rest.
- *Watch position* (`PlayerSession.saveProgress`): saved on every 250 ms tick (a disk write, and an upload for Ultima each time); now about
  every 5 s, at once after a seek, and flushed on pause, source switch, end and close.

**Test tools**
- `./gradlew :app:runLinkLab -Pjvm="-Dlinklab.order=mpvfirst" -Pargs="<dataDir> phisher,megix streamplay,cinestream 40 Inception|Breaking+Bad"`:
  collects real links of the chosen providers and probes each with an ExoPlayer-like request and with the real (headless) mpv; report and
  `-links.tsv` in the data dir. Other modes: `-Dlinklab.url=<address>` (one link), `-Dlinklab.get=<url,url>` (the app client with the error chain),
  `-Dlinklab.dns=<hosts>`, `-Dlinklab.mains=true` (provider main addresses), `-Dlinklab.crypto=true` (algorithm names extensions use).
  The data dir should have `dns_key=2` when the machine's own DNS filters (the user's is 1.1.1.2). Do not rebuild while it runs.
- `./gradlew :app:runHarness -Pargs="<dataDir> all full"`: every provider (home, search, load, links, playability probe). Its probe now reads only the
  first bytes (it downloaded whole videos and ran out of memory).
- `tools/StringScan.java`: crypto names in the extensions; Conscrypt + BouncyCastle cover all of them.

**Results (lab data dir, all repos installed)**
- StreamPlay ~127 links / 183 subtitles and CineStream ~261 links for the test titles; of 146 probed links mpv plays 92, and no link plays in
  ExoPlayer-style probing that mpv cannot (earlier ~70% before the DNS / UA / address fixes). The rest is source-side: single-use or expired
  tokens, Vidrock's segment CDN answers "domain forbidden", 4KHDHub workers answer 403 to any request without a byte range (Android has the same
  request), hosts that no longer exist.
- All 161 providers: 102 returned playable links. Of the others: dead domains (`UnknownHostException`), removed playlists (IPTV repos answer 404),
  login or settings required (Kartoons, CinemaCity, Jellyfin, StremioC, Ultima sections), geo-locked, hosts reset by the network (Einthusan, rezka,
  tamilultra: curl is reset too), sites that changed their markup, Cloudflare (needs the dialog, below). None failed for a missing class/method.
- In the app (lab data dir): StreamPlay: search -> details -> sources -> "Play now" -> playback in ~6 s with subtitles; CineStream: home, search,
  series details, S1E1 starts by itself once a preferred source is found (Allmovieland 1280x720, more sources keep loading). Cloudflare dialog
  (Zinkmovies) renders the challenge, stores `cf_clearance` and shows the site; Esc closes it (the extension reopens it for its next mirror).

**Known limits.** A search typed right after start, while 130 plugins are still loading, can show "No results". Searching all extensions with many
Cloudflare-protected ones opens one dialog per site, as on Android. The search box clears the "only in <extension>" scope together with its text.
Not tested here: real Firebase sync with the user's database, WebView-based extensions other than Zinkmovies/Cinemacity/TamilDhool.

## 17. Session 9 log (2026-10-02): start-up, flaky network, search, playback, notifications

User report: the app is "hangy" (initial load), search does not work in many extensions, remove Ultima's default warning, many videos
do not play, notifications must always be silent.

**Start-up (packaged exe 20 s to a window + 10 s freeze -> window after ~3.5 s, Home painted ~1 s later).** Measured, not guessed
(`-Dcloudstream.profile=<s>` samples the UI thread into `logs/startup-profile.txt`, `StartupProfile.mark` milestones in the log,
`HangWatchdog` now writes every UI freeze of 0.7 s+ to `logs/jank.txt` with the stack where it stood):
- jpackage's runtime has **no class-data-sharing archive** (`java -version` says "mixed mode" without "sharing"): JDK classes load about
  twice as slowly. `portableDist` now borrows the JDK's java.exe once (`-Xshare:dump`) to make `runtime/bin/server/classes.jsa`, then
  starts the app once on a throw-away data folder (port 8793), walks every screen and quits: the JVM writes `app/cloudstream.jsa`
  (AppCDS, `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=$APPDIR/cloudstream.jsa`; jpackage drops backslashes in java options, use
  `/`). If the jars change or the folder is copied without mtimes the JVM re-creates the archive at the next exit (one slow exit).
- Native JVM splash (`-splash:$APPDIR/splash.png`, `app/src/main/splash`, made by `tools/MakeSplash.java`) shows after 0.3 s and closes
  by itself when the window appears.
- Chromium (JCEF) starts 8 s after the app (libmpv is loaded 5 s after it), not at once: its message loop runs on the UI thread.
- `AppIcon` scaled the icon with `getScaledInstance` on the UI thread (1.7 s); now bicubic halving in a background thread.
- libmpv-2.dll (115 MB) was unpacked from the jar by JNA into the temp folder at every start (1.2 s freeze at the first Play); now
  unpacked once into `data/cache/natives` (`Mpv.unpackedNativesDir`). The duplicate `libmpv-2.dll` resource was removed (`mpv-2.dll` stays).
- What remains: two freezes of ~1-1.5 s while the window is created (Skiko D3D swap chain, first composition).

**Flaky network = "search does not work in many extensions" (found, fixed).**
- Cloudflare DoH (`dns_key=2`) returned CloudFront edges (3.175.86.x) of which some reset every connection on this network; OkHttp does
  not move to the next address after a reset in the TLS handshake, so ~45 % of all requests to such hosts failed and extensions
  swallow the error (empty search results). `net/ConnectionResilience.kt`: `Quarantine` (addresses that failed that way go last for
  10 min, learned from `EventListener.connectFailed`), `RetryQuickFailures` (GET/HEAD repeated up to 6 times when it failed within 4 s
  for such a reason). `net/UsableAddresses.kt`: no IPv6 addresses first on a machine without a global IPv6 address
  ("Network is unreachable" on every dual-stack CDN host). Result on this machine: TMDB 22/40 ok -> 40/40.
- `NetProxy` (mpv's tunnel): the first TLS flight is kept; if the server resets before answering, the connection is made to the next
  address and the same bytes are sent again (the player never notices), addresses are quarantined.
- **The whole app was on HTTP/1.1** (`Incorrect protocol: http/1.1` in the DoH logs): OkHttp chooses its platform once, and it was
  first touched before Conscrypt became the first TLS provider (Jdk9Platform: no ALPN). `main()` / `DesktopBootstrap.init*` now insert
  Conscrypt first (`/tlsinfo` dev endpoint shows platform and protocol: ConscryptPlatform, h2).
- `SearchViewModel.refreshRepos()`: the native Search page never called `reloadRepos`, so a search made after the view model existed
  but before all extensions had loaded only asked those; the page also repeats an empty search when more extensions have loaded
  (`SearchScreen`); a search has a 40 s default timeout (was 120 s) so the page stops "loading".
- `AiaTrust` fetches a missing issuer certificate up to 3 times and remembers a failure for 30 s (was 10 min).
- Dev endpoints: `/netcheck?url=a,b&n=` (app client, error kinds, protocol), `/searchreport?q=` (every extension, one line each),
  `/tlsinfo`, `/notify?title=&text=`.

**Playback.** `runLinkLab` on StreamPlay + CineStream (146 links): mpv plays 92 -> 98 (before -> after this session). Of the 48 that do
not: 28 are DahmerMovies answering 429 (rate limit), 7 MovieBox 429, the rest dead hosts, Vidrock segment CDN 403, geo locks. Other
providers (MovieBox, MovieBoxIN, Kisskh, AniVortex, Re:ANIME, CNC Verse's Netflix/Prime/Hotstar mirrors: 100 of 100 / 3 of 5 links play;
Netflix series answer an empty playlist ("verifyCheck: Waiting for your ads click") = the site, not the engine). libmpv is FFmpeg 8
(Lavc 63, dav1d, d3d11va): the codecs are not the problem. mpv opens plain files with its own libcurl stream, not ffmpeg's http.
- `player/RangeProxy.kt`: after a 400/403/405/406/416 when opening a plain video file (`MpvPlayer`, like the HLS "try through the
  playlist server" step) the file is played through a loopback server that fetches it in bounded 4 MiB ranges with the app's HTTP
  client, retrying a refused/ended chunk on a new connection and from the chunk's own start. Needed for the "Instant Download"
  workers of HubCloud/4KHDHub (403 to a plain GET, to `bytes=0-` and to HEAD; bounded ranges work, even then ~20 % are refused at
  random per connection). Verified: a 4K HEVC mkv of such a worker plays in 6 s.
- Not tested: torrents/magnets (TorrServer is downloaded on first use), real Widevine (impossible), live-event tokens.

**Silent notifications.** `platform/DesktopTrayNotifications.kt` was java.awt TrayIcon.displayMessage (plays the Windows sound). Now an
own tray icon through JNA (`Shell_NotifyIconW`, hidden top-level window with a message loop, TaskbarCreated handling, Open/Exit menu) and
every balloon carries `NIIF_NOSOUND`. Progress notifications stay in the app. Ultima's "default database is gone" toast
(`FirebaseHint`) was removed (the user's own database syncs).

**Env notes.** JNA `Native.load` of a DLL that is only inside a jar is slow at every start; `jar` DLLs should be unpacked once. The
Gradle `portableDist` training run uses port 8793 / ipc 52998 and `%TEMP%\cloudstream-cds-training`. Snapshotting `app/build/classes`
(+ `labcp.args`) lets a lab/harness JVM run while Gradle rebuilds. Scratch test sources of this session: NetTest*.java (OkHttp
experiments with the packaged jars), `shotseq.ps1` (PrintWindow captures of the starting exe), `wins.ps1` (windows of the process).

**Player loading and source failover (session 9, second part).** User: after "Play now" the loading screen vanished while the picture had
not started (nothing on screen for seconds), and when a source failed and the next one was tried the player ended up paused without any sign.
- Cause 1: `MpvPlayer` reported "playing" at `FILE_LOADED`, before the first picture (`PLAYBACK_RESTART`); the session then dropped the loading
  screen. Now the state is "opening" (buffering) until the first frame (`firstFrameLogged`), `loadPlayer` resets `fileLoaded`/`firstFrameLogged`/
  `isEnded` at once. Cause 2: `reloadPlayer` used `autoPlay = isPlaying` (false while opening/buffering) so a stall retry reloaded **paused**;
  now `userPaused` (set only by Pause / Toggle / dialogs) decides. Cause 3: a stream cut off early (RangeProxy gave up, a dropped connection)
  made mpv report end-of-file; with keep-open the player sat paused on the last frame ("ended"). `PlayerSession.endedTooEarly()` treats an end
  more than 25 s before the end of a 2 min+ video (or within 20 s of starting) as a lost connection: reconnect at the same position once, then
  the next source (`onPrematureEnd`).
- UI (`PlayerScreen`): the loading screen names the source ("Source 3 of 82", first line of its name), says why ("That source did not work.
  Trying the next one…", "The connection was lost. Reconnecting…", "Waiting for the source… 13 s"), has "Try next source" while a source is being
  opened (instead of the misleading "Play now"), "Stop looking" while waiting for more sources; a buffering ring (after 0.3 s) while playing; a
  round play badge when paused (also with hidden controls).
- If the source in use fails and no other exists yet but links are still being collected, the page waits (`waitForMoreSources`) and starts the
  best unfailed source when one arrives instead of showing a failure. A source that shows no picture after 20 s is skipped (was 35 s).
- Dev: `/player` (one line of page + mpv state), `/player?fail=1` (simulate a source error), `/player?end=1` (simulate end of file),
  `/nav?route=search&q=..&only=<provider>`. Seen in the packaged exe: the first (30 GB 4KHDHub) source opened but showed no picture, was skipped
  after 20 s with the new screen, the next source played.

## 17. Session 10 (2026-10-02): subtitles, start-up, lag

- **Stacked captions**: fixed in section 8's Captions note (`sub-clear-on-seek`).
- **OpenSubtitles "searches but does not play"** (2026-10-02, fixed 2026-10-03): the file behind a link of the new API (`www.opensubtitles.com/download/...`) is answered with an
  HTML "court order" page (airtel.in iframe) for some titles from India (new Indian releases), so mpv had no subtitle. The same subtitle is served by the older
  anonymous interface: `rest.opensubtitles.org/search/.../sublanguageid-eng` (ISO 639-2 code, other values get a redirect to host "_") and `dl.opensubtitles.org`.
  `OpenSubtitlesApi.getResources` now downloads the file itself, and on a web page asks the older interface (`OpenSubtitlesLegacy`: IMDb id from the result, then the
  name; same file name preferred, else the most downloaded of the language) and remembers for 30 min that the new one is blocked. The legacy client must use the
  system DNS and no proxy selector: the app's DNS over HTTPS client does not resolve those two hosts here. Dev: `/ossub?q=&lang=&n=` searches and applies a result,
  `/osdl` tries request variants, `/dns?host=`, `/mpv?name=&set=`, `/suburl?url=`, `/subfile?file=`.
- **Switching subtitles**: the user's pick is sticky (`subtitleChosenByUser`: the language based `autoSelectSubtitles` no longer takes it back); online subtitles are
  downloaded by the app once (cache by link, headers honoured) and mpv opens the file; a failed or vanished external track is retried after 15 s instead of
  never; a selection that cannot load says so. Could not be reproduced with embedded tracks, files, downloaded files or single-use links in the test instance.

- **Subtitle style** (`player/SubtitleStyler.kt`, `ui/screens/settings/SubtitleSettings.kt`): the engine's `SubtitlesScreen` preference list rendered with the Fluent
  list plus a live preview, as Settings > Player > Subtitles (hidden `Page.Subtitles`) and as "Customise..." in the player's subtitle settings. The style
  (`SaveCaptionStyle`) is mapped to mpv properties (font via `sub-fonts-dir` with the packaged fonts, size x2.2, colours, bold/italic, outline/shadow, background box,
  alignment, elevation -> `sub-margin-y`, remove captions -> `sub-filter-sdh`); uppercase, window colour, background radius and "remove bloat" have no mpv
  equivalent and are hidden. The colour picker has presets, opacity and hex (RRGGBB / AARRGGBB). Verified live: colour, bold, font, reset.
  Gotcha: the dialog layer shows only the newest dialog, so a card inside a dialog is left while a second dialog is open: callbacks of the second dialog must
  not use the card's `rememberCoroutineScope` (`prefScope` in FluentPreferences).
- **Login** (`ui/AppLoginDialog.kt`): a native dialog replaces the inflated Android layout for in-app logins (OpenSubtitles, SubDL...). The Accounts page used to freeze
  the window 0.6-3 s the first time: `WindowsHello.isAvailable()` runs PowerShell on the UI thread; it is now warmed 5 s after the start.
- **Start-up / lag** (measure with `-Dcloudstream.jank=250` and `logs/jank.txt`, `-Dcloudstream.profile=10`): plugin loading is limited to 3 at a time and starts after the
  first frame (`PluginLoadGate`), the update check waits 4 s more; Chromium (JCEF) starts on first use, not 8 s after launch (it takes the UI thread for ~2 s);
  mpv track lists are read by a worker, not on the UI thread (they blocked it 1 s+ while mpv was busy); the trust store and the resource index are read in
  parallel with the engine start; full-window `blur(40.dp)` backdrops became `SoftImage` (tiny decode, smooth scaling); Kotlin lambdas compile to classes
  (`-Xlambdas=class`) so the class-data-sharing archive covers them; the CDS training run also visits every settings page. What is left is the cold-JVM first
  frame (about 2 s: window + Direct3D device + first composition) and first use of text fields/dialogs.
- **Blurry settings**: 1 dp borders at 150% scaling were 1.5 px anti-aliased lines (every settings card has one); all `border(1.dp, ...)` are `Dp.Hairline` now.

## 18. Session 11 (2026-10-03): live DASH channels (JIO TV), subtitle size

- **JIO TV (PlayFy, CNC Verse) "endless loading, no sound"** (e.g. Pogo Hindi): a ClearKey DASH manifest with 15 representations (3 video, 12 audio: six languages x two
  bitrates). The picture started, then `applyPreferredAudio` selected the remembered audio language (Telugu from another film) in the open file. mpv answers a track switch
  with a "refresh seek" to a wall-clock time and ffmpeg's dash demuxer re-reads the manifest for every representation: the picture stood still for 45 s+ (first look: a
  native thread at 100% CPU, `time-pos=null`). The key handling was right (the extension looks the key up by the MPD's `default_KID` in `drmKeys`; peak meter of the
  Windows audio session shows real sound). Fixes: `MpvPlayer.setPreferredAudioTrack` on a DASH link reopens the stream with the track as a loadfile option
  (`loadfile url replace -1 aid=N`, ~5 s, `dashAudio` remembers it per link; a live manifest is told by `demuxer-start-time` > 1e9 and reopened without a start position),
  and `PlayerSession.applyPreferredAudio` leaves live channels on their own default audio. `demuxer-seekable-cache=no` does NOT stop the refresh seek (tried).
- **Subtitle size "changes only temporarily"**: the player's subtitle dialog Size slider was mpv `sub-scale` of the open video only. It now edits the saved style size
  (`PlayerSession.changeSubtitleSize`: same value as Settings > Subtitles > Font size 5..60, saved and sent to mpv 100 ms after the last change, "Reset" = 25).
- **Tools**: audio sessions of Windows (volume, mute, live peak per process) with a PowerShell COM script (scratchpad `audiosessions.ps1`; Core Audio `IAudioSessionManager2`):
  tells "no sound" apart from "silent stream"/"muted app" without ears. A `java.exe` dev process can have a muted mixer entry of its own (it did: mute, volume 0) while
  `CloudStream.exe` is fine; `/mpv?name=ao-mute` and `ao-volume` show the same. Test instances must never use the real sync settings: copy the data folder and point
  `firebaseUrl` in `ULTIMA_APP_SETTINGS_SYNC_CREDS` (rebuild_preference.xml) at a dead address first (a search in a test run pushed history to the user's cloud).

## 19. Session 12 (2026-10-03): repository clean-up, update checker, v0.1.0 installer

- **Repository** (github.com/lelouchcodes11/cs3d): pushed 2026-10-03 (commit e6df1a6 on top of the placeholder README commit); release v0.1.0 (pre-release) published with the MSI (SHA-256 3e1ea1bd...5b3c6). Build output, caches, `dist/` and the upstream checkout `reference/` are
  in `.gitignore`. The only file above GitHub's 100 MB limit, `app/src/main/resources/win32-x86-64/mpv-2.dll` (libmpv 0.41, 115 MB), is stored with Git LFS
  (`.gitattributes`; `git lfs install` before cloning/pushing). A second identical copy (`app/src/main/natives/windows-x64`, a dev convenience) was removed; a dev run unpacks
  the resource like the packaged app. No secrets or personal data in the sources (scanned: keys, tokens, sync URL, e-mail, local paths).
- **Versions**: `appVersion` (4.8.0) is the CloudStream engine version that extensions see (`BuildConfig.VERSION_NAME`); `desktopVersion` (0.1.0, `BuildConfig.DESKTOP_VERSION`) is
  this app's own release version: MSI `ProductVersion`, About page, update checker. Raise it for each release.
- **Update checker** (`desktop/update/AppUpdater.kt`, cards in `ui/screens/settings/UpdateCards.kt`, also the "Check for app updates" row of Updates & backup): GET
  `api.github.com/repos/lelouchcodes11/cs3d/releases` 12 s after start (at most every 6 h; pref `auto_update` switch; `skip` per tag), drafts ignored, pre-releases offered while the
  installed version is 0.x, newest tag > installed wins (404 = no releases yet = up to date). Installed copy: Fluent dialog "Install update" downloads the release's `.msi`
  (size and the asset's `digest` sha256 checked, a wrong file is deleted), writes `%TEMP%\cloudstream-update\cloudstream-update.cmd` (msiexec /i /passive, then start the exe again),
  runs it apart from the app and closes the app (halt after 10 s). Portable copy (`portable.txt` or `data` next to the exe, or no `jpackage.app-path`): "Open download page" only.
  Test hooks: `-Dcloudstream.updateApi=<url returning the releases JSON>` and `-Dcloudstream.updateRepo=owner/name`. Tested against a mock API (scratchpad `MockGithub.java`)
  with the packaged non-portable app: dialog, progress, download, checksum mismatch, helper script, msiexec start, relaunch. NOT tested: a real upgrade over an installed MSI.
- **Installer** `./gradlew :app:installerMsi` -> `dist/release/CloudStream-<ver>-windows-x64.msi` (358 MB): the createDistributable image + splash + start-up training
  (`trainStartupArchive`, shared with `portableDist`) packed by `jpackage --type msi --app-image`: per user (no admin, %LOCALAPPDATA%\CloudStream), start menu + desktop shortcut,
  folder chooser, same upgrade UUID every release (upgrades older, refuses downgrades). Needs WiX Toolset 3.x (candle/light) on PATH: not installed on this PC, the 3.14 binaries
  were unzipped into the session scratchpad only (`wix314-binaries.zip`, github.com/wixtoolset/wix3 release wix3141rtm) and put on PATH for the build (use a /c/... path in bash, a
  `C:/` entry breaks the colon separated PATH). The training run's `app/cloudstream.jsa` stays valid after the install location differs (checked with `-Xlog:cds`).
  `msiexec /a` (administrative extract) fails with 1603 for this per-user MSI: test the packaged app from `app/build/installer/CloudStream` instead.
- **Gotchas**: a few `build/` folders were left with files that Windows would not let anyone delete (access denied even to takeown, ACL of another sandbox user); the folders were
  moved out of the project to `%TEMP%\cs-stale-build`. Do not kill processes by image name (`java.exe`, `CloudStream.exe`) while the user may run the app: filter by path.
