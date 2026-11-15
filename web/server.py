"""Trending Web App Server (FastAPI).

Provides full parity with the Android app:
- Hacker News (Algolia API) & GitHub Trending APIs
- Real-time ranking (Hot, New, Rising)
- Signals: Security CVE/exploit tags, Rising velocity, Spam filtering, User interests
- Activity distribution histogram bars
- Topic chips & GitHub language filters
- Discussion & comments browser
- Local saved bookmarks and settings persistence
- Bookmarks export & import
- System & rate limit status monitoring
- Alerts & notifications detection
"""

from __future__ import annotations
import asyncio
from datetime import datetime, timezone
import json
import logging
import math
import os
from pathlib import Path
import threading
import time
from typing import Any, Dict, List, Optional, Set

import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

try:
    import engine
except ImportError:
    from web import engine

logger = logging.getLogger("trending.web")

BASE_DIR = Path(__file__).resolve().parent
# TRENDING_DATA_DIR lets tests (or a second instance) keep their own bookmarks/settings.
DATA_DIR = Path(os.environ.get("TRENDING_DATA_DIR") or (BASE_DIR / "data"))
STATIC_DIR = BASE_DIR / "static"
DATA_DIR.mkdir(parents=True, exist_ok=True)
STATIC_DIR.mkdir(parents=True, exist_ok=True)

SAVED_FILE = DATA_DIR / "saved.json"
SETTINGS_FILE = DATA_DIR / "settings.json"

APP_VERSION = "1.5.2"  # keep in sync with app/build.gradle.kts versionName
USER_AGENT = f"omalauncher-trending/{APP_VERSION}"

SERVER_START_TIME = int(time.time())

# Track GitHub API rate limits
GH_RATE_LIMIT: Dict[str, Any] = {
    "remaining": 60,
    "limit": 60,
    "reset": int(time.time()) + 3600,
    "updated": 0,
}

ALLOWED_ORIGINS = [
    "http://127.0.0.1:8080",
    "http://localhost:8080",
    "http://127.0.0.1:3000",
    "http://localhost:3000",
]

app = FastAPI(title="Trending Web", version=APP_VERSION)


@app.middleware("http")
async def block_cross_site_writes(request: Request, call_next):
    """Reject state-changing requests sent by other websites (CSRF).

    CORS only stops other sites from *reading* responses; a page on any site can
    still POST a form (e.g. a multipart bookmark import) to 127.0.0.1. Browsers
    always send Origin on cross-origin POST/PUT/DELETE, so refuse unknown ones.
    Requests without Origin (curl, scripts, tests) are allowed.
    """
    if request.method in ("POST", "PUT", "PATCH", "DELETE"):
        origin = request.headers.get("origin")
        fetch_site = request.headers.get("sec-fetch-site")
        if (origin and origin not in ALLOWED_ORIGINS) or fetch_site == "cross-site":
            return JSONResponse({"detail": "Cross-site request blocked"}, status_code=403)
    return await call_next(request)


app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Shared in-memory cache for API downloads
CACHE: Dict[str, Dict[str, Any]] = {}
CACHE_LOCK = asyncio.Lock()
CACHE_FRESH_SEC = 90
# Keys include the moving window start, so old keys are never read again; drop them.
CACHE_MAX_AGE_SEC = 15 * 60


def prune_cache(now_sec: int) -> None:
    """Drop cache entries that are too old to be served. Call with CACHE_LOCK held."""
    for key in [k for k, v in CACHE.items() if now_sec - v.get("fetched", 0) > CACHE_MAX_AGE_SEC]:
        del CACHE[key]

# Serialises reads/writes of the JSON files (sync endpoints run in a thread pool).
FILE_LOCK = threading.RLock()

HTTP_TIMEOUT = httpx.Timeout(30.0, connect=20.0)

DEFAULT_SETTINGS = {
    "source": "hn",
    "mode": "hot",
    "hours": 6,
    "themeMode": "dark",
    "palette": 0, # amber
    "hideSpam": True,
    "chartExpanded": True,
    "interests": ["AI", "Rust", "LLM", "Linux"],
    "sensitivity": 1,
    "githubToken": "",
}

