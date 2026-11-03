"""Integration & End-to-End tests for trending backend & frontend components."""

import io
import json
from pathlib import Path
import subprocess
import time
from fastapi.testclient import TestClient
import server

client = TestClient(server.app)

def test_status_endpoint():
    print("Testing GET /api/status...")
    resp = client.get("/api/status")
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["ok"] is True
    assert data.get("status") == "operational" or data.get("server_status") == "operational"
    assert "cache_count" in data or "cache_items" in data
    assert "rate_limit" in data
    rl = data["rate_limit"]
    assert "remaining" in rl and "limit" in rl and "reset" in rl
    print(f"✓ /api/status OK: status={data.get('status')}, cache_count={data.get('cache_count')}, rate_limit={rl}")

def test_saved_export_and_import():
    print("Testing Saved Bookmarks Export and Import...")
    # 1. Export
    export_resp = client.get("/api/saved/export")
    assert export_resp.status_code == 200, f"Export failed: {export_resp.status_code}"
    assert "application/json" in export_resp.headers.get("content-type", "")
    assert "trending-saved.json" in export_resp.headers.get("content-disposition", "")
    exported = export_resp.json()
    assert isinstance(exported, list)
    print(f"✓ GET /api/saved/export OK: {len(exported)} items exported")

    # 2. Import raw list of stories (JSON body)
    test_stories = [
        {
            "id": "test-story-999001",
            "title": "Test Security Vulnerability CVE-2026-9999",
            "url": "https://example.com/cve",
            "talk": "https://example.com/cve",
            "score": 42.0,
            "extra": 10.0,
            "meta": "author1",
            "blurb": "A critical 0day vulnerability discovered.",
            "t": int(time.time()),
        },
        {
            "story": {
                "id": "test-story-999002",
                "title": "Show HN: Fast Python Engine",
                "url": "https://example.com/hn",
                "talk": "https://example.com/hn",
                "score": 150.0,
                "extra": 25.0,
                "meta": "dev",
                "blurb": "Building tools in Rust and Python.",
                "t": int(time.time()),
            },
            "savedAt": int(time.time()),
        }
    ]
    import_resp = client.post("/api/saved/import", json=test_stories)
    assert import_resp.status_code == 200, f"Import failed: {import_resp.status_code}: {import_resp.text}"
    import_data = import_resp.json()
    assert import_data["ok"] is True
    print(f"✓ POST /api/saved/import OK: added/imported={import_data.get('imported')}, total={import_data.get('total')}")

    # 3. Deduplication check: import same stories again
    import_again = client.post("/api/saved/import", json=test_stories)
    assert import_again.status_code == 200
    again_data = import_again.json()
    assert again_data["imported"] == 0, f"Expected 0 newly imported due to deduplication, got {again_data['imported']}"
    print(f"✓ Deduplication verified: 0 duplicates inserted")

    # 4. Import via multipart file upload
    file_story = [{
        "id": "test-story-999003",
        "title": "Multipart File Upload Test",
        "url": "https://example.com/multipart",
        "talk": "https://example.com/multipart",
        "score": 50.0,
        "extra": 5.0,
        "meta": "qa",
        "blurb": "Testing multipart JSON upload.",
        "t": int(time.time()),
    }]
    f = io.BytesIO(json.dumps(file_story).encode("utf-8"))
    upload_resp = client.post("/api/saved/import", files={"file": ("saved.json", f, "application/json")})
    assert upload_resp.status_code == 200, f"Multipart import failed: {upload_resp.status_code}: {upload_resp.text}"
    assert upload_resp.json()["ok"] is True
    print(f"✓ POST /api/saved/import (multipart) OK: added={upload_resp.json().get('added')}")

    # Clean up test items
    client.delete("/api/saved/test-story-999001")
    client.delete("/api/saved/test-story-999002")
    client.delete("/api/saved/test-story-999003")

