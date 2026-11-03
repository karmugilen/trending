//! Per-row text work done once per download, and the trending topics built
//! from it. A slot keeps its prepared rows, so re-rendering on every tick,
//! keystroke or chip tap never normalizes a title twice.

use std::collections::{HashMap, HashSet};
use std::sync::LazyLock;

use serde::Serialize;

use crate::signals::{self, normalize};
use crate::{Source, Story};

/// What a row says regardless of the clock or the user's settings.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Pre {
    /// Title and description, normalized. On GitHub the language follows, as typed.
    pub norm: String,
    /// The Hacker News author, normalized. Search looks here, the signals do not.
    pub author: String,
    pub security: Option<String>,
    pub spam: bool,
    /// Distinct topic words and two-word phrases.
    pub terms: Vec<String>,
}

pub fn prepare(story: &Story, source: Source) -> Pre {
    let text = format!("{} {}", story.title, story.blurb);
    let mut norm = normalize(&text);
    let mut author = String::new();
    if !story.meta.is_empty() {
        match source {
            // The language field is a fair interest match on GitHub ("rust", "kotlin").
            Source::Gh => {
                norm.push_str(story.meta.to_lowercase().trim());
                norm.push(' ');
            }
            Source::Hn => author = normalize(&story.meta),
        }
    }
    let security = signals::security(&text, &norm);
    let spam = signals::is_spam(source, &story.title, &story.blurb);
    Pre { norm, author, security, spam, terms: terms_for(story, source) }
}

/// A word or phrase many rows in the window share.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Topic {
    pub word: String,
    /// Rows in the window that mention it.
    pub count: usize,
    /// How much more often it shows up now than earlier in the download. 0 when there is nothing to compare.
    pub lift: f64,
    /// Much more common now than before: an emerging topic.
    pub fresh: bool,
}

#[derive(Clone, Debug, PartialEq, Serialize)]
pub struct Lang {
    pub name: String,
    pub count: usize,
}

pub const TOPICS_MAX: usize = 14;
const LANGS_MAX: usize = 8;
/// Fewer earlier rows than this make the comparison noise.
const BASELINE_MIN: usize = 40;

/// Topics across `rows`, ranked by how many and how popular the rows are that
/// mention them. Rows in `baseline` are the earlier part of the same download;
/// a topic far more common now than then ranks higher and is marked fresh.
pub fn topics(rows: &[(&Story, &Pre)], baseline: &[(&Story, &Pre)]) -> Vec<Topic> {
    struct Acc<'a> {
        count: usize,
        weight: f64,
        forms: Vec<(&'a str, usize)>,
    }
    let mut acc: HashMap<String, Acc> = HashMap::new();
    for (story, pre) in rows {
        let weight = 1.0 + story.score.max(0.0).ln_1p();
        for term in &pre.terms {
            let entry = acc.entry(stem(term).to_string()).or_insert(Acc { count: 0, weight: 0.0, forms: Vec::new() });
            entry.count += 1;
            entry.weight += weight;
            match entry.forms.iter_mut().find(|(form, _)| *form == term.as_str()) {
                Some((_, n)) => *n += 1,
                None => entry.forms.push((term.as_str(), 1)),
            }
        }
    }
    let base_n = baseline.len();
    let mut base: HashMap<&str, usize> = HashMap::new();
    if base_n >= BASELINE_MIN {
        for (_, pre) in baseline {
            for term in &pre.terms {
                *base.entry(stem(term)).or_insert(0) += 1;
            }
        }
    }
    let n = rows.len().max(1) as f64;
    // In a busy window two mentions are chance.
    let min_count = if rows.len() >= 100 { 3 } else { 2 };
    // A phrase that covers most uses of its words replaces them.
    let mut covered: HashSet<&str> = HashSet::new();
    for (key, a) in &acc {
        if a.count < min_count || !key.contains(' ') {
            continue;
        }
        for part in key.split(' ') {
            if let Some(word) = acc.get(stem(part)) {
                if a.count * 3 >= word.count * 2 {
                    covered.insert(stem(part));
                }
            }
        }
    }
    let mut ranked: Vec<(f64, Topic)> = acc
        .iter()
        .filter(|(key, a)| a.count >= min_count && !covered.contains(key.as_str()))
        .map(|(key, a)| {
            let lift = if base_n >= BASELINE_MIN {
                let before = (*base.get(key.as_str()).unwrap_or(&0) as f64 + 0.5) / base_n as f64;
                (a.count as f64 / n) / before
            } else {
                0.0
            };
            let boost = if lift > 0.0 { lift.clamp(0.5, 6.0).sqrt() } else { 1.0 };
            let word = a.forms.iter().max_by(|x, y| x.1.cmp(&y.1).then(y.0.cmp(x.0))).map_or(key.as_str(), |f| f.0);
            let fresh = lift >= 3.0 && a.count >= 3;
            (a.weight * boost, Topic { word: word.to_string(), count: a.count, lift: (lift * 10.0).round() / 10.0, fresh })
        })
        .collect();
    ranked.sort_by(|a, b| b.0.total_cmp(&a.0).then(b.1.count.cmp(&a.1.count)).then(a.1.word.cmp(&b.1.word)));
    let mut out: Vec<Topic> = Vec::new();
    for (_, topic) in ranked {
        // "claude code cli" makes two phrases with the same rows; keep the stronger.
        let overlaps = topic.word.contains(' ')
            && out.iter().any(|o| {
                o.word.contains(' ') && o.count.abs_diff(topic.count) <= 1 && o.word.split(' ').any(|w| topic.word.split(' ').any(|x| x == w))
            });
        if overlaps {
            continue;
        }
        out.push(topic);
        if out.len() == TOPICS_MAX {
            break;
        }
    }
    out
}