def _write_json_atomic(path: Path, data: Any) -> None:
    """Write to a temp file and rename, so a crash never leaves half a file."""
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def _read_json(path: Path, expected: type) -> Optional[Any]:
    """Read JSON; on a corrupt file keep a backup copy instead of letting it be overwritten."""
    if not path.is_file():
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, expected):
            return data
        raise ValueError(f"expected {expected.__name__}, got {type(data).__name__}")
    except Exception as e:
        backup = path.with_name(f"{path.name}.corrupt-{int(time.time())}")
        try:
            os.replace(path, backup)
            logger.warning("Unreadable %s (%s); moved it to %s", path.name, e, backup.name)
        except OSError:
            logger.warning("Unreadable %s (%s); could not back it up", path.name, e)
        return None


def load_settings() -> Dict[str, Any]:
    with FILE_LOCK:
        data = _read_json(SETTINGS_FILE, dict)
    return {**DEFAULT_SETTINGS, **data} if data else dict(DEFAULT_SETTINGS)

def save_settings(settings: Dict[str, Any]) -> None:
    with FILE_LOCK:
        _write_json_atomic(SETTINGS_FILE, settings)

def load_saved() -> List[Dict[str, Any]]:
    with FILE_LOCK:
        data = _read_json(SAVED_FILE, list)
    return [i for i in data if isinstance(i, dict)] if data else []

def save_saved(items: List[Dict[str, Any]]) -> None:
    with FILE_LOCK:
        _write_json_atomic(SAVED_FILE, items)

def update_gh_rate_limit(headers: httpx.Headers) -> None:
    global GH_RATE_LIMIT
    rem = headers.get("x-ratelimit-remaining")
    lim = headers.get("x-ratelimit-limit")
    rst = headers.get("x-ratelimit-reset")
    if rem is not None:
        try:
            GH_RATE_LIMIT["remaining"] = int(rem)
        except (ValueError, TypeError):
            pass
    if lim is not None:
        try:
            GH_RATE_LIMIT["limit"] = int(lim)
        except (ValueError, TypeError):
            pass
    if rst is not None:
        try:
            GH_RATE_LIMIT["reset"] = int(rst)
        except (ValueError, TypeError):
            pass
    GH_RATE_LIMIT["updated"] = int(time.time())

class SettingsUpdate(BaseModel):
    source: Optional[str] = None
    mode: Optional[str] = None
    hours: Optional[int] = None
    themeMode: Optional[str] = None
    palette: Optional[int] = None
    hideSpam: Optional[bool] = None
    chartExpanded: Optional[bool] = None
    interests: Optional[List[str]] = None
    sensitivity: Optional[int] = None
    githubToken: Optional[str] = None

class ToggleSavedRequest(BaseModel):
    story: Dict[str, Any]

# ----------------- Data Fetching -----------------

async def fetch_hn_stories(origin: int, client: httpx.AsyncClient, before: Optional[int] = None) -> List[Dict[str, Any]]:
    """Fetch stories from HN Algolia API bounded by origin, optionally bounded by before."""
    if before and before > 0:
        filters = f"created_at_i>{origin},created_at_i<={before}"
    else:
        filters = f"created_at_i>{origin}"
    url = (
        "https://hn.algolia.com/api/v1/search_by_date?"
        f"tags=story&hitsPerPage=1000&numericFilters={filters}"
    )
    headers = {"User-Agent": USER_AGENT}
    resp = None
    for attempt in range(3):
        try:
            resp = await client.get(url, headers=headers)
            break
        except httpx.RequestError:
            if attempt == 2:
                raise
            await asyncio.sleep(0.5)
    if resp.status_code != 200:
        raise HTTPException(status_code=resp.status_code, detail="Hacker News API error")
    data = resp.json()
    hits = data.get("hits", [])
    stories: List[Dict[str, Any]] = []
    for hit in hits:
        obj_id = str(hit.get("objectID") or "")
        if not obj_id:
            continue
        title = hit.get("title") or "Untitled"
        url = hit.get("url") or f"https://news.ycombinator.com/item?id={obj_id}"
        talk = f"https://news.ycombinator.com/item?id={obj_id}"
        score = float(hit.get("points") or 0.0)
        extra = float(hit.get("num_comments") or 0.0)
        author = hit.get("author") or ""
        created_at_i = int(hit.get("created_at_i") or 0)
        story_text = hit.get("story_text") or ""
        stories.append({
            "id": obj_id,
            "title": title,
            "url": url,
            "talk": talk,
            "score": score,
            "extra": extra,
            "meta": author,
            "blurb": story_text[:300] if story_text else "",
            "t": created_at_i,
        })
    return stories

