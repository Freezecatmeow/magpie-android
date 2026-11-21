#!/usr/bin/env bash
# FR-01/FR-24: NDK CGO_ENABLED=1 cross-compile of libmagpie.so (bionic DNS fix).
# Requires: Android NDK r26d/r27c, Go >= 1.27, upstream yetone/magpie source checked out.
set -euo pipefail
NDK=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to NDK r26d/r27c}
CC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android24-clang"
SRC=${1:-../magpie}          # upstream source tree
OUT=${2:-app/src/main/jniLibs/arm64-v8a/libmagpie.so}
CC="$CC" CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
  go build -tags nogui -trimpath \
  -ldflags "-s -w -X main.version=v0.2.0 -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$OUT" .
# verify: readelf -d "$OUT" -> NEEDED [libc.so]; PT_LOAD align 0x4000
