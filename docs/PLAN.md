# CloudStream Desktop — Plan and Handoff

This is the only doc. Read it fully before changing code. At the end of every session update section 6
("Status") and section 5 ("Work packages": tick items, add what you learned). Keep it short: replace stale
text, don't append logs.

Last updated: 2026-10-03 (session 14, second round: see section 21b).

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
- **User customisation** (session 14, `ui/fluent/Appearance.kt`, Settings > Appearance > Layout and style): navigation position (left/right rail, top tabs, bottom dock),
  rail style, corner radius 0-28 (FluentShapes.control = r/2, card = r, overlay = 1.2 r, small = 0.7 r; default 12), poster size, spacing, backdrop (Ambient artwork
  colours / Solid / OLED black), see-through bars, interface size 80-130 % (`ScaledContent`, not the caption buttons), animations (Full/Reduced/Off via `FluentMotion`),
  hover zoom, startup animation, player controls (Modern floating glass bar / Classic). All Compose state, saved under `desktop_look_*` prefs. Never hard-code a radius
  for cards/controls: use `FluentShapes`.
- **Shape/spacing/motion** (before session 14): controls 4 dp, cards/flyouts/dialogs 8 dp; 4/8/12/16/24/32 spacing; 83 ms
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

## 20. Session 13 (2026-10-03): live DASH channels looped two seconds ("JIO TV videos play in a 2 sec loop after some time")