async def fetch_gh_repos(origin: int, token: Optional[str], client: httpx.AsyncClient, page: int = 1) -> List[Dict[str, Any]]:
    """Fetch repos from GitHub search API with pagination support."""
    dt = datetime.fromtimestamp(origin, tz=timezone.utc)
    stamp = dt.strftime("%Y-%m-%dT%H:%M:00Z")
    query = f"created:>{stamp}"
    url = f"https://api.github.com/search/repositories?q={query}&sort=stars&order=desc&per_page=100&page={page}"
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    resp = None
    for attempt in range(3):
        try:
            resp = await client.get(url, headers=headers)
            break
        except httpx.RequestError:
            if attempt == 2:
                raise
            await asyncio.sleep(0.5)
    update_gh_rate_limit(resp.headers)
    if resp.status_code != 200:
        rate_limit = resp.headers.get("x-ratelimit-remaining")
        raise HTTPException(
            status_code=resp.status_code,
            detail=f"GitHub API error ({resp.status_code}). Rate limit remaining: {rate_limit}"
        )
    data = resp.json()
    items = data.get("items", [])
    stories: List[Dict[str, Any]] = []
    for item in items:
        repo_id = str(item.get("id"))
        name = item.get("full_name") or item.get("name") or "repo"
        html_url = item.get("html_url") or ""
        stars = float(item.get("stargazers_count") or 0.0)
        forks = float(item.get("forks_count") or 0.0)
        language = item.get("language") or ""
        desc = item.get("description") or ""
        created_at_str = item.get("created_at") or ""
        t = 0
        if created_at_str:
            try:
                t = int(datetime.fromisoformat(created_at_str.replace("Z", "+00:00")).timestamp())
            except Exception:
                t = int(time.time())
        stories.append({
            "id": repo_id,
            "title": name,
            "url": html_url,
            "talk": html_url,
            "score": stars,
            "extra": forks,
            "meta": language,
            "blurb": desc,
            "t": t,
        })
    return stories

# ----------------- Endpoints -----------------

def sanitize_settings_for_client(settings: Dict[str, Any]) -> Dict[str, Any]:
    safe = dict(settings)
    raw_token = safe.get("githubToken") or ""
    safe["hasGithubToken"] = bool(raw_token.strip())
    safe["githubToken"] = "••••••••" if raw_token.strip() else ""
    return safe

@app.get("/api/settings")
def get_settings():
    return sanitize_settings_for_client(load_settings())

@app.post("/api/settings")
def update_settings(update: SettingsUpdate):
    data = update.model_dump(exclude_unset=True)
    if "githubToken" in data:
        token_val = data["githubToken"]
        # "••••••••" is the mask the UI sends back, not a secret.
        if token_val == "••••••••" or token_val is None:  # noqa: S105  # nosec B105
            del data["githubToken"]
    with FILE_LOCK:
        curr = load_settings()
        for k, v in data.items():
            if v is not None:
                curr[k] = v
        save_settings(curr)
    return sanitize_settings_for_client(curr)

@app.get("/api/saved")
def get_saved():
    return load_saved()

@app.get("/api/saved/export")
def export_saved():
    """Returns JSON attachment or content of all saved bookmarks."""
    items = load_saved()
    return JSONResponse(
        content=items,
        headers={
            "Content-Disposition": 'attachment; filename="trending-saved.json"',
            "Content-Type": "application/json",
        },
    )

