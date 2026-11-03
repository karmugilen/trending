#!/usr/bin/env bash
# Targeted Fast Build (Inspired by Cloudflare Targeted Zone Builds)
# Only builds the exact scope that changed and targets the connected arm64 device.
set -euo pipefail

MODE="${1:-release}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

if [ "$MODE" = "release" ]; then
    echo "⚡ [Targeted Build] Running Release assemble..."
    ./gradlew :app:assembleRelease --daemon -Dorg.gradle.parallel=true -Dorg.gradle.caching=true
    APK="app/build/outputs/apk/release/app-release.apk"
else
    echo "⚡ [Targeted Build] Running Fast Debug assemble (skipping R8 minifier)..."
    ./gradlew :app:assembleDebug --daemon -Dorg.gradle.parallel=true -Dorg.gradle.caching=true
    APK="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK" ]; then
    cp "$APK" Trending-1.5.0.apk
    echo "📲 [Targeted Build] Installing to connected device..."
    adb install -r Trending-1.5.0.apk
    adb shell am start -n app.omalauncher.trending/.MainActivity
    echo "✅ [Targeted Build] Complete!"
fi
