"""Core ranking, signals, topics and window logic matching core/src/ in Rust."""

from __future__ import annotations
import math
import re
import time
from typing import Any, Dict, List, Optional, Set, Tuple

# Security terms from core/src/signals.rs
SECURITY_TERMS: List[Tuple[str, int]] = [
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
]

SPAM_TERMS: List[Tuple[str, int]] = [
    ("pc desktop", 2), ("executor", 2), ("aimbot", 2), ("keygen", 2), ("drainer", 2),
    ("sniping bot", 2), ("arbitrage bot", 2), ("free download", 2), ("mod menu", 2),
    ("spoofer", 2), ("cheat", 1), ("cheats", 1), ("crack", 1), ("cracked", 1), ("activator", 1),
    ("injector", 1), ("loader", 1), ("unlocker", 1), ("trading bot", 1), ("airdrop", 1),
    ("whale tracker", 1), ("smart money", 1),
]

STOP_WORDS = {
    "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
    "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
    "by", "can", "did", "do", "does", "doing", "don", "down", "during", "each", "few", "for",
    "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
    "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself", "just",
    "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on", "once",
    "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own", "s", "same", "she",
    "should", "so", "some", "such", "t", "than", "that", "the", "their", "theirs", "them",
    "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too",
    "under", "until", "up", "very", "was", "we", "were", "what", "when", "where", "which",
    "while", "who", "whom", "why", "will", "with", "you", "your", "yours", "yourself",
    "yourselves", "new", "show", "hn", "ask", "tell", "using", "use", "used", "release", "v1",
    "via", "app", "open", "source", "fast", "simple", "tool", "first", "day", "days", "free",
    "best", "make", "made", "get", "like", "one", "way", "see", "look", "time", "year", "years",
}

CVE_REGEX = re.compile(r"\bcve[-_ ](\d{4})[-_ ](\d{4,7})\b", re.IGNORECASE)

def normalize(text: str) -> str:
    """Lowercase, keep letters & digits, collapse rest into spaces, pad ends with space."""
    cleaned = re.sub(r"[^a-zA-Z0-9]+", " ", text).strip().lower()
    return f" {cleaned} " if cleaned else " "

def domain_of(url: str) -> str:
    if not url:
        return ""
    m = re.search(r"https?://([^/:]+)", url)
    if not m:
        return ""
    host = m.group(1).lower()
    if host.startswith("www."):
        host = host[4:]
    return host

def find_cve(text: str) -> Optional[str]:
    m = CVE_REGEX.search(text)
    if m:
        return f"CVE-{m.group(1)}-{m.group(2)}"
    return None

def detect_security(text: str, norm: str) -> Optional[str]:
    cve = find_cve(text)
    if cve:
        return cve
    weight = 0
    best_term: Optional[Tuple[str, int]] = None
    for term, w in SECURITY_TERMS:
        if f" {term} " in norm:
            weight += w
            if best_term is None or w > best_term[1]:
                best_term = (term, w)
    if weight >= 2 and best_term:
        term_clean = best_term[0].upper() if len(best_term[0]) <= 4 else best_term[0].title()
        return term_clean
    return None

def detect_spam(source: str, title: str, blurb: str) -> bool:
    if source != "gh":
        return False
    norm = normalize(f"{title} {blurb}")
    weight = 0
    for term, w in SPAM_TERMS:
        if f" {term} " in norm:
            weight += w
    return weight >= 2

def heat(score: float, t: int, now: int) -> float:
    """Points or stars per hour of life."""
    if t <= 0 or math.isnan(score):
        return 0.0
    age_h = max(max(now - t, 0) / 3600.0, 0.5)
    return score / age_h