@app.post("/api/saved/import")
async def import_saved(request: Request):
    """Accepts a JSON list of saved stories and merges them (deduplicating by story id) into web/data/saved.json."""
    content_type = request.headers.get("content-type", "")
    incoming_data: Any = None

    if "multipart/form-data" in content_type:
        form = await request.form()
        file_upload = form.get("file")
        if file_upload and hasattr(file_upload, "read"):
            file_bytes = await file_upload.read()
            try:
                incoming_data = json.loads(file_bytes.decode("utf-8"))
            except Exception as e:
                raise HTTPException(status_code=400, detail=f"Invalid JSON file: {str(e)}") from e
        else:
            raise HTTPException(status_code=400, detail="No file found in multipart upload")
    else:
        try:
            incoming_data = await request.json()
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid JSON payload: {str(e)}") from e

    if isinstance(incoming_data, dict):
        if "items" in incoming_data and isinstance(incoming_data["items"], list):
            incoming_items = incoming_data["items"]
        elif "saved" in incoming_data and isinstance(incoming_data["saved"], list):
            incoming_items = incoming_data["saved"]
        elif "stories" in incoming_data and isinstance(incoming_data["stories"], list):
            incoming_items = incoming_data["stories"]
        elif "story" in incoming_data and isinstance(incoming_data["story"], dict):
            incoming_items = [incoming_data]
        else:
            raise HTTPException(status_code=400, detail="Expected a JSON list of saved stories or object containing 'items'")
    elif isinstance(incoming_data, list):
        incoming_items = incoming_data
    else:
        raise HTTPException(status_code=400, detail="Expected a JSON list of saved stories")

    with FILE_LOCK:
        return _merge_imported(incoming_items)


def _merge_imported(incoming_items: List[Any]) -> Dict[str, Any]:
    items = load_saved()
    existing_ids = {str(s.get("story", {}).get("id") or s.get("id") or "") for s in items if isinstance(s, dict)}
    added = 0
    now = int(time.time())

    for item in incoming_items:
        if not isinstance(item, dict):
            continue
        if "story" in item and isinstance(item["story"], dict):
            story = item["story"]
            saved_at = item.get("savedAt", now)
        else:
            story = item
            saved_at = item.get("savedAt", now)

        if not isinstance(story, dict):
            continue
        sid = str(story.get("id") or "")
        if sid and sid not in existing_ids:
            existing_ids.add(sid)
            items.append({"story": story, "savedAt": saved_at})
            added += 1

    save_saved(items)
    return {"ok": True, "added": added, "imported": added, "total": len(items), "items": items}

@app.post("/api/saved/toggle")
def toggle_saved(req: ToggleSavedRequest):
    story = req.story
    with FILE_LOCK:
        items = load_saved()
        existing = next((i for i, item in enumerate(items) if item.get("story", {}).get("id") == story.get("id")), None)
        now = int(time.time())
        if existing is not None:
            items.pop(existing)
            save_saved(items)
            return {"saved": False, "items": items}
        items.insert(0, {"story": story, "savedAt": now})
        save_saved(items)
        return {"saved": True, "items": items}

@app.delete("/api/saved/{story_id}")
def delete_saved(story_id: str):
    with FILE_LOCK:
        items = load_saved()
        items = [item for item in items if item.get("story", {}).get("id") != story_id]
        save_saved(items)
    return {"ok": True, "items": items}

