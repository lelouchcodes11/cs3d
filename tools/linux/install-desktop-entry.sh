#!/bin/sh
# Adds CloudStream to the app menu of the current user (no root needed). Run it from the unpacked CloudStream folder.
set -e
dir="$(cd "$(dirname "$0")" && pwd)"
icon="$dir/lib/CloudStream.png"
[ -f "$icon" ] || icon="cloudstream"
apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$apps"
cat > "$apps/cloudstream.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=CloudStream
Comment=Watch films, series and live TV through extensions
Exec="$dir/bin/CloudStream" %u
Icon=$icon
Terminal=false
Categories=AudioVideo;Video;Player;
MimeType=x-scheme-handler/cloudstreamrepo;x-scheme-handler/cloudstreamapp;
StartupWMClass=CloudStream
EOF
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$apps" || true
echo "CloudStream added to the app menu ($apps/cloudstream.desktop)"
