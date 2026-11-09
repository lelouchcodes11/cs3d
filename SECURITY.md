# Security Policy & Windows Warnings Explained

CloudStream for Windows is completely open-source (GPL-3.0) and contains **no telemetry, no ads, and no bundled third-party scripts**. Because it is an independent open-source desktop application without a paid EV code-signing certificate, Windows may show warnings that look alarming if you haven't seen them before. This document explains each one and how you can verify your download.

---

## 1. Windows SmartScreen ("Windows protected your PC")

When running the installer (`-setup.exe` or `.msi`) or `CloudStream.exe` for the first time, Windows SmartScreen may display **"Windows protected your PC — Microsoft Defender SmartScreen prevented an unrecognized app from starting."**

- **Why this happens:** Microsoft requires developers to purchase an annual code-signing certificate (typically hundreds of dollars per year) to bypass SmartScreen Reputation on newly published executables.
- **How to verify your file:** Every official release on [GitHub Releases](../../releases) includes the **SHA-256 digest** of each installer asset. You can verify your downloaded file in PowerShell before running it:
  ```powershell
  Get-FileHash .\CloudStream-*-windows-x64-setup.exe -Algorithm SHA256
  ```
- **How to proceed:** Click **More info** → **Run anyway**.

---

## 2. Windows Security Alert in `http_cache` (`Trojan:HTML/Redirector`)

