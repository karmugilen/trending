//! In-memory cache of parsed rows, one snapshot per slot name. Pages cross JNI
//! once; switching lists, re-rendering every tick, and checking alerts all
//! read the parsed rows from here. Snapshots export to compact JSON so the app
//! can keep them on disk and start instantly, even offline.

use std::collections::HashMap;
use std::sync::{LazyLock, Mutex, MutexGuard, OnceLock};

use serde::{Deserialize, Serialize};

use crate::topics::{self, Pre};
use crate::{bad_data_message, merge_stories, parse_gh, parse_hn, Source, Story};

const VERSION: u32 = 1;
/// Plenty for a busy 24 hours on Hacker News, and a cap on memory.
const MAX_STORIES: usize = 4000;
const MAX_SLOTS: usize = 24;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Snapshot {
    pub v: u32,
    pub source: i32,
    pub origin: i64,
    pub fetched: i64,
    pub total: i64,
    pub stories: Vec<Story>,
    /// Text work for each row, done on first use and kept until the rows change.
    #[serde(skip)]
    pub pre: OnceLock<Vec<Pre>>,
}

impl Snapshot {
    pub fn source(&self) -> Source {
        crate::source_from(self.source)
    }

    /// One [Pre] per row, in row order.
    pub fn prepared(&self) -> &[Pre] {
        self.pre.get_or_init(|| self.stories.iter().map(|s| topics::prepare(s, self.source())).collect())
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Chunk {
    pub count: usize,
    pub oldest: i64,
    pub stored: usize,
}

static STORE: LazyLock<Mutex<HashMap<String, Snapshot>>> = LazyLock::new(|| Mutex::new(HashMap::new()));
static HISTORY: LazyLock<Mutex<HashMap<String, Vec<crate::signals::MetricSample>>>> = LazyLock::new(|| Mutex::new(HashMap::new()));

fn store() -> MutexGuard<'static, HashMap<String, Snapshot>> {
    STORE.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub fn record_history(stories: &[Story], now: i64) {
    let mut guard = HISTORY.lock().unwrap_or_else(|p| p.into_inner());
    for s in stories {
        let entry = guard.entry(s.id.clone()).or_insert_with(Vec::new);
        if let Some(last) = entry.last_mut() {
            if now - last.t < 180 {
                last.score = s.score;
                last.extra = s.extra;
                last.t = now;
                continue;
            }
        }
        entry.push(crate::signals::MetricSample { t: now, score: s.score, extra: s.extra });
        if entry.len() > 96 {
            entry.remove(0);
        }
    }
    if guard.len() > 2000 {
        let cutoff = now - 14 * 86_400;
        guard.retain(|_, v| {
            v.retain(|m| m.t >= cutoff);
            !v.is_empty()
        });
    }
}

pub fn get_history(id: &str) -> Vec<crate::signals::MetricSample> {
    let guard = HISTORY.lock().unwrap_or_else(|p| p.into_inner());
    guard.get(id).cloned().unwrap_or_default()
}

pub fn is_new_and_trending(story: &Story, now: i64) -> bool {
    let repo = crate::canonical_repo(&story.url, &story.title);
    let (owner, name) = match repo {
        Some(r) => r,
        None => return false,
    };
    let guard = store();
    let is_in_off = guard.iter().any(|(k, snap)| {
        k.starts_with("ui:gh:off:") && snap.stories.iter().any(|s| {
            crate::canonical_repo(&s.url, &s.title).as_ref() == Some(&(owner.clone(), name.clone()))
        })
    });
    let is_in_new = guard.iter().any(|(k, snap)| {
        k.starts_with("ui:gh:") && !k.starts_with("ui:gh:off:") && snap.stories.iter().any(|s| {
            crate::canonical_repo(&s.url, &s.title).as_ref() == Some(&(owner.clone(), name.clone()))
        })
    });
    (is_in_off && is_in_new) || (is_in_off && story.t > 0 && (now - story.t) <= 7 * 86_400)
}

pub fn export_history() -> String {
    let guard = HISTORY.lock().unwrap_or_else(|p| p.into_inner());
    serde_json::to_string(&*guard).unwrap_or_else(|_| "{}".to_string())
}

pub fn import_history(json: &str) -> bool {
    if let Ok(map) = serde_json::from_str::<HashMap<String, Vec<crate::signals::MetricSample>>>(json) {
        let mut guard = HISTORY.lock().unwrap_or_else(|p| p.into_inner());
        for (k, v) in map {
            guard.insert(k, v);
        }
        true
    } else {
        false
    }
}

/// Parse one page and keep it under `slot`. With `append`, the page joins the
/// rows already there (same origin only), and the first page's total stays.
pub fn ingest(slot: &str, source: Source, origin: i64, now: i64, body: &str, append: bool) -> Result<Chunk, String> {
    let parsed = match source {
        Source::Hn => parse_hn(body),
        Source::Gh => parse_gh(body),
    };
    let (stories, total) = parsed.map_err(|()| bad_data_message(source))?;
    let count = stories.len();
    let oldest = stories.iter().map(|s| s.t).filter(|t| *t > 0).min().unwrap_or(0);
    record_history(&stories, now);
    let mut map = store();
    let joined = match map.get_mut(slot) {
        Some(existing) if append && existing.origin == origin && existing.source == source as i32 => {
            let mut merged = merge_stories(&existing.stories, &stories);
            merged.truncate(MAX_STORIES);
            existing.stories = merged;
            existing.pre = OnceLock::new();
            existing.fetched = now;
            existing.stories.len()
        }
        _ => {
            let mut stories = stories;
            stories.truncate(MAX_STORIES);
            let stored = stories.len();
            evict(&mut map, slot);
            map.insert(
                slot.to_string(),
                Snapshot { v: VERSION, source: source as i32, origin, fetched: now, total, stories, pre: OnceLock::new() },
            );
            stored
        }
    };
    Ok(Chunk { count, oldest, stored: joined })
}

/// Drop the oldest slot when full so a long session cannot grow forever.
fn evict(map: &mut HashMap<String, Snapshot>, incoming: &str) {
    if map.len() < MAX_SLOTS || map.contains_key(incoming) {
        return;
    }
    if let Some(oldest) = map.iter().min_by_key(|(_, snap)| snap.fetched).map(|(k, _)| k.clone()) {
        map.remove(&oldest);
    }
}

pub fn with_slot<T>(slot: &str, f: impl FnOnce(&Snapshot) -> T) -> Option<T> {
    store().get(slot).map(f)
}

pub fn export(slot: &str) -> Option<String> {
    with_slot(slot, |snap| serde_json::to_string(snap).ok()).flatten()
}

/// Restore a slot from disk. A snapshot already in memory wins unless the
/// imported one is newer.
pub fn import(slot: &str, json: &str) -> bool {
    let Ok(snap) = serde_json::from_str::<Snapshot>(json) else { return false };
    if snap.v != VERSION || (snap.source != 0 && snap.source != 1) {
        return false;
    }
    let mut map = store();
    if let Some(existing) = map.get(slot) {
        if existing.fetched >= snap.fetched {
            return false;
        }
    }
    evict(&mut map, slot);
    map.insert(slot.to_string(), snap);
    true
}

#[cfg(test)]
pub fn clear() {
    store().clear();
}

#[cfg(test)]
mod tests {
    use super::*;

    fn page(ids: &[(i32, i64, i32)]) -> String {
        let hits: Vec<String> = ids
            .iter()
            .map(|(id, t, pts)| format!(r#"{{"objectID":"{id}","title":"s{id}","points":{pts},"created_at_i":{t}}}"#))
            .collect();
        format!(r#"{{"nbHits":{},"hits":[{}]}}"#, 50, hits.join(","))
    }

    #[test]
    fn ingest_replaces_and_appends_by_origin() {
        let slot = "test:ingest";
        let c = ingest(slot, Source::Hn, 100, 1000, &page(&[(1, 300, 5), (2, 200, 9)]), false).unwrap();
        assert_eq!(Chunk { count: 2, oldest: 200, stored: 2 }, c);
        let c = ingest(slot, Source::Hn, 100, 1010, &page(&[(2, 200, 9), (3, 150, 1)]), true).unwrap();
        assert_eq!(Chunk { count: 2, oldest: 150, stored: 3 }, c);
        assert_eq!(Some(50), with_slot(slot, |s| s.total));
        // A different origin cannot append; it replaces.
        let c = ingest(slot, Source::Hn, 120, 1020, &page(&[(4, 400, 1)]), true).unwrap();
        assert_eq!(1, c.stored);
        assert!(ingest(slot, Source::Hn, 120, 1030, "nope", false).is_err());
        assert_eq!(Some(1), with_slot(slot, |s| s.stories.len()));
    }

    #[test]
    fn export_import_round_trips_and_keeps_newer() {
        let slot = "test:disk";
        ingest(slot, Source::Gh, 0, 500, r#"{"total_count":1,"items":[{"id":7,"full_name":"a/b","stargazers_count":3}]}"#, false).unwrap();
        let json = export(slot).unwrap();
        assert!(!import(slot, &json), "same age does not replace");
        let mut older: Snapshot = serde_json::from_str(&json).unwrap();
        older.fetched = 100;
        assert!(!import(slot, &serde_json::to_string(&older).unwrap()));
        assert!(import("test:disk2", &json));
        assert_eq!(Some("gh:7".to_string()), with_slot("test:disk2", |s| s.stories[0].id.clone()));
        assert!(!import("test:disk3", "{\"v\":99}"));
        assert!(!import("test:disk3", "garbage"));
    }
}
