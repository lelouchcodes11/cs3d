#!/bin/bash
# Playback-only libmpv for CloudStream desktop: everything static in one mpv-2.dll, no Vulkan / encoders / scripts / disc formats.
# Same mpv (a1bf4b655) and FFmpeg (7e1f63d5d) as the full build it replaces; the other libraries are taken as of that date.
# Needs MSYS2 (https://www.msys2.org) with: pacman -S git make diffutils patch autoconf automake libtool mingw-w64-ucrt-x86_64-{gcc,meson,ninja,pkgconf,nasm,cmake,python-jinja,shaderc,spirv-cross,zlib,uchardet}
# Usage (UCRT64 shell): bash tools/libmpv/build.sh [stage...]   stages: fetch deps ffmpeg mpv (default: all); work folder ~/mpvbuild
# Result: ~/mpvbuild/mpv-2.dll -> app/native/windows-x64/mpv-2.dll
set -eo pipefail
ROOT=~/mpvbuild
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$ROOT/src
P=$ROOT/prefix
LOG=$ROOT/logs
DATE="2026-09-27"
mkdir -p "$SRC" "$P" "$LOG"
export PKG_CONFIG_PATH="$P/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$P/lib/pkgconfig:/ucrt64/lib/pkgconfig"
export CFLAGS="-O2 -pipe -I$P/include" CXXFLAGS="-O2 -pipe -I$P/include" LDFLAGS="-L$P/lib"
JOBS=$(nproc)
MESON="meson setup --prefix=$P --libdir=lib --buildtype=release --default-library=static --prefer-static -Db_ndebug=true --wrap-mode=nodownload"
CMAKE="cmake -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$P -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON"

step() { echo "== $1 $(date +%H:%M:%S)"; }

# a library at its state of $DATE (mpv / ffmpeg are pinned by commit)
pin() { # dir url [ref]
    if [ ! -d "$SRC/$1" ]; then git clone -q --filter=tree:0 "$2" "$SRC/$1"; fi
    (cd "$SRC/$1" && git checkout -q "${3:-$(git rev-list -1 --before=$DATE HEAD)}" && git submodule -q update --init --recursive --depth 1 2>/dev/null || true
     echo "   $1 $(git log -1 --format='%h %cs')")
}

fetch() {
    step fetch
    pin mpv https://github.com/mpv-player/mpv.git a1bf4b655
    pin ffmpeg https://github.com/FFmpeg/FFmpeg.git 7e1f63d5d
    pin freetype https://github.com/freetype/freetype.git
    pin fribidi https://github.com/fribidi/fribidi.git
    pin harfbuzz https://github.com/harfbuzz/harfbuzz.git
    pin libass https://github.com/libass/libass.git
    pin dav1d https://github.com/videolan/dav1d.git
    pin libplacebo https://github.com/haasn/libplacebo.git
    pin libxml2 https://github.com/GNOME/libxml2.git
    pin nghttp2 https://github.com/nghttp2/nghttp2.git
    pin curl https://github.com/curl/curl.git
    pin zimg https://github.com/sekrit-twc/zimg.git
}

mbuild() { # name meson-options...
    local n=$1; shift
    step "$n"
    rm -rf "$SRC/$n/_b"
    (cd "$SRC/$n" && $MESON "$@" _b >"$LOG/$n.log" 2>&1 && ninja -C _b install >>"$LOG/$n.log" 2>&1) || { tail -30 "$LOG/$n.log"; exit 1; }
}

cbuild() { # name cmake-options...
    local n=$1; shift
    step "$n"
    rm -rf "$SRC/$n/_b"
    (cd "$SRC/$n" && $CMAKE "$@" -B _b >"$LOG/$n.log" 2>&1 && ninja -C _b install >>"$LOG/$n.log" 2>&1) || { tail -30 "$LOG/$n.log"; exit 1; }
}

