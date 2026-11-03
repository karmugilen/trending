#!/usr/bin/env bash
# Targeted Fast Build (Inspired by Cloudflare Targeted Zone Builds)
# Only builds the exact scope that changed and targets the connected arm64 device.
set -euo pipefail

MODE="${1:-release}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

if [ "$MODE" = "release" ]; then
    echo "[Targeted Build] Running Release assemble..."
    ./gradlew :app:assembleRelease --daemon -Dorg.gradle.parallel=true -Dorg.gradle.caching=true
    if [ -f "app/build/outputs/apk/release/app-arm64-v8a-release.apk" ]; then
        APK="app/build/outputs/apk/release/app-arm64-v8a-release.apk"
    else
        APK="app/build/outputs/apk/release/app-universal-release.apk"
    fi
else
    echo "[Targeted Build] Running Fast Debug assemble (skipping R8 minifier)..."
    ./gradlew :app:assembleDebug --daemon -Dorg.gradle.parallel=true -Dorg.gradle.caching=true
    if [ -f "app/build/outputs/apk/debug/app-arm64-v8a-debug.apk" ]; then
        APK="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
    elif [ -f "app/build/outputs/apk/debug/app-universal-debug.apk" ]; then
        APK="app/build/outputs/apk/debug/app-universal-debug.apk"
    else
        APK="app/build/outputs/apk/debug/app-debug.apk"
    fi
fi

if [ -f "$APK" ]; then
    echo "[Targeted Build] Installing to connected device ($APK)..."
    adb install -r "$APK"
    adb shell am start -n app.omalauncher.trending/.MainActivity
    echo "[Targeted Build] Complete."
fi
