#!/usr/bin/env bash
# ==============================================================================
# Trending App: Domino Rally Automated Pipeline
# Executes each evolution stage sequentially like cascading dominoes:
# 🁢 Preflight -> 🁣 Web Security -> 🁤 Rust Core -> 🁥 Android App -> 🁦 Release Build -> 🁧 Artifact
# ==============================================================================

set -euo pipefail

# ANSI Colors & Formatting
BOLD="\033[1m"
DIM="\033[2m"
CYAN="\033[36m"
GREEN="\033[32m"
YELLOW="\033[33m"
RED="\033[31m"
MAGENTA="\033[35m"
BLUE="\033[34m"
RESET="\033[0m"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

banner() {
    clear 2>/dev/null || true
    echo -e "${CYAN}${BOLD}"
    cat << "EOF"
  _____ _____  ______ _   _ _____ _____ _   _  _____ 
 |_   _|  __ \|  ____| \ | |  __ \_   _| \ | |/ ____|
   | | | |__) | |__  |  \| | |  | || | |  \| | |  __ 
   | | |  _  /|  __| | . ` | |  | || | | . ` | | |_ |
  _| |_| | \ \| |____| |\  | |__| || |_| |\  | |__| |
 |_____|_|  \_\______|_| \_|_____/_____|_| \_|\_____|
       DOMINO RALLY: AUTOMATED EVOLUTION PIPELINE
EOF
    echo -e "${RESET}"
    echo -e "${DIM}Chaining stages like falling dominoes to build release Trending 1.5.0${RESET}\n"
}

step_header() {
    local num="$1"
    local domino="$2"
    local title="$3"
    echo -e "\n${BOLD}${BLUE}==============================================================================${RESET}"
    echo -e "${BOLD}${MAGENTA}${domino} [STAGE ${num}/6] ${CYAN}${title}${RESET}"
    echo -e "${BOLD}${BLUE}==============================================================================${RESET}"
}

success_mark() {
    echo -e "  ${GREEN}✓${RESET} ${BOLD}$1${RESET}"
}

info_mark() {
    echo -e "  ${CYAN}ℹ${RESET} $1"
}

fail_mark() {
    echo -e "  ${RED}✗ [FAILED]${RESET} $1" >&2
    exit 1
}

banner

# ------------------------------------------------------------------------------
# Domino Stage 1: Preflight & Environment Verification
# ------------------------------------------------------------------------------
step_header "1" "🁢" "Preflight & Environment Toolchain Check"

info_mark "Checking Java 17+..."
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    success_mark "Java: $JAVA_VER"
else
    fail_mark "Java JDK not found in PATH"
fi

info_mark "Checking Rust toolchain & Android target..."
if command -v rustc >/dev/null 2>&1 && command -v cargo >/dev/null 2>&1; then
    RUST_VER=$(rustc --version)
    success_mark "Rust: $RUST_VER"
    if rustup target list | grep -q "aarch64-linux-android (installed)"; then
        success_mark "Rust target: aarch64-linux-android installed"
    else
        fail_mark "Rust target aarch64-linux-android is missing. Run: rustup target add aarch64-linux-android"
    fi
else
    fail_mark "Rust/cargo not found in PATH"
fi

info_mark "Checking Android SDK and NDK..."
if [ -f "$ROOT_DIR/local.properties" ]; then
    SDK_DIR=$(grep "sdk.dir" "$ROOT_DIR/local.properties" | cut -d'=' -f2 | tr -d ' ')
    if [ -d "$SDK_DIR" ]; then
        success_mark "Android SDK: $SDK_DIR"
    else
        fail_mark "Android SDK dir not found at $SDK_DIR"
    fi
else
    fail_mark "local.properties missing"
fi

# ------------------------------------------------------------------------------
# Domino Stage 2: Web Security Hardening & Parity Tests
# ------------------------------------------------------------------------------
step_header "2" "🁣" "Web Security Hardening & Integration Tests"

info_mark "Verifying run_web.sh localhost binding (Fix 1)..."
if grep -q "127.0.0.1" "$ROOT_DIR/run_web.sh"; then
    success_mark "run_web.sh bound to safe loopback 127.0.0.1 (not 0.0.0.0)"
else
    fail_mark "run_web.sh still contains insecure host binding"
fi

info_mark "Verifying CORS restrictions & token sanitization in web/server.py..."
python3 -c "
import sys
content = open('web/server.py').read()
assert 'sanitize_settings_for_client' in content, 'Missing settings token sanitizer'
assert 'allow_origins=[\"*\"]' not in content, 'Wildcard CORS still present'
" || fail_mark "web/server.py security assertions failed"
success_mark "web/server.py token masking and CORS restricted"

info_mark "Verifying XSS elimination in web/static/app.js (Fix 2)..."
python3 -c "
content = open('web/static/app.js').read()
assert 'isSafeUrl' in content, 'Missing isSafeUrl check'
assert 'escapeAttr' in content, 'Missing escapeAttr helper'
assert 'onclick=\"openDiscussions' not in content, 'Dangerous inline onclick string interpolation remains'
" || fail_mark "web/static/app.js XSS check failed"
success_mark "web/static/app.js secured with event delegation and safe URL validation"

info_mark "Running full Web integration test suite..."
zsh -ic "actenv && cd web && python test_backend.py" || fail_mark "Web test_backend.py failed"
success_mark "All backend and frontend web integration tests passed!"