@app.get("/api/feed")
async def get_feed(
    source: str = Query("hn", pattern="^(hn|gh)$"),
    mode: str = Query("hot", pattern="^(hot|new|rising)$"),
    hours: int = Query(6, ge=1, le=168),
    query: str = Query(""),
    topic: str = Query(""),
    lang: str = Query(""),
    bar: int = Query(-1),
    only: str = Query(""), # "security", "interest", "rising", "unread"
    limit: int = Query(50, ge=1, le=200),
    hide_spam: bool = Query(True),
    sensitivity: int = Query(1, ge=0, le=2),
    force_refresh: bool = Query(False),
    before: Optional[int] = Query(None),
    page: int = Query(1, ge=1),
):
    now_sec = int(time.time())
    window = engine.calculate_window(now_sec, hours)
    origin = window["origin"]

    settings = load_settings()
    interests = settings.get("interests", [])
    github_token = settings.get("githubToken")

    cache_key = f"{source}:{origin}:{before or 0}:{page}"
    stories: List[Dict[str, Any]] = []

    async with CACHE_LOCK:
        cached = CACHE.get(cache_key)
        if not force_refresh and cached and (now_sec - cached["fetched"] < CACHE_FRESH_SEC):
            stories = cached["stories"]

    if not stories:
        try:
            async with httpx.AsyncClient(timeout=HTTP_TIMEOUT) as client:
                if source == "hn":
                    stories = await fetch_hn_stories(origin, client, before=before)
                else:
                    stories = await fetch_gh_repos(origin, github_token, client, page=page)
        except httpx.HTTPError as e:
            name = "Hacker News" if source == "hn" else "GitHub"
            logger.warning("Fetching %s failed: %s", name, e)
            raise HTTPException(
                status_code=502,
                detail=f"Could not reach {name}. Check your connection and try again.",
            ) from e
        async with CACHE_LOCK:
            prune_cache(now_sec)
            CACHE[cache_key] = {"stories": stories, "fetched": now_sec}

    oldest_t = min((s["t"] for s in stories if s.get("t", 0) > 0), default=0)

    # Prepare stories with signals
    prepared: List[Dict[str, Any]] = []
    hot_bars = [0.0] * window["bars"]
    new_bars = [0.0] * window["bars"]
    hidden_count = 0
    inside_window: List[Dict[str, Any]] = []

    saved_items = load_saved()
    saved_ids = {s.get("story", {}).get("id") for s in saved_items}

    step = window["step"]
    bars_count = window["bars"]
    end_t = origin + bars_count * step + 180

    for s in stories:
        is_sp = engine.detect_spam(source, s["title"], s["blurb"])
        if hide_spam and is_sp:
            hidden_count += 1
            continue

        t = s["t"]
        score = s["score"]
        h = engine.heat(score, t, now_sec)
        age_h = max((now_sec - t) / 3600.0, 0.0) if t > 0 else 999.0

        bucket_idx: Optional[int] = None
        if origin <= t < end_t:
            idx = int(math.floor((t - origin) / step))
            if 0 <= idx < bars_count:
                bucket_idx = idx
                hot_bars[idx] += score
                new_bars[idx] += 1.0

        norm = engine.normalize(f"{s['title']} {s['blurb']}")
        if source == "gh" and s["meta"]:
            norm += f" {s['meta'].lower()} "

        sec_tag = engine.detect_security(f"{s['title']} {s['blurb']}", norm)
        int_match = engine.matches_interests(norm, interests)
        rising = engine.is_rising(source, score, h, age_h, sensitivity)
        terms = engine.extract_terms(s["title"], s["blurb"])

        item = {
            **s,
            "heat": round(h, 1),
            "age_h": round(age_h, 1),
            "ago": engine.format_ago(t, now_sec),
            "domain": engine.domain_of(s["url"]) if source == "hn" and s["url"] != s["talk"] else "",
            "is_spam": is_sp,
            "security": sec_tag,
            "interest": int_match,
            "rising": rising,
            "terms": terms,
            "bucket": bucket_idx,
            "is_saved": s["id"] in saved_ids,
        }
        prepared.append(item)
        if bucket_idx is not None:
            inside_window.append(item)

    # Calculate topic aggregations
    term_counts: Dict[str, Dict[str, Any]] = {}
    for item in inside_window:
        weight = 1.0 + math.log1p(max(0.0, item["score"]))
        for term in item["terms"]:
            if term not in term_counts:
                term_counts[term] = {"word": term, "count": 0, "weight": 0.0}
            term_counts[term]["count"] += 1
            term_counts[term]["weight"] += weight

    topics_list = [v for v in term_counts.values() if v["count"] >= 2]
    topics_list.sort(key=lambda x: (x["weight"], x["count"]), reverse=True)
    top_topics = topics_list[:14]

    # Calculate languages for GitHub
    lang_counts: Dict[str, int] = {}
    if source == "gh":
        for item in inside_window:
            lang_name = item.get("meta") or ""
            if lang_name:
                lang_counts[lang_name] = lang_counts.get(lang_name, 0) + 1
    langs_list = [{"name": k, "count": v} for k, v in sorted(lang_counts.items(), key=lambda x: x[1], reverse=True)[:8]]

    # Filtering
    query_clean = query.strip().lower()
    query_words = query_clean.split() if query_clean else []

    filtered: List[Dict[str, Any]] = []
    counts = {"security": 0, "interest": 0, "rising": 0}

    for item in prepared:
        # Match search query
        if query_words:
            searchable = f"{item['title']} {item['blurb']} {item['meta']} {item.get('domain', '')}".lower()
            if not all(w in searchable for w in query_words):
                continue

        # Topic filter
        if topic and topic.lower() not in [t.lower() for t in item["terms"]]:
            continue

        # Language filter
        if lang and item["meta"].lower() != lang.lower():
            continue

        # Bar filter
        if bar >= 0 and item["bucket"] != bar:
            continue

        # Tally counts
        if item["security"]:
            counts["security"] += 1
        if item["interest"]:
            counts["interest"] += 1
        if item["rising"]:
            counts["rising"] += 1

        # Quick filter toggle ("only")
        if only == "security" and not item["security"]:
            continue
        if only == "interest" and not item["interest"]:
            continue
        if only == "rising" and not item["rising"]:
            continue

        filtered.append(item)

    # Sort
    if mode == "hot":
        filtered.sort(key=lambda x: x["score"], reverse=True)
    elif mode == "new":
        filtered.sort(key=lambda x: x["t"], reverse=True)
    elif mode == "rising":
        filtered.sort(key=lambda x: x["heat"], reverse=True)

    matching_count = len(filtered)
    shown = filtered[:limit]

    peak_score = max((s["score"] for s in shown), default=1.0)
    peak_heat = max((s["heat"] for s in shown), default=1.0)

    # Bar labels
    bar_labels: List[str] = []
    for i in range(bars_count):
        bar_t = origin + i * step
        dt = datetime.fromtimestamp(bar_t)
        if hours == 1:
            bar_labels.append(dt.strftime("%H:%M"))
        elif hours <= 24:
            bar_labels.append(dt.strftime("%H:00"))
        else:
            bar_labels.append(dt.strftime("%m/%d"))

    return {
        "ok": True,
        "source": source,
        "mode": mode,
        "hours": hours,
        "page": page,
        "before": before,
        "oldest_t": oldest_t,
        "hasMore": len(filtered) > limit or (len(stories) >= 1000 if source == "hn" else len(stories) >= 100),
        "total": len(stories),
        "matching": matching_count,
        "hidden": hidden_count,
        "peak": peak_score,
        "peakHeat": peak_heat,
        "hotBars": hot_bars,
        "newBars": new_bars,
        "barLabels": bar_labels,
        "topics": top_topics,
        "langs": langs_list,
        "counts": counts,
        "shown": shown,
        "window": window,
        "fetched": now_sec,
    }