deps() {
    # zlib, shaderc, spirv-cross, uchardet: static libraries of MSYS2 (/ucrt64)
    mbuild freetype -Dzlib=system -Dpng=disabled -Dbzip2=disabled -Dbrotli=disabled -Dharfbuzz=disabled -Dtests=disabled
    mbuild fribidi -Ddocs=false -Dbin=false -Dtests=false
    mbuild harfbuzz -Dfreetype=enabled -Dglib=disabled -Dgobject=disabled -Dcairo=disabled -Dchafa=disabled -Dicu=disabled \
        -Dgraphite2=disabled -Dgdi=disabled -Ddirectwrite=disabled -Dtests=disabled -Ddocs=disabled -Dutilities=disabled -Dintrospection=disabled
    mbuild libass -Dfontconfig=disabled -Ddirectwrite=enabled -Dcoretext=disabled -Dlibunibreak=disabled -Dasm=enabled -Dtest=disabled -Dprofile=disabled -Dcompare=disabled -Dfuzz=disabled -Dcheckasm=disabled
    mbuild dav1d -Denable_tools=false -Denable_tests=false -Denable_examples=false -Dbitdepths=8,16
    mbuild libplacebo -Dvulkan=disabled -Dopengl=disabled -Dd3d11=disabled -Dglslang=disabled -Dshaderc=disabled -Dlcms=disabled \
        -Ddovi=enabled -Dlibdovi=disabled -Dxxhash=disabled -Dunwind=disabled -Ddemos=false -Dtests=false -Dbench=false -Dfuzz=false
    cbuild libxml2 -DLIBXML2_WITH_ICONV=OFF -DLIBXML2_WITH_LZMA=OFF -DLIBXML2_WITH_ZLIB=OFF -DLIBXML2_WITH_ICU=OFF -DLIBXML2_WITH_PYTHON=OFF \
        -DLIBXML2_WITH_PROGRAMS=OFF -DLIBXML2_WITH_TESTS=OFF -DLIBXML2_WITH_HTTP=OFF -DLIBXML2_WITH_FTP=OFF -DLIBXML2_WITH_MODULES=OFF \
        -DLIBXML2_WITH_DEBUG=OFF -DLIBXML2_WITH_DOCS=OFF
    cbuild nghttp2 -DENABLE_LIB_ONLY=ON -DBUILD_STATIC_LIBS=ON -DENABLE_DOC=OFF -DBUILD_TESTING=OFF
    curlb
    zimgb
}

curlb() {
    cbuild curl "-DCMAKE_C_FLAGS=$CFLAGS -DNGHTTP2_STATICLIB" -DCURL_USE_SCHANNEL=ON -DCURL_USE_OPENSSL=OFF -DUSE_NGHTTP2=ON -DHTTP_ONLY=ON -DBUILD_CURL_EXE=OFF -DBUILD_TESTING=OFF \
        -DCURL_USE_LIBPSL=OFF -DUSE_LIBIDN2=OFF -DUSE_WIN32_IDN=ON -DCURL_BROTLI=OFF -DCURL_ZSTD=OFF -DCURL_USE_LIBSSH2=OFF -DCURL_USE_LIBSSH=OFF \
        -DCURL_DISABLE_LDAP=ON -DENABLE_UNICODE=ON -DCURL_STATIC_CRT=OFF -DBUILD_LIBCURL_DOCS=OFF -DBUILD_MISC_DOCS=OFF -DENABLE_CURL_MANUAL=OFF \
        -DCURL_CA_PATH=none -DCURL_CA_BUNDLE=none
}

zimgb() {
    step zimg
    (cd "$SRC/zimg" && ./autogen.sh >"$LOG/zimg.log" 2>&1 && ./configure --prefix="$P" --enable-static --disable-shared >>"$LOG/zimg.log" 2>&1 \
        && make -j"$JOBS" >>"$LOG/zimg.log" 2>&1 && make install >>"$LOG/zimg.log" 2>&1) || { tail -30 "$LOG/zimg.log"; exit 1; }
}

ffmpeg() {
    step ffmpeg
    # spdif muxer: audio passthrough (audio decoder HW / HW+); rtsp, rtmp: live streams
    local dec="h264,hevc,vp8,vp9,av1,libdav1d,mpeg1video,mpeg2video,mpeg4,msmpeg4v3,h263,vc1,wmv3,mjpeg,png,bmp,gif,webp"
    dec+=",aac,aac_latm,aac_fixed,ac3,ac3_fixed,eac3,truehd,mlp,dca,mp1,mp2,mp3,mp3float,flac,opus,vorbis,alac,wmav2,wmapro,amrnb,amrwb,pcm_*"
    dec+=",ass,ssa,srt,subrip,webvtt,mov_text,text,dvdsub,dvbsub,pgssub,ccaption,microdvd,mpl2,sami,subviewer,realtext,vplayer,jacosub,stl,pjs"
    local dmx="aac,ac3,eac3,asf,avi,dash,dts,dtshd,flac,flv,live_flv,h264,hevc,av1,obu,ivf,hls,loas,m4v,matroska,mov,mp3,mpegps,mpegts,mpegvideo,ogg,wav,w64,truehd,mlp,mpjpeg"
    dmx+=",webvtt,srt,ass,microdvd,mpl2,sami,subviewer,subviewer1,realtext,vplayer,jacosub,stl,pjs,lrc,image2,image_png_pipe,image_jpeg_pipe,image_webp_pipe,data,concat"
    local par="aac,aac_latm,ac3,av1,dca,flac,h263,h264,hevc,mlp,mpeg4video,mpegaudio,mpegvideo,opus,vorbis,vp8,vp9,vc1,dvdsub,dvbsub,png,mjpeg,webp,gif,bmp"
    local hw="h264_d3d11va,h264_d3d11va2,h264_dxva2,hevc_d3d11va,hevc_d3d11va2,hevc_dxva2,vp9_d3d11va,vp9_d3d11va2,vp9_dxva2,av1_d3d11va,av1_d3d11va2,av1_dxva2"
    hw+=",mpeg2_d3d11va,mpeg2_d3d11va2,mpeg2_dxva2,vc1_d3d11va,vc1_d3d11va2,vc1_dxva2,wmv3_d3d11va,wmv3_d3d11va2,wmv3_dxva2"
    rm -rf "$SRC/ffmpeg/_b" && mkdir -p "$SRC/ffmpeg/_b"
    (cd "$SRC/ffmpeg/_b" && ../configure --prefix="$P" --pkg-config-flags=--static --enable-static --disable-shared --disable-programs --disable-doc \
        --disable-debug --enable-gpl --enable-version3 --disable-autodetect --disable-everything --disable-avdevice \
        --enable-avcodec --enable-avformat --enable-avfilter --enable-swscale --enable-swresample --enable-network \
        --enable-zlib --enable-libxml2 --enable-libdav1d --enable-schannel --enable-d3d11va --enable-dxva2 --enable-w32threads \
        --enable-decoder="$dec" --enable-demuxer="$dmx" --enable-parser="$par" --enable-hwaccel="$hw" --enable-bsfs \
        --enable-protocol=file,http,https,httpproxy,tcp,tls,udp,crypto,data,pipe,subfile,concat,cache,rtmp,rtmps,rtmpt,rtp,srtp \
        --enable-demuxer=rtsp,rtp,sdp \
        --enable-muxer=spdif \
        --enable-filter=aformat,anull,aresample,format,null,scale,hwdownload,hwupload,crop,pad,setpts,bwdif,yadif,volume \
        --extra-cflags="-I$P/include" --extra-ldflags="-L$P/lib" >"$LOG/ffmpeg.log" 2>&1 \
     && make -j"$JOBS" >>"$LOG/ffmpeg.log" 2>&1 && make install >>"$LOG/ffmpeg.log" 2>&1) || { tail -30 "$LOG/ffmpeg.log"; exit 1; }
}

