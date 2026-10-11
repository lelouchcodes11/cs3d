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

## 4. Reporting a Vulnerability

If you discover a security vulnerability in CloudStream for Windows:
- Please report it via [GitHub Issues](../../issues) or contact the maintainer on [Discord](https://discord.gg/u82JU5JDM) / [Telegram](https://t.me/cs3d_official).
- Include the affected version, steps to reproduce, and any relevant logs (`Settings → About → Copy diagnostics`).