@app.get("/api/alerts")
async def get_alerts(
    source: Optional[str] = Query(None, pattern="^(hn|gh|all)?$"),
    hours: int = Query(6, ge=1, le=168),
    hide_spam: bool = Query(True),
    force_refresh: bool = Query(False),
):
    """Scans stories in the current window and returns alerts for:
    a) High-severity security issues (CVEs, 0days)
    b) Breaking stories with velocity heat >= 30/hr
    c) Stories matching user interest keywords
    Returns { ok: True, alerts: [...] } with story id, title, label, severity, t.
    """
    now_sec = int(time.time())
    window = engine.calculate_window(now_sec, hours)
    origin = window["origin"]

    settings = load_settings()
    interests = settings.get("interests", ["AI", "Rust", "LLM", "Linux"])
    github_token = settings.get("githubToken")

    target_source = source or settings.get("source", "hn")
    sources = ["hn", "gh"] if target_source == "all" else [target_source]

    all_alerts: List[Dict[str, Any]] = []
    seen_ids: Set[str] = set()

    for src in sources:
        cache_key = f"{src}:{origin}:0:1"
        stories: List[Dict[str, Any]] = []

        async with CACHE_LOCK:
            cached = CACHE.get(cache_key)
            if not force_refresh and cached and (now_sec - cached["fetched"] < CACHE_FRESH_SEC):
                stories = cached["stories"]

        if not stories:
            try:
                async with httpx.AsyncClient(timeout=HTTP_TIMEOUT) as client:
                    if src == "hn":
                        stories = await fetch_hn_stories(origin, client)
                    else:
                        stories = await fetch_gh_repos(origin, github_token, client)
                async with CACHE_LOCK:
                    prune_cache(now_sec)
                    CACHE[cache_key] = {"stories": stories, "fetched": now_sec}
            except Exception as e:
                logger.warning("Alert scan skipped %s: %s", src, e)
                continue

        for story in stories:
            sid = str(story.get("id") or "")
            if not sid or sid in seen_ids:
                continue

            alert = engine.evaluate_story_alerts(
                story=story,
                source=src,
                interests=interests,
                now_sec=now_sec,
                breaking_threshold=30.0,
            )
            if alert:
                seen_ids.add(sid)
                all_alerts.append(alert)

    # Sort alerts by severity (critical > high > medium > low), then heat
    severity_order = {"critical": 0, "high": 1, "medium": 2, "low": 3, "info": 4}
    all_alerts.sort(key=lambda a: (severity_order.get(a["severity"], 99), -a["heat"], -a["t"]))

    return {
        "ok": True,
        "count": len(all_alerts),
        "alerts": all_alerts,
    }

