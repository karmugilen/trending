//! Text signals: security news, likely spam repos, interest keywords, and how
//! fast a row is climbing. Everything matches on normalized text, so
//! "CVE-2026-1234", "cve 2026 1234" and "Cve_2026_1234" look the same.

use crate::Source;

pub const FLAG_RISING: u32 = 1;
pub const FLAG_SECURITY: u32 = 2;
pub const FLAG_SPAM: u32 = 4;
pub const FLAG_INTEREST: u32 = 8;
pub const FLAG_ACCELERATING: u32 = 16;
pub const FLAG_BREAKOUT: u32 = 32;
pub const FLAG_STACK_AFFECTED: u32 = 64;
pub const FLAG_CROSS_SOURCE: u32 = 128;
pub const FLAG_NEW_AND_TRENDING: u32 = 256;

/// Lowercase, keep letters and digits, collapse everything else into single
/// spaces, and pad both ends so a phrase can be found with `contains(" x y ")`.
pub fn normalize(text: &str) -> String {
    let mut out = String::with_capacity(text.len() + 2);
    out.push(' ');
    let mut space = true;
    for ch in text.chars() {
        if ch.is_alphanumeric() {
            out.extend(ch.to_lowercase());
            space = false;
        } else if !space {
            out.push(' ');
            space = true;
        }
    }
    if !space {
        out.push(' ');
    }
    out
}

/// Normalized phrase with its weight. A row counts as security news at 2.
const SECURITY_TERMS: &[(&str, u8)] = &[
    ("cve", 2), ("cves", 2), ("zero day", 2), ("zeroday", 2), ("0 day", 2), ("0day", 2),
    ("rce", 2), ("remote code execution", 2), ("vulnerability", 2), ("vulnerabilities", 2),
    ("vulnerable", 2), ("vuln", 2), ("vulns", 2), ("exploit", 2), ("exploits", 2),
    ("exploited", 2), ("exploitation", 2), ("security advisory", 2), ("privilege escalation", 2),
    ("sql injection", 2), ("command injection", 2), ("xss", 2), ("ssrf", 2), ("backdoor", 2),
    ("backdoored", 2), ("supply chain attack", 2), ("ransomware", 2), ("malware", 2),
    ("spyware", 2), ("infostealer", 2), ("cvss", 2), ("security flaw", 2), ("security bug", 2),
    ("security hole", 2), ("use after free", 2), ("buffer overflow", 2), ("heap overflow", 2),
    ("auth bypass", 2), ("authentication bypass", 2), ("sandbox escape", 2), ("data breach", 2),
    ("breached", 2), ("cisa", 2), ("pwned", 2), ("rootkit", 2), ("botnet", 2),
    ("csrf", 1), ("stealer", 1), ("kev", 1), ("poc", 1), ("patch", 1), ("patched", 1),
    ("breach", 1), ("hacked", 1), ("hack", 1), ("hacker", 1), ("hackers", 1), ("leak", 1),
    ("leaks", 1), ("leaked", 1), ("compromised", 1), ("compromise", 1), ("attack", 1),
    ("attacks", 1), ("attackers", 1), ("phishing", 1), ("security", 1), ("threat", 1),
    ("exposed", 1), ("jailbreak", 1), ("scanner", 1), ("injection", 1), ("bypass", 1), ("ddos", 1),
];

/// Lures for cracked software, crypto drainers and cheat loaders that flood
/// GitHub's newest-and-starred list.
const SPAM_TERMS: &[(&str, u8)] = &[
    ("pc desktop", 2), ("executor", 2), ("aimbot", 2), ("keygen", 2), ("drainer", 2),
    ("sniping bot", 2), ("arbitrage bot", 2), ("free download", 2), ("mod menu", 2),
    ("spoofer", 2), ("cheat", 1), ("cheats", 1), ("crack", 1), ("cracked", 1), ("activator", 1),
    ("injector", 1), ("loader", 1), ("unlocker", 1), ("trading bot", 1), ("airdrop", 1),
    ("whale tracker", 1), ("smart money", 1),
];