- **Cause** (measured with ffmpeg's demuxer log, `-Dcloudstream.mpvlog=v` plus `msg-level=ffmpeg/demuxer=v`): JIO TV's manifest (Broadpeak packager, `type=dynamic`, SegmentTimeline with
  `$Time$`, `timeShiftBufferDepth` 60 s, `minimumUpdatePeriod` 2 s) is cached by the CDN and runs one or two segments behind what the CDN already serves (a segment is there ~2 s before the
  manifest lists it; one that is not there yet is a 404). ffmpeg's `dashdec.c` keeps a position into the list: at the live edge it asks for the "next" segment, which maps to the end of the
  stale list, so it gets that segment (valid), then asks for the next position, which maps to the *same* time again, and so on until the manifest has caught up: the same audio (and video)
  segment 2 to 7 times in a row (log: `new fragment: min[0] max[29]` with an identical `DASH request for url` each time; mpv: `Invalid audio PTS: 122.005 -> 120.000` every 2 s,
  `Audio/Video desynchronisation detected`, playback speed 0.7x). It starts when the demuxer has caught up with the live edge (the first ~20 s read the 60 s backlog at full speed),
  hence "after some time". The player starts 60 s behind live: ffmpeg's start position for a dynamic timeline is "60 seconds before the end".
- **Fix** `player/DashProxy.kt` (loopback server like `HlsProxy`/`RangeProxy`, used for every DASH link; `MpvPlayer.loadPlayer`, with the same "try the other way once" fallback after an error):
  manifest and segments (addressed relative to it) are fetched with the app's client and the link's headers and passed on unchanged (status, Range, content type, retry on a new connection);
  for a live manifest a segment (its address ends in a number of 6+ digits) that was just sent completely is not sent again: the repeat gets a `404` after 250 ms, ffmpeg reads the manifest
  again and goes on to the next position, until the real next segment is asked for (a not yet published segment also gets a short pause before its 404, ffmpeg would spin). Every segment
  is delivered once. Static (on-demand) manifests are passed through.
- **Checked** (dev instance, copy of the data folder with a dead sync address, JIO TV > Pogo Hindi): before, glitches from ~1 min after the open (`Invalid audio PTS` back by 2 s, playback speed
  0.7-0.8x for a minute or more); with the proxy 12+ min with no backward jump, speed exactly 1.00x, A/V sync 0.00001 s; pause 80 s and resume (plays the cache, then continues live; the
  ~25 s forward jumps in the log are the live content that was not kept); audio language switch (`A` key reopens the stream with `aid`) works; on-demand DASH (Akamai bbb_30fps.mpd, 4K h264)
  plays through the proxy (`runLinkLab -Pjvm="-Dlinklab.url=..."`). NOT tested: other live DASH channels (Cricify, SKTech), a manifest with an absolute `BaseURL` (its segments bypass the proxy
  and are not protected), pauses longer than a few minutes.
- **Tools**: ffmpeg's own messages (`DASH request for url`, `old fragment`, `new fragment`, `Failed to open fragment`) only show with `msg-level` set for the module; the quickest way is a temporary
  `mpv_set_option_string(ctx, "msg-level", "all=no,ffmpeg/demuxer=v,lavf=v,ad=v,cplayer=v,curl=v")` before `mpv_initialize` (no video-decoder trace: `ffmpeg=trace` writes 20 MB/min). `dashdec.c` of the
  bundled libavformat 63.7 matches FFmpeg master (github.com/FFmpeg/FFmpeg, libavformat/dashdec.c: `get_current_fragment`, `refresh_manifest`, `read_data`).
- **Release v0.1.1** (pre-release, tag on d25e040): `desktopVersion` 0.1.1, `installerMsi` with the WiX 3.14 binaries from the session-11 scratchpad (4 min), MSI 359 MB, SHA-256 4649949e...c7311,
  ProductVersion 0.1.1 with the same upgrade code, `DashProxy` in the app jar; published through the GitHub API (draft, upload, size/digest check, publish) like 0.1.0. The packaged app was
  not started with JIO TV (the build's start-up training run starts it and visits every screen; the runtime has `jdk.httpserver`). Real upgrade 0.1.0 -> 0.1.1 over an installed MSI still NOT tested.

## 21. Session 14 (2026-10-03): live channels drifting / slow motion after a seek, UI overhaul

- **User report**: "live streams most of them not working, some play but after some time audio/video out of sync, video slow mo"; then "when WILLOW is played and seeked
  1 min further the problem occurs". Early reviews: "UI too basic, player too basic; want a modern rich UI, customisable (navbar position, corner radius), smooth start
  animation, buttery smooth".
- **Live lab** (`runLinkLab -Pjvm="-Dlinklab.live=<s> [-Dlinklab.seek=60] [-Dlinklab.clamp=1] [-Dlinklab.linkname=WILLOW] [-Dlinklab.parallel=1]" -Pargs="<dataDir> cnc
  cricify,livxow,playfy,playztv,sktech,sportzx <channels>"`): channels from the providers' main pages, each played headless for <s> seconds, reports speed of the media clock,
  backward jumps, stalls, max A/V sync. `-Dlinklab.url=... -Dlinklab.live=60` probes one address. Result for the 6 live plugins of CNC Verse: every reachable stream played
  at 1.00x; failures were the source's: Paramount+ / TNT nitro edges geo-blocked (403/400 with plain curl too), dead domain (NXDOMAIN), DaddyLive-type 403 (curl too),
  placeholder links (`url=Ok`, `https://no.link`), SK LIVE = a 13 s VOD placeholder clip.
- **Cause of the drift** (WILLOW, aiv-cdn MediaPackage, SegmentTimeline + `$Number$`, 2 s segments): ffmpeg's dash demuxer cannot seek live DASH (`dash_read_seek` returns
  ENOSYS when `is_live`), so mpv only seeks inside its cache; +60 s on a 20-40 s cache landed past it and the demuxer hung for good. Clamped to the reported duration (= cache
  end) it played at the live edge, where ffmpeg asked for segments the manifest did not list yet: 404, it skipped ahead (`cur_seq_no++`), then `refresh_manifest` mapped it
  back and the audio got segments it had played already (`Invalid audio PTS 109.96 -> 107.98`, A/V desync 1.9 s, picture slowed down to meet the sound). Asking the CDN early
  also makes it cache the 404 for seconds (segments then took 3-7 s each).
- **Fixes**: `player/DashTimeline.kt` parses the live manifest's SegmentTemplates (`$Number$`/`$Time$`, `%0Nd`, `$RepresentationID$`, `$Bandwidth$`; SegmentTimeline -> newest
  listed segment, `duration` templates -> from availabilityStartTime and the server clock of the `Date` header). `DashProxy` holds a request for a segment the manifest does not
  list yet (re-reads the manifest at most once a second, up to 3 segment lengths + 4 s) instead of passing the CDN's 404; the repeat guard now also keys on the matched stream
  (WILLOW's 4-digit numbers were missed by the old 6-digit rule); segment reads time out after 6 s (fresh-connection retries fit inside mpv's 15 s). `MpvPlayer`: `liveStream`
  (DashProxy/HlsProxy say so, or a wall-clock start time), seeks of a live stream are kept inside the buffered range and 6 s short of its end (`liveTarget`, from
  `demuxer-cache-state` seekable-ranges), `goLive()`, `liveLatency()`, and a watchdog that reopens a live stream that waited 12 s for data. Result (headless 3 min and the real
  window): +60 s -> clamped to 107 s of a 2..113 s buffer, 25 fps, 0 drops, A/V sync ~1e-5 s, speed 1.00, no backward jumps, one repeat refused.
- **Render check**: real window 1922x1130 and full screen 1920x1200, 1080p25: 25.0 fps delivered, ~3-5 ms CPU per frame on the render thread, no drops
  (`-Dcloudstream.videostats=1` logs `VideoSurface:` lines; dev `/videostats`). 50/60 fps channels were not available to test.
