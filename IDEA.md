# Trending: Product Direction

Based on a read of the 1.4.0 source (`core/`, `app/`, `web/`). Market claims are my judgment and have not been researched or tested with users.

## The short version

**Today:** a fast, filterable feed of Hacker News stories and new GitHub repos, with security, spam and interest tags.

**Proposed:** a personal radar for developers.

> **Know what broke out, what's being exploited, and what affects your stack, in two minutes a day.**

A feed competes with the HN front page, GitHub Trending, and every other HN client, and those are free and already a habit. A tool that answers "did I miss anything that matters to me?" and then lets you close it is a different product.

The three biggest changes, in order:

1. **Record history**, so that "rising" means actually rising.
2. **Add a "Since you last looked" briefing** as the home screen.
3. **Match security news against the user's own dependencies.**

## What's already good

Keep these. They are the base the new direction builds on.

- **Topic detection with lift** (`core/src/topics.rs`): comparing a topic's rate now against earlier rows is already an emerging-trend detector.
- **Alert discipline** (`core/src/alerts.rs`): a story alerts once, bursts are capped, and spam is excluded.
- **GitHub spam filter** (`core/src/signals.rs`): it catches a real problem that makes raw GitHub data unpleasant to browse.
- **Cross-linking** (`core/src/lib.rs`, `hn_discuss_url`): finding the HN threads for a repo is a small feature that hints at the bigger idea below.
- **No account and no backend on Android:** this is a real privacy and cost advantage, so new features should preserve it where possible.

## Gaps in the current product

| Gap | Where | Why it matters |
|---|---|---|
| "Rising" is really a lifetime average | `heat = score / age` in `signals.rs` | A story that peaked hours ago still looks hot, and one that just took off looks ordinary. |
| GitHub only shows repos *created* inside the window | `created:>stamp` in `gh_url` | An old repo gaining 2,000 stars today never appears, and that is most of what "trending" means on GitHub. |
| History is thrown away | The worker overwrites its slots (`append = false`) | No "up 3 days running", no week-over-week, no comparison beyond one download. |
| Security is keyword matching | `SECURITY_TERMS` | It can't tell a critical bug being exploited now from a blog post about CVEs, or whether it affects you. |
| Sources are separate tabs | `source` is `hn` or `gh` | The most interesting signal, one project moving on both at once, is invisible. |
| Interests are exact keywords | `Interest::matches` | No exclusions and no learning. "AI" matches almost everything, and related terms are missed. |
| Alerts are one-shot | `state.sent` | No follow-up when a story you were alerted about turns out to be much bigger. |

## The roadmap

### Phase 1: Real signals (the foundation)

**Snapshots.** On each worker run, store `(id, time, score, comments)` in SQLite on the device.

- `WatchWorker` already polls every 15+ minutes, so most of the collection already exists.
- Replace lifetime heat with **recent velocity**: points gained in the last hour or so.
- Add **acceleration**, so the app can say "gaining faster than an hour ago".
- Keep about 14 days and prune older data. Rows are small.

**A true GitHub momentum feed.**

- Track a pool of repos: anything that has appeared in the app, plus saved and followed repos.
- Compute stars gained per day from snapshots, which catches old repos taking off.
- Watch the rate limit. Search allows far fewer requests than core REST, so use search for discovery and batched GraphQL for refreshing known repos with the user's token.

**Better ranking.** Compare each item with what's normal for its source and time of day, so 40 points at 3am UTC can outrank 40 points at peak US hours.

*Done when:* a story's sparkline matches its real climb, and an old repo going viral shows up within a few hours.

### Phase 2: The briefing (the new home screen)

Open the app and see **what changed since your last visit**, not a list:

```
Since yesterday 9:14 PM · 3 min read

BROKE OUT
  sqlite-vec          ▁▂▅█  +1.2k stars · on HN too
  "Postgres 18"       ▁▁▃█  new topic, 6× usual

AFFECTS YOU
  CVE-2026-xxxx in tokio   KEV-listed · in 2 of your projects

FOR YOU
  3 stories on Rust, 1 on Linux

You've seen the rest. ✓
```

- **It has an end.** "You're caught up" is the feature, and the opposite of an endless feed.
- **Scheduled digest notification:** one a day at a chosen time, with instant alerts kept for critical items only. Add quiet hours.
- **Home screen widget:** top three items since last look. Android currently has none, and it is the cheapest route to a daily habit.
- **Story clusters:** group several posts about the same event by shared URL and title terms, and show one card.
- **Cross-source projects:** treat a repo and its HN threads as one entity, and merge `gh` and `hn` into a combined view. "Moving on both" is the strongest signal the app could offer.

*Done when:* the user can act on a day's news from the briefing alone without opening the full feed.

### Phase 3: "Does this affect me?" (the differentiator)

This is the part generic readers can't easily copy.

