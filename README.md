# ⚡ Trending

> **A fast, native developer radar for Hacker News & GitHub.**  
> Know what broke out, what's being discussed, and what affects your stack — in seconds.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Rust](https://img.shields.io/badge/Core-Rust%202021-DEA584.svg?logo=rust&logoColor=white)](https://www.rust-lang.org)
[![Kotlin](https://img.shields.io/badge/UI-Jetpack%20Compose-7F52FF.svg?logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-blue.svg)](https://m3.material.io)

---

## ✨ Overview

**Trending** is a native Android application built for software engineers who want signal over noise. Instead of infinite scrolling, it delivers high-signal trend detection across Hacker News and GitHub.

The application uses a **hybrid architecture**:
- **High-Performance Rust Core (`core/`)**: Cross-compiled JNI library handling tokenization, Bayesian spam detection, trend velocity scoring, windowed aggregation, and local caching.
- **Modern Jetpack Compose UI (`app/`)**: Responsive Material 3 design, custom fluid activity wave charts, instant navigation, and dynamic theming.
- **Background Radar**: Local WorkManager engine scanning for breakouts, security alerts, and tech stack dependencies without tracking or external servers.

---

## 🚀 Key Features

- **Dual-Source Intelligence**:
  - **Hacker News**: Real-time points, comments, velocity ranking, and linked discussion threads.
  - **GitHub**: Official trending lists (Today / This Week / This Month) alongside breakout new repositories.
- **Smooth Activity Wave Charts**: Visual hourly velocity showing when stories and repositories pick up momentum.
- **Streamlined Navigation**: Clean segmented controls for feeds, customizable time windows (1h, 6h, 12h, 24h), and mode filters (Top, Rising, New).
- **Radar & Stack Alerts**: Proactive notifications when packages in your tech stack trend or have reported vulnerabilities.
- **Spam & Noise Filtering**: High-precision local filtering to strip SEO farming and star-inflation repos.
- **Zero Privacy Footprint**: No accounts required, no telemetry, all cached locally on your device.
- **Web Companion**: Embedded lightweight Python FastAPI server (`web/`) for desktop browsing on localhost.

---

## 🏗️ Architecture

```
trending/
├── app/                  # Android Native Client (Kotlin + Jetpack Compose)
│   └── src/main/java/app/omalauncher/trending/
│       ├── ui/           # Screens, Components, Theme, Wave Charts
│       ├── data/         # SettingsStore, Preferences, Disk Cache
│       ├── worker/       # WorkManager Background Alert Radar
│       └── logic/        # Native JNI Glue & Models
├── core/                 # Core Engine (Rust cdylib)
│   ├── src/
│   │   ├── lib.rs        # JNI Bridge entrypoints
│   │   ├── feed.rs       # Parsing, scoring & window aggregation
│   │   ├── signals.rs    # Velocity calculation & spam heuristic
│   │   ├── topics.rs     # Dynamic topic extraction with lift
│   │   └── alerts.rs     # Deduplication & alerting discipline
│   └── Cargo.toml
├── web/                  # Desktop Web Client (FastAPI / HTML5)
├── fast-build.sh         # Targeted incremental build runner (sub-minute assemble)
└── domino_rally.sh       # Evolution pipeline runner
```

---

## 🛠️ Getting Started & Building

### Prerequisites

- **JDK 17+**
- **Android SDK & NDK** (API 26+)
- **Rust Toolchain** with Android targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
  ```

### Configuration

Create a `local.properties` file in the project root pointing to your Android SDK:
```properties
sdk.dir=/path/to/your/Android/Sdk
```

### Fast Targeted Build

For rapid iterative development and testing:
```bash
# Debug build (skips minification, ultra-fast incremental rebuild)
./fast-build.sh debug

# Optimized Release build (full R8 minification, bytecode optimization, resource shrinking)
./fast-build.sh release
```

### Standard Gradle Build

```bash
# Run Rust unit tests
cargo test --manifest-path core/Cargo.toml

# Build release APK
./gradlew :app:assembleRelease
```
The output APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 🖥️ Web Companion

To run the local desktop viewer:
```bash
./run_web.sh
```
Then open `http://127.0.0.1:8080` in your browser.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