/// Most common GitHub languages in `rows`.
pub fn langs(rows: &[(&Story, &Pre)]) -> Vec<Lang> {
    let mut count: HashMap<&str, usize> = HashMap::new();
    for (story, _) in rows {
        if !story.meta.is_empty() {
            *count.entry(story.meta.as_str()).or_insert(0) += 1;
        }
    }
    let mut out: Vec<Lang> = count.into_iter().map(|(name, count)| Lang { name: name.to_string(), count }).collect();
    out.sort_by(|a, b| b.count.cmp(&a.count).then(a.name.cmp(&b.name)));
    out.truncate(LANGS_MAX);
    out
}

/// True when the row mentions `topic` (normalized), counting "agent" and "agents" as one.
pub fn has_topic(pre: &Pre, topic: &str) -> bool {
    let want = stem(topic);
    pre.terms.iter().any(|term| stem(term) == want)
}

/// "agents" counts as "agent". Short words and "ss" endings stay as they are.
fn stem(term: &str) -> &str {
    if term.len() > 3 && term.ends_with('s') && !term.ends_with("ss") {
        &term[..term.len() - 1]
    } else {
        term
    }
}

fn terms_for(story: &Story, source: Source) -> Vec<String> {
    let text = match source {
        // The owner is noise; the repo name usually says what it is.
        Source::Gh => {
            let repo = story.title.split_once('/').map_or(story.title.as_str(), |(_, repo)| repo);
            format!("{} {}", split_camel(repo), story.blurb)
        }
        Source::Hn => story.title.clone(),
    };
    let norm = normalize(&text);
    let words: Vec<&str> = norm.split_whitespace().collect();
    let mut out: Vec<String> = Vec::new();
    let mut push = |term: String| {
        if !out.contains(&term) {
            out.push(term);
        }
    };
    for (i, word) in words.iter().enumerate() {
        if !topic_word(word) {
            continue;
        }
        push(word.to_string());
        if let Some(next) = words.get(i + 1) {
            if topic_word(next) && next != word {
                push(format!("{word} {next}"));
            }
        }
    }
    out
}

/// "BlazingFastDotnet" reads as "Blazing Fast Dotnet".
fn split_camel(name: &str) -> String {
    let mut out = String::with_capacity(name.len() + 8);
    let mut prev_lower = false;
    for ch in name.chars() {
        if ch.is_uppercase() && prev_lower {
            out.push(' ');
        }
        prev_lower = ch.is_lowercase() || ch.is_ascii_digit();
        out.push(ch);
    }
    out
}

fn topic_word(word: &str) -> bool {
    let chars = word.chars().count();
    if chars > 24 || word.bytes().all(|b| b.is_ascii_digit()) {
        return false;
    }
    if chars < 3 && !SHORT_WORDS.contains(&word) {
        return false;
    }
    !STOP.contains(word)
}

/// Two-letter words that do carry a topic.
const SHORT_WORDS: &[&str] = &["ai", "ml", "go", "os", "js", "ui", "ux", "vr", "ar", "3d", "5g", "eu", "uk", "qt", "db"];

static STOP: LazyLock<HashSet<&'static str>> = LazyLock::new(|| {
    "the and for are but not you all any can had her was one our out day get has him his how man new now old see two way who
     boy did its let put say she too use with that this from have they will your what when where which while about after
     again also been before being below between both could does doing down during each even every few first further here
     into just like made make many more most much must never next only other over same should some such than then there
     these those through under until very want well were whom would yet why yes off own per via vs versus etc
     show ask tell launch hn hacker news story post posts thread comment comments discussion
     using used uses based build built building simple easy fast faster better best good great free open source tool tools
     app apps application applications project projects repo repository library framework official awesome list lists
     my me we it is an to of in on at by or as be do if so no up
     year years month months week weeks today yesterday time times thing things people way ways part
     why how what who lot lots really still back know think need work works working world life home
     itself them their themselves ours yours mine himself herself
     http https www com org net github io html pdf video videos blog article paper
     can't don dont doesn didn isn wasn aren won wouldn couldn shouldn ve ll re
     getting got going gone take taking took give gives given come comes came look looking looks
     use user users run running runs found find finding try trying tried say says said
     version release released update updated new newest latest first last next
     one two three four five six seven eight nine ten hundred thousand million billion
     day days hour hours minute minutes second seconds
     based powered written write writing implementation implemented support supports supported
     help helps without within across around against among toward towards upon
     less least small big large high low long short full real true false
     via you're i'm it's that's there's here's we're they're"
        .split_whitespace()
        .collect()
});