Some users reported Windows Security flagging a `.tmp` or `.1` file inside `%APPDATA%\CloudStream\cache\http_cache\` as `Trojan:HTML/Redirector` or `HTML/FakeAlert`.

- **What happened:** When you browse or search using third-party streaming extensions, those extensions send HTTP requests to streaming websites. Many free streaming sites embed deceptive ad/redirect JavaScript inside their HTML pages. Previously, OkHttp's disk cache saved all HTTP responses—including raw HTML pages—into `%APPDATA%\CloudStream\cache\http_cache\`, and Windows Defender's real-time scanner flagged the cached HTML text file.
- **Was it ever executed?** **No.** The cached file was inert text read by an extension's HTML parser (Jsoup) to find video links; the app never executes downloaded HTML or scripts on your system.
- **How it is fixed:** Starting with the latest builds, [`NoPageCache.kt`](app/src/main/kotlin/com/lagradost/desktop/net/NoPageCache.kt) prevents HTML pages, scripts, SVGs, and event streams from ever being written to the disk cache (only JSON metadata and poster images are cached), and any previously cached web pages are purged on startup.

---

## 3. Clean Installers (No PowerShell Custom Actions)

Earlier MSI builds (`1.0.0` – `1.0.2`) included a WiX custom action that invoked `powershell.exe -WindowStyle Hidden` during upgrades to preserve legacy `0.1.3`/`0.1.4` data folders. Because hidden PowerShell invocations during MSI installation look suspicious in process monitors and antivirus heuristics:
- All PowerShell custom actions have been **completely removed** from the MSI installer, and [`tools/patch-msi.ps1`](tools/patch-msi.ps1) verifies at build time that zero script or shell custom actions exist in the MSI table.
- We also provide a standard **Inno Setup installer (`-setup.exe`)** and a **Portable ZIP (`-portable.zip`)** that needs no installer at all.

---

## 4. Extensions run in a Windows sandbox

CloudStream extensions are programs written by third parties and published in repositories that anyone can create. **They run in a separate process that Windows itself locks down**, an *AppContainer* (the sandbox of the Windows Store apps), held in a job object. This is enforced by the operating system, not by the app's code:

- **Files:** only the app's own program files and the extension's own folder (read), and a folder of its own (write). Your documents, downloads, browser data, keys and the rest of the disk are closed to it, for reading as well as for writing.
- **Programs:** it cannot start another program (the job allows one process), so it cannot run a script, add a startup entry or open a shell.
- **Network:** outbound internet only. It cannot reach your home or company network, and it cannot connect to this PC's own loopback addresses (so not to the app's local servers either).
- **Windows, clipboard, registry:** closed. It has no window and no desktop. It ends when the app ends, is held to 1.5 GB of memory, shows no crash dialog, and runs at below-normal priority: an extension that spins the processor cannot make the app or the video stutter.
- **What it may ask of the app**, over a pipe and always checked by the app: search results and links it found (parsed against the library's own classes, never classes it names), a hidden browser for a Cloudflare check (http and https pages only, two at a time, 90 seconds at most), and a short message for you. Opening your browser or a program is refused.
- It gets an environment of its own (the app's variables are not passed on) and a copy of the app's settings that extensions read, without anything that looks like a credential.

`ExtensionSandboxTest` starts a process in the real AppContainer and checks all of this from the inside: it cannot read a file of yours, list your documents, write outside its folder, start a program or reach the loopback address; it can write its own folder and reach the internet.

**What the app does for an extension in the sandbox.** Nothing an extension may need makes it leave the sandbox:
- its **settings page** is opened in the app's window on your click (from a copy of the extension that registers nothing, so its search and links stay in the sandbox);
- a **browser page it could not get** (no Edge WebView2 on this PC, too many pages at once) fails for that call only; the extension stays loaded in the sandbox with everything else it does. Needing a browser is not dangerous, so it is never a reason to unload or block an extension;
- its **video interceptor** runs in the sandbox, around a request the sandbox makes, while the app's player reads the answer piece by piece;
- a **browser view of its own** (a WebView: a Cloudflare check, a page that only a browser can read) is a page of the app's browser. The app opens it, hidden, only for http and https addresses; it does what the extension asks of it (load, run a script, click, answer the page's calls to the extension's own object) and sends back what the page does (requests, console lines, the page finishing). The number of pages, the size of scripts and data, the headers and the cookies are bounded, and cookies are accepted only for the site of the page being opened. When the extension shows that page in a dialog (a check you must pass), the app shows it in a window of its own, under the rules of the Cloudflare switch: only while you are using that extension, only when *Pass Cloudflare checks* is on, with a Cancel button that ends the extension's dialog, and not over and over;
- the **video actions** an extension registers (open in another player) have nothing to run in on the desktop, so they stay unused in the sandbox;
- the **video hosts** it reads (its extractors) are in the app's list, so the app's own lookups find them (a trailer); the work is done in the sandbox.

**The start-up archive of the sandbox is made by the app.** To start faster, the sandbox program maps an archive of its own classes (Java class data sharing). The app makes it once per version by starting the program outside the sandbox with a throw-away folder and no extension, closing it as soon as it is ready; the file lies in a folder the sandbox may only read, so an extension cannot leave crafted classes in it for a later start. The JVM's own messages go to standard error, never into the channel to the app.

**The sandbox fails closed.** If Windows cannot start the sandbox (or the app cannot read the files it needs), **no extension is loaded**: the app says so ("Extensions are not loaded: the sandbox could not start (...)"), Settings > Network and security > *Where extensions run* shows why, and nothing of an extension runs with your rights. Earlier versions fell back to running every extension in the app without saying so; that was the wrong default. *Run extensions in the app if the sandbox cannot start* (off by default) brings the old behaviour back, under the weaker Java sandbox described next, for someone who chooses it. What always runs in the app is only an extension you mark *Trust*, and everything when you switch the sandbox off yourself. Settings > Network and security > *Where extensions run* lists which extension runs where. **One switch**, *Sandbox for extensions* in Settings > Network and security, turns both layers on or off. *Erase all data* also removes the sandbox's Windows profile.

**The rights of the sandbox are read from Windows at every start.** The sandbox may read the app's own files and the extension's file, and write its own folder, nothing else. Whether it has those rights is checked in the files' access lists each time (not remembered in a file): a folder that was replaced by an update or a new build has lost them, and a sandbox program that cannot read its own Java runtime dies at start. A start that fails is not repeated for every extension (a pause of 15 s, 1 min, 5 min), and the reason (exit code, what the program printed) is shown. The program also runs with Windows policies that it cannot lift: no extension points (hook DLLs), no DLL from a network share or a low-integrity folder, DLLs looked up in System32 first (a DLL planted next to the program is not picked), every DLL moved to a random address and the heap and stack placed at random (forced relocation, bottom-up and high-entropy ASLR), the program ended at the first sign of heap corruption, and no font that is not a Windows system font (font files are a known way to attack Windows itself) (strict handle checks are off on purpose: with them on, the Java runtime dies when one thread closes a socket another polls, and the isolation does not depend on them).

**The app's browser is not a way in for the sandbox.** A page that an extension asks the app to open (for a Cloudflare check, or a browser view of its own) may not reach this PC's own services or the home network: an address of this PC (localhost, the app's own local servers), of the local network (192.168.x.x, 10.x.x.x, 172.16-31.x.x, .local, .lan, single-word names), link-local (169.254.x.x, where cloud metadata and routers are) and carrier-grade (100.64.x.x) is refused for the page, for everything it loads and for every redirect; a name is looked up and refused when any of its addresses is such an address; and the page is checked again before the extension may run a script in it (against a name that was turned toward this PC after it was opened). Only the Windows WebView2 engine is used for these pages, because it is the one whose every request the app can look at.

### The Java sandbox (extensions that run in the app)

CloudStream extensions are programs written by third parties and published in repositories that anyone can create. They run inside the app, with the rights of the Windows user. The app watches what they do (`Settings → Network and security → Extension sandbox`, **on by default**).

**What an extension is stopped from doing**
- running programs (also by asking Windows to "open" an executable, a script or a shortcut),
- reading files outside the app's own folders (documents, downloads, browser data, keys) and changing or deleting files outside the data folder and the temp folder, which blocks the usual malware moves such as adding a startup entry,
- ending the app, switching the sandbox off, loading native libraries, loading code through a class loader of its own, or using the JDK's internals and JNA (the way to Windows' own system functions),
- taking screenshots, watching the keyboard or reading the clipboard, and changing the proxy, cookie or certificate handling of the whole app.

It keeps the network and everything it needs to find sources. The windows of an extension you are not using are held back too: a donation or news popup is not shown at all, and a Cloudflare check window that the extension asks for while it loads (AnimePahe does) waits, for up to three minutes, until you use that extension, or is closed unseen when its page passes the check by itself. When something is stopped the extension gets an error, a toast names the extension and the action, and **Settings → Network and security → Blocked by the sandbox** lists it, so a source that breaks can be understood. An extension you know and trust can be marked **Trust** in that list (Settings → Network and security → Trusted extensions takes it back): the sandbox then leaves it alone. The switch turns the whole sandbox off.

**How it works.** The launcher starts the JVM with `-Djava.security.manager=allow` and the app installs a Java `SecurityManager` that acts only when code of an extension is on the call stack (a class of a dex class loader). The app's own work passes at once. The few things the app does for an extension on purpose (opening a link in your browser, copying text to the clipboard) go through a gate that checks them first: a file can only be opened when it is a media, subtitle, picture or document file. `-Dcloudstream.sandbox=audit` only logs what would be stopped; `=off` does not install it.

**What it is not.** It is not a virtual machine and not a barrier against code written to defeat this very sandbox (for example by abusing a feature of the app). It makes the usual attacks fail and shows what an extension tries. Add only repositories you trust, keep Windows Defender on, and report extensions that behave badly.

---

## 5. What Windows itself enforces

The Java sandbox above is a check inside the app's own process (a Java security manager), so it is only as strong as that process. Besides the Windows sandbox of the extensions, two more things are enforced by Windows, whatever the program does:

- **The torrent engine** (TorrServer, which talks to strangers on the internet) is downloaded only when its SHA-256 matches the one GitHub states for the release file (or the release this app knows), is checked again before every start, and then runs in a Windows *job* (it ends with the app, cannot start other programs, cannot use the clipboard, other windows or the desktop) at the *low integrity level* (it can read, but cannot change your documents, the startup entries of the registry or this app's data; only its own cache folder is writable). `ProcessConfineTest` starts a real process under these limits and checks that it cannot write outside its folder or start a program.
- **The port used to hand links to the running app** (a second start of the app, a `cloudstreamapp://` link) answers only a sender that knows a secret that the running app keeps in its data folder.

Updates are installed only when GitHub's SHA-256 for the file matches the download. **Reset** (Settings > Backup and reset) deletes the app's data at the next start; an extension that syncs your data never sees the empty state.

**Still open:** an extension you mark *Trust*, and every extension when you allow the app to run them if the sandbox cannot start, run in the app and are guarded by the Java sandbox only; a name can be turned toward this PC between the check and the connection (DNS rebinding) in the time of one request, which the browser engine gives the app no way to pin; that security manager is removed from Java 24 and later, which is why the app stays on Java 21. The page of an extension's browser view is made by the app's own browser engine (Windows WebView2): the extension can load web pages and read what its own scripts return, with the cookies the app holds for that site, and nothing else of your PC.

---

## 6. Reporting a Vulnerability

If you discover a security vulnerability in CloudStream for Windows:
- Please report it via [GitHub Issues](../../issues) or contact the maintainer on [Discord](https://discord.gg/u82JU5JDM) / [Telegram](https://t.me/cs3d_official).
- Include the affected version, steps to reproduce, and any relevant logs (`Settings → About → Copy diagnostics`).