- **UI** (all verified with screenshots in the dev instance): `Appearance` + `FluentMotion` (see 2.1); `ui/Startup.kt` animated start (logo, sweeping accent ring, name, progress
  line while extensions load; the app composes underneath and fades/settles in; ~1.1 s, max ~2.4 s); `AppShell` rewritten for the four nav positions (top tabs live in the
  title bar band and are `noWindowDrag`: `/hit` reports CLIENT on tabs, CAPTION between them), ambient backdrop (`AmbientArtwork(url)` from Home hero and Details, tiny decode,
  scrim), translucent page layer, smoother page transitions, animated toasts, accent nav indicator; Home hero with Ken Burns zoom, fade-to-page mask, chips, pill buttons,
  progress dots; poster cards zoom + shadow + rising accent play button; Details header the same way; player: `PlayerChrome` (top gradient with glass back button and
  source chips; Modern = floating frosted-glass bar: the video frame under it drawn again through `Modifier.blur`; Classic = full width), seek bar that thickens with a glow,
  thumb and time/stamp bubble, accent play disc, volume slider that slides out, LIVE pill (red when at live, "LIVE -32 s" + click = go live), artwork behind the loading screen.
  Dev: `/look?nav=Top&radius=20&backdrop=Solid&player=Classic&scale=1.2`, `/playlive?provider=livxow&link=WILLOW`.
- **Gotchas**: Skia `drawImageRect(image, src, dst, sampling, paint, strict)` drew nothing in a Compose layer (also with a blur image filter or saveLayer); the 2-argument
  `drawImageRect(image, dst)` works, and the blur comes from `Modifier.blur`. Reading an animation value in composition re-composes every frame (hero progress, startup):
  read it inside `graphicsLayer {}` / `drawBehind {}`. The skiko FPS counter (`-Dskiko.fps.enabled=true`) counts idle gaps as long frames; the jank watcher
  (`-Dcloudstream.jank=45`) showed no UI-thread stall >= 45 ms while scrolling and switching pages.
- **Not done / not tested**: real-mouse clicks on the top tabs (another window covered the dev window; the hit test was checked with `/hit`), 50/60 fps live channels,
  a release build (MSI) with these changes.

### 21b. Session 14, second round (2026-10-03): full revamp, start-up, logins, external players

- **Revamp** (verified by screenshots): `ui/fluent/Rich.kt` (PageHeader, RichSectionHeader, PillTabs, EmptyState, ResumeCard, RankedPoster, FeaturedStrip, PillButton, ArtChip, `glass()`), deeper palette (`bg #0E0F13`), brand
  mark in the rail, Home (hero with thumbnail strip, landscape Continue watching, ranked rows), Details (tabs Episodes / More like this / Cast, episode grid with stills or the show artwork, cast cards),
  Search landing (big field, recent chips, type tiles), Library (pill tabs with counts), Extensions (stat tiles, repo list, plugin cards), Settings (coloured category chips), Downloads, Setup, dialogs (rise + shadow).
  Default navigation is **Top**. Never put `shadow()` / `shadowElevation` under a translucent background: the shadow shows through as a grey tab (that was the "grey tab" on repos and extensions).
- **Window bar** (reworked the same day): ONE full-width opaque title bar (`ui/shell/Caption.kt` `TitleBarRow`: Back, logo, title at the left, min/max/close at the right, 36 dp). A window that is not maximized has it as a row of its own
  ABOVE the app (`ExternalTitleBar` in `NativeApp`, the app starts below it, Back is hidden from the app bars then: `WinChrome.windowedBar`). A maximized window hides it (`WinChrome.autoHides`, `captionHidden`, `pollReveal()` polls the pointer
  every 20 ms): the pointer at the top edge shows it over the app (`RevealedTitleBar`), and it is hidden at once when the pointer is off it. Full screen and PiP have none. `captionInset` is always 0. Dev: `/maximize?on=0|1`, `/pointer?x=900&y=2` pretends the pointer (the real one cannot be used when another program covers the window), `/pointer?off=1`.
- **Start-up**: one loader (`ui/Startup.kt`: logo from the native splash, which is now only the logo on transparent, a slim line, one status word). The app is composed underneath and shown when: local extensions loaded,
  app update check done (3 s cap, started at once), the extension update waited for at most 1.2 s, and **no frame took > 90 ms for 0.6 s** (the "hand"); never later than 3 s after the window. Home content is NOT waited for
  (it depends on the network; the first version waited 15 s for it). The update dialog and the profile picker wait for `Startup.revealed`. Before: 19 s to the first revealed frame in the packaged exe.
- **Logins**: the root cause of the AniList / MAL / Simkl failures: `WindowsProtocols` ran `reg add ... /d "\"exe\" \"%1\""`; Java does not escape the inner quotes, `reg` said "Invalid syntax", only the empty scheme keys
  were written and Windows had no program for `cloudstreamapp://`. Now written with JNA `Advapi32Util` and read back (logged). Verified: a second launch with a `cloudstreamapp://anilistlogin#...` link reaches the running
  window (`handleAppIntent`). All account services answer from the app's HTTP client. FebBox (StreamPlay / CineStream): `JcefRuntime.allCookies` on the UI thread returned the last snapshot, so `getCookie` in
  `onPageFinished` did not see the cookie the page had just set; now a fresh read (700 ms cap) and a cookie refresh before `onPageFinished` is delivered (`refreshCookiesThen`); dev `/cookietest`. Google sign-in itself is untested.