def calculate_window(now_sec: int, hours: int) -> Dict[str, Any]:
    if hours <= 1:
        hours = 1
        step = 600
        bars = 6
    elif hours <= 6:
        hours = 6
        step = 3600
        bars = 6
    elif hours <= 12:
        hours = 12
        step = 3600
        bars = 12
    elif hours <= 24:
        hours = 24
        step = 3600
        bars = 24
    elif hours <= 48:
        hours = 48
        step = 7200
        bars = 24
    else:
        hours = 168
        step = 25200
        bars = 16
    aligned = (now_sec // step) * step
    origin = aligned - (bars - 1) * step
    return {
        "hours": hours,
        "step": step,
        "bars": bars,
        "origin": origin,
    }

def extract_terms(title: str, blurb: str) -> List[str]:
    words = [w for w in re.sub(r"[^a-zA-Z0-9]+", " ", f"{title} {blurb}").lower().split() if len(w) >= 3 and w not in STOP_WORDS]
    terms: List[str] = []
    seen: Set[str] = set()
    for w in words:
        if w not in seen:
            seen.add(w)
            terms.append(w)
    # 2-word phrases
    for i in range(len(words) - 1):
        w1, w2 = words[i], words[i + 1]
        phrase = f"{w1} {w2}"
        if phrase not in seen:
            seen.add(phrase)
            terms.append(phrase)
    return terms

def is_rising(source: str, score: float, h: float, age_h: float, sensitivity: int) -> bool:
    # 0 low, 1 normal, 2 high
    if source == "hn":
        bars = [(50.0, 5.0, 12.0), (20.0, 2.0, 12.0), (8.0, 1.0, 12.0)]
    else:
        bars = [(200.0, 3.0, 72.0), (80.0, 1.0, 72.0), (25.0, 0.3, 72.0)]
    sens_idx = max(0, min(2, sensitivity))
    min_score, min_heat, max_age = bars[sens_idx]
    return score >= min_score and h >= min_heat and age_h <= max_age

def matches_interests(norm: str, interests: List[str]) -> Optional[str]:
    for item in interests:
        clean = item.strip().lower()
        if not clean:
            continue
        if f" {clean} " in norm:
            return item.strip()
    return None

def format_compact(n: float) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n / 1_000:.1f}k"
    return f"{int(n)}"

def format_ago(t: int, now: int) -> str:
    diff = max(0, now - t)
    if diff < 60:
        return "now"
    m = diff // 60
    if m < 60:
        return f"{m}m"
    h = m // 60
    if h < 24:
        return f"{h}h"
    d = h // 24
    return f"{d}d"


def is_zero_day(text: str) -> bool:
    return bool(re.search(r"\b(0[- ]?day|zero[- ]day|0days|zerodays)\b", text, re.IGNORECASE))


def evaluate_story_alerts(
    story: Dict[str, Any],
    source: str,
    interests: List[str],
    now_sec: int,
    breaking_threshold: float = 30.0,
) -> Optional[Dict[str, Any]]:
    """Evaluate if a story qualifies for an alert:
    a) High-severity security issues (CVEs, 0days)
    b) Breaking stories with velocity heat >= 30/hr
    c) Stories matching user interest keywords
    """
    title_blurb = f"{story.get('title', '')} {story.get('blurb', '')}"
    norm = normalize(title_blurb)
    if source == "gh" and story.get("meta"):
        norm += f" {story['meta'].lower()} "

    if detect_spam(source, story.get("title", ""), story.get("blurb", "")):
        return None

    score = float(story.get("score") or 0.0)
    t = int(story.get("t") or 0)
    h = heat(score, t, now_sec)

    cve = find_cve(title_blurb)
    zero_day = is_zero_day(title_blurb)
    sec_tag = detect_security(title_blurb, norm)

    is_high_sec = bool(cve or zero_day)
    is_breaking = h >= breaking_threshold
    int_match = matches_interests(norm, interests)

    if not (is_high_sec or sec_tag or is_breaking or int_match):
        return None

    if cve:
        label = cve
        severity = "critical"
        kind = "security"
    elif zero_day:
        label = "0day"
        severity = "critical"
        kind = "security"
    elif is_breaking:
        label = f"Breaking ({round(h, 1)}/hr)"
        severity = "high"
        kind = "breaking"
    elif sec_tag:
        label = sec_tag
        severity = "high"
        kind = "security"
    else:
        label = str(int_match)
        severity = "medium"
        kind = "interest"

    return {
        "id": str(story["id"]),
        "title": story.get("title", ""),
        "label": label,
        "severity": severity,
        "kind": kind,
        "t": t,
        "heat": round(h, 1),
        "score": score,
        "source": source,
        "url": story.get("url", ""),
        "talk": story.get("talk", ""),
        "blurb": story.get("blurb", ""),
        "meta": story.get("meta", ""),
    }