/// Security weight and the best label for it, for example "CVE-2026-1234" or "RCE".
pub fn security(text: &str, norm: &str) -> Option<String> {
    let mut weight = 0u32;
    let mut best: Option<(&str, u8)> = None;
    for &(term, w) in SECURITY_TERMS {
        if contains_phrase(norm, term) {
            weight += w as u32;
            if best.map_or(true, |(_, bw)| w > bw) {
                best = Some((term, w));
            }
        }
    }
    if weight < 2 {
        return None;
    }
    if let Some(id) = find_cve(text) {
        return Some(id);
    }
    let (term, w) = best?;
    if w < 2 {
        // Only weak words like "hacker" + "leaks"; a generic label reads better.
        return Some("security".to_string());
    }
    Some(match term {
        "rce" | "xss" | "ssrf" | "csrf" | "cve" | "cves" | "cvss" | "cisa" | "kev" | "poc" | "ddos" => {
            term.to_uppercase()
        }
        "0 day" | "0day" | "zeroday" | "zero day" => "zero-day".to_string(),
        other => other.to_string(),
    })
}

/// How spammy a GitHub repo looks. Two or more points hides it.
pub fn spam_score(full_name: &str, blurb: &str) -> u32 {
    let (owner, repo) = full_name.split_once('/').unwrap_or(("", full_name));
    let mut score = 0u32;
    if repo == ".github" {
        score += 2;
    }
    let norm = normalize(&format!("{full_name} {blurb}"));
    for &(term, w) in SPAM_TERMS {
        if contains_phrase(&norm, term) {
            score += w as u32;
        }
    }
    if blurb.contains('🔥') {
        score += 1;
    }
    if random_looking(owner) {
        score += 1;
    }
    score
}

pub fn is_spam(source: Source, full_name: &str, blurb: &str) -> bool {
    source == Source::Gh && spam_score(full_name, blurb) >= 2
}

/// Throwaway account names like "qxqfvfpjzxnv": long, only letters, few vowels.
fn random_looking(owner: &str) -> bool {
    if owner.len() < 9 || !owner.bytes().all(|b| b.is_ascii_lowercase()) {
        return false;
    }
    let vowels = owner.bytes().filter(|b| b"aeiou".contains(b)).count();
    let mut run = 0;
    let mut longest = 0;
    for b in owner.bytes() {
        if b"aeiou".contains(&b) {
            run = 0;
        } else {
            run += 1;
            longest = longest.max(run);
        }
    }
    (vowels as f64) / (owner.len() as f64) < 0.26 || longest >= 5
}

/// One interest keyword, ready to match normalized text.
#[derive(Clone, Debug, PartialEq)]
pub struct Interest {
    pub label: String,
    needle: String,
    plural: String,
}

impl Interest {
    pub fn new(raw: &str) -> Option<Interest> {
        let label = raw.trim();
        let core = normalize(label);
        let core = core.trim();
        if core.chars().count() < 2 {
            return None;
        }
        Some(Interest {
            label: label.to_string(),
            needle: format!(" {core} "),
            plural: format!(" {core}s "),
        })
    }

    pub fn matches(&self, norm: &str) -> bool {
        norm.contains(&self.needle) || norm.contains(&self.plural)
    }
}

pub fn interests(raw: &[String]) -> Vec<Interest> {
    let mut out: Vec<Interest> = Vec::new();
    for item in raw {
        if let Some(interest) = Interest::new(item) {
            if !out.iter().any(|seen| seen.needle == interest.needle) {
                out.push(interest);
            }
        }
    }
    out
}

pub fn first_interest<'a>(norm: &str, list: &'a [Interest]) -> Option<&'a Interest> {
    list.iter().find(|interest| interest.matches(norm))
}

/// Score per hour of life. The first half hour counts as half an hour so a
/// two-minute-old post with three points does not look like a rocket.
pub fn heat(score: f64, t: i64, now: i64) -> f64 {
    if t <= 0 || !score.is_finite() {
        return 0.0;
    }
    let age_h = ((now - t).max(0) as f64 / 3600.0).max(0.5);
    score / age_h
}

pub fn age_hours(t: i64, now: i64) -> f64 {
    if t <= 0 {
        return f64::INFINITY;
    }
    (now - t).max(0) as f64 / 3600.0
}

/// A bar a row has to clear before it is called out.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Bar {
    pub min_score: f64,
    pub min_heat: f64,
    pub max_age_h: f64,
}

impl Bar {
    pub fn clears(&self, score: f64, heat: f64, age_h: f64) -> bool {
        score >= self.min_score && heat >= self.min_heat && age_h <= self.max_age_h
    }
}

fn pick(sensitivity: u8, low: Bar, normal: Bar, high: Bar) -> Bar {
    match sensitivity {
        0 => low,
        2 => high,
        _ => normal,
    }
}