@app.get("/api/status")
async def get_status():
    """Returns current GitHub API rate limit status (remaining, limit, reset timestamp),
    cache item count, and server status.
    """
    settings = load_settings()
    token = settings.get("githubToken")

    # If rate limit hasn't been set yet, probe rate_limit endpoint
    if GH_RATE_LIMIT["updated"] == 0:
        headers = {
            "User-Agent": USER_AGENT,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(5.0)) as client:
                resp = await client.get("https://api.github.com/rate_limit", headers=headers)
                if resp.status_code == 200:
                    data = resp.json()
                    resources = data.get("resources", {})
                    search = resources.get("search", {})
                    core = resources.get("core", {})
                    target = search if search else core
                    if target:
                        GH_RATE_LIMIT["remaining"] = int(target.get("remaining", 10 if not token else 30))
                        GH_RATE_LIMIT["limit"] = int(target.get("limit", 10 if not token else 30))
                        GH_RATE_LIMIT["reset"] = int(target.get("reset", int(time.time()) + 60))
                        GH_RATE_LIMIT["updated"] = int(time.time())
                else:
                    update_gh_rate_limit(resp.headers)
        except Exception as e:
            logger.warning("GitHub rate-limit probe failed: %s", e)

    async with CACHE_LOCK:
        cache_count = len(CACHE)
        cache_keys = list(CACHE.keys())

    return {
        "ok": True,
        "status": "operational",
        "server": "operational",
        "server_status": "operational",
        "cache_count": cache_count,
        "cache_items": cache_count,
        "cache_keys": cache_keys,
        "rate_limit": {
            "remaining": GH_RATE_LIMIT["remaining"],
            "limit": GH_RATE_LIMIT["limit"],
            "reset": GH_RATE_LIMIT["reset"],
        },
        "github_rate_limit": {
            "remaining": GH_RATE_LIMIT["remaining"],
            "limit": GH_RATE_LIMIT["limit"],
            "reset": GH_RATE_LIMIT["reset"],
        },
        "uptime_sec": int(time.time() - SERVER_START_TIME),
        "version": APP_VERSION,
    }

@app.get("/api/discussions")
async def get_discussions(id: str = Query(...), source: str = Query("hn")):
    """Fetch comments/discussion tree for an item."""
    if source == "hn":
        # Only numeric HN item ids; anything else could rewrite the Algolia URL path.
        if not id.isdigit():
            raise HTTPException(status_code=400, detail="Invalid Hacker News item id")
        url = f"https://hn.algolia.com/api/v1/items/{id}"
        async with httpx.AsyncClient(timeout=HTTP_TIMEOUT) as client:
            resp = await client.get(url, headers={"User-Agent": USER_AGENT})
            if resp.status_code != 200:
                raise HTTPException(status_code=resp.status_code, detail="Cannot fetch HN discussion")
            return resp.json()
    else:
        # GitHub item: story ids are numeric repo ids, so only build a link for owner/repo.
        if "/" in id:
            return {"text": f"GitHub discussions available at https://github.com/{id}"}
        return {"text": "GitHub discussions are available on the repository page."}

# Serve frontend static files
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

@app.api_route("/", methods=["GET", "HEAD"])
def serve_index():
    return FileResponse(STATIC_DIR / "index.html")

if __name__ == "__main__":
    import uvicorn
    # Localhost only: the server has no authentication.
    uvicorn.run("server:app", host="127.0.0.1", port=8080, reload=True)