def test_feed_pagination():
    print("Testing /api/feed pagination & oldest_t...")
    # HN feed page 1
    resp1 = client.get("/api/feed?source=hn&hours=6&limit=10")
    assert resp1.status_code == 200, f"Feed failed: {resp1.status_code}: {resp1.text}"
    data1 = resp1.json()
    assert data1["ok"] is True
    assert "oldest_t" in data1, "oldest_t missing from feed response"
    oldest_t = data1["oldest_t"]
    assert isinstance(oldest_t, int)
    print(f"✓ /api/feed (HN page 1) OK: {len(data1['shown'])} stories shown, oldest_t={oldest_t}")

    # HN feed page 2 with before=oldest_t
    if oldest_t > 0:
        resp2 = client.get(f"/api/feed?source=hn&hours=6&before={oldest_t}&limit=10")
        assert resp2.status_code == 200, f"Feed with before failed: {resp2.status_code}: {resp2.text}"
        data2 = resp2.json()
        assert data2["ok"] is True
        assert "oldest_t" in data2
        # Verify pagination: stories returned should have timestamp <= oldest_t
        for s in data2["shown"]:
            assert s["t"] <= oldest_t, f"Story {s['id']} t={s['t']} > before={oldest_t}"
        print(f"✓ /api/feed (HN with before={oldest_t}) OK: {len(data2['shown'])} stories shown, oldest_t={data2['oldest_t']}")

    # GH feed with page=1
    gh_resp1 = client.get("/api/feed?source=gh&hours=24&page=1&limit=5")
    assert gh_resp1.status_code == 200, f"GH feed failed: {gh_resp1.status_code}: {gh_resp1.text}"
    gh_data1 = gh_resp1.json()
    assert gh_data1["ok"] is True
    assert "oldest_t" in gh_data1
    print(f"✓ /api/feed (GH page 1) OK: {len(gh_data1['shown'])} repos shown, oldest_t={gh_data1['oldest_t']}")

    # GH feed with page=2
    gh_resp2 = client.get("/api/feed?source=gh&hours=24&page=2&limit=5")
    assert gh_resp2.status_code == 200, f"GH page 2 failed: {gh_resp2.status_code}: {gh_resp2.text}"
    gh_data2 = gh_resp2.json()
    assert gh_data2["ok"] is True
    assert "oldest_t" in gh_data2
    print(f"✓ /api/feed (GH page 2) OK: {len(gh_data2['shown'])} repos shown, oldest_t={gh_data2['oldest_t']}")

def test_alerts_endpoint():
    print("Testing GET /api/alerts...")
    resp = client.get("/api/alerts?hours=6")
    assert resp.status_code == 200, f"Alerts failed: {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["ok"] is True
    assert "alerts" in data
    assert isinstance(data["alerts"], list)
    print(f"✓ GET /api/alerts OK: {len(data['alerts'])} alerts detected")
    if data["alerts"]:
        first = data["alerts"][0]
        # Must have story id, title, label, severity, t
        for key in ["id", "title", "label", "severity", "t"]:
            assert key in first, f"Key '{key}' missing in alert: {first}"
        print(f"   Example alert: [{first['severity'].upper()}] {first['label']}: {first['title'][:50]} (t={first['t']})")

def test_alert_logic_unit():
    print("Testing engine alert evaluation logic...")
    import engine
    now = int(time.time())
    interests = ["Rust", "Python", "AI", "Kernel"]

    # 1. High-severity CVE
    s1 = {"id": "1", "title": "Critical RCE found: CVE-2026-1122 in sudo", "blurb": "Details on vulnerability", "score": 10.0, "t": now - 3600}
    a1 = engine.evaluate_story_alerts(s1, "hn", interests, now)
    assert a1 is not None
    assert a1["severity"] == "critical"
    assert a1["label"] == "CVE-2026-1122"

    # 2. High-severity 0day
    s2 = {"id": "2", "title": "Unpatched zero-day in WebKit exploited in the wild", "blurb": "Active 0day attacks", "score": 15.0, "t": now - 3600}
    a2 = engine.evaluate_story_alerts(s2, "hn", interests, now)
    assert a2 is not None
    assert a2["severity"] == "critical"
    assert a2["label"] == "0day"

    # 3. Breaking story (heat >= 30/hr)
    s3 = {"id": "3", "title": "Database Engine Released", "blurb": "Fast ACID storage", "score": 90.0, "t": now - 3600} # age 1h => heat 90/h >= 30
    a3 = engine.evaluate_story_alerts(s3, "hn", interests, now)
    assert a3 is not None
    assert a3["severity"] == "high"
    assert "Breaking" in a3["label"]

    # 4. Interest keyword match
    s4 = {"id": "4", "title": "Rewriting legacy drivers in Rust for reliability", "blurb": "Modern systems engineering", "score": 5.0, "t": now - 3600} # heat 5/h < 30
    a4 = engine.evaluate_story_alerts(s4, "hn", interests, now)
    assert a4 is not None
    assert a4["severity"] == "medium"
    assert a4["label"] == "Rust"

    # 5. Spam repo should be ignored
    s5 = {"id": "5", "title": "Free Aimbot executor download keygen", "blurb": "Undetected cheat", "score": 50.0, "t": now - 1800}
    a5 = engine.evaluate_story_alerts(s5, "gh", interests, now)
    assert a5 is None, "Spam story was not filtered"

    print("✓ Alert logic unit tests passed")

