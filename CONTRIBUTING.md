# Contributing to CloudStream for Windows

Thank you for your interest in contributing to **CloudStream for Windows**!

---

## Prerequisites

1. **Windows 10 or 11 (64-bit)**
2. **JDK 21** (e.g., [Eclipse Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21))
3. **[Git LFS](https://git-lfs.com/)** — Required because the native video libraries (`app/native/windows-x64/mpv-2.dll` and `vulkan-1.dll`) are stored in Git LFS:
   ```powershell
   git lfs install
   git lfs pull
   ```
4. **Microsoft Edge WebView2 Runtime** (pre-installed on Windows 11 and modern Windows 10)
5. *Optional (for building installers):*
   - [WiX Toolset 3.x](https://wixtoolset.org/) (`candle.exe` and `light.exe` on `PATH`) for building the `.msi`
   - [Inno Setup 6](https://jrsoftware.org/isinfo.php) (`ISCC.exe`) for building the `-setup.exe`

---

## Common Gradle Tasks

```powershell
# Run the desktop app from source (supports -PdataDir=<dir> and -PdevPort=<port>)
.\gradlew.bat :app:runDev

# Run unit tests
.\gradlew.bat :app:test

# Build the portable folder in dist/CloudStream-Portable
.\gradlew.bat :app:portableDist

# Build the release Portable ZIP in dist/release/
.\gradlew.bat :app:portableZip

# Build the Windows MSI installer in dist/release/
.\gradlew.bat :app:installerMsi
```

> **Note when modifying `app/native/windows-x64/`:** Compose Desktop's `createDistributable` task may stay `UP-TO-DATE` if only native DLLs change. Delete `app/build/compose/binaries/main/app` before running `portableDist` or `installerMsi` after changing native binaries.

---

## Repository Structure

| Directory | Description |
| :--- | :--- |
| [`app/`](app) | Compose Desktop UI (`com.lagradost.desktop.ui`), native `libmpv` player (`com.lagradost.desktop.player`), Stremio & TorrServer integration, TMDB enrichment, in-app updater, and packaging tasks. |
| [`android/`](android) | Desktop JVM implementation of the Android framework APIs (`Context`, `SharedPreferences`, `WebView` via WebView2/JCEF, `View`/`Fragment` bridge) that CloudStream and its `.cs3` extensions run on. |
| [`library/`](library), [`shared/`](shared) | Upstream CloudStream 4.8 plugin contracts, extractors, and shared logic. |
| [`tools/`](tools) | Build and packaging scripts (`tools/installer/cloudstream.iss`, `tools/patch-msi.ps1`, `tools/libmpv/build.sh`). |
| [`docs/`](docs) | Architecture notes ([`docs/PLAN.md`](docs/PLAN.md)) and engineering roadmap ([`docs/IMPROVEMENT_PLAN.md`](docs/IMPROVEMENT_PLAN.md)). |

---

## Pull Requests & Issues

- **Extension bugs:** Do not open issues or PRs here for broken third-party extensions/providers. This repository only contains the desktop application and runtime.
- **Code style:** Keep changes focused, preserve existing comments, and add unit tests under `app/src/test/kotlin/` where practical.
- **Testing UI changes:** You can launch the app with `-PdevPort=8765` and inspect `/state`, `/screenshot?sk=1`, and `/log` via [`DevServer.kt`](app/src/main/kotlin/com/lagradost/desktop/tools/DevServer.kt).
