#!/usr/bin/env bash
# EmuCoreC: build FFmpeg 8.1.1 for Android (aarch64) with the NDK.
#
# The RPCS3 3rdparty/ffmpeg submodule only carries headers + vcpkg triplets,
# and the RPCS3-Android/ffmpeg-android prebuilts are stuck at 5.1, which is too
# old for the FFmpeg 6+ APIs RPCS3 master uses. So we build the matching
# upstream release from source. Output lands in app/build/ffmpeg-android and is
# consumed by android/ffmpeg.cmake via 3rdparty_ffmpeg.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=8.1.1
API=29
LOCAL="${LOCALAPPDATA//\\//}"
NDK="${NDK_DIR:-$LOCAL/Android/Sdk/ndk/29.0.14206865}"
SRC_DIR="${FFMPEG_SRC_DIR:-$ROOT/app/build/ffmpeg-src}"
OUT_DIR="${FFMPEG_OUT_DIR:-$ROOT/app/build/ffmpeg-android}"
MAKE="${FFMPEG_MAKE:-$LOCAL/mingw/mingw64/bin/mingw32-make.exe}"
JOBS="${FFMPEG_JOBS:-8}"

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/windows-x86_64"
CC="$TOOLCHAIN/bin/clang"
AR="$LOCAL/mingw/mingw64/bin/ar"
RANLIB="$LOCAL/mingw/mingw64/bin/ranlib"
NM="$LOCAL/mingw/mingw64/bin/nm"
STRIP="$TOOLCHAIN/bin/llvm-strip"
SYSROOT="$TOOLCHAIN/sysroot"
HOST_CC="$LOCAL/mingw/mingw64/bin/gcc"

# Download and unpack the source once.
if [ ! -f "$SRC_DIR/FFmpeg-n$VERSION/configure" ]; then
  mkdir -p "$SRC_DIR"
  if [ ! -f "$SRC_DIR/ffmpeg-$VERSION.tar.gz" ]; then
    curl -L --fail -o "$SRC_DIR/ffmpeg-$VERSION.tar.gz" \
      "https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n$VERSION.tar.gz"
  fi
  tar -xzf "$SRC_DIR/ffmpeg-$VERSION.tar.gz" -C "$SRC_DIR"
fi

cd "$SRC_DIR/FFmpeg-n$VERSION"

mkdir -p "$OUT_DIR"

./configure \
  --prefix="$OUT_DIR" \
  --cc="$CC" \
  --host-cc="$HOST_CC" \
  --ar="$AR" --ranlib="$RANLIB" --nm="$NM" --strip="$STRIP" \
  --target-os=android \
  --arch=aarch64 \
  --enable-cross-compile \
  --extra-cflags="--target=aarch64-linux-android$API --sysroot=$SYSROOT -O3 -fPIC" \
  --extra-ldflags="--target=aarch64-linux-android$API --sysroot=$SYSROOT" \
  --enable-static --disable-shared --enable-pic \
  --disable-asm \
  --disable-programs --disable-doc --disable-debug \
  --disable-avdevice --disable-avfilter \
  --disable-network --disable-autodetect

# mingw32-make defaults to cmd.exe as its shell, which breaks ffmpeg's
# response-file recipe (echo $^ > file). Force a POSIX shell.
"$MAKE" SHELL="$(command -v sh)" -j"$JOBS" || true
"$MAKE" SHELL="$(command -v sh)" install || true

# The ar @response-file step is unreliable under Windows make (the .objs list
# file is frequently missing when ar runs); re-archive from the object globs.
cd "$SRC_DIR/FFmpeg-n$VERSION"
for lib in libavcodec libavformat libavutil libswscale libswresample; do
  if [ ! -s "$lib/$lib.a" ] || [ "$(stat -c%s "$lib/$lib.a")" -lt 1000 ]; then
    echo "EmuCoreC: re-archiving $lib from object glob"
    "$AR" rcs "$lib/$lib.a" $(find "$lib" -name "*.o" ! -path "*tests*")
  fi
done

mkdir -p "$OUT_DIR/lib"
cp libavcodec/libavcodec.a libavformat/libavformat.a \
   libavutil/libavutil.a libswscale/libswscale.a libswresample/libswresample.a \
   "$OUT_DIR/lib/"

echo "EmuCoreC: FFmpeg $VERSION built into $OUT_DIR"