mpv() {
    step mpv
    # Schannel checks certificate revocation, which Android (OkHttp) and the old OpenSSL build never did: OK.ru's CDN (IStreamFlare)
    # serves a revoked certificate, mpv's open failed ("SSL connect error"). Same behaviour as Android: no revocation check.
    grep -q CURLSSLOPT_NO_REVOKE "$SRC/mpv/stream/stream_curl.c" || \
        sed -i 's/(long)CURLSSLOPT_NATIVE_CA);/(long)(CURLSSLOPT_NATIVE_CA | CURLSSLOPT_NO_REVOKE));/' "$SRC/mpv/stream/stream_curl.c"
    # static shaderc / SPIRV-Cross of MSYS2 under the names mpv looks up (MSYS2 describes them as DLLs only)
    mkdir -p "$P/lib/pkgconfig" && cp "$HERE"/pkgconfig/*.pc "$P/lib/pkgconfig/"
    rm -rf "$SRC/mpv/_b"
    # one self-contained DLL: libgcc, libstdc++ (shaderc, spirv-cross) and winpthread linked in
    (cd "$SRC/mpv" && LDFLAGS="$LDFLAGS -static -static-libgcc -static-libstdc++" meson setup --prefix="$P" --libdir=lib --buildtype=release \
        --default-library=shared --prefer-static -Db_ndebug=true -Dlibmpv=true -Dcplayer=false -Dtests=false -Dfuzzers=false \
        -Dauto_features=disabled -Dgpl=true -Dlibcurl=enabled -Dd3d11=enabled -Dshaderc=enabled -Dspirv-cross=enabled -Ddirect3d=disabled \
        -Dwasapi=enabled -Dwin32-threads=enabled -Duchardet=enabled -Dzlib=enabled -Dd3d-hwaccel=enabled -Dd3d9-hwaccel=disabled \
        -Diconv=enabled -Dvector=enabled -Dlua=disabled -Djavascript=disabled -Dvulkan=disabled -Dgl=disabled -Dlibarchive=disabled -Dlibbluray=disabled \
        -Ddvdnav=disabled -Dcdda=disabled -Drubberband=disabled -Dvapoursynth=disabled -Dzimg=enabled -Dlcms2=disabled -Djpeg=disabled \
        -Dsdl2-audio=disabled -Dsdl2-video=disabled -Dopenal=disabled -Dhtml-build=disabled -Dmanpage-build=disabled -Dpdf-build=disabled \
        _b >"$LOG/mpv.log" 2>&1 && ninja -C _b >>"$LOG/mpv.log" 2>&1) || { tail -40 "$LOG/mpv.log"; exit 1; }
    strip -s "$SRC/mpv/_b/libmpv-2.dll" -o "$ROOT/mpv-2.dll"
    ls -la "$ROOT/mpv-2.dll"
    objdump -p "$ROOT/mpv-2.dll" | grep 'DLL Name' | sort -u | tr -d '\t' | tr '\n' ' '; echo
}

stages=${*:-fetch deps ffmpeg mpv}
for s in $stages; do $s; done
step done