def test_discussions_endpoint():
    print("Testing GET /api/discussions...")
    # HN discussion
    resp_hn = client.get("/api/discussions?id=1&source=hn")
    assert resp_hn.status_code == 200, f"HN discussion failed: {resp_hn.status_code}"
    hn_data = resp_hn.json()
    assert "author" in hn_data or "id" in hn_data, "Expected HN discussion payload"
    print("✓ /api/discussions (HN) OK")

    # GitHub discussion
    resp_gh = client.get("/api/discussions?id=owner/repo&source=gh")
    assert resp_gh.status_code == 200, f"GH discussion failed: {resp_gh.status_code}"
    gh_data = resp_gh.json()
    assert "text" in gh_data and "owner/repo" in gh_data["text"]
    print("✓ /api/discussions (GH) OK")

def test_static_and_html_components():
    print("Testing static assets & HTML UI components...")
    # Check index.html route
    resp = client.get("/")
    assert resp.status_code == 200, f"GET / failed: {resp.status_code}"
    html = resp.text

    # Required UI components
    components = {
        "Alerts Bell": 'id="btn-alerts"',
        "Alerts Modal": 'id="alerts-modal"',
        "Pagination Container": 'id="pagination-container"',
        "Show More Button": 'id="btn-show-more"',
        "Shortcuts Modal": 'id="shortcuts-modal"',
        "Shortcuts Button": 'id="btn-shortcuts"',
        "Export Saved Button": 'id="btn-export-saved"',
        "Import Saved Button": 'id="btn-import-saved"',
        "Import File Input": 'id="saved-import-file"',
        "Footer Status Box": 'id="footer-status-box"',
        "Footer Rate Limit": 'id="footer-rate-limit"',
        "Footer Status Dot": 'id="footer-status-dot"',
    }
    for comp_name, marker in components.items():
        assert marker in html, f"Missing {comp_name} ({marker}) in index.html"
    print(f"✓ index.html contains all {len(components)} required UI components")

    # Check CSS
    resp_css = client.get("/static/style.css")
    assert resp_css.status_code == 200, f"GET /static/style.css failed: {resp_css.status_code}"
    assert "text/css" in resp_css.headers.get("content-type", "")
    assert len(resp_css.content) > 1000
    print("✓ /static/style.css served OK")

    # Check JS
    resp_js = client.get("/static/app.js")
    assert resp_js.status_code == 200, f"GET /static/app.js failed: {resp_js.status_code}"
    assert "text/javascript" in resp_js.headers.get("content-type", "")
    assert len(resp_js.content) > 5000
    print("✓ /static/app.js served OK")

def test_node_syntax():
    print("Testing app.js syntax with node --check...")
    js_path = Path(__file__).resolve().parent / "static" / "app.js"
    res = subprocess.run(["node", "--check", str(js_path)], capture_output=True, text=True)
    assert res.returncode == 0, f"Node syntax check failed:\n{res.stderr}"
    print("✓ app.js passed node --check syntax verification")

def test_settings_token_masking_and_security():
    print("Testing Settings Token Masking and Security (Fix 1)...")
    # Set a dummy token
    update_resp = client.post("/api/settings", json={"githubToken": "ghp_secret_dummy_token_12345"})
    assert update_resp.status_code == 200
    updated = update_resp.json()
    assert updated.get("githubToken") == "••••••••", f"Expected masked token, got {updated.get('githubToken')}"
    assert updated.get("hasGithubToken") is True

    # Get settings - raw token must NOT be exposed
    get_resp = client.get("/api/settings")
    assert get_resp.status_code == 200
    settings = get_resp.json()
    assert settings.get("githubToken") == "••••••••"
    assert "ghp_secret_dummy_token_12345" not in json.dumps(settings)

    # Submitting with masked token should preserve stored token
    update2 = client.post("/api/settings", json={"githubToken": "••••••••", "sensitivity": 2})
    assert update2.status_code == 200
    assert update2.json()["hasGithubToken"] is True
    # Verify underlying file preserves token
    underlying = server.load_settings()
    assert underlying.get("githubToken") == "ghp_secret_dummy_token_12345"
    assert underlying.get("sensitivity") == 2

    # Clean up token
    client.post("/api/settings", json={"githubToken": ""})
    print("✓ Token sanitization and masking verified successfully")

if __name__ == "__main__":
    test_alert_logic_unit()
    test_settings_token_masking_and_security()
    test_status_endpoint()
    test_saved_export_and_import()
    test_feed_pagination()
    test_alerts_endpoint()
    test_discussions_endpoint()
    test_static_and_html_components()
    test_node_syntax()
    print("\nALL BACKEND & FRONTEND INTEGRATION TESTS PASSED SUCCESSFULLY! 🚀")
