#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME}/ndk/27.2.12479018}"
if [ ! -d "$ANDROID_NDK_HOME" ]; then
    # Try finding any ndk
    ANDROID_NDK_HOME="$(find "${ANDROID_HOME}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
fi

echo "Using NDK: $ANDROID_NDK_HOME"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

cd "$ROOT_DIR/upstream/maayys/agent"

export GOOS=android
export CGO_ENABLED=1

# Build arm64-v8a
echo "Building Agent for arm64-v8a..."
mkdir -p "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
GOARCH=arm64 CC="$TOOLCHAIN/aarch64-linux-android26-clang" \
    go build -trimpath -ldflags '-s -w -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/libmaayys_agent.so" .

# Build x86_64
echo "Building Agent for x86_64..."
mkdir -p "$ROOT_DIR/app/src/main/jniLibs/x86_64"
GOARCH=amd64 CC="$TOOLCHAIN/x86_64-linux-android26-clang" \
    go build -trimpath -ldflags '-s -w -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$ROOT_DIR/app/src/main/jniLibs/x86_64/libmaayys_agent.so" .

echo "Agent build complete!"
