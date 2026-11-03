//! Window math, parsing, and formatting for the trending app.
//! The phone UI calls this through JNI. `cargo test` covers the same cases
//! the Kotlin port used to.

mod android;
pub mod alerts;
pub mod signals;
pub mod store;
pub mod topics;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use std::collections::HashSet;

use signals::Interest;
use topics::{Lang, Pre, Topic};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Source {
    Hn = 0,
    Gh = 1,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    Hot = 0,
    New = 1,
    /// Points or stars per hour, so young fast climbers come first.
    Rising = 2,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Story {
    pub id: String,
    pub title: String,
    pub url: String,
    pub talk: String,
    pub score: f64,
    pub extra: f64,
    pub meta: String,
    pub blurb: String,
    pub t: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct BucketWindow {
    pub hours: i32,
    pub step: i64,
    pub bars: i32,
    pub origin: i64,
}

/// A listed row with what the signals said about it.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Shown {
    #[serde(flatten)]
    pub story: Story,
    pub heat: f64,
    pub flags: u32,
    pub tag: String,
    pub matched: String,
    /// Site the link points to, like "nytimes.com". Empty for GitHub and Ask HN.
    pub domain: String,
    #[serde(default)]
    pub sparkline: String,
    #[serde(default)]
    pub affects_stack: String,
    #[serde(default)]
    pub cross_source: bool,
}

impl std::ops::Deref for Shown {
    type Target = Story;

    fn deref(&self) -> &Story {
        &self.story
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct Feed {
    pub hot_bars: Vec<f64>,
    pub new_bars: Vec<f64>,
    pub shown: Vec<Shown>,
    pub peak: f64,
    pub peak_heat: f64,
    pub total: i64,
    /// Rows dropped as likely spam.
    pub hidden: usize,
    /// Rows that pass the search, before the list limit.
    pub matching: usize,
    /// What the window is talking about, strongest first.
    pub topics: Vec<Topic>,
    /// GitHub languages in the window. Empty for Hacker News.
    pub langs: Vec<Lang>,
    /// How many rows each quick filter would list.
    pub counts: Counts,
}

/// Rows that pass the search, topic, language and bar filters, by kind.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize)]
pub struct Counts {
    pub security: usize,
    pub interest: usize,
    pub rising: usize,
    pub unread: usize,
}

#[derive(Clone, Debug, PartialEq)]
pub struct FeedOptions {
    pub hide_spam: bool,
    pub interests: Vec<Interest>,
    pub sensitivity: u8,
    /// Search words, already normalized. Every word has to start a word in the row.
    pub query: Vec<String>,
    /// How many rows to list.
    pub limit: usize,
    /// A topic chip, normalized. Rows have to mention it.
    pub topic: String,
    /// A GitHub language chip. Rows have to be in it.
    pub lang: String,
    /// A chart bar index. Rows have to be posted in that bar; -1 for any.
    pub bar: i32,
    /// Flag bits; rows need at least one of them. 0 for any.
    pub only: u32,
    /// Ids the user has opened.
    pub seen: HashSet<String>,
    /// List only rows not in [seen].
    pub unread_only: bool,
}

impl Default for FeedOptions {
    fn default() -> Self {
        FeedOptions {
            hide_spam: false,
            interests: Vec::new(),
            sensitivity: 1,
            query: Vec::new(),
            limit: SHOWN_DEFAULT,
            topic: String::new(),
            lang: String::new(),
            bar: -1,
            only: 0,
            seen: HashSet::new(),
            unread_only: false,
        }
    }
}

pub const SHOWN_DEFAULT: usize = 30;
const SHOWN_MAX: usize = 300;

/// Split a search box into normalized words.
pub fn query_words(text: &str) -> Vec<String> {
    signals::normalize(text).split_whitespace().map(str::to_string).collect()
}

fn matches_query(pre: &Pre, words: &[String]) -> bool {
    words.iter().all(|w| {
        let start = format!(" {w}");
        pre.norm.contains(&start) || pre.author.contains(&start)
    })
}

/// "https://www.Example.com:8080/a?b" is "example.com".
pub fn domain_of(url: &str) -> String {
    let rest = url.split_once("://").map_or(url, |(_, r)| r);
    let host = rest.split(['/', '?', '#']).next().unwrap_or("");
    let host = host.rsplit('@').next().unwrap_or(host);
    let host = host.split(':').next().unwrap_or(host).to_lowercase();
    host.strip_prefix("www.").map(str::to_string).unwrap_or(host)
}

pub fn source_from(code: i32) -> Source {
    if code == 1 { Source::Gh } else { Source::Hn }
}

pub fn mode_from(code: i32) -> Mode {
    match code {
        1 => Mode::New,
        2 => Mode::Rising,
        _ => Mode::Hot,
    }
}

pub fn normalize_hours(hours: i32) -> i32 {
    if hours == 1 || hours == 6 || hours == 12 || hours == 24 { hours } else { 6 }
}

pub fn window_for(now_sec: i64, hours: i32) -> BucketWindow {
    let h = normalize_hours(hours);
    let step: i64 = if h == 1 { 600 } else { 3600 };
    let aligned = ((now_sec as f64) / (step as f64)).floor() as i64 * step;
    let bars = if h == 1 { 6 } else { h };
    let origin = aligned - (bars as i64 - 1) * step;
    BucketWindow { hours: h, step, bars, origin }
}

pub fn github_stamp(origin_sec: i64) -> String {
    let (y, m, d, hh, mm, _) = civil_from_unix(origin_sec);
    format!("{y:04}-{m:02}-{d:02}T{hh:02}:{mm:02}:00Z")
}

pub fn fmt_bar_label(epoch_sec: i64, hours: i32, offset_sec: i32) -> String {
    let local = epoch_sec.saturating_add(offset_sec as i64);
    let (_, _, _, hh, mm, _) = civil_from_unix(local);
    // Half-hour zones such as India start every bar at :30, so say so.
    if hours == 1 || mm != 0 {
        format!("{hh:02}:{mm:02}")
    } else {
        format!("{hh:02}")
    }
}

pub fn merge_stories(first: &[Story], second: &[Story]) -> Vec<Story> {
    if second.is_empty() {
        return first.to_vec();
    }
    let mut ids = std::collections::HashSet::with_capacity(first.len());
    for story in first {
        ids.insert(story.id.as_str());
    }
    let mut out = Vec::with_capacity(first.len() + second.len());
    out.extend(first.iter().cloned());
    out.extend(second.iter().filter(|story| !ids.contains(story.id.as_str())).cloned());
    out
}

pub fn build_feed(stories: &[Story], mode: Mode, window: BucketWindow, total: i64) -> Feed {
    let now = window.origin + window.bars as i64 * window.step;
    build_feed_with(stories, Source::Hn, mode, window, total, now, &FeedOptions::default())
}

/// Bars over the window, then the top rows for the list. Charts only count
/// rows inside the window; the list uses every row it was given.
pub fn build_feed_with(
    stories: &[Story],
    source: Source,
    mode: Mode,
    window: BucketWindow,
    total: i64,
    now: i64,
    opts: &FeedOptions,
) -> Feed {
    let pres: Vec<Pre> = stories.iter().map(|s| topics::prepare(s, source)).collect();
    let rows: Vec<(&Story, &Pre)> = stories.iter().zip(pres.iter()).collect();
    assemble(&rows, &[], source, mode, window, total, now, opts)
}

/// [build_feed_with] over prepared rows. `baseline` holds earlier rows from the
/// same download; they are not listed, only compared against for topics.
#[allow(clippy::too_many_arguments)]
fn assemble(
    rows: &[(&Story, &Pre)],
    baseline: &[(&Story, &Pre)],
    source: Source,
    mode: Mode,
    window: BucketWindow,
    total: i64,
    now: i64,
    opts: &FeedOptions,
) -> Feed {
    let end = window.origin + window.bars as i64 * window.step + 180;
    let mut hot = vec![0.0; window.bars as usize];
    let mut fresh = vec![0.0; window.bars as usize];
    let mut hidden = 0;
    let mut inside: Vec<(&Story, &Pre)> = Vec::with_capacity(rows.len());
    let mut counts = Counts::default();
    let mut listed: Vec<(&Story, signals::Signals)> = Vec::with_capacity(rows.len());
    let bucket_of = |t: i64| -> Option<i32> {
        if t >= window.origin && t < end {
            let raw = (((t - window.origin) as f64) / (window.step as f64)).floor() as i32;
            Some(raw.clamp(0, window.bars - 1))
        } else {
            None
        }
    };
    let bar = if (0..window.bars).contains(&opts.bar) { Some(opts.bar) } else { None };
    let topic = opts.topic.trim();
    for &(story, pre) in rows {
        if opts.hide_spam && pre.spam {
            hidden += 1;
            continue;
        }
        let is_gh_trending_story = story.id.starts_with("gh:trending:");
        let bucket = bucket_of(story.t);
        // Charts and topics show the whole window; the filters only narrow the list.
        if is_gh_trending_story {
            inside.push((story, pre));
        } else if let Some(index) = bucket {
            hot[index as usize] += story.score;
            fresh[index as usize] += 1.0;
            inside.push((story, pre));
        }
        if !matches_query(pre, &opts.query)
            || (!topic.is_empty() && !topics::has_topic(pre, topic))
            || (!opts.lang.is_empty() && !story.meta.eq_ignore_ascii_case(&opts.lang))
            || (!is_gh_trending_story && bar.is_some() && bucket != bar)
        {
            continue;
        }
        let sig = signals::read_pre(story, pre, source, now, &opts.interests, opts.sensitivity);
        let unread = !opts.seen.contains(&story.id);
        counts.security += (sig.flags & signals::FLAG_SECURITY != 0) as usize;
        counts.interest += (sig.flags & signals::FLAG_INTEREST != 0) as usize;
        counts.rising += (sig.flags & signals::FLAG_RISING != 0) as usize;
        counts.unread += unread as usize;
        if (opts.only != 0 && sig.flags & opts.only == 0) || (opts.unread_only && !unread) {
            continue;
        }
        listed.push((story, sig));
    }
    let matching = listed.len();
    // Stable sorts, so ties keep arrival order.
    let is_gh_trending = !listed.is_empty() && listed[0].0.id.starts_with("gh:trending:");
    if !is_gh_trending {
        match mode {
            Mode::Hot => listed.sort_by(|a, b| b.0.score.total_cmp(&a.0.score)),
            Mode::New => listed.sort_by(|a, b| b.0.t.cmp(&a.0.t)),
            Mode::Rising => listed.sort_by(|a, b| b.1.heat.total_cmp(&a.1.heat)),
        }
    }
    listed.truncate(opts.limit.clamp(1, SHOWN_MAX));
    let shown: Vec<Shown> = listed
        .into_iter()
        .map(|(story, sig)| {
            let domain = match source {
                Source::Hn if story.url != story.talk => domain_of(&story.url),
                _ => String::new(),
            };
            let samples = store::get_history(&story.id);
            let sparkline = signals::sparkline_for_samples(&samples, 4);
            let cross = source == Source::Hn && canonical_repo(&story.url, &story.title).is_some();
            Shown {
                story: story.clone(),
                heat: sig.heat,
                flags: sig.flags | (if cross { signals::FLAG_CROSS_SOURCE } else { 0 }),
                tag: sig.tag,
                matched: sig.matched,
                domain,
                sparkline,
                affects_stack: sig.affects_stack,
                cross_source: cross || sig.cross_source,
            }
        })
        .collect();
    let peak = max_or_one(shown.iter().map(|row| row.score));
    let peak_heat = max_or_one(shown.iter().map(|row| row.heat));
    let langs = if source == Source::Gh { topics::langs(&inside) } else { Vec::new() };
    let topics = topics::topics(&inside, baseline);
    Feed { hot_bars: hot, new_bars: fresh, shown, peak, peak_heat, total, hidden, matching, topics, langs, counts }
}

fn max_or_one(values: impl Iterator<Item = f64>) -> f64 {
    values.fold(None, |acc: Option<f64>, v| Some(acc.map_or(v, |best| best.max(v)))).unwrap_or(1.0)
}

pub struct SlotFeed {
    pub feed: Feed,
    pub fetched: i64,
    /// The slot starts after the window, so its first hours are missing.
    pub stale: bool,
    /// Built from a wider download. Exact for Hacker News (it has every
    /// story), only a preview for GitHub (its list is a top 40).
    pub derived: bool,
}

/// Feed for a stored slot and any window it covers. A 24h Hacker News
/// download answers 1h, 6h and 12h too, so switching windows needs no network.
pub fn feed_for_slot(slot: &str, mode: Mode, hours: i32, now: i64, opts: &FeedOptions) -> Option<SlotFeed> {
    store::with_slot(slot, |snap| {
        let window = window_for(now, hours);
        let source = snap.source();
        let derived = snap.origin < window.origin;
        let mut rows = Vec::with_capacity(snap.stories.len());
        let mut earlier = Vec::new();
        for pair in snap.stories.iter().zip(snap.prepared()) {
            if derived && pair.0.t < window.origin {
                earlier.push(pair);
            } else {
                rows.push(pair);
            }
        }
        let total = if derived && source == Source::Hn { rows.len() as i64 } else { snap.total };
        let feed = assemble(&rows, &earlier, source, mode, window, total, now, opts);
        SlotFeed { feed, fetched: snap.fetched, stale: snap.origin > window.origin, derived }
    })
}

const HN_FIELDS: &str = "title,url,points,num_comments,created_at_i,author,objectID";

/// Newest stories after `origin`. Algolia stops at 1000 hits per query, so the
/// next older page is a new query bounded by `before` rather than page=1.
pub fn hn_url(origin: i64, before: i64) -> String {
    let filters = if before > 0 {
        format!("created_at_i>{origin},created_at_i<={before}")
    } else {
        format!("created_at_i>{origin}")
    };
    format!(
        "https://hn.algolia.com/api/v1/search_by_date?tags=story&hitsPerPage=1000&attributesToRetrieve={}&attributesToHighlight=none&analytics=false&numericFilters={}",
        url_encode(HN_FIELDS),
        url_encode(&filters),
    )
}

/// GitHub repository search. kind 0 is new repos, 1 is security, 2 is interests.
pub fn gh_url(kind: i32, origin: i64, keywords: &[String]) -> String {
    let stamp = github_stamp(origin);
    let terms: Vec<String> = keywords
        .iter()
        .map(|k| k.replace('"', " ").split_whitespace().collect::<Vec<_>>().join(" "))
        .filter(|k| k.chars().count() >= 2)
        .take(5)
        .map(|k| if k.contains(' ') { format!("\"{k}\"") } else { k })
        .collect();
    let (query, per_page) = match kind {
        1 => (format!("CVE OR exploit OR vulnerability OR RCE OR 0day in:name,description created:>{stamp}"), 30),
        2 if !terms.is_empty() => (format!("{} in:name,description,topics created:>{stamp}", terms.join(" OR ")), 30),
        _ => (format!("created:>{stamp}"), 40),
    };
    format!(
        "https://api.github.com/search/repositories?q={}&sort=stars&order=desc&per_page={per_page}",
        url_encode(&query),
    )
}

/// Hacker News stories that link to the same page (or, for a repo, anywhere in it).
pub fn hn_discuss_url(link: &str) -> String {
    let rest = link.split_once("://").map_or(link, |(_, r)| r);
    let rest = rest.strip_prefix("www.").unwrap_or(rest);
    let rest = rest.split('#').next().unwrap_or(rest).trim_end_matches('/');
    format!(
        "https://hn.algolia.com/api/v1/search?query={}&restrictSearchableAttributes=url&tags=story&hitsPerPage=30&attributesToRetrieve={}&attributesToHighlight=none&analytics=false",
        url_encode(rest),
        url_encode(HN_FIELDS),
    )
}

/// The best Hacker News threads from a [hn_discuss_url] answer, minus `skip_id`.
pub fn discussions_json(body: &str, skip_id: &str) -> String {
    match parse_hn(body) {
        Ok((mut stories, _)) => {
            stories.retain(|s| s.id != skip_id);
            stories.sort_by(|a, b| b.score.total_cmp(&a.score));
            stories.truncate(DISCUSSIONS_MAX);
            serde_json::json!({ "ok": true, "stories": stories }).to_string()
        }
        Err(()) => error_json(&bad_data_message(Source::Hn)),
    }
}

const DISCUSSIONS_MAX: usize = 8;

pub fn canonical_repo(url: &str, title: &str) -> Option<(String, String)> {
    let target = if url.contains("github.com/") { url } else { title };
    if let Some(pos) = target.find("github.com/") {
        let path = &target[pos + 11..];
        let mut parts = path.split('/');
        let owner = parts.next()?;
        let repo = parts.next()?.split('#').next()?.split('?').next()?;
        let repo = repo.strip_suffix(".git").unwrap_or(repo);
        if !owner.is_empty() && !repo.is_empty() && !owner.contains(' ') && !repo.contains(' ') {
            return Some((owner.to_lowercase(), repo.to_lowercase()));
        }
    }
    None
}

#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Briefing {
    pub period_start: i64,
    pub period_end: i64,
    pub read_time_mins: u32,
    pub broke_out: Vec<Shown>,
    pub affects_you: Vec<Shown>,
    pub cross_source: Vec<Shown>,
    pub for_you: Vec<Shown>,
    pub caught_up_count: usize,
}

pub fn generate_briefing(
    since_sec: i64,
    now_sec: i64,
    hn_slot: &str,
    gh_slot: &str,
    stack_deps: &[String],
    interests: &[signals::Interest],
    sensitivity: u8,
) -> Briefing {
    let stack = signals::StackProfile::new(stack_deps.to_vec());
    let mut broke_out = Vec::new();
    let mut affects_you = Vec::new();
    let mut cross_source = Vec::new();
    let mut for_you = Vec::new();
    let mut seen_ids = std::collections::HashSet::new();

    let mut all_stories: Vec<(Source, Story)> = Vec::new();
    store::with_slot(hn_slot, |snap| {
        for s in &snap.stories {
            all_stories.push((Source::Hn, s.clone()));
        }
    });
    store::with_slot(gh_slot, |snap| {
        for s in &snap.stories {
            all_stories.push((Source::Gh, s.clone()));
        }
    });

    let period_start = if since_sec > 0 { since_sec } else { now_sec - 12 * 3600 };
    let mut caught_up = 0;

    for (source, story) in all_stories {
        if seen_ids.contains(&story.id) {
            continue;
        }
        seen_ids.insert(story.id.clone());
        caught_up += 1;

        let pre = topics::prepare(&story, source);
        if pre.spam {
            continue;
        }
        let mut sig = signals::read_pre(&story, &pre, source, now_sec, interests, sensitivity);
        let samples = store::get_history(&story.id);
        sig.sparkline = signals::sparkline_for_samples(&samples, 4);

        let vel = signals::kinetic_velocity(&samples, now_sec, 3600);
        if signals::is_accelerating(&samples, now_sec) || vel >= 25.0 || sig.heat >= 35.0 {
            sig.flags |= signals::FLAG_BREAKOUT;
        }

        if let Some(pkg) = stack.matches(&pre.norm) {
            sig.affects_stack = pkg.clone();
            sig.flags |= signals::FLAG_STACK_AFFECTED;
        }

        if source == Source::Hn && canonical_repo(&story.url, &story.title).is_some() {
            sig.cross_source = true;
            sig.flags |= signals::FLAG_CROSS_SOURCE;
        }

        let domain = match source {
            Source::Hn if story.url != story.talk => domain_of(&story.url),
            _ => String::new(),
        };

        let shown = Shown {
            story,
            heat: sig.heat,
            flags: sig.flags,
            tag: sig.tag,
            matched: sig.matched,
            domain,
            sparkline: sig.sparkline,
            affects_stack: sig.affects_stack,
            cross_source: sig.cross_source,
        };

        if (shown.flags & signals::FLAG_STACK_AFFECTED != 0)
            || ((shown.flags & signals::FLAG_SECURITY != 0) && !shown.affects_stack.is_empty())
        {
            if affects_you.len() < 6 {
                affects_you.push(shown.clone());
            }
        }
        if (shown.flags & signals::FLAG_BREAKOUT != 0) || (shown.flags & signals::FLAG_RISING != 0 && shown.heat >= 30.0) {
            if broke_out.len() < 6 {
                broke_out.push(shown.clone());
            }
        }
        if shown.cross_source {
            if cross_source.len() < 5 {
                cross_source.push(shown.clone());
            }
        }
        if shown.flags & signals::FLAG_INTEREST != 0 {
            if for_you.len() < 6 {
                for_you.push(shown);
            }
        }
    }

    broke_out.sort_by(|a, b| b.heat.total_cmp(&a.heat));
    affects_you.sort_by(|a, b| b.score.total_cmp(&a.score));
    cross_source.sort_by(|a, b| b.heat.total_cmp(&a.heat));
    for_you.sort_by(|a, b| b.heat.total_cmp(&a.heat));

    let items_count = broke_out.len() + affects_you.len() + cross_source.len() + for_you.len();
    let read_time_mins = (items_count as u32 / 3).max(1).min(5);

    Briefing {
        period_start,
        period_end: now_sec,
        read_time_mins,
        broke_out,
        affects_you,
        cross_source,
        for_you,
        caught_up_count: caught_up,
    }
}

pub fn briefing_json(
    since_sec: i64,
    now_sec: i64,
    hn_slot: &str,
    gh_slot: &str,
    stack_json: &str,
    interests_json: &str,
    sensitivity: u8,
) -> String {
    let stack: Vec<String> = serde_json::from_str(stack_json).unwrap_or_default();
    let interests_raw: Vec<String> = serde_json::from_str(interests_json).unwrap_or_default();
    let interests = signals::interests(&interests_raw);
    let briefing = generate_briefing(since_sec, now_sec, hn_slot, gh_slot, &stack, &interests, sensitivity);
    serde_json::to_string(&briefing).unwrap_or_else(|_| "{}".to_string())
}

fn url_encode(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 3);
    for b in text.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

pub fn fmt_compact(n: f64) -> String {
    if !n.is_finite() {
        return "0".to_string();
    }
    let neg = n < 0.0;
    let v = n.abs();
    const SCALES: [(f64, &str); 4] = [
        (1_000_000_000_000.0, "T"),
        (1_000_000_000.0, "B"),
        (1_000_000.0, "M"),
        (1_000.0, "k"),
    ];
    for (div, suffix) in SCALES {
        if v >= div {
            let scaled = v / div;
            let text = if scaled < 10.0 {
                let rounded = (scaled * 10.0).round() / 10.0;
                if rounded >= 10.0 {
                    format!("{}", rounded.round() as i64)
                } else if (rounded - rounded.round()).abs() < 1e-9 {
                    format!("{}", rounded.round() as i64)
                } else {
                    format!("{rounded:.1}")
                }
            } else {
                let whole = scaled.round() as i64;
                if whole >= 1000 && suffix != "T" {
                    let bumped = (whole as f64) * div;
                    return fmt_compact(if neg { -bumped } else { bumped });
                }
                format!("{whole}")
            };
            return if neg { format!("-{text}{suffix}") } else { format!("{text}{suffix}") };
        }
    }
    let whole = v.round() as i64;
    if whole >= 1000 {
        return fmt_compact(if neg { -(whole as f64) } else { whole as f64 });
    }
    if neg { format!("-{whole}") } else { format!("{whole}") }
}

pub fn fmt_ago(t: i64, now: i64) -> String {
    let delta = now - t;
    let span = delta.unsigned_abs();
    let body = if span < 60 {
        "now".to_string()
    } else if span < 3_600 {
        format!("{}m", span / 60)
    } else if span < 86_400 {
        format!("{}h", span / 3_600)
    } else if span < 7 * 86_400 {
        format!("{}d", span / 86_400)
    } else if span < 365 * 86_400 {
        format!("{}w", span / (7 * 86_400))
    } else {
        format!("{}y", span / (365 * 86_400))
    };
    if delta < 0 && body != "now" { format!("in {body}") } else { body }
}

pub fn source_name(source: Source) -> &'static str {
    match source {
        Source::Hn => "Hacker News",
        Source::Gh => "GitHub",
    }
}

/// What to tell the user when a download fails. No code means no connection.
pub fn trouble_message(source: Source, http_code: Option<i32>) -> String {
    let name = source_name(source);
    match (source, http_code) {
        (Source::Gh, Some(403)) | (_, Some(429)) => format!("{name} is limiting requests. Try again in a minute."),
        (_, Some(code)) if code >= 500 => format!("{name} is having trouble right now (error {code})."),
        (_, Some(code)) => format!("{name} turned down the request (error {code})."),
        (_, None) => format!("Couldn't reach {name}. Check your connection."),
    }
}

/// The answer arrived but could not be read.
pub fn bad_data_message(source: Source) -> String {
    format!("{} sent something unexpected. Try again shortly.", source_name(source))
}

pub fn parse_hn(body: &str) -> Result<(Vec<Story>, i64), ()> {
    let root: Value = serde_json::from_str(body).map_err(|_| ())?;
    let hits = root.get("hits").and_then(Value::as_array);
    let mut stories = Vec::new();
    if let Some(hits) = hits {
        for hit in hits {
            let Some(obj) = hit.as_object() else { continue };
            let Some(object_id) = json_text(obj.get("objectID")) else { continue };
            let talk = format!("https://news.ycombinator.com/item?id={object_id}");
            let link = json_text(obj.get("url"));
            stories.push(Story {
                id: format!("hn:{object_id}"),
                title: json_text(obj.get("title")).unwrap_or_else(|| "untitled".to_string()),
                url: link.unwrap_or_else(|| talk.clone()),
                talk,
                score: json_f64(obj.get("points")),
                extra: json_f64(obj.get("num_comments")),
                meta: json_text(obj.get("author")).unwrap_or_default(),
                blurb: String::new(),
                t: json_f64(obj.get("created_at_i")) as i64,
            });
        }
    }
    let total = json_i64(root.get("nbHits")).unwrap_or(stories.len() as i64);
    Ok((stories, total))
}

fn strip_html_tags(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut inside = false;
    for c in s.chars() {
        if c == '<' {
            inside = true;
        } else if c == '>' {
            inside = false;
        } else if !inside {
            out.push(c);
        }
    }
    out.split_whitespace().collect::<Vec<_>>().join(" ")
}

pub fn parse_gh_trending_html(body: &str) -> Result<(Vec<Story>, i64), ()> {
    let mut stories = Vec::new();

    for article in body.split("<article").skip(1) {
        let end_idx = article.find("</article>").unwrap_or(article.len());
        let part = &article[..end_idx];

        let Some(h2_start) = part.find("<h2") else { continue };
        let h2_slice = &part[h2_start..];
        let Some(h2_end) = h2_slice.find("</h2>") else { continue };
        let h2_content = &h2_slice[..h2_end];

        let Some(href_start) = h2_content.find("href=\"/") else { continue };
        let href_slice = &h2_content[href_start + 6..];
        let Some(href_end) = href_slice.find('"') else { continue };
        let repo_path = href_slice[..href_end].trim_matches('/');
        if repo_path.is_empty() || !repo_path.contains('/') {
            continue;
        }

        let title = repo_path.to_string();
        let url = format!("https://github.com/{title}");
        let talk = url.clone();
        let id = format!("gh:trending:{}", title.replace('/', "_"));

        let after_h2 = &h2_slice[h2_end..];
        let blurb = if let Some(p_start) = after_h2.find("<p ").or_else(|| after_h2.find("<p>")) {
            let p_slice = &after_h2[p_start..];
            if let (Some(open_end), Some(close_tag)) = (p_slice.find('>'), p_slice.find("</p>")) {
                if open_end < close_tag {
                    strip_html_tags(&p_slice[open_end + 1..close_tag])
                } else {
                    String::new()
                }
            } else {
                String::new()
            }
        } else {
            String::new()
        };

        let meta = if let Some(lang_start) = part.find("itemprop=\"programmingLanguage\"") {
            let lang_slice = &part[lang_start..];
            if let (Some(tag_close), Some(next_open)) = (lang_slice.find('>'), lang_slice.find("</")) {
                if tag_close < next_open {
                    lang_slice[tag_close + 1..next_open].trim().to_string()
                } else {
                    String::new()
                }
            } else {
                String::new()
            }
        } else {
            String::new()
        };

        let score = if let Some(star_idx) = part.find("/stargazers") {
            let after = &part[star_idx..];
            if let (Some(tag_close), Some(a_end)) = (after.find('>'), after.find("</a>")) {
                if tag_close < a_end {
                    let star_text = strip_html_tags(&after[tag_close + 1..a_end]).replace(',', "");
                    star_text.trim().parse::<f64>().unwrap_or(0.0)
                } else { 0.0 }
            } else { 0.0 }
        } else { 0.0 };

        let extra = if let Some(fork_idx) = part.find("/forks") {
            let after = &part[fork_idx..];
            if let (Some(tag_close), Some(a_end)) = (after.find('>'), after.find("</a>")) {
                if tag_close < a_end {
                    let fork_text = strip_html_tags(&after[tag_close + 1..a_end]).replace(',', "");
                    fork_text.trim().parse::<f64>().unwrap_or(0.0)
                } else { 0.0 }
            } else { 0.0 }
        } else { 0.0 };

        let stars_period = if let Some(idx) = part.find("stars today").or_else(|| part.find("stars this week")).or_else(|| part.find("stars this month")) {
            let before_slice = &part[..idx];
            before_slice
                .split(|c: char| !c.is_ascii_digit() && c != ',')
                .filter(|s| !s.is_empty())
                .last()
                .and_then(|num_str| num_str.replace(',', "").parse::<f64>().ok())
                .unwrap_or(0.0)
        } else {
            0.0
        };

        stories.push(Story {
            id,
            title,
            url,
            talk,
            score,
            extra: if stars_period > 0.0 { stars_period } else { extra },
            meta,
            blurb,
            t: 0,
        });
    }

    if stories.is_empty() {
        return Err(());
    }
    let total = stories.len() as i64;
    Ok((stories, total))
}

pub fn parse_gh(body: &str) -> Result<(Vec<Story>, i64), ()> {
    if body.contains("<article") {
        return parse_gh_trending_html(body);
    }
    let root: Value = serde_json::from_str(body).map_err(|_| ())?;
    let items = root.get("items").and_then(Value::as_array);
    let mut stories = Vec::new();
    if let Some(items) = items {
        for item in items {
            let Some(obj) = item.as_object() else { continue };
            let Some(id) = json_i64(obj.get("id")) else { continue };
            let html = json_text(obj.get("html_url")).unwrap_or_else(|| "https://github.com".to_string());
            stories.push(Story {
                id: format!("gh:{id}"),
                title: json_text(obj.get("full_name")).unwrap_or_else(|| "repo".to_string()),
                url: html.clone(),
                talk: html,
                score: json_f64(obj.get("stargazers_count")),
                extra: json_f64(obj.get("forks_count")),
                meta: json_text(obj.get("language")).unwrap_or_default(),
                blurb: json_text(obj.get("description")).unwrap_or_default(),
                t: json_text(obj.get("created_at")).map(|text| parse_github_time(&text)).unwrap_or(0),
            });
        }
    }
    let total = json_i64(root.get("total_count")).unwrap_or(stories.len() as i64);
    Ok((stories, total))
}

/// Parse both pages, drop duplicate Hacker News ids, and build the charts.
pub fn aggregate(source: Source, mode: Mode, hours: i32, now_sec: i64, page0: &str, page1: &str) -> Result<Feed, String> {
    let window = window_for(now_sec, hours);
    let (stories, total) = match source {
        Source::Hn => {
            let (first, total) = parse_hn(page0).map_err(|()| bad_data_message(source))?;
            let stories = if page1.is_empty() {
                first
            } else {
                match parse_hn(page1) {
                    Ok((second, _)) => merge_stories(&first, &second),
                    Err(()) => first,
                }
            };
            (stories, total)
        }
        Source::Gh => parse_gh(page0).map_err(|()| bad_data_message(source))?,
    };
    Ok(build_feed(&stories, mode, window, total))
}

pub fn feed_json(feed: &Feed) -> String {
    feed_json_at(feed, 0, false)
}

pub fn feed_json_at(feed: &Feed, fetched: i64, stale: bool) -> String {
    slot_feed_json(&SlotFeed { feed: feed.clone(), fetched, stale, derived: false })
}

pub fn slot_feed_json(slot: &SlotFeed) -> String {
    let (feed, fetched, stale, derived) = (&slot.feed, slot.fetched, slot.stale, slot.derived);
    serde_json::json!({
        "ok": true,
        "total": feed.total,
        "peak": feed.peak,
        "peakHeat": feed.peak_heat,
        "hotBars": feed.hot_bars,
        "newBars": feed.new_bars,
        "shown": feed.shown,
        "hidden": feed.hidden,
        "matching": feed.matching,
        "topics": feed.topics,
        "langs": feed.langs,
        "counts": feed.counts,
        "fetched": fetched,
        "stale": stale,
        "derived": derived,
    }).to_string()
}

pub fn error_json(trouble: &str) -> String {
    serde_json::json!({ "ok": false, "trouble": trouble }).to_string()
}

pub fn window_json(now_sec: i64, hours: i32) -> String {
    let window = window_for(now_sec, hours);
    serde_json::json!({
        "hours": window.hours,
        "step": window.step,
        "bars": window.bars,
        "origin": window.origin,
        "stamp": github_stamp(window.origin),
    }).to_string()
}

fn json_text(value: Option<&Value>) -> Option<String> {
    match value {
        Some(Value::String(text)) if !text.is_empty() => Some(text.clone()),
        Some(Value::Number(number)) => Some(number.to_string()),
        _ => None,
    }
}

fn json_f64(value: Option<&Value>) -> f64 {
    match value {
        Some(Value::Number(number)) => number.as_f64().unwrap_or(0.0),
        Some(Value::String(text)) => text.parse().unwrap_or(0.0),
        _ => 0.0,
    }
}

fn json_i64(value: Option<&Value>) -> Option<i64> {
    match value {
        Some(Value::Number(number)) => number.as_i64().or_else(|| number.as_f64().map(|n| n as i64)),
        Some(Value::String(text)) => text.parse().ok(),
        _ => None,
    }
}

fn parse_github_time(text: &str) -> i64 {
    let bytes = text.as_bytes();
    if bytes.len() < 20 || bytes[4] != b'-' || bytes[7] != b'-' || bytes[10] != b'T' || bytes[13] != b':' || bytes[16] != b':' {
        return 0;
    }
    let Some(year) = text[0..4].parse::<i32>().ok() else { return 0 };
    let Some(month) = text[5..7].parse::<u32>().ok() else { return 0 };
    let Some(day) = text[8..10].parse::<u32>().ok() else { return 0 };
    let Some(hour) = text[11..13].parse::<u32>().ok() else { return 0 };
    let Some(min) = text[14..16].parse::<u32>().ok() else { return 0 };
    let Some(sec) = text[17..19].parse::<u32>().ok() else { return 0 };
    if !(1..=12).contains(&month) || !(1..=31).contains(&day) || hour > 23 || min > 59 || sec > 60 {
        return 0;
    }
    let epoch = days_from_civil(year, month, day) * 86_400
        + hour as i64 * 3_600
        + min as i64 * 60
        + sec as i64;
    let rest = text[19..].trim_start_matches(|c: char| c == '.' || c.is_ascii_digit());
    match rest.chars().next() {
        Some('Z') | None => epoch,
        Some('+') | Some('-') => {
            let sign = if rest.starts_with('-') { -1 } else { 1 };
            let off = &rest[1..];
            let (oh, om) = if off.len() >= 5 && off.as_bytes().get(2) == Some(&b':') {
                (off[0..2].parse::<i64>().unwrap_or(0), off[3..5].parse::<i64>().unwrap_or(0))
            } else if off.len() >= 4 {
                (off[0..2].parse::<i64>().unwrap_or(0), off[2..4].parse::<i64>().unwrap_or(0))
            } else {
                (0, 0)
            };
            epoch - sign * (oh * 3_600 + om * 60)
        }
        _ => epoch,
    }
}

fn days_from_civil(y: i32, m: u32, d: u32) -> i64 {
    let m = m as i64;
    let d = d as i64;
    let mut year = y as i64;
    year -= if m <= 2 { 1 } else { 0 };
    let era = if year >= 0 { year } else { year - 399 } / 400;
    let yoe = (year - era * 400) as u64;
    let mp = m + if m > 2 { -3 } else { 9 };
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy as u64;
    era * 146_097 + doe as i64 - 719_468
}

fn civil_from_unix(secs: i64) -> (i32, u32, u32, u32, u32, u32) {
    let day = secs.div_euclid(86_400);
    let rem = secs.rem_euclid(86_400) as u32;
    let (y, m, d) = civil_from_days(day);
    (y, m, d, rem / 3_600, (rem % 3_600) / 60, rem % 60)
}

fn civil_from_days(z: i64) -> (i32, u32, u32) {
    let z = z + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = (z - era * 146_097) as u64;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe as i64 + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let m = (if mp < 10 { mp + 3 } else { mp - 9 }) as u32;
    let year = (y + if m <= 2 { 1 } else { 0 }) as i32;
    (year, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn story(id: &str, score: f64, t: i64) -> Story {
        Story {
            id: id.to_string(),
            title: id.to_string(),
            url: format!("https://example.com/{id}"),
            talk: format!("https://example.com/{id}/talk"),
            score,
            extra: 0.0,
            meta: "author".to_string(),
            blurb: String::new(),
            t,
        }
    }

    #[test]
    fn one_hour_window_has_six_ten_minute_bars() {
        let now = 1_700_000_123;
        let window = window_for(now, 1);
        assert_eq!(1, window.hours);
        assert_eq!(600, window.step);
        assert_eq!(6, window.bars);
        let aligned = (now / 600) * 600;
        assert_eq!(aligned - 5 * 600, window.origin);
    }

    #[test]
    fn six_hour_window_is_hourly_and_aligned() {
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        assert_eq!(6, window.bars);
        assert_eq!(3600, window.step);
        let aligned = (now / 3600) * 3600;
        assert_eq!(aligned - 5 * 3600, window.origin);
    }

    #[test]
    fn odd_hour_falls_back_to_six() {
        assert_eq!(6, window_for(1_700_000_000, 5).hours);
        assert_eq!(24, window_for(1_700_000_000, 24).bars);
    }

    #[test]
    fn charts_use_the_window_and_the_list_uses_every_hit() {
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        let inside = story("a", 10.0, window.origin + 10);
        let next = story("b", 4.0, window.origin + 3600 + 10);
        let slack = story("c", 7.0, window.origin + 6 * 3600 + 100);
        let too_late = story("d", 100.0, window.origin + 6 * 3600 + 200);
        let too_early = story("e", 3.0, window.origin - 5);
        let feed = build_feed(&[inside, next, slack, too_late, too_early], Mode::Hot, window, 99);
        assert_eq!(10.0, feed.hot_bars[0]);
        assert_eq!(4.0, feed.hot_bars[1]);
        assert_eq!(7.0, feed.hot_bars[5]);
        assert_eq!(1.0, feed.new_bars[0]);
        assert_eq!(1.0, feed.new_bars[1]);
        assert_eq!(1.0, feed.new_bars[5]);
        assert_eq!(0.0, feed.hot_bars[2]);
        assert_eq!(vec!["d", "a", "c", "b", "e"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(100.0, feed.peak);
        assert_eq!(99, feed.total);
    }

    #[test]
    fn upcoming_sorts_by_time_and_caps_at_thirty() {
        let window = window_for(1_700_000_000, 6);
        let stories: Vec<_> = (0..40).map(|i| story(&format!("s{i}"), i as f64, window.origin + i)).collect();
        let feed = build_feed(&stories, Mode::New, window, 40);
        assert_eq!(30, feed.shown.len());
        assert_eq!("s39", feed.shown[0].id);
        assert_eq!("s10", feed.shown.last().unwrap().id);
    }

    #[test]
    fn equal_scores_keep_arrival_order() {
        let window = window_for(1_700_000_000, 1);
        let stories = vec![story("first", 5.0, window.origin), story("second", 5.0, window.origin + 1)];
        let feed = build_feed(&stories, Mode::Hot, window, 2);
        assert_eq!(vec!["first", "second"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
    }

    #[test]
    fn empty_list_peaks_at_one_and_zero_scores_stay_zero() {
        let window = window_for(1_700_000_000, 6);
        let empty = build_feed(&[], Mode::Hot, window, 0);
        assert_eq!(1.0, empty.peak);
        assert_eq!(vec![0.0; 6], empty.hot_bars);
        let zeros = build_feed(&[story("z", 0.0, window.origin)], Mode::Hot, window, 1);
        assert_eq!(0.0, zeros.peak);
        assert_eq!(0.0, zeros.hot_bars[0]);
        assert_eq!(1.0, zeros.new_bars[0]);
    }

    #[test]
    fn merge_drops_duplicate_ids_from_the_older_page() {
        let window = window_for(1_700_000_000, 12);
        let a = story("hn:1", 1.0, window.origin);
        let b = story("hn:2", 2.0, window.origin);
        let again = story("hn:1", 9.0, window.origin + 1);
        let c = story("hn:3", 3.0, window.origin + 1);
        let merged = merge_stories(&[a, b], &[again, c]);
        assert_eq!(vec!["hn:1", "hn:2", "hn:3"], merged.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(1.0, merged[0].score);
    }

    #[test]
    fn github_stamp_is_utc() {
        assert_eq!("1970-01-01T00:00:00Z", github_stamp(0));
        assert_eq!("1970-01-01T01:10:00Z", github_stamp(3600 + 600));
    }

    #[test]
    fn bar_labels_follow_the_offset() {
        assert_eq!("00", fmt_bar_label(0, 6, 0));
        assert_eq!("01:10", fmt_bar_label(3600 + 600, 1, 0));
        assert_eq!("05:30", fmt_bar_label(0, 1, 5 * 3600 + 30 * 60));
        assert_eq!("05:30", fmt_bar_label(0, 6, 5 * 3600 + 30 * 60));
        assert_eq!("05", fmt_bar_label(0, 6, 5 * 3600));
    }

    #[test]
    fn compact_numbers_match_the_owl_rules() {
        assert_eq!("950", fmt_compact(950.0));
        assert_eq!("999", fmt_compact(999.4));
        assert_eq!("1k", fmt_compact(1000.0));
        assert_eq!("1.5k", fmt_compact(1500.0));
        assert_eq!("2k", fmt_compact(1950.0));
        assert_eq!("12k", fmt_compact(12_000.0));
        assert_eq!("3.4M", fmt_compact(3_400_000.0));
        assert_eq!("2.5B", fmt_compact(2_500_000_000.0));
        assert_eq!("1T", fmt_compact(1_000_000_000_000.0));
        assert_eq!("1M", fmt_compact(999_600.0));
        assert_eq!("-1.5k", fmt_compact(-1500.0));
    }

    #[test]
    fn ages_use_the_short_units() {
        let now = 10_000_000;
        assert_eq!("now", fmt_ago(now - 30, now));
        assert_eq!("5m", fmt_ago(now - 5 * 60, now));
        assert_eq!("3h", fmt_ago(now - 3 * 3600 - 100, now));
        assert_eq!("2d", fmt_ago(now - 2 * 86_400, now));
        assert_eq!("6w", fmt_ago(now - 6 * 7 * 86_400, now));
        assert_eq!("1y", fmt_ago(now - 365 * 86_400, now));
        assert_eq!("in 10m", fmt_ago(now + 10 * 60, now));
        assert_eq!("now", fmt_ago(now + 10, now));
    }

    #[test]
    fn trouble_copy_names_the_source_and_the_fix() {
        assert_eq!("Hacker News is having trouble right now (error 500).", trouble_message(Source::Hn, Some(500)));
        assert_eq!("Couldn't reach Hacker News. Check your connection.", trouble_message(Source::Hn, None));
        assert_eq!("GitHub is limiting requests. Try again in a minute.", trouble_message(Source::Gh, Some(403)));
        assert_eq!("GitHub turned down the request (error 422).", trouble_message(Source::Gh, Some(422)));
        assert_eq!("GitHub sent something unexpected. Try again shortly.", bad_data_message(Source::Gh));
    }

    #[test]
    fn hacker_news_parse_fills_missing_links_and_keeps_the_total() {
        let body = r#"{"nbHits":9,"hits":[
            {"objectID":"10","title":"Hello","url":"https://example.com","points":4,"num_comments":2,"created_at_i":100,"author":"ada"},
            {"objectID":"11","points":null,"url":"","author":null}
        ]}"#;
        let (stories, total) = parse_hn(body).unwrap();
        assert_eq!(9, total);
        assert_eq!("hn:10", stories[0].id);
        assert_eq!("https://example.com", stories[0].url);
        assert_eq!("https://news.ycombinator.com/item?id=10", stories[0].talk);
        assert_eq!(4.0, stories[0].score);
        assert_eq!("ada", stories[0].meta);
        assert_eq!("untitled", stories[1].title);
        assert_eq!("https://news.ycombinator.com/item?id=11", stories[1].url);
        assert_eq!(0.0, stories[1].score);
        assert_eq!("", stories[1].meta);
    }

    #[test]
    fn github_parse_reads_the_repo_fields() {
        let body = r#"{"total_count":3,"items":[{
            "id":42,"full_name":"a/b","html_url":"https://github.com/a/b",
            "stargazers_count":8,"forks_count":1,"language":"Rust",
            "description":"fast","created_at":"1970-01-01T01:00:00Z"
        }]}"#;
        let (stories, total) = parse_gh(body).unwrap();
        assert_eq!(3, total);
        assert_eq!("gh:42", stories[0].id);
        assert_eq!("a/b", stories[0].title);
        assert_eq!(8.0, stories[0].score);
        assert_eq!("Rust", stories[0].meta);
        assert_eq!("fast", stories[0].blurb);
        assert_eq!(3600, stories[0].t);
    }

    #[test]
    fn aggregate_merges_hacker_news_pages_and_rejects_bad_json() {
        let now = 1_700_000_000;
        let origin = window_for(now, 12).origin;
        let page0 = format!(r#"{{"nbHits":2,"hits":[{{"objectID":"1","title":"A","points":5,"created_at_i":{origin}}}]}}"#);
        let page1 = format!(r#"{{"hits":[{{"objectID":"1","title":"A","points":9,"created_at_i":{origin}}},{{"objectID":"2","title":"B","points":1,"created_at_i":{origin}}}]}}"#);
        let feed = aggregate(Source::Hn, Mode::Hot, 12, now, &page0, &page1).unwrap();
        assert_eq!(2, feed.total);
        assert_eq!(vec!["1", "2"], feed.shown.iter().map(|s| s.id.trim_start_matches("hn:")).collect::<Vec<_>>());
        assert!(aggregate(Source::Hn, Mode::Hot, 6, now, "not-json", "").is_err());
    }

    #[test]
    fn rising_sorts_by_points_per_hour_and_flags_rows() {
        let now = 1_700_000_000;
        let window = window_for(now, 24);
        let old_big = story("big", 900.0, now - 18 * 3600);
        let young = story("young", 120.0, now - 3600);
        let sec = Story { title: "Chrome zero-day exploited".into(), ..story("sec", 50.0, now - 2 * 3600) };
        let opts = FeedOptions { interests: signals::interests(&["chrome".into()]), ..FeedOptions::default() };
        let feed = build_feed_with(&[old_big, young, sec], Source::Hn, Mode::Rising, window, 3, now, &opts);
        assert_eq!(vec!["young", "big", "sec"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(120.0, feed.peak_heat);
        assert!(feed.shown[0].flags & signals::FLAG_RISING != 0);
        assert_eq!(signals::FLAG_SECURITY | signals::FLAG_INTEREST, feed.shown[2].flags);
        assert_eq!("zero-day", feed.shown[2].tag);
        assert_eq!("chrome", feed.shown[2].matched);
        let json = feed_json_at(&feed, 5, true);
        assert!(json.contains("\"heat\":120.0") && json.contains("\"stale\":true") && json.contains("\"title\":\"young\""));
    }

    #[test]
    fn spam_is_hidden_from_list_and_charts_on_request() {
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        let spam = Story { title: "bot/.github".into(), blurb: "pc-desktop".into(), ..story("gh:1", 500.0, now - 60) };
        let real = Story { title: "rust-lang/rust".into(), ..story("gh:2", 10.0, now - 60) };
        let opts = FeedOptions { hide_spam: true, ..FeedOptions::default() };
        let feed = build_feed_with(&[spam.clone(), real.clone()], Source::Gh, Mode::Hot, window, 2, now, &opts);
        assert_eq!(1, feed.hidden);
        assert_eq!(vec!["gh:2"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(10.0, feed.hot_bars.iter().sum::<f64>());
        let shown = build_feed_with(&[spam, real], Source::Gh, Mode::Hot, window, 2, now, &FeedOptions::default());
        assert_eq!(0, shown.hidden);
        assert!(shown.shown[0].flags & signals::FLAG_SPAM != 0);
    }

    #[test]
    fn stale_slots_drop_rows_that_left_the_window() {
        let then = 1_700_000_000;
        let origin = window_for(then, 1).origin;
        let page = format!(
            r#"{{"nbHits":2,"hits":[{{"objectID":"1","title":"A","points":5,"created_at_i":{a}}},{{"objectID":"2","title":"B","points":1,"created_at_i":{b}}}]}}"#,
            a = origin + 10,
            b = then - 30,
        );
        store::ingest("test:stale", Source::Hn, origin, then, &page, false).unwrap();
        let got = feed_for_slot("test:stale", Mode::Hot, 1, then, &FeedOptions::default()).unwrap();
        assert_eq!((2, then, false, false), (got.feed.shown.len(), got.fetched, got.stale, got.derived));
        assert_eq!(2, got.feed.total);
        // Forty minutes on, the 1h window starts later: the old rows drop out
        // and the count follows the rows that are left.
        let later = then + 40 * 60;
        let got = feed_for_slot("test:stale", Mode::Hot, 1, later, &FeedOptions::default()).unwrap();
        assert!(got.derived && !got.stale);
        assert_eq!(1, got.feed.total);
        assert_eq!(vec!["hn:2"], got.feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        // A 6h window starts before this 1h download, so its first hours are missing.
        assert!(feed_for_slot("test:stale", Mode::Hot, 6, then, &FeedOptions::default()).unwrap().stale);
        assert!(feed_for_slot("test:none", Mode::Hot, 1, then, &FeedOptions::default()).is_none());
    }

    #[test]
    fn search_narrows_the_list_but_not_the_charts() {
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        let rows = vec![
            Story { title: "Rust 2.0 released".into(), ..story("a", 50.0, now - 60) },
            Story { title: "Go generics".into(), blurb: "trusty".into(), ..story("b", 20.0, now - 60) },
            Story { title: "Zig".into(), meta: "Rustacean".into(), ..story("c", 10.0, now - 60) },
        ];
        let opts = FeedOptions { query: query_words("  RUST "), ..FeedOptions::default() };
        let feed = build_feed_with(&rows, Source::Hn, Mode::Hot, window, 3, now, &opts);
        assert_eq!(vec!["a", "c"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(2, feed.matching);
        assert_eq!(80.0, feed.hot_bars.iter().sum::<f64>());
        let both = FeedOptions { query: query_words("rust released"), ..FeedOptions::default() };
        assert_eq!(1, build_feed_with(&rows, Source::Hn, Mode::Hot, window, 3, now, &both).matching);
    }

    #[test]
    fn limit_pages_the_list() {
        let window = window_for(1_700_000_000, 6);
        let rows: Vec<_> = (0..50).map(|i| story(&format!("s{i}"), i as f64, window.origin)).collect();
        let opts = FeedOptions { limit: 45, ..FeedOptions::default() };
        let feed = build_feed_with(&rows, Source::Hn, Mode::Hot, window, 50, 1_700_000_000, &opts);
        assert_eq!((45, 50), (feed.shown.len(), feed.matching));
        assert_eq!(30, build_feed(&rows, Mode::Hot, window, 50).shown.len());
    }

    #[test]
    fn domains_are_short_and_only_for_outside_links() {
        assert_eq!("example.com", domain_of("https://www.Example.com:8080/a?b#c"));
        assert_eq!("news.bbc.co.uk", domain_of("http://user@news.bbc.co.uk/x"));
        assert_eq!("", domain_of(""));
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        let ask = Story { url: "https://news.ycombinator.com/item?id=1".into(), talk: "https://news.ycombinator.com/item?id=1".into(), ..story("ask", 1.0, now) };
        let link = story("link", 2.0, now);
        let feed = build_feed_with(&[ask, link], Source::Hn, Mode::Hot, window, 2, now, &FeedOptions::default());
        assert_eq!(vec!["example.com", ""], feed.shown.iter().map(|s| s.domain.as_str()).collect::<Vec<_>>());
    }

    #[test]
    fn urls_are_encoded_and_bounded() {
        let hn = hn_url(100, 0);
        assert!(hn.contains("numericFilters=created_at_i%3E100"));
        assert!(hn.contains("hitsPerPage=1000"));
        assert!(!hn.contains("page="));
        assert!(hn_url(100, 250).contains("created_at_i%3E100%2Ccreated_at_i%3C%3D250"));
        assert_eq!(
            "https://api.github.com/search/repositories?q=created%3A%3E1970-01-01T00%3A00%3A00Z&sort=stars&order=desc&per_page=40",
            gh_url(0, 0, &[]),
        );
        assert!(gh_url(1, 0, &[]).contains("CVE%20OR%20exploit"));
        let interest = gh_url(2, 0, &["game".into(), " open  source ".into(), "x".into(), "a\"b".into()]);
        assert!(interest.contains("q=game%20OR%20%22open%20source%22%20OR%20%22a%20b%22%20in%3Aname"), "{interest}");
        assert_eq!(gh_url(0, 0, &[]), gh_url(2, 0, &[]));
    }

    #[test]
    fn rate_limits_read_the_same_on_both_sources() {
        assert_eq!("GitHub is limiting requests. Try again in a minute.", trouble_message(Source::Gh, Some(429)));
        assert_eq!("Hacker News is limiting requests. Try again in a minute.", trouble_message(Source::Hn, Some(429)));
        assert_eq!("Hacker News turned down the request (error 403).", trouble_message(Source::Hn, Some(403)));
        assert_eq!(Mode::Rising, mode_from(2));
    }

    #[test]
    fn quick_filters_bar_topic_and_language_narrow_the_list_and_count() {
        let now = 1_700_000_000;
        let window = window_for(now, 6);
        let early = window.origin + 60;
        let rows = vec![
            Story { title: "Chrome zero-day exploited".into(), ..story("a", 50.0, early) },
            Story { title: "AI agents everywhere".into(), ..story("b", 400.0, now - 1800) },
            Story { title: "AI agents at work".into(), ..story("c", 20.0, now - 1800) },
            Story { title: "Gardening".into(), ..story("d", 5.0, early) },
        ];
        let base = FeedOptions { interests: signals::interests(&["gardening".into()]), ..FeedOptions::default() };
        let ids = |opts: &FeedOptions| {
            build_feed_with(&rows, Source::Hn, Mode::Hot, window, 4, now, opts)
                .shown.iter().map(|s| s.id.clone()).collect::<Vec<_>>()
        };
        let all = build_feed_with(&rows, Source::Hn, Mode::Hot, window, 4, now, &base);
        assert_eq!(Counts { security: 1, interest: 1, rising: 1, unread: 4 }, all.counts);
        assert_eq!("ai agents", all.topics[0].word);
        assert_eq!(vec!["a"], ids(&FeedOptions { only: signals::FLAG_SECURITY, ..base.clone() }));
        assert_eq!(vec!["a", "d"], ids(&FeedOptions { only: signals::FLAG_SECURITY | signals::FLAG_INTEREST, ..base.clone() }));
        assert_eq!(vec!["b", "c"], ids(&FeedOptions { topic: "agent".into(), ..base.clone() }));
        // The first bar holds the two early rows; the charts still show everything.
        let first = FeedOptions { bar: 0, ..base.clone() };
        assert_eq!(vec!["a", "d"], ids(&first));
        assert_eq!(475.0, build_feed_with(&rows, Source::Hn, Mode::Hot, window, 4, now, &first).hot_bars.iter().sum::<f64>());
        assert_eq!(4, ids(&FeedOptions { bar: 99, ..base.clone() }).len());
        let seen: HashSet<String> = ["b".to_string()].into();
        let unread = FeedOptions { seen: seen.clone(), unread_only: true, ..base.clone() };
        assert_eq!(vec!["a", "c", "d"], ids(&unread));
        assert_eq!(3, build_feed_with(&rows, Source::Hn, Mode::Hot, window, 4, now, &unread).counts.unread);
        let gh = vec![Story { meta: "Rust".into(), ..story("r", 1.0, now) }, Story { meta: "Go".into(), ..story("g", 2.0, now) }];
        let rust = FeedOptions { lang: "rust".into(), ..FeedOptions::default() };
        let feed = build_feed_with(&gh, Source::Gh, Mode::Hot, window, 2, now, &rust);
        assert_eq!(vec!["r"], feed.shown.iter().map(|s| s.id.as_str()).collect::<Vec<_>>());
        assert_eq!(2, feed.langs.len());
    }

    #[test]
    fn discussions_search_the_link_and_drop_the_story_itself() {
        let url = hn_discuss_url("https://www.github.com/astral-sh/uv/#readme");
        assert!(url.contains("query=github.com%2Fastral-sh%2Fuv&restrictSearchableAttributes=url"), "{url}");
        let body = r#"{"hits":[
            {"objectID":"1","title":"uv","points":5,"created_at_i":1},
            {"objectID":"2","title":"uv again","points":90,"created_at_i":2},
            {"objectID":"3","title":"self","points":900,"created_at_i":3}
        ]}"#;
        let json = discussions_json(body, "hn:3");
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();
        let ids: Vec<&str> = parsed["stories"].as_array().unwrap().iter().map(|s| s["id"].as_str().unwrap()).collect();
        assert_eq!(vec!["hn:2", "hn:1"], ids);
        assert!(discussions_json("nope", "").contains("\"ok\":false"));
    }

    #[test]
    fn unix_epoch_is_the_civil_epoch() {
        assert_eq!(0, days_from_civil(1970, 1, 1));
        assert_eq!((1970, 1, 1, 0, 0, 0), civil_from_unix(0));
        assert_eq!(3600, parse_github_time("1970-01-01T01:00:00.123Z"));
        assert_eq!(0, parse_github_time("1970-01-01T05:30:00+05:30"));
    }

    #[test]
    fn canonical_repo_and_briefing_tests() {
        assert_eq!(
            Some(("astral-sh".to_string(), "uv".to_string())),
            canonical_repo("https://github.com/astral-sh/uv#readme", "")
        );
        assert_eq!(
            Some(("tokio-rs".to_string(), "tokio".to_string())),
            canonical_repo("", "Release v1.0 at github.com/tokio-rs/tokio")
        );
        assert_eq!(None, canonical_repo("https://news.ycombinator.com", "Just a normal title"));

        let hn_slot = "test:briefing:hn";
        let gh_slot = "test:briefing:gh";
        let now = 10_000;
        let hn_body = r#"{"hits":[
            {"objectID":"101","title":"Critical security advisory CVE-2026-101 in tokio","points":300,"created_at_i":9000,"url":"https://github.com/tokio-rs/tokio"},
            {"objectID":"102","title":"Postgres 18 Released","points":450,"created_at_i":9500,"url":"https://postgres.org"}
        ]}"#;
        let gh_body = r#"{"items":[
            {"id":201,"full_name":"sqlite-vec/sqlite-vec","stargazers_count":1200,"forks_count":50,"description":"vector search for sqlite"}
        ]}"#;
        store::ingest(hn_slot, Source::Hn, 8000, now, hn_body, false).unwrap();
        store::ingest(gh_slot, Source::Gh, 8000, now, gh_body, false).unwrap();

        let json = briefing_json(8000, now, hn_slot, gh_slot, r#"["tokio", "postgres"]"#, r#"["sqlite"]"#, 1);
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert!(parsed["read_time_mins"].as_u64().unwrap() >= 1);
        assert!(!parsed["affects_you"].as_array().unwrap().is_empty(), "Tokio security story should affect user");
        assert!(!parsed["cross_source"].as_array().unwrap().is_empty(), "Tokio repo linked on HN should be cross_source");
    }

    #[test]
    fn github_trending_html_parses_repos() {
        let sample = r#"
          <article class="Box-row">
            <h2 class="h3 lh-condensed">
              <a href="/vercel-labs/agent-desktop" class="Link">
                <span class="text-normal">vercel-labs / </span>agent-desktop
              </a>
            </h2>
            <p class="col-9 color-fg-muted my-1">Computer use CLI for AI agents</p>
            <div class="f6 color-fg-muted mt-2">
              <span class="tmp-mr-3"><span itemprop="programmingLanguage">Rust</span></span>
              <a href="/vercel-labs/agent-desktop/stargazers">10,547</a>
              <a href="/vercel-labs/agent-desktop/forks">707</a>
              <span class="float-sm-right">2,275 stars today</span>
            </div>
          </article>
        "#;
        let (stories, total) = parse_gh(sample).unwrap();
        assert_eq!(1, total);
        assert_eq!("vercel-labs/agent-desktop", stories[0].title);
        assert_eq!("https://github.com/vercel-labs/agent-desktop", stories[0].url);
        assert_eq!("Rust", stories[0].meta);
        assert_eq!("Computer use CLI for AI agents", stories[0].blurb);
        assert_eq!(10547.0, stories[0].score);
        assert_eq!(2275.0, stories[0].extra);
    }
}