const fn bar(min_score: f64, min_heat: f64, max_age_h: f64) -> Bar {
    Bar { min_score, min_heat, max_age_h }
}

/// Rising fast: lots of points or stars soon after posting.
pub fn breaking_bar(source: Source, sensitivity: u8) -> Bar {
    match source {
        Source::Hn => pick(sensitivity, bar(250.0, 40.0, 8.0), bar(100.0, 20.0, 8.0), bar(50.0, 12.0, 8.0)),
        Source::Gh => pick(sensitivity, bar(300.0, 20.0, 24.0), bar(100.0, 8.0, 24.0), bar(40.0, 4.0, 24.0)),
    }
}

/// Security rows only need to be getting noticed.
pub fn security_bar(source: Source, sensitivity: u8) -> Bar {
    match source {
        Source::Hn => pick(sensitivity, bar(80.0, 4.0, 24.0), bar(30.0, 2.0, 24.0), bar(12.0, 1.0, 24.0)),
        Source::Gh => pick(sensitivity, bar(200.0, 3.0, 72.0), bar(80.0, 1.0, 72.0), bar(25.0, 0.3, 72.0)),
    }
}

pub fn interest_bar(source: Source, sensitivity: u8) -> Bar {
    match source {
        Source::Hn => pick(sensitivity, bar(80.0, 3.0, 24.0), bar(30.0, 1.5, 24.0), bar(10.0, 0.5, 24.0)),
        Source::Gh => pick(sensitivity, bar(80.0, 1.0, 72.0), bar(20.0, 0.3, 72.0), bar(8.0, 0.1, 72.0)),
    }
}

/// A single timestamped observation of an item's engagement metrics.
#[derive(Clone, Copy, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct MetricSample {
    pub t: i64,
    pub score: f64,
    pub extra: f64,
}

pub fn kinetic_velocity(samples: &[MetricSample], now: i64, window_secs: i64) -> f64 {
    if samples.is_empty() {
        return 0.0;
    }
    let cutoff = now - window_secs;
    let oldest = samples.iter().filter(|s| s.t >= cutoff).min_by_key(|s| s.t);
    let newest = samples.iter().max_by_key(|s| s.t);
    match (oldest, newest) {
        (Some(o), Some(n)) if n.t > o.t => {
            let delta_score = (n.score - o.score).max(0.0);
            let delta_hours = ((n.t - o.t) as f64 / 3600.0).max(0.25);
            delta_score / delta_hours
        }
        _ => 0.0,
    }
}

pub fn is_accelerating(samples: &[MetricSample], now: i64) -> bool {
    let recent_vel = kinetic_velocity(samples, now, 3600);
    let prior_samples: Vec<MetricSample> = samples.iter().filter(|s| s.t < now - 3600).cloned().collect();
    let prior_vel = kinetic_velocity(&prior_samples, now - 3600, 3600);
    recent_vel >= 15.0 && (recent_vel >= prior_vel * 1.3 || prior_vel == 0.0)
}

pub fn sparkline_for_samples(samples: &[MetricSample], bars: usize) -> String {
    if samples.len() < 2 {
        return String::new();
    }
    let blocks = [' ', '▂', '▃', '▄', '▅', '▆', '▇', '█'];
    let n = samples.len();
    let step = (n as f64 / bars as f64).max(1.0);
    let mut sampled = Vec::new();
    for i in 0..bars {
        let idx = ((i as f64 * step) as usize).min(n - 1);
        sampled.push(samples[idx].score);
    }
    let min = sampled.iter().cloned().fold(f64::INFINITY, f64::min);
    let max = sampled.iter().cloned().fold(f64::NEG_INFINITY, f64::max);
    let range = (max - min).max(1.0);
    sampled
        .iter()
        .map(|&s| {
            let norm = ((s - min) / range).clamp(0.0, 1.0);
            let b_idx = ((norm * (blocks.len() - 1) as f64).round() as usize).min(blocks.len() - 1);
            blocks[b_idx]
        })
        .collect()
}

/// User's tech stack dependencies parsed from Cargo.lock, package.json, requirements.txt, or plain list.
#[derive(Clone, Debug, Default, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct StackProfile {
    pub packages: Vec<String>,
}

impl StackProfile {
    pub fn new(packages: Vec<String>) -> Self {
        let mut clean = Vec::new();
        for p in packages {
            let n = p.trim().to_lowercase();
            if n.len() >= 2 && !clean.contains(&n) {
                clean.push(n);
            }
        }
        StackProfile { packages: clean }
    }