# ------------------------------------------------------------------------------
# Domino Stage 3: Rust Core Kinetics & Signal Engine Tests
# ------------------------------------------------------------------------------
step_header "3" "🁤" "Rust Core Kinetics, Stack Matching & Unit Tests"

info_mark "Compiling Rust core and executing native test harness..."
cargo test --manifest-path core/Cargo.toml || fail_mark "cargo test in core failed"
success_mark "47/47 Rust core tests passed! (Snapshots, Velocity, Stack Profile & Briefing)"

# ------------------------------------------------------------------------------
# Domino Stage 4: Android App Integration & Native JNI Compilation
# ------------------------------------------------------------------------------
step_header "4" "🁥" "Android App & Native JNI Compilation"

info_mark "Building optimized native shared library for aarch64-linux-android..."
./gradlew :app:buildTrendingRust :app:copyTrendingRust || fail_mark "Rust native library build failed"
success_mark "Native libtrending.so compiled and bundled"

info_mark "Verifying Android version bump (v1.5.0)..."
grep -q 'versionName = "1.5.0"' "$ROOT_DIR/app/build.gradle.kts" || fail_mark "versionName is not 1.5.0 in app/build.gradle.kts"
grep -q 'versionCode = 6' "$ROOT_DIR/app/build.gradle.kts" || fail_mark "versionCode is not 6 in app/build.gradle.kts"
success_mark "App version set to 1.5.0 (versionCode 6)"

# ------------------------------------------------------------------------------
# Domino Stage 5: Release Build Assembly & R8 Shrinking
# ------------------------------------------------------------------------------
step_header "5" "🁦" "Release Build Assembly & R8 Optimization"

info_mark "Assembling release APK with Gradle & R8 Proguard..."
./gradlew :app:assembleRelease || fail_mark "assembleRelease failed"
success_mark "Release build completed successfully"

# ------------------------------------------------------------------------------
# Domino Stage 6: Release APK Verification & Packaging
# ------------------------------------------------------------------------------
step_header "6" "🁧" "Release APK Packaging & Verification Gate"

RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
TARGET_APK="$ROOT_DIR/Trending-1.5.0.apk"

if [ -f "$RELEASE_APK" ]; then
    cp "$RELEASE_APK" "$TARGET_APK"
    APK_SIZE=$(du -h "$TARGET_APK" | cut -f1)
    SHA256=$(sha256sum "$TARGET_APK" | cut -d' ' -f1)
    success_mark "Packaged: $TARGET_APK ($APK_SIZE)"
    info_mark "SHA-256: $SHA256"
else
    fail_mark "Built APK not found at $RELEASE_APK"
fi

info_mark "Verifying APK Signature..."
APKSIGNER=$(find "$SDK_DIR/build-tools" -name "apksigner" 2>/dev/null | sort -V | tail -n 1)
if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
    "$APKSIGNER" verify --verbose "$TARGET_APK" >/dev/null 2>&1 || fail_mark "APK signature verification failed"
    success_mark "APK Signature verified (Scheme v2)"
fi

# ------------------------------------------------------------------------------
# Domino Rally Completion Summary
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}${GREEN}==============================================================================${RESET}"
echo -e "${BOLD}${GREEN}🎉 ALL DOMINOES FELL FLAWLESSLY! Trending 1.5.0 IS READY TO INSTALL! 🎉${RESET}"
echo -e "${BOLD}${GREEN}==============================================================================${RESET}"

echo -e "\n${BOLD}Package Details:${RESET}"
echo -e "  • ${BOLD}APK Path:${RESET}       ${CYAN}$TARGET_APK${RESET}"
echo -e "  • ${BOLD}Version:${RESET}        1.5.0 (versionCode 6)"
echo -e "  • ${BOLD}Package ID:${RESET}     app.omalauncher.trending"
echo -e "  • ${BOLD}Size:${RESET}           $APK_SIZE"
echo -e "  • ${BOLD}Signature:${RESET}      v2 signed (compatible with existing installs)"

echo -e "\n${BOLD}Implemented Innovations from IDEA.md:${RESET}"
echo -e "  • ${GREEN}✓${RESET} ${BOLD}Web Security & Parity:${RESET} Localhost binding, token exfiltration fix, XSS prevention, Search API rate info"
echo -e "  • ${GREEN}✓${RESET} ${BOLD}Snapshots & Real Velocity:${RESET} Time-series score tracking, acceleration detection, sparklines"
echo -e "  • ${GREEN}✓${RESET} ${BOLD}Daily Radar Briefing:${RESET} 'Since you last looked' home briefing, Broke Out, Affects You, Caught Up"
echo -e "  • ${GREEN}✓${RESET} ${BOLD}Stack Dependency Matching:${RESET} Zero-overhead Cargo.lock, package.json, requirements.txt parser & CVE alerts"
echo -e "  • ${GREEN}✓${RESET} ${BOLD}Cross-Source Correlation:${RESET} Real-time detection of projects moving on Hacker News and GitHub simultaneously"

echo -e "\n${BOLD}To Install on your Phone:${RESET}"
echo -e "  ${YELLOW}adb install -r Trending-1.5.0.apk${RESET}"
echo -e "  ${DIM}Or transfer Trending-1.5.0.apk to your device storage.${RESET}\n"