- **Stack profile:** the user adds a `package.json`, `Cargo.lock`, `requirements.txt`, or picks GitHub repos. Parse it on the device and store it locally.
- **Enrichment using free public data:**
  - [OSV](https://osv.dev): maps vulnerabilities to package versions
  - [CISA KEV](https://www.cisa.gov/known-exploited-vulnerabilities-catalog): confirmed exploited in the wild
  - [EPSS](https://www.first.org/epss/): probability of exploitation
- **Real severity:** replace the keyword labels with "CVSS 9.8 · actively exploited · your version is affected · fixed in 1.38.2".
- **Stack as interests:** dependencies seed the interest list automatically, so setup takes seconds, and releases and news about them surface too.
- **Privacy:** OSV lookups reveal package names to a third party. Download the data in bulk where practical, and say clearly what leaves the device.

*Done when:* a new KEV entry hitting a user's dependency produces one correct notification, and an unrelated CVE produces none.

### Phase 4: Smarter personalization

- **Learn from behaviour:** opens, saves and dismissals adjust a local score per topic. No account or server needed.
- **Better interest controls:** exclusions (`AI -crypto`), per-interest alert levels, and "more/less like this" on a card.
- **Explain every item:** "Why am I seeing this?" with the actual reason, such as "matches Rust, +180 points in the last hour". Trust is the reason to use this over an algorithmic feed.
- **Optional AI summaries:** one-line "why this matters", plus a summary of the HN thread. Bring-your-own key or on-device, off by default, always labelled, and only for items the user opens or that reach the briefing, to keep costs at zero for the project.
- **Follow-ups:** one extra alert when something you were told about becomes much bigger, or when a saved repo ships a release.

### Phase 5: Reach and sustainability

- **More sources:** Lobsters, Reddit programming subreddits, Product Hunt, arXiv, package registries. Add them behind one `Source` interface, and only ones that strengthen the cross-source signal. Check each one's terms and API limits first.
- **Outputs:** RSS for your filtered view, a webhook, a Slack or Discord daily post, and a terminal command (`trending brief`). These suit the audience and are cheap to build.
- **A hosted web version:** the current web app is single-user with settings in a shared JSON file. Going public needs per-user storage and a shared fetch cache, and it adds real running costs, so do it only after the briefing has proven useful.

**Possible money, if wanted:** keep the reader free and open, and charge for team features such as shared stack monitoring, Slack alerts, and a weekly security report. Teams will pay for "tell us when our dependencies are at risk", while individuals rarely pay for a news reader.

## What not to build

- **Comments, voting, or social features:** HN and GitHub already have the communities.
- **Many sources early:** more input without better ranking just makes more noise.
- **A cloud account for Android:** it would give up the privacy advantage for little gain. If sync is needed, use an encrypted file or the user's own storage.
- **An AI-generated news feed:** it costs money and trust, and removes the reason to believe the signals.

## Fix first

These are existing problems I found while reading the code. The first two are security issues and should be fixed before anyone else runs the web app, or before running it on an untrusted network.

1. **The GitHub token can be read by other machines and websites.** `run_web.sh` binds to `0.0.0.0`, there is no authentication, CORS allows every origin, and `GET /api/settings` returns `githubToken`. Bind to `127.0.0.1`, restrict CORS, and never return the token to the client.
2. **Likely script injection from story titles.** `app.js:418` puts titles into an inline `onclick` string, but `escapeHtml` doesn't escape quotes, and browsers decode entities in attributes anyway. A crafted HN title or repo name could run script in the app, which could then read the token. Use `data-*` attributes with event listeners, and allow only `http(s)` URLs in links, because imported bookmarks are untrusted too. I found this by reading the code and did not run an exploit.
3. **Release APKs are signed with a key published in the source folder.** `debug.keystore` and its passwords are in the project (`BUILD.txt`), so anyone with the source could sign an update your phone would accept. Create a private release key before any public distribution. Changing keys means users reinstall once, so do it early.
4. **The web and Android logic have drifted apart.** `web/engine.py` claims to match Rust, but it doesn't:
   - The rising thresholds differ (HN normal is 20 points and 2/h versus Rust's 100 and 20/h for breaking).
   - The Python spam check lacks the random-owner and `.github` rules.
   - Topics have no stemming or lift.
   - Story IDs differ (`123` versus `hn:123`), which will duplicate bookmarks moved between the two.
   - **Fix:** compile `core/` for the server using PyO3 or WASM, so there is one implementation.
5. **"Breaking" triggers too easily on web.** The alert has no minimum score, and heat floors age at 30 minutes, so 15 points on a brand-new post counts as "Breaking (30/hr)". Use the Rust `Bar` thresholds.
6. **The GitHub token help text is misleading.** The settings page promises 60 to 5,000 requests/hour, but the feed uses the Search API, which is limited to about 10 requests a minute without a token and 30 with one.
7. **Web alerts only work while the tab is open,** because there is no service worker. Say so in the UI, or add a service worker when push support is built.

## What to measure

Keep analytics local or opt-in, to match the privacy stance. Start by tracking your own use.

- **Days per week the briefing gets opened.** This is the main measure of whether the idea works.
- **Time to "caught up":** the target is under 3 minutes.
- **Alert precision:** the share of notifications opened or saved. Below roughly 30%, tighten the thresholds.
- **Lead time:** how much earlier the app flags something than it reaches the HN front page or GitHub Trending. This is the most compelling public proof of value.
- **Security false positives:** alerts for things that don't affect the user. The target is close to zero.

## First three steps

1. Apply fixes 1 and 2 (about an afternoon).
2. Add snapshots and real velocity to `core/`, with tests for the climb and decay cases. Everything else depends on this history.
3. Build a rough "Since you last looked" screen on Android and use it daily for two weeks. If you don't keep opening it, change the briefing before starting on stack matching.