    pub fn matches(&self, norm: &str) -> Option<String> {
        for pkg in &self.packages {
            let needle = format!(" {pkg} ");
            let plural = format!(" {pkg}s ");
            if norm.contains(&needle) || norm.contains(&plural) {
                return Some(pkg.clone());
            }
        }
        None
    }
}

pub fn parse_stack_dependencies(raw: &str) -> Vec<String> {
    let mut out = Vec::new();
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return out;
    }

    // Try parsing as JSON (package.json)
    if (trimmed.starts_with('{') && trimmed.ends_with('}'))
        || (trimmed.starts_with('[') && trimmed.ends_with(']'))
    {
        if let Ok(val) = serde_json::from_str::<serde_json::Value>(trimmed) {
            if let Some(arr) = val.as_array() {
                for item in arr {
                    if let Some(s) = item.as_str() {
                        let s = s.trim().to_lowercase();
                        if s.len() >= 2 && !out.contains(&s) {
                            out.push(s);
                        }
                    }
                }
                return out;
            } else if let Some(obj) = val.as_object() {
                for dep_key in &["dependencies", "devDependencies", "peerDependencies"] {
                    if let Some(deps) = obj.get(*dep_key).and_then(|v| v.as_object()) {
                        for (k, _) in deps {
                            let k = k.trim().to_lowercase();
                            let clean = k.split('/').next_back().unwrap_or(&k).to_string();
                            if clean.len() >= 2 && !out.contains(&clean) {
                                out.push(clean);
                            }
                        }
                    }
                }
                if !out.is_empty() {
                    return out;
                }
            }
        }
    }

    // Line-by-line parsing: handles Cargo.lock, requirements.txt, go.mod, or comma/line list
    for line in raw.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') || line.starts_with("//") {
            continue;
        }

        // Cargo.lock: name = "package_name"
        if let Some(rest) = line.strip_prefix("name =") {
            let clean = rest.trim().trim_matches('"').trim().to_lowercase();
            if clean.len() >= 2 && !out.contains(&clean) {
                out.push(clean);
            }
            continue;
        }

        // go.mod: require github.com/owner/repo v1.0
        if line.starts_with("require ") || line.starts_with("replace ") {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 {
                let pkg = parts[1].split('/').next_back().unwrap_or(parts[1]).to_lowercase();
                if pkg.len() >= 2 && !out.contains(&pkg) {
                    out.push(pkg);
                }
                continue;
            }
        }

        // requirements.txt or comma list: e.g. "flask>=2.0", "requests==2.28", "tokio, serde"
        for sub in line.split(',') {
            let sub = sub.trim();
            let pkg = sub
                .split(['=', '>', '<', '~', '!', ';', '@', '['])
                .next()
                .unwrap_or("")
                .trim()
                .to_lowercase();
            let pkg = pkg.strip_prefix("-r ").unwrap_or(&pkg).trim();
            if pkg.len() >= 2
                && !matches!(pkg, "the" | "and" | "for" | "with" | "from" | "import" | "name")
                && !out.contains(&pkg.to_string())
            {
                out.push(pkg.to_string());
            }
        }
    }
    out
}

/// Every signal for one row at once.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Signals {
    pub heat: f64,
    pub flags: u32,
    pub tag: String,
    pub matched: String,
    pub sparkline: String,
    pub affects_stack: String,
    pub cross_source: bool,
}

pub fn read(story: &crate::Story, source: Source, now: i64, interests: &[Interest], sensitivity: u8) -> Signals {
    read_pre(story, &crate::topics::prepare(story, source), source, now, interests, sensitivity)
}

/// [read] with the text work already done.
pub fn read_pre(
    story: &crate::Story,
    pre: &crate::topics::Pre,
    source: Source,
    now: i64,
    interests: &[Interest],
    sensitivity: u8,
) -> Signals {
    let heat = heat(story.score, story.t, now);
    let age = age_hours(story.t, now);
    let mut out = Signals { heat, ..Signals::default() };
    if pre.spam {
        out.flags |= FLAG_SPAM;
    }
    if breaking_bar(source, sensitivity).clears(story.score, heat, age) {
        out.flags |= FLAG_RISING;
    }
    if let Some(tag) = &pre.security {
        out.flags |= FLAG_SECURITY;
        out.tag = tag.clone();
    }
    if let Some(interest) = first_interest(&pre.norm, interests) {
        out.flags |= FLAG_INTEREST;
        out.matched = interest.label.clone();
    }
    out
}

