//! Which rows deserve a notification. The app passes the slots it just
//! refreshed, the user's choices, and the state from last time; it gets back
//! the alerts to post and the state to keep. A row is only ever sent once.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::signals::{self, age_hours, Bar};
use crate::{store, Source, Story};

/// Forget sent rows after four days; nothing that old is still trending.
const KEEP_SECS: i64 = 4 * 86_400;
const MAX_KEPT: usize = 3000;

#[derive(Clone, Debug, Deserialize)]
#[serde(default)]
pub struct AlertConfig {
    pub breaking: bool,
    pub security: bool,
    pub interests: Vec<String>,
    pub sensitivity: u8,
    pub hn: bool,
    pub gh: bool,
    pub max: usize,
}

impl Default for AlertConfig {
    fn default() -> Self {
        AlertConfig { breaking: true, security: true, interests: Vec::new(), sensitivity: 1, hn: true, gh: true, max: 4 }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct AlertState {
    /// Row id to the time it was sent (or skipped because of the cap).
    pub sent: HashMap<String, i64>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Security,
    Breaking,
    Interest,
}

#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Alert {
    pub id: String,
    pub kind: Kind,
    pub source: &'static str,
    pub title: String,
    pub url: String,
    pub talk: String,
    pub score: f64,
    pub extra: f64,
    pub heat: f64,
    pub t: i64,
    pub reason: String,
    pub blurb: String,
    pub meta: String,
}

#[derive(Debug, PartialEq)]
pub struct Outcome {
    pub alerts: Vec<Alert>,
    pub state: AlertState,
    pub suppressed: usize,
}

/// Decide alerts for rows already tagged with their source.
pub fn decide(rows: &[(Source, Story)], cfg: &AlertConfig, mut state: AlertState, now: i64) -> Outcome {
    state.sent.retain(|_, t| now - *t < KEEP_SECS);
    let interests = signals::interests(&cfg.interests);
    let mut candidates: Vec<Alert> = Vec::new();
    let mut looked = std::collections::HashSet::new();
    for (source, story) in rows {
        let wanted = match source {
            Source::Hn => cfg.hn,
            Source::Gh => cfg.gh,
        };
        if !wanted || state.sent.contains_key(&story.id) || !looked.insert(story.id.as_str()) {
            continue;
        }
        let sig = signals::read(story, *source, now, &interests, cfg.sensitivity);
        if sig.flags & signals::FLAG_SPAM != 0 {
            continue;
        }
        let age = age_hours(story.t, now);
        let clears = |bar: Bar| bar.clears(story.score, sig.heat, age);
        let pick = if cfg.security && sig.flags & signals::FLAG_SECURITY != 0 && clears(signals::security_bar(*source, cfg.sensitivity)) {
            Some((Kind::Security, sig.tag.clone()))
        } else if cfg.breaking && sig.flags & signals::FLAG_RISING != 0 {
            Some((Kind::Breaking, format!("{}/h", crate::fmt_compact(sig.heat))))
        } else if sig.flags & signals::FLAG_INTEREST != 0 && clears(signals::interest_bar(*source, cfg.sensitivity)) {
            Some((Kind::Interest, sig.matched.clone()))
        } else {
            None
        };
        if let Some((kind, reason)) = pick {
            candidates.push(Alert {
                id: story.id.clone(),
                kind,
                source: if *source == Source::Gh { "gh" } else { "hn" },
                title: story.title.clone(),
                url: story.url.clone(),
                talk: story.talk.clone(),
                score: story.score,
                extra: story.extra,
                heat: sig.heat,
                t: story.t,
                reason,
                blurb: story.blurb.clone(),
                meta: story.meta.clone(),
            });
        }
    }
    candidates.sort_by(|a, b| a.kind.cmp(&b.kind).then(b.heat.total_cmp(&a.heat)));
    // Everything that qualified is marked, so a burst does not trickle in
    // over the next hour. The app can say "+N more".
    for alert in &candidates {
        state.sent.insert(alert.id.clone(), now);
    }
    let cap = cfg.max.clamp(1, 10);
    let suppressed = candidates.len().saturating_sub(cap);
    candidates.truncate(cap);
    if state.sent.len() > MAX_KEPT {
        let mut by_age: Vec<(String, i64)> = state.sent.drain().collect();
        by_age.sort_by(|a, b| b.1.cmp(&a.1));
        by_age.truncate(MAX_KEPT);
        state.sent = by_age.into_iter().collect();
    }
    Outcome { alerts: candidates, state, suppressed }
}

/// Decide alerts for stored slots. Unknown slots are skipped.
pub fn check(slots: &[String], cfg: &AlertConfig, state_json: &str, now: i64) -> Outcome {
    let state: AlertState = serde_json::from_str(state_json).unwrap_or_default();
    let mut rows: Vec<(Source, Story)> = Vec::new();
    for slot in slots {
        store::with_slot(slot, |snap| {
            let source = snap.source();
            rows.extend(snap.stories.iter().cloned().map(|s| (source, s)));
        });
    }
    decide(&rows, cfg, state, now)
}

pub fn outcome_json(outcome: &Outcome) -> String {
    serde_json::json!({
        "alerts": outcome.alerts,
        "state": serde_json::to_string(&outcome.state).unwrap_or_else(|_| "{}".to_string()),
        "suppressed": outcome.suppressed,
    })
    .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOW: i64 = 1_800_000_000;

    fn row(source: Source, id: &str, title: &str, score: f64, age_h: f64) -> (Source, Story) {
        (
            source,
            Story {
                id: id.to_string(),
                title: title.to_string(),
                url: format!("https://example.com/{id}"),
                talk: String::new(),
                score,
                extra: 0.0,
                meta: String::new(),
                blurb: String::new(),
                t: NOW - (age_h * 3600.0) as i64,
            },
        )
    }

    fn cfg() -> AlertConfig {
        AlertConfig { interests: vec!["game".into()], ..AlertConfig::default() }
    }

    #[test]
    fn picks_breaking_security_and_interest_once() {
        let rows = vec![
            row(Source::Hn, "hn:1", "A new database", 300.0, 2.0),
            row(Source::Hn, "hn:2", "Chrome zero-day exploited in the wild", 40.0, 3.0),
            row(Source::Hn, "hn:3", "I made a game in a weekend", 45.0, 4.0),
            row(Source::Hn, "hn:4", "Quiet post", 3.0, 1.0),
            row(Source::Hn, "hn:5", "Old giant", 900.0, 20.0),
        ];
        let out = decide(&rows, &cfg(), AlertState::default(), NOW);
        let got: Vec<(&str, Kind)> = out.alerts.iter().map(|a| (a.id.as_str(), a.kind)).collect();
        assert_eq!(vec![("hn:2", Kind::Security), ("hn:1", Kind::Breaking), ("hn:3", Kind::Interest)], got);
        assert_eq!("zero-day", out.alerts[0].reason);
        assert_eq!("game", out.alerts[2].reason);
        assert_eq!("150/h", out.alerts[1].reason);
        let again = decide(&rows, &cfg(), out.state, NOW + 900);
        assert!(again.alerts.is_empty());
    }

    #[test]
    fn respects_switches_spam_and_cap() {
        let rows = vec![
            row(Source::Gh, "gh:1", "onchain-trading-bot/.github", 500.0, 2.0),
            row(Source::Gh, "gh:2", "someone/CVE-2026-1111-poc", 120.0, 10.0),
            row(Source::Hn, "hn:1", "A new database", 300.0, 2.0),
        ];
        let mut c = cfg();
        c.hn = false;
        let out = decide(&rows, &c, AlertState::default(), NOW);
        assert_eq!(vec!["gh:2"], out.alerts.iter().map(|a| a.id.as_str()).collect::<Vec<_>>());
        assert_eq!("gh", out.alerts[0].source);
        assert_eq!("CVE-2026-1111", out.alerts[0].reason);

        let many: Vec<_> = (0..7).map(|i| row(Source::Hn, &format!("hn:{i}"), "Big launch", 400.0 + i as f64, 2.0)).collect();
        let out = decide(&many, &AlertConfig { max: 3, ..cfg() }, AlertState::default(), NOW);
        assert_eq!(3, out.alerts.len());
        assert_eq!(4, out.suppressed);
        assert_eq!(7, out.state.sent.len());
        assert_eq!("hn:6", out.alerts[0].id);
    }

    #[test]
    fn state_expires_and_survives_bad_json() {
        let mut state = AlertState::default();
        state.sent.insert("hn:1".into(), NOW - 5 * 86_400);
        let rows = vec![row(Source::Hn, "hn:1", "A new database", 300.0, 2.0)];
        assert_eq!(1, decide(&rows, &cfg(), state, NOW).alerts.len());
        let out = check(&["missing".into()], &cfg(), "not json", NOW);
        assert!(out.alerts.is_empty());
        let json = outcome_json(&out);
        assert!(json.contains("\"suppressed\":0"));
    }
}
