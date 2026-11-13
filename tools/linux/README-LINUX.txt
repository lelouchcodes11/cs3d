CloudStream for Linux (EXPERIMENTAL)
====================================

This is an early, untested-on-many-systems build of CloudStream for Linux (x86-64).
The Windows version is the main one; expect rough edges here and please report them
(Settings > About > Copy diagnostics, then a GitHub issue or the Discord server).

Start it
--------
  1. Unpack:            tar xzf CloudStream-*-linux-x64.tar.gz
  2. Install libmpv:    Debian/Ubuntu: sudo apt install libmpv2      (older releases: libmpv1)
                        Fedora:        sudo dnf install mpv-libs
                        Arch:          sudo pacman -S mpv
  3. Run:               ./CloudStream/bin/CloudStream

Optional: ./CloudStream/install-desktop-entry.sh adds CloudStream to your app menu.

Your data (settings, extensions, library) is kept in ~/.local/share/CloudStream
unless a "data" folder or portable.txt sits next to the app.

What is different from Windows
------------------------------
  - No extension sandbox: extensions run inside the app (the Windows sandbox is made of
    Windows parts). Install only extensions from repositories you trust.
  - No built-in browser page: sites behind a Cloudflare check may not open, and
    "Pass Cloudflare" / WebView features are not available.
  - No tray notifications, media keys, Windows Hello or in-app updates.
  - Video is played by your system's libmpv; hardware decoding depends on its drivers.