- **External players** (`player/ExternalPlayers.kt`): only VLC (found by registry / Program Files / PATH; referer + user agent as VLC options, other headers through RangeProxy / HlsProxy, first downloadable subtitle as
  `--sub-file`, `--start-time`) and the browser. `VideoClickActionHolder` lists only `PlayInBrowserAction` and `DesktopVlcAction` (episode menu, default player setting); the player has an "Open in another player" button.
  VLC cannot play DASH or protected links: it says so. Option syntax checked against the installed VLC headless.
- **Player**: seek bar flat (no glow), plain dark control bar (the frosted copy of the video is gone), subtitles rise above the controls (`PlayerSession.liftSubtitles`, mpv `sub-margin-y`, bottom-aligned only),
  subtitle warning toasts removed (log only), a source that was switched to after a failure is no longer left paused (`MpvPlayer.ensurePlaying`: mpv's keep-open pause after the failed file landed after our "play").
  Glitches every 2 to 3 s on WILLOW: NOT reproduced (25.0 fps, gaps <= 59 ms, A/V sync 1e-5, no underruns, no GC pause > 2 ms, also at the live edge); init segments have no edit list. Added a 0.4 s audio buffer and a higher
  priority for the frame thread; `-Dcloudstream.videostats=1` logs frame gaps (`late(>56 ms)`).
- **Settings**: removed what does nothing here (gestures, Android TV, player layout, links, bananas, seek preview, software decoding, Test extensions, logcat, cast panel, metadata overlay, clock, random button, poster toggles,
  ExoPlayer cache/buffer size, resize/speed button toggles, start paused). The file settings (download path, backup folder, restore) work: they open native dialogs, which `PrintWindow` does not capture.
- **Torrents removed**: providers with only torrent types are not listed (`filterProviderByPreferredMedia`), TORRENT / MAGNET links are not offered (`LOADTYPE_INAPP`), no Torrent type in Search, Setup or Preferred media.
- **Search scope**: a search started from Home (one extension) keeps that scope on the Search page (`Route.Search.only`) until "Search all extensions".
- **Gotchas**: `perl -pi` with `\x{...}` in the replacement re-encodes the whole file (mojibake in every non-ASCII line; repaired with a Java program); `\u` escapes written through perl or the Write tool lose the backslash:
  write the glyph with `printf '\xee\xa4\xa1'`; a native `FileDialog` is invisible to `/screenshot`; backups write into the real Downloads folder (a test run left one, deleted).

### 21c. Session 14, third round (2026-10-03): VLC launch, search, Continue watching, player controls
- **VLC did not launch** when it was the preferred player: `VideoClickActionHolder.allVideoClickActions` still held the Android app packages (the earlier registry edit never reached the file), so `DesktopVlcAction` was not in it and `getPlayerAction` fell back to the in-app player. The list is now `PlayInBrowserAction` + `DesktopVlcAction` only; Settings > Player > Preferred video player offers exactly Internal player, Play in Browser, Play in VLC. Checked end to end in the dev instance: preferred = VLC, Continue watching card, "Finding sources", link list ("Play in VLC"), pick one -> `vlc.exe` starts with the referer / user agent / title options. Dev endpoints `/pref` (also prints the resolved action and the registry) and `/vlctest`.
- **Search** (`SearchViewModel`, `SearchScreen`): each extension is published as soon as it answers (a copy of the map is posted, the old code posted the same mutable map, which Compose never saw change until the end); extensions without results are not kept; `progress` (pending names, total) drives the page: a placeholder row (name + "searching...") for up to 3 extensions still running and "N more extensions still searching", header says "Searched 22 of 24 extensions" with a determinate bar in a fixed 3 dp slot (no layout jump when it ends); rows appear in the order extensions finish and never move (fade in once, no reorder); "All results" merges what has arrived; scroll position resets for every new search. The landing page is one flat search box, the kinds of title as chips, and "Recent" as plain rows (hover only changes the colour, the remove button has a reserved slot): no gradient tiles, no glow, no hover reflow.
- **Continue watching**: plain poster cards like every other row (progress bar along the bottom, hover play button), the line below reads "S1 · E3 · 40 min left" ("26 min left" for a film). `ResumeCard` (blurred landscape card) deleted.
- **Player controls** (`PlayerScreen.kt`): soft eased shades at the top and bottom instead of the dark rounded bar; one seek row with elapsed time, the bar and the length; buttons 40 dp with 18-20 dp glyphs; named pills "Sources" (sources and subtitles dialog) and "Tracks" (audio and video tracks, only when there is a choice) replace the CC and audio icons (icon only below 900 dp width); source name and resolution sit quietly at the top right; Classic style keeps a solid band. Dev endpoint `/playurl?url=&name=` plays any link to look at the controls.
- **WILLOW glitches, again not reproduced**: render thread 25.0 fps with no late frame, `frame-drop-count` 0, A/V sync 1e-5, and the Windows audio session peak meter sampled at 350 Hz for 40 s showed zero silent samples (no dropouts). Not ruled out: judder between 25 fps video and a 60 Hz window (software render path presents on the next vsync). `/mpvsample` (frame counter / audio clock sampler) exists but `estimated-frame-number` follows time-pos, so its "gaps" are not real hitches.
- Gotchas: `sed 'c\'` with several lines joins them on one line; the shell tool chokes on a heredoc with curly quotes (write such files with the Write tool).

### 21d. Session 14, fourth round (2026-10-03): sign-in keys and syncing, player buttons at the top right, small defaults
- **Account sign-in failed because of missing API clients, not the browser** (`client_id=null`, "Client authentication failed" from AniList): `app/build.gradle.kts` writes `SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET`, `MAL_KEY` and `ANILIST_KEY` into `BuildConfig` from env vars / `local.properties` and the text `"null"` when neither exists, so every sign-in page and every Simkl call (`simkl-api-key`, `client_id`) carried `null`, in any browser. The upstream app's keys are secrets of its release pipeline and cannot be used here. Now `syncproviders/ApiKeys.kt` holds the keys at run time (the user's, stored in the DataStore folder `sign_in_clients`; the build-time value is only a fallback), the three providers read it per call (`AniListApi.key`, `MALApi.key`, `SimklApi.CLIENT_ID/CLIENT_SECRET` are getters), and **Settings > Accounts & security > Sign-in keys** (`ui/ApiKeysDialog.kt`) lets the user paste the IDs with the steps, the exact redirect to register (`cloudstreamapp://anilistlogin`, `cloudstreamapp://mallogin`, `cloudstreamapp://simkl`), "Open developer page" and "Copy redirect". Clicking an account that has no key opens that dialog (`SettingsAccount.addAccount`, `AuthRepo.openOAuth2Page`) instead of a page that cannot work. Simkl's PIN sign-in needs only the ID; its secret is only for the browser flow. NOT tested against the real services (no keys of mine, and the user asked for no app testing); it compiles.
- **The in-app sign-in window was removed again** (it was a workaround for a problem it could not fix; `ui/SignInWindow.kt` and its dev endpoints are gone): sign-in is the system browser plus the `cloudstreamapp://` link, which `WindowsProtocols` registers with JNA at start-up and forwards to the running window. Lesson kept: a WINDOWED JCEF browser inside a Swing dialog is dead here (its native windows belong to the AWT event thread, which never pumps Windows messages: it paints once and ignores all input); any visible browser must use offscreen rendering (`createBrowser(url, true, false)`).
- **Syncing, not only signing in** (found by reading the code, upstream `GeneratorPlayer` / `ResultFragmentPhone` against the native pages): (1) the player never told its `SyncViewModel` which AniList / MAL / Simkl ids the title has (`sync.addSyncs(syncData)` was missing, `Route.Player.syncData` was carried but unused), so `modifyMaxEpisode` at 90 % had nothing to report to: `PlayerSession` now takes `syncData` and registers it; (2) the Details page only called `addFromUrl`: it now registers `page.syncData` too (`addSyncs` + `updateMetaAndUser`); (3) the native Details page had no way to change list status / score / episodes on the services (the Android "sync" card): new `TrackerCard` under the header (status, score 1 to 10, episodes -/+ and Save = `publishUserData`), shown only when the title is known to a service the user is signed in to. The model changes its status object in place and posts the same object again, so the card listens with its own observers instead of `observeAsState`. Library lists of the three services come from the unchanged `LibraryViewModel`. Not exercised against live accounts.
- **Player**: "Sources" and "Tracks" are text-only pills at the top right (Tracks only when there is an audio / video choice); the source name and picture size chips sit under the title; the bottom row keeps only the playback buttons.
- **Home ranked rows**: the accent glow around the big rank numbers is gone (flat outline).
- **Subtitle defaults**: size 17 (`DEFAULT_SUBTITLE_SIZE`, also the fallback everywhere and what Reset restores) and edge = drop shadow. Saved styles are untouched (the real data already had size 17 / shadow).
- **Top bar reveal** (maximized window): the trigger strip at the top edge is 2 dp instead of 4 and the pointer has to stay there 140 ms (`REVEAL_DWELL_MS`); hiding is still immediate. Checked with `/pointer`: y=4 px does not reveal, y=1 px does after the dwell.
- Gotchas: a killed Gradle build left a truncated `app-<hash>.jar` in `app/build/compose/binaries/main/app` and the next `createDistributable` called it UP-TO-DATE (the packaged exe then died with "Could not find or load main class"): delete that folder and rebuild; never `./gradlew --stop` while `portableDist` runs; the Bash tool's working directory inside `dist/CloudStream-Portable/...` makes `portableDist` fail with "Unable to delete directory"; the dev endpoint `/maximize?on=0` can hang the dev server thread (cross-thread `ShowWindow`), kill that test instance by PID.
- **"Authorise" did not bring the user back to the app** (reported after the keys worked): the return trip was a `cloudstreamapp://` link that the browser hands to Windows and Windows to a second copy of the app (browser prompt, registry entry, IPC to the first copy, window raise): every step can fail silently, and the log of a normal run shows none of it. Now `desktop/net/OAuthCallback.kt` listens on `http://localhost:52526/<service>` (127.0.0.1 and ::1, only armed for 15 minutes after a sign-in was started in `AuthRepo.openOAuth2Page`): the redirect lands on a small page of the app, its script posts `search` and `hash` (AniList's token is after the `#`, a server never sees it) to `/_cb`, which builds the same `cloudstreamapp://<service>/#...` / `?code=...` string the old link had and hands it to `NativeLinks.open` (the unchanged login code), then raises the window. Guards: Host must be localhost / 127.0.0.1 / [::1] on that port (DNS rebinding), a foreign `Origin` is refused, nothing is accepted unless a sign-in is armed, one use per sign-in. Simkl sends the loopback address as `redirect_uri` (authorize, token and PIN calls); AniList and MAL use the one registered, so users register `http://localhost:52526/anilistlogin`, `/mallogin`, `/simkl` (the dialog shows them). The old link keeps working for clients registered with `cloudstreamapp://...`. Compiled, not run.
- **Built-in keys for everyone**: client IDs are public identifiers (upstream ships them in every APK); put them in `local.properties` (git-ignored: `anilist.key`, `mal.key`, `simkl.id`) and every build, including the installer, has them as the fallback of `ApiKeys`; users can still override in Sign-in keys. Secrets are not needed: AniList implicit grant and MAL (App Type "other", PKCE) have none, Simkl uses the PIN flow (ID only); a secret in a shipped binary can be read by anyone, keep it out of the repo.

## 22. Release 0.1.3 (2026-10-03)

- Published as a **pre-release** (tag `v0.1.3` on `691cf38`, `CloudStream-0.1.3-windows-x64.msi`, 377 MB, sha256 `59da4247...c35077`) through the GitHub API with the token from `git credential fill` (draft, upload with curl, publish with `make_latest=false`; script `release013.ps1` in the session scratchpad). WiX 3.14 came from an earlier session's scratch folder (`wix/bin` on PATH for `./gradlew :app:installerMsi`). `:app:test` passed in the same build.
- Pre-releases are offered by the update checker while the installed version is 0.x, so 0.1.1 installs should see it; the README tells people on 0.1.0 / 0.1.1 to install the new MSI once if they do not (the repository owner's own GitHub edit said the older builds do not detect updates: not verified against the published binaries).
- The update checker now also runs while the app stays open (`AppUpdater.keepCheckingWhileOpen`: every 30 minutes it checks whether 6 hours passed since the last check; a version is offered once per run, never when skipped, and not while the Player page is open). Before, it ran only a few seconds after the start.
- README rewritten short and simple, centred on the Telegram link (the owner had edited the README on GitHub, "new pre-release soon": replaced, rebase conflict resolved in favour of the new file). Changelog entry 0.1.3. No keys of the tracker services are built in: users enter their own (Sign-in keys), the owner chose not to ship any.
- Not done / untested: a real upgrade from an installed 0.1.1 through the in-app updater, AniList / MyAnimeList / Simkl sign-in against the live services with real keys, the loopback return page in a real browser.

## 23. Session 16 (2026-10-04): subtitles that did not play, a loading pill, one look for every subtitle

- **"StreamPlay subtitles mostly do not play"**: `MpvPlayer.syncSubtitles` fetched EVERY external subtitle of the source (StreamPlay lists ~180) on the 4 workers, and ran again on every update of the list, so the subtitle the viewer (or the language auto-pick) wanted waited in that queue (each fetch up to 15 s, duplicates blocked a worker up to 70 s). Now only the wanted subtitle is fetched (when it is picked), stale work is dropped (`fileGeneration`, `preferredSubtitle != sub`), and the file opens ~0.4 s after the video (measured on StreamPlay "Inception": file opened, `sub-add` 0.3 s later).
- **Dead subtitles are skipped**: `MpvPlayer` posts `SubtitleLoadEvent` (Loading / Loaded / Failed); `PlayerSession.onSubtitleLoad` keeps a `failedSubtitles` set and, for an AUTOMATIC choice, tries the next subtitle of the language (up to 8); what the viewer picked is never replaced, only reported. `loadLink` now stores the auto pick in `selectedSubtitle` (it did not), and `autoSelectSubtitles` keeps a working pick instead of restarting it whenever more subtitles arrive. Tested with a local server (`dead` 404 -> `html` block page -> `slow` 4 s -> loaded).
- **Indicator** (`PlayerScreen.SubtitlePill`, top right, under the Sources pill, independent of the controls): ring + "Loading subtitles · name" (shown after 0.3 s, so quick loads do not flicker), green check "Subtitles on · name" for 2 s, warning "Couldn't load subtitles · name" for 5 s, "Looking for subtitles…" while sources are still being collected and none shows (not over the full-screen loader). Timers are keyed on the status OBJECT (keying on its text left a pill on screen when two events carried the same words).
- **One look for every subtitle** (`SubtitleStyler`): `sub-ass-override=force` + `sub-ass-style-overrides=Bold,Italic,Alignment` make ASS/SSA (files and embedded tracks) follow the saved style's font, size, colours, edge, weight and position like SRT/WebVTT did (an ASS file with yellow 90 px bold top-aligned blue-outlined text now renders pixel-identical to the SRT line; measured with `/addsub` + screenshots). Typesetting signs in anime are restyled too (the price of "common"); bitmap subtitles (PGS/VobSub) cannot be styled.
- **The drop shadow was invisible**: libass has ONE back colour and mpv's `sub-shadow-color` is the same setting as `sub-back-color`; the styler set the shadow colour and then the (transparent) background colour right after, wiping it. The back colour is now the edge colour unless a background box is wanted. A saved style whose edge is "outline" (the old default) is switched to the shadow once (`SHADOW_DEFAULT_KEY` in `SubtitlesFragment.loadSavedStyle`); a later explicit choice stays.
- Test tooling: dev endpoint `/subdeliver?urls=a%7Cb&names=English%200%7CEnglish%201&lang=en` (subtitles arriving from an "extension" while playing, left to the automatic choice). Test data = scratchpad COPY of `dist/CloudStream-Portable/data` (shared_prefs, files, code_cache; Ultima firebaseUrl replaced by `http://127.0.0.1:9/`); a hard kill shortly after start sets the app's safe mode flag in the copy (re-copy `shared_prefs` + `files`).

### 23b. Session 16, second round (2026-10-04): "subtitle lift and video are hangy", source fallback order

- **Measured, not guessed**: `MpvSurfaceView.present` (`PresentStats`, `/videostats` line `present:`) times what the window does with a finished frame: publish -> draw latency, frames dropped before drawing, gaps between drawn frames. Clean playback: 25 draws/s, 0.8 ms, no late frames. With the loading RING of the subtitle pill animating over the video: 6.9 ms avg and 6 late frames (>56 ms, max 84 ms) in 5 s; with the subtitle lift (8 `sub-margin-y` changes per controls show/hide): a late frame of 78 ms. mpv itself (`/mpvsample`) was fine in all cases (4K HEVC, d3d11va-copy, 24 fps, no gaps), so the hitching is the UI redraw competing with the video draw. **Rule: nothing may animate continuously (rings, pulses) over the video while it plays**; my "Looking for subtitles…" ring ran for as long as sources were still loading, which made everything feel hangy.
- **Removed**: the subtitle lift (`liftSubtitles`, `SubtitleStyler.extraMarginY`; subtitles stay at their margin and the controls draw over them), the pill's ring and fades (static glyph, appears / disappears at once), the ring in the Sources dialog header (text "more loading…"). After: no late frames while toggling controls with an ASS subtitle, nor during a subtitle load. `LivePill` still pulses (live channels, only while the controls show).
- **Source fallback**: a picked source that fails no longer sends the player straight back to the one that was playing. `PlayerSession.backToFallback`: the sources AFTER the picked one are tried in order (`nextLink` skips `fallbackLink`), and only when none plays does it go back to the one that played before. A pick made while an earlier pick is still loading keeps the source that really played. Dev endpoints `/playurls?urls=a%7Cb&names=..` (several sources) and `/pick?n=` (choose one) tested: [good, dead, dead, good] pick #2 -> #3 -> #4; [dead, good(playing), dead] pick #1 -> #3 -> back to #2.

### 23c. Session 16, third round (2026-10-04): online subtitle download indicator, OpenSubtitles search, uneven frame pacing ("slow motion")

- **Indicator for the download of an online subtitle** (`PlayerSession.applyOnlineSubtitle`, `addFirstOnlineSubtitle`): picking a search result closes the dialog and the provider's download then ran silently (log only). The pill now says "Downloading subtitles · name" for it (and "Searching subtitles online…" for "Add the first online result"), is replaced by the player's own "Loading / on" steps without blinking out in between (a pill that is up stays up, `SubtitlePill`), and says "Couldn't download subtitles · name" when the provider gives nothing. The search dialog shows results as each provider answers (`searchSubtitles(onPartial)`): one that times out (Addic7ed, 15 s) no longer holds the list back.
- **OpenSubtitles "not opening"**: `api.opensubtitles.com` answers some requests with the airtel.in court-order web page (India) - the SEARCH too, not only the files (seen live: 0 results for "Inception", `Search Req => <iframe ... airtel.in/court-orders`); it comes and goes. `OpenSubtitlesApi.search` now falls back to the older anonymous interface (`OpenSubtitlesLegacy.search`, which works with the system DNS) when the answer is a web page, an error, an exception, empty, or when the new API was seen blocked in the last 30 minutes; the result's data carries the file link (`||name|link`), `getResources` downloads it directly. Dev: `/ossub?...&provider=opensubtitles&block=1` behaves as if the new API were blocked.
- **"Videos play a little slow-motion, mainly in StreamPlay"** - measured: not the speed. Media time vs wall clock on 4 StreamPlay sources: 1.0011 / 0.9996 / 0.9987 / 1.0001, no dropped frames, avsync 0.00001, 4K H.264/HEVC `d3d11va-copy` at 24 fps even at ~1900x1130. What differed: the beat between a 23.976 fps film and the 59.99 Hz screen. Unpaced, the rhythm of 2 and 3 refreshes per frame is perfect for a while and then, for a few seconds of every cycle, frames flip between two refreshes (72/48 instead of 60/60, 26 repeated gaps per 5 s instead of 0): judder, which reads as "rusty / slow motion", and only on 24 fps film (StreamPlay), not on 25/30 fps.
  - **Measuring it**: `MpvSurfaceView.present` (`/videostats` line `present:`) classifies each drawn frame by the refresh it is SHOWN at, from real vblank stamps (`VBlankClock`: a helper thread on `D3DKMTWaitForVerticalBlankEvent`, only alive while frames ask for the grid, stops 4 s after). Do NOT classify gaps between draw start times: 41.7 ms is exactly 2.5 refreshes, so the rounding flips at random (I first reported 38 % "repeats" that way: an artifact).
  - **Fix** (`player/VBlankClock.kt`, `FramePacer`): `mpv_render_context_render` is called with BLOCK_FOR_TARGET_TIME=0, mpv then hands over each frame ~37 ms before its target time (render 2.7 ms); the target time comes from `mpv_render_context_get_info(NEXT_FRAME_INFO)` (**nanoseconds**, although `mpv_get_time_us` is microseconds). Each frame's refresh is the next refresh of a schedule carried forward from the previous frame (spacing of the exact target times, not the jitter of the render thread), nudged 1/8 per frame towards the real grid, and the frame is handed to the window just after the refresh before its slot. Result: 23.976 fps on 59.99 Hz 60/60 with 0-5 repeats in six 5 s windows; 25 fps 75/50; 1.5x speed 75/113; seek, pause and resume recover. First try (decide each frame from absolute timing, mpv blocking) made it worse (1- and 4-refresh gaps): the carried schedule is what makes it stable.
  - Fallbacks: no frame timing from mpv, a target further than 2 s from now, or no refresh grid (remote desktop, failed D3DKMT call) -> mpv's own blocking (or waiting for the target time while the grid is learned, ~130 ms). `-Dcloudstream.nopacing=true` switches the pacer off. A second monitor with another refresh rate is paced to the primary one's grid.

### 23d. Session 16, release round (2026-10-04): the search dialog killed the OpenSubtitles download
- Found only by clicking through the real UI (the dev endpoint `/ossub` ran outside the dialog and passed): a click on a search result started `applyOnlineSubtitle` on the DIALOG's `rememberCoroutineScope()` and called `dismiss()`; the scope ended with the dialog (`ForgottenCoroutineScopeException: rememberCoroutineScope left the composition`), the download was cancelled and every OpenSubtitles / SubDL result "did nothing". Now `PlayerSession.downloadOnlineSubtitle` starts it on the session's scope. Also: a search started while another still runs (Enter, Search, language) used to be ignored (the dialog showed the old empty result); it now replaces the running one (`generation` counter in `openSubtitleSearch`). Verified through the dialog: click -> "Downloading subtitles" pill -> `sub-add` success -> selected -> Loaded; also with the new API forced blocked (`/ossub?...&block=1`, 66 legacy hits -> 40 results, real cue text rendered via `sub-delay`).
- Lesson: test dialog flows through the dialog; anything launched from a dialog's own scope dies with it.

## 24. Release 0.1.4 (2026-10-04)

- Published as a **pre-release** (tag `v0.1.4` on `3c876df`, `CloudStream-0.1.4-windows-x64.msi`, 377,118,924 bytes, sha256 `a1d0ffff4c03f9fd4e4ea35500610fcf3940a84a0ae00a0f2b3d04376142367d`; ProductVersion 0.1.4 read from the MSI), `make_latest=false`; release id 402880492. Process as for 0.1.3: `./gradlew :app:test :app:installerMsi` with JAVA_HOME and WiX `bin` (8aab2d1d scratchpad) on PATH (4 min), commit, `git push origin main`, then `release014.ps1` (draft -> upload with curl -> publish). Changelog entry 0.1.4 is the release notes.
- Gotcha: the PowerShell tool does not forward stdin to `git credential fill` ("refusing to work with credential missing protocol field"): fetch the token in Bash (`printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p'`), export it as `CS_TOKEN` for the script, never print it, unset it afterwards.
- Not done / untested: a real upgrade from an installed 0.1.3 through the in-app updater; the pacer on a second monitor with another refresh rate and on remote desktop (falls back to mpv's blocking); the real OpenSubtitles block live during playback (the blocked path was forced with `/ossub?...&block=1`).
