# Changelog

## 1.5.2 (versionCode 9)

**Heads-up:** releases are now signed with a private release key instead of the public
`debug.keystore`. Android will refuse to update an install signed with the old key, so
existing users must uninstall and reinstall once (export saved stories first).

### Features
- Android: Spikes tab merges HN + Trending + New Repos, ranked by recent spike strength, with Stories/Words views and source/time filters.
- Android: home-screen Breakout widget for quick breakout glances.
- Android / Rust / web: richer growth sparklines (`spark` score samples) and per-tab memory for hours/mode/filters when switching feeds.

### Security
- Release builds read the signing key from env vars or a gitignored `keystore.properties`; with no key the APK is unsigned. The public debug key is never used for releases.
- Web viewer: block cross-site POST/PUT/DELETE (bookmark import CSRF), validate HN ids in `/api/discussions`, bind `127.0.0.1` in `__main__`, allowlist comment sanitizer, `textContent` in toasts.

### Fixes
- Rust core: non-ASCII GitHub timestamps no longer panic (which aborted the app).
- Web viewer: atomic `saved.json`/`settings.json` writes with a lock; a corrupt file is backed up instead of wiped.
- Web viewer: `/api/feed` returns a clear 502 when HN/GitHub is unreachable; stale cache entries are pruned.
- Web viewer: "Open on GitHub" now opens the repository instead of a 404.
- Android: bookmark writes moved off the UI thread; briefing errors are logged.

### Build and tooling
- `web/requirements.txt` (adds missing `python-multipart`) and `web/requirements-dev.txt`.
- `run_web.sh` / `domino_rally.sh` use a project `.venv` instead of a personal shell alias; `domino_rally.sh` reads the version from Gradle.
- Web tests use a temporary data folder and cover the new fixes.
- One version string across app, Rust core and web viewer; F-Droid metadata license corrected to MIT.