pub fn is_new_and_trending(story: &crate::Story, sig: &Signals, now: i64) -> bool {
    if story.id.starts_with("gh:trending:") {
        story.t > 0 && (now - story.t) <= 7 * 86_400
    } else if story.url.contains("github.com/") {
        sig.heat >= 25.0 || (sig.flags & FLAG_BREAKOUT != 0)
    } else {
        false
    }
}

pub fn contains_phrase(norm: &str, term: &str) -> bool {
    // Terms are already lowercase words split by single spaces.
    let bytes = norm.as_bytes();
    let needle = term.as_bytes();
    let mut start = 0;
    while let Some(pos) = norm[start..].find(term) {
        let at = start + pos;
        let before = at == 0 || bytes[at - 1] == b' ';
        let end = at + needle.len();
        let after = end >= bytes.len() || bytes[end] == b' ';
        if before && after {
            return true;
        }
        start = at + 1;
        while start < norm.len() && !norm.is_char_boundary(start) {
            start += 1;
        }
    }
    false
}

/// First "CVE-YYYY-NNNN…" in the text, uppercased.
fn find_cve(text: &str) -> Option<String> {
    let bytes = text.as_bytes();
    let mut i = 0;
    while i + 3 < bytes.len() {
        if bytes[i].eq_ignore_ascii_case(&b'c')
            && bytes[i + 1].eq_ignore_ascii_case(&b'v')
            && bytes[i + 2].eq_ignore_ascii_case(&b'e')
            && (i == 0 || !bytes[i - 1].is_ascii_alphanumeric())
        {
            let mut j = i + 3;
            if j < bytes.len() && (bytes[j] == b'-' || bytes[j] == b'_' || bytes[j] == b' ') {
                j += 1;
            }
            let year_start = j;
            while j < bytes.len() && bytes[j].is_ascii_digit() {
                j += 1;
            }
            if j - year_start == 4 && j < bytes.len() && (bytes[j] == b'-' || bytes[j] == b'_' || bytes[j] == b' ') {
                let num_start = j + 1;
                let mut k = num_start;
                while k < bytes.len() && bytes[k].is_ascii_digit() {
                    k += 1;
                }
                if k - num_start >= 4 {
                    return Some(format!("CVE-{}-{}", &text[year_start..j], &text[num_start..k]));
                }
            }
        }
        i += 1;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sec(text: &str) -> Option<String> {
        security(text, &normalize(text))
    }

    #[test]
    fn normalize_splits_on_symbols_and_pads() {
        assert_eq!(" cve 2026 1234 exploit ", normalize("CVE-2026-1234_exploit!"));
        assert_eq!(" ", normalize("--"));
        assert_eq!(" été 2 ", normalize("Été 2"));
    }

    #[test]
    fn security_headlines_are_caught_with_a_label() {
        assert_eq!(Some("RCE".to_string()), sec("Evolving Threat: http2shell is an RCE in Node"));
        assert_eq!(Some("CVE".to_string()), sec("CVE Tracker"));
        assert_eq!(Some("CVE-2026-109618".to_string()), sec("junaire/CVE-2026-109618-exploit"));
        assert_eq!(Some("CVE-2024-3094".to_string()), sec("xz backdoor (cve 2024 3094) explained"));
        assert_eq!(Some("zero-day".to_string()), sec("Chrome 0-day exploited in the wild"));
        assert!(sec("Hacker leaks 281 GB of data from a party").is_some());
    }

    #[test]
    fn ordinary_headlines_are_not_security() {
        assert_eq!(None, sec("Twelve years of distributed SQLite"));
        assert_eq!(None, sec("Show HN: A security-focused notes app"));
        assert_eq!(None, sec("Fixing a memory leak in our Go service"));
        assert_eq!(None, sec("Hacker reverses Samsung TV"));
        assert_eq!(None, sec("Discover the best recipes"));
    }

    #[test]
    fn spam_repos_score_high_and_real_ones_low() {
        assert!(spam_score("onchain-trading-bot/.github", "onchain-trading-bot-pc-desktop") >= 2);
        assert!(spam_score("aotoycffyxns/delta", "executor-pc") >= 2);
        assert!(spam_score("qxqfvfpjzxnv/liquidity-sniping-bot-pc-de", "🔥 liquidity-sniping-bot-pc-desktop 🔥") >= 2);
        assert!(spam_score("EgorBo/BlazingFastDotnet", "Tips & tricks to tune .NET workloads") < 2);
        assert!(spam_score("ivarvong/lantern", "Animated, steppable code explainers") < 2);
        assert!(spam_score("rust-lang/rust", "Empowering everyone to build reliable software") < 2);
        assert!(!is_spam(Source::Hn, "x/.github", "pc-desktop executor"));
    }

    #[test]
    fn random_owner_names_are_spotted() {
        assert!(random_looking("qxqfvfpjzxnv"));
        assert!(random_looking("tytpaskanvdc"));
        assert!(!random_looking("akhiranandan"));
        assert!(!random_looking("EgorBo"));
        assert!(!random_looking("short"));
    }

    #[test]
    fn interests_match_whole_words_and_plurals() {
        let list = interests(&["Game".into(), "apk".into(), "open source".into(), "c".into(), "game".into()]);
        assert_eq!(3, list.len());
        let hit = |text: &str| first_interest(&normalize(text), &list).map(|i| i.label.clone());
        assert_eq!(Some("Game".to_string()), hit("A tiny game engine"));
        assert_eq!(Some("Game".to_string()), hit("Games I loved"));
        assert_eq!(Some("apk".to_string()), hit("Signed APK builds on CI"));
        assert_eq!(Some("open source".to_string()), hit("We went open-source"));
        assert_eq!(None, hit("Gameplay footage"));
        assert_eq!(None, hit("apkg format"));
    }

    #[test]
    fn heat_floors_young_posts_at_half_an_hour() {
        assert_eq!(0.0, heat(10.0, 0, 1000));
        assert_eq!(20.0, heat(10.0, 1000, 1060));
        assert_eq!(50.0, heat(100.0, 0 + 1, 7201));
    }

    #[test]
    fn bars_tighten_with_sensitivity() {
        let low = breaking_bar(Source::Hn, 0);
        let high = breaking_bar(Source::Hn, 2);
        assert!(low.min_score > high.min_score);
        assert_eq!(breaking_bar(Source::Hn, 1), breaking_bar(Source::Hn, 9));
        assert!(breaking_bar(Source::Hn, 1).clears(191.0, 34.0, 5.5));
        assert!(!breaking_bar(Source::Hn, 1).clears(932.0, 51.0, 18.4));
    }

    #[test]
    fn stack_dependencies_parse_manifests_and_match() {
        // Cargo.lock style
        let cargo_lock = r#"
            [[package]]
            name = "tokio"
            version = "1.35.0"

            [[package]]
            name = "serde"
            version = "1.0.193"
        "#;
        let deps = parse_stack_dependencies(cargo_lock);
        assert!(deps.contains(&"tokio".to_string()));
        assert!(deps.contains(&"serde".to_string()));

        // package.json style
        let pkg_json = r#"{
            "dependencies": {
                "react": "^18.2.0",
                "@next/bundle-analyzer": "14.0.0"
            }
        }"#;
        let deps2 = parse_stack_dependencies(pkg_json);
        assert!(deps2.contains(&"react".to_string()));
        assert!(deps2.contains(&"bundle-analyzer".to_string()));

        // requirements.txt style
        let req_txt = "flask>=2.0.0\nrequests==2.28.1\n# comment\ntorch\n";
        let deps3 = parse_stack_dependencies(req_txt);
        assert!(deps3.contains(&"flask".to_string()));
        assert!(deps3.contains(&"requests".to_string()));
        assert!(deps3.contains(&"torch".to_string()));

        // Match against headline
        let profile = StackProfile::new(deps);
        assert_eq!(Some("tokio".to_string()), profile.matches(&normalize("Critical RCE vulnerability in tokio runtime")));
        assert_eq!(None, profile.matches(&normalize("Critical bug in django")));
    }

    #[test]
    fn kinetic_velocity_acceleration_and_sparklines() {
        let samples = vec![
            MetricSample { t: 1000, score: 10.0, extra: 2.0 },
            MetricSample { t: 2800, score: 50.0, extra: 8.0 },
            MetricSample { t: 4600, score: 120.0, extra: 20.0 },
        ];
        let vel = kinetic_velocity(&samples, 4600, 3600);
        assert!(vel > 50.0);
        let spark = sparkline_for_samples(&samples, 3);
        assert_eq!(3, spark.chars().count());
        assert!(is_accelerating(&samples, 4600));
    }
}