#[cfg(test)]
mod tests {
    use super::*;

    fn row(id: &str, title: &str, score: f64) -> Story {
        Story {
            id: id.into(),
            title: title.into(),
            url: String::new(),
            talk: String::new(),
            score,
            extra: 0.0,
            meta: String::new(),
            blurb: String::new(),
            t: 1,
        }
    }

    fn prepared(stories: &[Story], source: Source) -> Vec<Pre> {
        stories.iter().map(|s| prepare(s, source)).collect()
    }

    #[test]
    fn terms_skip_stop_words_and_pair_neighbours() {
        let pre = prepare(&row("a", "Show HN: Claude Code agents for Rust", 1.0), Source::Hn);
        assert!(pre.terms.contains(&"claude code".to_string()));
        // Phrases are neighbours only; "for" sits between these two.
        assert!(!pre.terms.contains(&"agents rust".to_string()));
        assert!(!pre.terms.iter().any(|t| t == "show" || t == "hn" || t == "for"));
        let gh = prepare(&Story { blurb: "A tiny 3D engine".into(), ..row("g", "someone/BlazingFastDotnet", 1.0) }, Source::Gh);
        assert!(gh.terms.contains(&"blazing".to_string()) && gh.terms.contains(&"dotnet".to_string()));
        assert!(gh.terms.contains(&"3d engine".to_string()));
        assert!(!gh.terms.contains(&"someone".to_string()));
    }

    #[test]
    fn topics_rank_shared_words_and_phrases_replace_their_words() {
        let stories = vec![
            row("1", "Claude Code ships plugins", 300.0),
            row("2", "Using Claude Code for refactors", 120.0),
            row("3", "Claude Code in CI", 50.0),
            row("4", "AI agents are eating SaaS", 40.0),
            row("5", "Building AI agent memory", 10.0),
            row("6", "Postgres 18 released", 5.0),
        ];
        let pres = prepared(&stories, Source::Hn);
        let rows: Vec<_> = stories.iter().zip(pres.iter()).collect();
        let got = topics(&rows, &[]);
        let words: Vec<&str> = got.iter().map(|t| t.word.as_str()).collect();
        assert_eq!("claude code", words[0], "{words:?}");
        assert!(!words.contains(&"claude") && !words.contains(&"code"), "{words:?}");
        // "agents" and "agent" are one topic.
        assert!(words.contains(&"ai agents") || words.contains(&"ai agent"), "{words:?}");
        assert!(!words.contains(&"postgres"), "one row is not a trend");
        assert_eq!(3, got[0].count);
        assert_eq!(0.0, got[0].lift);
    }

    #[test]
    fn a_word_that_was_rare_before_is_fresh() {
        let mut stories: Vec<Story> = (0..60).map(|i| row(&format!("b{i}"), &format!("Kernel scheduler notes {i}"), 5.0)).collect();
        let base_len = stories.len();
        stories.extend((0..5).map(|i| row(&format!("n{i}"), &format!("Kernel {i}: the Zorbex outage"), 50.0)));
        let pres = prepared(&stories, Source::Hn);
        let all: Vec<_> = stories.iter().zip(pres.iter()).collect();
        let (base, now) = all.split_at(base_len);
        let got = topics(now, base);
        let zorbex = got.iter().find(|t| t.word.contains("zorbex")).expect("zorbex listed");
        assert!(zorbex.fresh && zorbex.lift > 3.0, "{got:?}");
        let kernel = got.iter().find(|t| t.word == "kernel").expect("kernel listed");
        assert!(!kernel.fresh, "{got:?}");
    }

    #[test]
    fn topic_filter_and_langs() {
        let pre = prepare(&row("1", "Why AI agents fail", 1.0), Source::Hn);
        assert!(has_topic(&pre, "agent") && has_topic(&pre, "agents") && has_topic(&pre, "ai agent"));
        assert!(!has_topic(&pre, "fail now"));
        let stories = vec![
            Story { meta: "Rust".into(), ..row("a", "x/a", 1.0) },
            Story { meta: "Rust".into(), ..row("b", "x/b", 1.0) },
            Story { meta: "Go".into(), ..row("c", "x/c", 1.0) },
            row("d", "x/d", 1.0),
        ];
        let pres = prepared(&stories, Source::Gh);
        let rows: Vec<_> = stories.iter().zip(pres.iter()).collect();
        assert_eq!(vec![("Rust", 2), ("Go", 1)], langs(&rows).iter().map(|l| (l.name.as_str(), l.count)).collect::<Vec<_>>());
    }
}
