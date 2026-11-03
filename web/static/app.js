// Trending Web Application Client
// Parity with OmaLauncher & Material 3 Design Patterns

const PALETTES = [
  { name: "Amber", hex: "#E68E0D" },
  { name: "Blue", hex: "#1E88E5" },
  { name: "Teal", hex: "#00897B" },
  { name: "Green", hex: "#43A047" },
  { name: "Purple", hex: "#8E24AA" },
  { name: "Pink", hex: "#D81B60" },
  { name: "Red", hex: "#E53935" },
  { name: "Indigo", hex: "#3949AB" },
];

const state = {
  source: "hn",
  mode: "hot",
  hours: 6,
  query: "",
  topic: "",
  lang: "",
  bar: -1,
  only: "",
  page: 1,
  hasMore: true,
  isLoadingMore: false,
  seen: new Set(JSON.parse(localStorage.getItem("trending_seen") || "[]")),
  savedItems: [],
  settings: {
    themeMode: "dark",
    palette: 0,
    interests: ["AI", "Rust", "LLM", "Linux"],
    hideSpam: true,
    sensitivity: 1,
    githubToken: "",
  },
  currentStories: [],
  selectedIndex: -1,
  activeBarLabel: "",
  alerts: [],
  alertFilter: "all",
  notifiedStoryIds: new Set(JSON.parse(sessionStorage.getItem("trending_notified") || "[]")),
};

// Initialize
document.addEventListener("DOMContentLoaded", async () => {
  setupEventHandlers();
  await loadSettings();
  await loadSaved();
  await loadApiStatus();
  await refreshFeed();

  // Periodically refresh GitHub API rate limit status
  setInterval(loadApiStatus, 60000);
});

function applyTheme(themeMode, paletteIdx) {
  document.body.setAttribute("data-theme", themeMode || "dark");
  const p = PALETTES[paletteIdx ?? 0] || PALETTES[0];
  document.documentElement.style.setProperty("--primary", p.hex);
  document.documentElement.style.setProperty("--primary-soft", `${p.hex}26`);
  document.documentElement.style.setProperty("--primary-border", `${p.hex}59`);

  // Update dots in settings
  document.querySelectorAll(".palette-dot").forEach((dot, idx) => {
    dot.classList.toggle("selected", idx === paletteIdx);
  });
}

async function loadSettings() {
  try {
    const res = await fetch("/api/settings");
    if (res.ok) {
      const data = await res.json();
      state.settings = data;
      state.source = data.source || "hn";
      state.mode = data.mode || "hot";
      state.hours = data.hours || 6;
      applyTheme(data.themeMode, data.palette);

      // sync UI controls
      document.querySelector(`[data-source="${state.source}"]`)?.click();
      document.querySelector(`[data-mode="${state.mode}"]`)?.click();
      document.getElementById("select-hours").value = state.hours;
    }
  } catch (err) {
    console.warn("Could not load settings:", err);
  }
}

async function loadSaved() {
  try {
    const res = await fetch("/api/saved");
    if (res.ok) {
      state.savedItems = await res.json();
      updateSavedBadge();
    }
  } catch (err) {
    console.warn("Could not load saved stories:", err);
  }
}

function updateSavedBadge() {
  const badge = document.getElementById("saved-badge");
  const countSpan = document.getElementById("saved-count");
  if (countSpan) countSpan.textContent = state.savedItems.length;
  if (badge) {
    badge.style.display = state.savedItems.length > 0 ? "block" : "none";
  }
}

// ----------------- Feed Fetching & Rendering -----------------

async function refreshFeed(force = false) {
  const container = document.getElementById("stories-list");
  const refreshBtn = document.getElementById("btn-refresh");
  refreshBtn.classList.add("spinning");

  state.page = 1;
  state.hasMore = true;

  const params = new URLSearchParams({
    source: state.source,
    mode: state.mode,
    hours: state.hours,
    query: state.query,
    topic: state.topic,
    lang: state.lang,
    bar: state.bar,
    only: state.only,
    hide_spam: state.settings.hideSpam,
    sensitivity: state.settings.sensitivity,
    force_refresh: force,
    page: 1,
  });

  try {
    const res = await fetch(`/api/feed?${params.toString()}`);
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.detail || "Error loading feed");
    }
    const data = await res.json();
    renderFeed(data);
  } catch (err) {
    container.innerHTML = `
      <div class="loading-box">
        <div style="font-size: 32px;">⚠️</div>
        <div style="color: var(--accent-red); font-weight: 600;">${escapeHtml(err.message)}</div>
        <button class="segmented-btn active" onclick="refreshFeed(true)">Try Again</button>
      </div>
    `;
    document.getElementById("pagination-container").style.display = "none";
  } finally {
    refreshBtn.classList.remove("spinning");
  }
}

function renderFeed(data) {
  state.currentStories = data.shown || [];
  state.selectedIndex = -1;
  state.hasMore = data.hasMore ?? (state.currentStories.length >= 50);

  // Header meta info
  const summaryEl = document.getElementById("feed-meta-summary");
  const timeEl = document.getElementById("feed-fetched-time");
  summaryEl.textContent = `Showing ${state.currentStories.length} stories (${data.total} in window, ${data.hidden} hidden spam)`;
  timeEl.textContent = `Updated ${new Date(data.fetched * 1000).toLocaleTimeString()}`;

  // Counts for quick filters
  document.getElementById("count-security").textContent = data.counts?.security || 0;
  document.getElementById("count-interest").textContent = data.counts?.interest || 0;
  document.getElementById("count-rising").textContent = data.counts?.rising || 0;

  // Score label in chart
  document.getElementById("legend-score-label").textContent = state.source === "hn" ? "Points" : "Stars";

  // Render chart
  renderChart(data.hotBars, data.newBars, data.barLabels);

  // Render topics & languages
  renderTopics(data.topics || []);
  renderLangs(data.langs || []);

  // Update live alerts bell & badge
  updateAlerts(data.shown || []);

  // Render story cards
  const container = document.getElementById("stories-list");
  const paginationContainer = document.getElementById("pagination-container");
  const showMoreBtn = document.getElementById("btn-show-more");
  const showMoreText = document.getElementById("show-more-text");

  if (!state.currentStories.length) {
    container.innerHTML = `
      <div class="loading-box">
        <div style="font-size: 32px;">🔍</div>
        <div>No stories match the active filters or time bucket.</div>
        <button class="action-pill" onclick="clearAllFilters()">Reset Filters</button>
      </div>
    `;
    paginationContainer.style.display = "none";
    return;
  }

  container.innerHTML = state.currentStories.map((story, idx) => renderStoryCard(story, idx + 1)).join("");

  // Pagination button state
  paginationContainer.style.display = "flex";
  if (state.hasMore) {
    showMoreBtn.disabled = false;
    showMoreText.textContent = "Show More Stories";
  } else {
    showMoreBtn.disabled = true;
    showMoreText.textContent = "All Stories Loaded";
  }
}

async function loadMoreStories() {
  if (state.isLoadingMore || !state.hasMore) return;
  state.isLoadingMore = true;

  const showMoreBtn = document.getElementById("btn-show-more");
  const showMoreText = document.getElementById("show-more-text");
  const spinner = document.getElementById("show-more-spinner");

  showMoreBtn.disabled = true;
  spinner.style.display = "inline-block";
  showMoreText.textContent = "Loading more...";

  const nextPage = (state.page || 1) + 1;
  const validTs = state.currentStories.map(s => s.t).filter(t => t > 0);
  const oldest_t = validTs.length ? Math.min(...validTs) : null;

  const params = new URLSearchParams({
    source: state.source,
    mode: state.mode,
    hours: state.hours,
    query: state.query,
    topic: state.topic,
    lang: state.lang,
    bar: state.bar,
    only: state.only,
    hide_spam: state.settings.hideSpam,
    sensitivity: state.settings.sensitivity,
    page: nextPage,
  });

  if (state.source === "hn" && oldest_t) {
    params.append("before", oldest_t);
  }

  try {
    const res = await fetch(`/api/feed?${params.toString()}`);
    if (!res.ok) throw new Error("Failed to load more stories");
    const data = await res.json();
    const newStories = data.shown || [];

    const existingIds = new Set(state.currentStories.map(s => s.id));
    const unique = newStories.filter(s => !existingIds.has(s.id));

    if (unique.length === 0) {
      state.hasMore = false;
      showMoreBtn.disabled = true;
      showMoreText.textContent = "All Stories Loaded";
    } else {
      const startRank = state.currentStories.length + 1;
      state.currentStories.push(...unique);
      state.page = nextPage;
      state.hasMore = data.hasMore ?? (unique.length >= 50);

      // Append new story cards to DOM without resetting scroll position
      const listEl = document.getElementById("stories-list");
      const fragment = document.createDocumentFragment();
      unique.forEach((story, idx) => {
        const wrapper = document.createElement("div");
        wrapper.innerHTML = renderStoryCard(story, startRank + idx);
        fragment.appendChild(wrapper.firstElementChild);
      });
      listEl.appendChild(fragment);

      // Update meta summary count
      document.getElementById("feed-meta-summary").textContent =
        `Showing ${state.currentStories.length} stories (${data.total} in window, ${data.hidden} hidden spam)`;

      // Update alerts
      updateAlerts(state.currentStories);

      showMoreBtn.disabled = !state.hasMore;
      showMoreText.textContent = state.hasMore ? "Show More Stories" : "All Stories Loaded";
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, "error");
    showMoreBtn.disabled = false;
    showMoreText.textContent = "Try Again";
  } finally {
    spinner.style.display = "none";
    state.isLoadingMore = false;
  }
}

function renderChart(hotBars, newBars, barLabels) {
  const container = document.getElementById("chart-container");
  container.innerHTML = "";
  if (!hotBars || !hotBars.length) return;

  const maxHot = Math.max(...hotBars, 1.0);
  const maxNew = Math.max(...newBars, 1.0);

  hotBars.forEach((hotScore, idx) => {
    const newCount = newBars[idx] || 0;
    const label = barLabels[idx] || "";
    const hotPct = Math.round((hotScore / maxHot) * 85);
    const newPct = Math.round((newCount / maxNew) * 85);

    const slot = document.createElement("div");
    slot.className = `chart-bar-slot ${state.bar === idx ? "selected" : ""}`;
    slot.title = `Time: ${label}\n${state.source === "hn" ? "Points" : "Stars"}: ${Math.round(hotScore)}\nNew Items: ${newCount}`;

    slot.innerHTML = `
      <div class="bar-visual-stack">
        <div class="bar-visual hot" style="height: ${Math.max(hotPct, 4)}%;"></div>
        <div class="bar-visual new" style="height: ${Math.max(newPct, 4)}%;"></div>
      </div>
      <span class="bar-label">${idx % 2 === 0 || barLabels.length <= 8 ? label : ""}</span>
    `;

    slot.addEventListener("click", () => {
      if (state.bar === idx) {
        state.bar = -1;
        state.activeBarLabel = "";
        document.getElementById("bar-filter-indicator").style.display = "none";
      } else {
        state.bar = idx;
        state.activeBarLabel = label;
        document.getElementById("bar-filter-indicator").style.display = "inline-flex";
        document.getElementById("bar-filter-name").textContent = label;
      }
      refreshFeed();
    });

    container.appendChild(slot);
  });
}

function renderTopics(topics) {
  const row = document.getElementById("topics-row");
  if (!topics.length) {
    row.innerHTML = "";
    return;
  }
  row.innerHTML = topics.map(t => {
    const active = state.topic === t.word ? "active" : "";
    return `<button class="chip topic-chip ${active}" data-topic="${escapeHtml(t.word)}">${escapeHtml(t.word)} <span class="count">${t.count}</span></button>`;
  }).join("");

  row.querySelectorAll(".topic-chip").forEach(btn => {
    btn.addEventListener("click", () => {
      const w = btn.getAttribute("data-topic");
      state.topic = state.topic === w ? "" : w;
      refreshFeed();
    });
  });
}

function renderLangs(langs) {
  const row = document.getElementById("langs-row");
  if (state.source !== "gh" || !langs.length) {
    row.style.display = "none";
    return;
  }
  row.style.display = "flex";
  row.innerHTML = langs.map(l => {
    const active = state.lang === l.name ? "active" : "";
    return `<button class="chip lang-chip ${active}" data-lang="${escapeHtml(l.name)}">${escapeHtml(l.name)} <span class="count">${l.count}</span></button>`;
  }).join("");

  row.querySelectorAll(".lang-chip").forEach(btn => {
    btn.addEventListener("click", () => {
      const l = btn.getAttribute("data-lang");
      state.lang = state.lang === l ? "" : l;
      refreshFeed();
    });
  });
}

function renderStoryCard(story, rank) {
  const isRead = state.seen.has(story.id);
  const isSaved = state.savedItems.some(s => s.story?.id === story.id);
  const scoreIcon = state.source === "hn" ? "▲" : "★";
  const commentIcon = state.source === "hn" ? "💬" : "⑂";
  const safeUrl = isSafeUrl(story.url) ? story.url : "#";

  return `
    <article class="story-card ${isRead ? "read" : ""}" data-id="${escapeAttr(story.id)}" id="story-${escapeAttr(story.id)}">
      <div class="story-rank">${rank}</div>
      <div class="story-main">
        <div class="story-title-row">
          <a href="${escapeAttr(safeUrl)}" target="_blank" rel="noopener noreferrer" class="story-title" data-story-id="${escapeAttr(story.id)}">
            ${escapeHtml(story.title)}
          </a>
          ${story.domain ? `<a href="https://${escapeAttr(story.domain)}" target="_blank" rel="noopener noreferrer" class="domain-pill">${escapeHtml(story.domain)}</a>` : ""}
          ${story.security ? `<span class="badge-tag security">🛡️ ${escapeHtml(story.security)}</span>` : ""}
          ${story.interest ? `<span class="badge-tag interest">⭐ ${escapeHtml(story.interest)}</span>` : ""}
          ${story.rising ? `<span class="badge-tag rising">🔥 +${Math.round(story.heat)}/hr</span>` : ""}
        </div>

        ${story.blurb ? `<div class="story-blurb">${escapeHtml(story.blurb)}</div>` : ""}

        <div class="story-meta-row">
          <div class="story-stats">
            <span class="stat-item score">${scoreIcon} ${Math.round(story.score)}</span>
            <span class="stat-item">${commentIcon} ${Math.round(story.extra)}</span>
            ${story.meta ? `<span class="stat-item">by ${escapeHtml(story.meta)}</span>` : ""}
            <span class="stat-item">🕒 ${story.ago}</span>
          </div>

          <div class="story-actions">
            <button class="action-pill action-discuss" data-id="${escapeAttr(story.id)}" data-title="${escapeAttr(story.title)}" data-meta="${escapeAttr(story.meta || "")}">
              💬 Discuss
            </button>
            <button class="action-pill ${isSaved ? "active" : ""} action-save" data-id="${escapeAttr(story.id)}" title="Save for later">
              ${isSaved ? "★ Saved" : "☆ Save"}
            </button>
            <button class="action-pill action-copy" data-url="${escapeAttr(story.url)}">
              📋 Copy
            </button>
          </div>
        </div>
      </div>
    </article>
  `;
}

// ----------------- Keyboard Navigation -----------------

function selectStory(newIndex, scroll = true) {
  if (!state.currentStories.length) return;
  const clamped = Math.max(0, Math.min(newIndex, state.currentStories.length - 1));

  if (state.selectedIndex >= 0 && state.selectedIndex < state.currentStories.length) {
    const prevId = state.currentStories[state.selectedIndex].id;
    document.getElementById(`story-${prevId}`)?.classList.remove("selected");
  }

  state.selectedIndex = clamped;
  const currId = state.currentStories[clamped].id;
  const card = document.getElementById(`story-${currId}`);
  if (card) {
    card.classList.add("selected");
    if (scroll) {
      card.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }
}

// ----------------- Actions & Handlers -----------------

function markSeen(id) {
  state.seen.add(id);
  localStorage.setItem("trending_seen", JSON.stringify(Array.from(state.seen)));
  const card = document.getElementById(`story-${id}`);
  if (card) card.classList.add("read");
}

function toggleReadStory(id) {
  const card = document.getElementById(`story-${id}`);
  if (state.seen.has(id)) {
    state.seen.delete(id);
    if (card) card.classList.remove("read");
    showToast("Marked as unread");
  } else {
    state.seen.add(id);
    if (card) card.classList.add("read");
    showToast("Marked as read");
  }
  localStorage.setItem("trending_seen", JSON.stringify(Array.from(state.seen)));
}

async function toggleSaveStory(id) {
  let story = state.currentStories.find(s => s.id === id);
  if (!story) {
    const saved = state.savedItems.find(s => s.story?.id === id);
    if (saved) story = saved.story;
  }
  if (!story) return;

  try {
    const res = await fetch("/api/saved/toggle", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ story }),
    });
    if (res.ok) {
      const data = await res.json();
      state.savedItems = data.items || [];
      updateSavedBadge();

      // Update card button UI in feed
      const card = document.getElementById(`story-${id}`);
      if (card) {
        const btn = card.querySelector(`button[title="Save for later"]`);
        if (btn) {
          const isSaved = data.saved;
          btn.classList.toggle("active", isSaved);
          btn.innerHTML = isSaved ? "★ Saved" : "☆ Save";
        }
      }

      showToast(data.saved ? "Story bookmarked! ★" : "Bookmark removed");
      if (document.getElementById("saved-modal").classList.contains("open")) {
        renderSavedModal();
      }
    }
  } catch (err) {
    showToast("Could not update saved stories", "error");
  }
}

function copyLink(url) {
  if (!url) return;
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(() => {
      showToast("Link copied to clipboard! 📋");
    }).catch(() => {
      fallbackCopyText(url);
    });
  } else {
    fallbackCopyText(url);
  }
}

function fallbackCopyText(text) {
  const ta = document.createElement("textarea");
  ta.value = text;
  ta.style.position = "fixed";
  ta.style.opacity = "0";
  document.body.appendChild(ta);
  ta.focus();
  ta.select();
  try {
    document.execCommand("copy");
    showToast("Link copied to clipboard! 📋");
  } catch (e) {
    showToast("Failed to copy link", "error");
  }
  document.body.removeChild(ta);
}

function showToast(message, type = "info", duration = 2800) {
  const container = document.getElementById("toast-container");
  if (!container) return;
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.classList.add("toast-exit");
    setTimeout(() => {
      toast.remove();
    }, 200);
  }, duration);
}

// ----------------- Discussions & Rich Comments -----------------

async function openDiscussions(id, title, author = "") {
  const modal = document.getElementById("discuss-modal");
  const titleEl = document.getElementById("discuss-title");
  const bodyEl = document.getElementById("discuss-body");
  titleEl.textContent = `Discussion: ${title}`;
  bodyEl.innerHTML = `<div class="loading-box"><div class="spinner"></div><div>Loading comments thread...</div></div>`;
  modal.classList.add("open");

  try {
    const res = await fetch(`/api/discussions?id=${id}&source=${state.source}`);
    if (!res.ok) throw new Error("Could not load comments");
    const data = await res.json();

    if (state.source === "gh") {
      bodyEl.innerHTML = `
        <div style="padding: 16px;">
          <p style="color: var(--text-muted); margin-bottom: 16px;">GitHub repository discussions and issues are hosted directly on GitHub.</p>
          <a href="https://github.com/${id}" target="_blank" rel="noopener noreferrer" class="show-more-btn" style="text-decoration: none;">
            Open on GitHub &rarr;
          </a>
        </div>
      `;
      return;
    }

    const comments = data.children || [];
    if (!comments.length) {
      bodyEl.innerHTML = `<div style="padding: 30px; text-align: center; color: var(--text-muted);">No comments yet on this story.</div>`;
      return;
    }

    const html = comments.map(c => renderCommentNode(c, author, 0)).filter(Boolean).join("");
    bodyEl.innerHTML = `<div class="comments-container">${html}</div>`;
  } catch (err) {
    bodyEl.innerHTML = `<div style="color: var(--accent-red); padding: 24px;">Failed to load comments: ${escapeHtml(err.message)}</div>`;
  }
}

function countCommentReplies(node) {
  if (!node || !node.children) return 0;
  let count = node.children.length;
  for (const ch of node.children) {
    count += countCommentReplies(ch);
  }
  return count;
}

function renderCommentNode(c, opAuthor, depth = 0) {
  if (!c || (!c.text && (!c.children || !c.children.length))) return "";
  const author = c.author || "[deleted]";
  const isOP = opAuthor && author === opAuthor;
  const timeAgo = c.created_at_i ? formatTimeAgo(c.created_at_i) : (c.created_at ? new Date(c.created_at).toLocaleDateString() : "");
  const points = c.points ? `<span class="comment-points">▲ ${c.points}</span>` : "";
  const sanitizedText = sanitizeCommentHtml(c.text || "<em>[no text]</em>");
  const totalReplies = countCommentReplies(c);

  let childrenHtml = "";
  if (depth < 6 && c.children && c.children.length) {
    childrenHtml = c.children.map(ch => renderCommentNode(ch, opAuthor, depth + 1)).filter(Boolean).join("");
  }

  return `
    <div class="comment-node" id="comment-${c.id || Math.random().toString(36).substring(7)}">
      <div class="comment-header">
        <button class="comment-collapse-btn" onclick="toggleCommentCollapse(this)" title="Collapse / expand replies">[-]</button>
        <span class="comment-author">${escapeHtml(author)}</span>
        ${isOP ? `<span class="author-op-badge">OP</span>` : ""}
        <span class="comment-meta">
          ${timeAgo ? `<span>🕒 ${timeAgo}</span>` : ""}
          ${points}
        </span>
        <span class="comment-collapse-summary" onclick="toggleCommentCollapse(this)">(${totalReplies} ${totalReplies === 1 ? "reply" : "replies"} collapsed)</span>
      </div>
      <div class="comment-body">
        ${sanitizedText}
      </div>
      ${childrenHtml ? `<div class="comment-replies">${childrenHtml}</div>` : ""}
    </div>
  `;
}

function toggleCommentCollapse(btn) {
  const node = btn.closest(".comment-node");
  if (!node) return;
  node.classList.toggle("collapsed");
  const collapseBtn = node.querySelector(".comment-header .comment-collapse-btn");
  if (collapseBtn) {
    collapseBtn.textContent = node.classList.contains("collapsed") ? "[+]" : "[-]";
  }
}

function sanitizeCommentHtml(rawHtml) {
  if (!rawHtml) return "";
  const doc = new DOMParser().parseFromString(rawHtml, "text/html");
  const blockedTags = ["script", "style", "iframe", "object", "embed", "form", "input", "textarea"];
  blockedTags.forEach(t => doc.querySelectorAll(t).forEach(el => el.remove()));

  doc.querySelectorAll("*").forEach(el => {
    for (let i = el.attributes.length - 1; i >= 0; i--) {
      const attr = el.attributes[i].name;
      if (attr.startsWith("on") || attr === "style") {
        el.removeAttribute(attr);
      }
    }
    if (el.tagName === "A") {
      el.setAttribute("target", "_blank");
      el.setAttribute("rel", "noopener noreferrer");
      const href = el.getAttribute("href") || "";
      if (!href.startsWith("http://") && !href.startsWith("https://") && !href.startsWith("mailto:")) {
        el.removeAttribute("href");
      }
    }
  });
  return doc.body.innerHTML;
}

function formatTimeAgo(sec) {
  const now = Math.floor(Date.now() / 1000);
  const diff = Math.max(0, now - sec);
  if (diff < 60) return "just now";
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

// ----------------- Saved Stories Export & Import -----------------

function renderSavedModal() {
  const body = document.getElementById("saved-body");
  if (!state.savedItems.length) {
    body.innerHTML = `<div class="loading-box"><div>No saved stories yet. Click the "Save" button on any story to bookmark it.</div></div>`;
  } else {
    body.innerHTML = state.savedItems.map((item, i) => renderStoryCard(item.story, i + 1)).join("");
  }
  updateSavedBadge();
}

function exportSavedStories() {
  if (!state.savedItems || !state.savedItems.length) {
    showToast("No saved stories to export.", "warn");
    return;
  }
  const jsonStr = JSON.stringify(state.savedItems, null, 2);
  const blob = new Blob([jsonStr], { type: "application/json;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "saved_stories.json";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  showToast(`Exported ${state.savedItems.length} stories as saved_stories.json`);
}

async function importSavedStories(file) {
  if (!file) return;
  try {
    const text = await file.text();
    let parsed;
    try {
      parsed = JSON.parse(text);
    } catch (e) {
      showToast("Invalid JSON file", "error");
      return;
    }

    const res = await fetch("/api/saved/import", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(parsed),
    });
    if (!res.ok) {
      throw new Error("Server rejected import");
    }
    const data = await res.json();
    state.savedItems = data.items || [];
    renderSavedModal();
    showToast(`Successfully imported ${data.added || 0} stories! (Total: ${state.savedItems.length})`);
    refreshFeed();
  } catch (err) {
    showToast(`Import failed: ${err.message}`, "error");
  }
}

// ----------------- Live Alerts & Desktop Notifications -----------------

async function updateAlerts(stories) {
  // Client-side detection of alerts from active stories
  const clientAlerts = (stories || []).filter(s => {
    const isBreaking = (s.heat >= 30);
    const isSec = Boolean(s.security);
    const isInt = Boolean(s.interest);
    return isBreaking || isSec || isInt;
  });

  let mergedAlerts = clientAlerts;
  try {
    const res = await fetch(`/api/alerts?hours=${state.hours}&source=${state.source}`);
    if (res.ok) {
      const data = await res.json();
      if (data.ok && Array.isArray(data.alerts) && data.alerts.length > 0) {
        const seen = new Set();
        mergedAlerts = [];
        // Server alerts
        data.alerts.forEach(a => {
          const s = a.story || a;
          const sid = s.id || a.id;
          if (sid && !seen.has(sid)) {
            seen.add(sid);
            mergedAlerts.push({
              ...s,
              id: sid,
              heat: a.heat ?? s.heat ?? 0,
              security: a.cve || a.security || s.security || (a.type === "cve" ? (a.reason || "CVE") : ""),
              interest: a.keyword || a.interest || s.interest || (a.type === "interest" ? (a.reason || "Interest") : ""),
              severity: a.severity || (a.heat >= 30 ? "high" : "medium"),
            });
          }
        });
        // Merge any client-side items
        clientAlerts.forEach(c => {
          if (c.id && !seen.has(c.id)) {
            seen.add(c.id);
            mergedAlerts.push(c);
          }
        });
      }
    }
  } catch (e) {
    // fallback to clientAlerts
  }

  state.alerts = mergedAlerts;

  const badge = document.getElementById("alerts-badge");
  const modalCount = document.getElementById("alerts-modal-count");
  const countAll = document.getElementById("count-alert-all");
  const countBreaking = document.getElementById("count-alert-breaking");
  const countSecurity = document.getElementById("count-alert-security");
  const countInterest = document.getElementById("count-alert-interest");

  const breakingCount = state.alerts.filter(s => s.heat >= 30).length;
  const securityCount = state.alerts.filter(s => Boolean(s.security)).length;
  const interestCount = state.alerts.filter(s => Boolean(s.interest)).length;

  if (badge) {
    if (state.alerts.length > 0) {
      badge.textContent = state.alerts.length;
      badge.style.display = "flex";
    } else {
      badge.style.display = "none";
    }
  }
  if (modalCount) modalCount.textContent = state.alerts.length;
  if (countAll) countAll.textContent = state.alerts.length;
  if (countBreaking) countBreaking.textContent = breakingCount;
  if (countSecurity) countSecurity.textContent = securityCount;
  if (countInterest) countInterest.textContent = interestCount;

  // Check desktop notification permission status
  updateNotificationBanner();
  checkDesktopNotifications(state.alerts);
}

function updateNotificationBanner() {
  const btn = document.getElementById("btn-request-notif");
  const sub = document.getElementById("notif-banner-sub");
  if (!btn || !sub) return;

  if (!("Notification" in window)) {
    btn.disabled = true;
    btn.textContent = "Not Supported";
    sub.textContent = "Browser notifications not supported in this environment.";
    return;
  }

  if (Notification.permission === "granted") {
    btn.disabled = true;
    btn.textContent = "✓ Active";
    btn.classList.remove("active");
    sub.textContent = "Desktop notifications enabled for breaking spikes and critical CVEs.";
  } else if (Notification.permission === "denied") {
    btn.disabled = true;
    btn.textContent = "Blocked";
    btn.classList.remove("active");
    sub.textContent = "Notifications are blocked in your browser site settings.";
  } else {
    btn.disabled = false;
    btn.textContent = "Enable Notifications";
    btn.classList.add("active");
    sub.textContent = "Get real-time browser alerts when stories spike or match critical CVEs.";
  }
}

function checkDesktopNotifications(alerts) {
  if (!("Notification" in window) || Notification.permission !== "granted") return;
  alerts.forEach(s => {
    if (!state.notifiedStoryIds.has(s.id)) {
      state.notifiedStoryIds.add(s.id);
      sessionStorage.setItem("trending_notified", JSON.stringify(Array.from(state.notifiedStoryIds)));
      const tag = s.security ? `🛡️ ${s.security}` : (s.heat >= 30 ? `🔥 Spike +${Math.round(s.heat)}/hr` : `⭐ Matched Interest`);
      try {
        new Notification(`Trending Alert: ${tag}`, {
          body: s.title,
        });
      } catch (e) {
        // Notification error fallback
      }
    }
  });
}

function renderAlertsList() {
  const container = document.getElementById("alerts-list");
  if (!container) return;

  const filter = state.alertFilter || "all";
  let items = state.alerts;
  if (filter === "breaking") items = items.filter(s => s.heat >= 30);
  else if (filter === "security") items = items.filter(s => Boolean(s.security));
  else if (filter === "interest") items = items.filter(s => Boolean(s.interest));

  if (!items.length) {
    container.innerHTML = `
      <div class="loading-box" style="padding: 40px 20px;">
        <div style="font-size: 32px;">🔔</div>
        <div>No active alerts in this category right now.</div>
      </div>
    `;
    return;
  }

  container.innerHTML = items.map(s => {
    const isSaved = state.savedItems.some(item => item.story?.id === s.id);
    let cardType = "breaking";
    if (s.security) cardType = "security";
    else if (s.interest) cardType = "interest";

    return `
      <div class="alert-card ${cardType}">
        <div class="alert-header-row">
          <div class="alert-tag-badges">
            ${s.security ? `<span class="badge-tag security">🛡️ ${escapeHtml(s.security)}</span>` : ""}
            ${s.heat >= 30 ? `<span class="badge-tag rising">🔥 Velocity +${Math.round(s.heat)}/hr</span>` : ""}
            ${s.interest ? `<span class="badge-tag interest">⭐ ${escapeHtml(s.interest)}</span>` : ""}
          </div>
          <span class="stat-item" style="font-size: 11.5px;">🕒 ${s.ago}</span>
        </div>
        <a href="${escapeAttr(isSafeUrl(s.url) ? s.url : "#")}" target="_blank" rel="noopener noreferrer" class="alert-title" data-story-id="${escapeAttr(s.id)}">
          ${escapeHtml(s.title)}
        </a>
        <div class="alert-meta-row">
          <div class="story-stats">
            <span class="stat-item score">${state.source === "hn" ? "▲" : "★"} ${Math.round(s.score)}</span>
            <span class="stat-item">💬 ${Math.round(s.extra)}</span>
            ${s.meta ? `<span class="stat-item">by ${escapeHtml(s.meta)}</span>` : ""}
          </div>
          <div class="story-actions">
            <button class="action-pill action-discuss" data-id="${escapeAttr(s.id)}" data-title="${escapeAttr(s.title)}" data-meta="${escapeAttr(s.meta || "")}">💬 Discuss</button>
            <button class="action-pill ${isSaved ? "active" : ""} action-save" data-id="${escapeAttr(s.id)}">${isSaved ? "★ Saved" : "☆ Save"}</button>
            <button class="action-pill action-copy" data-url="${escapeAttr(s.url)}">📋 Copy</button>
          </div>
        </div>
      </div>
    `;
  }).join("");
}

// ----------------- GitHub API Status Indicator -----------------

async function loadApiStatus() {
  const footerEl = document.getElementById("footer-rate-limit");
  const dotEl = document.getElementById("footer-status-dot");
  const searchEl = document.getElementById("settings-rate-search");
  const coreEl = document.getElementById("settings-rate-core");
  const resetEl = document.getElementById("settings-rate-reset");
  const authEl = document.getElementById("settings-rate-auth");

  try {
    const res = await fetch("/api/status");
    if (!res.ok) throw new Error("Status API unavailable");
    const data = await res.json();

    const rl = data.rate_limit || data.github_rate_limit || data.search || {};
    const searchRemaining = rl.remaining ?? data.search?.remaining ?? "--";
    const searchLimit = rl.limit ?? data.search?.limit ?? "--";
    const coreRemaining = data.core?.remaining ?? (typeof searchLimit === "number" && searchLimit > 30 ? 5000 : 60);
    const coreLimit = data.core?.limit ?? (typeof searchLimit === "number" && searchLimit > 30 ? 5000 : 60);
    const isAuth = data.authenticated ?? (Boolean(state.settings.githubToken) || (typeof searchLimit === "number" && searchLimit > 10));

    let resetStr = "--";
    const resetTimestamp = rl.reset ?? data.search?.reset;
    if (resetTimestamp) {
      const diffSec = Math.max(0, resetTimestamp - Math.floor(Date.now() / 1000));
      if (diffSec < 60) resetStr = `${diffSec}s`;
      else resetStr = `${Math.ceil(diffSec / 60)}m`;
    }

    if (footerEl) {
      footerEl.textContent = `${searchRemaining}/${searchLimit} search remaining (${resetStr})`;
    }
    if (dotEl) {
      dotEl.className = "status-indicator-dot";
      if (typeof searchRemaining === "number" && searchRemaining <= 0) dotEl.classList.add("error");
      else if (typeof searchRemaining === "number" && searchRemaining <= 3) dotEl.classList.add("warning");
    }

    if (searchEl) searchEl.textContent = `${searchRemaining} / ${searchLimit}`;
    if (coreEl) coreEl.textContent = `${coreRemaining} / ${coreLimit}`;
    if (resetEl) resetEl.textContent = resetStr;
    if (authEl) authEl.textContent = isAuth ? "Token Active (5k/hr)" : "Anonymous (10/hr)";

    return data;
  } catch (err) {
    if (footerEl) footerEl.textContent = "Rate status unavailable";
    if (dotEl) dotEl.className = "status-indicator-dot warning";
  }
}

// ----------------- Setup Event Handlers -----------------

function clearAllFilters() {
  state.query = "";
  state.topic = "";
  state.lang = "";
  state.bar = -1;
  state.only = "";
  document.getElementById("search-input").value = "";
  document.getElementById("search-clear").style.display = "none";
  document.getElementById("bar-filter-indicator").style.display = "none";
  document.querySelectorAll("#quick-filters-row .chip").forEach(c => {
    c.classList.toggle("active", c.getAttribute("data-filter") === "");
  });
  refreshFeed();
}

function setupEventHandlers() {
  // Source switches (HN / GH)
  document.querySelectorAll("#source-group .segmented-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#source-group .segmented-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.source = btn.getAttribute("data-source");
      state.topic = "";
      state.lang = "";
      state.bar = -1;
      document.getElementById("bar-filter-indicator").style.display = "none";
      refreshFeed();
    });
  });

  // Mode switches (Hot / New / Rising)
  document.querySelectorAll("#mode-group .segmented-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#mode-group .segmented-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.mode = btn.getAttribute("data-mode");
      refreshFeed();
    });
  });

  // Hours dropdown
  document.getElementById("select-hours").addEventListener("change", e => {
    state.hours = parseInt(e.target.value, 10);
    state.bar = -1;
    document.getElementById("bar-filter-indicator").style.display = "none";
    refreshFeed();
  });

  // Refresh button
  document.getElementById("btn-refresh").addEventListener("click", () => {
    refreshFeed(true);
  });

  // Delegated click handler for stories, saved stories, and alerts
  document.addEventListener("click", e => {
    const discussBtn = e.target.closest(".action-discuss");
    if (discussBtn) {
      e.preventDefault();
      openDiscussions(discussBtn.dataset.id, discussBtn.dataset.title, discussBtn.dataset.meta);
      return;
    }
    const saveBtn = e.target.closest(".action-save");
    if (saveBtn) {
      e.preventDefault();
      toggleSaveStory(saveBtn.dataset.id);
      return;
    }
    const copyBtn = e.target.closest(".action-copy");
    if (copyBtn) {
      e.preventDefault();
      copyLink(copyBtn.dataset.url);
      return;
    }
    const titleLink = e.target.closest(".story-title, .alert-title");
    if (titleLink && titleLink.dataset.storyId) {
      markSeen(titleLink.dataset.storyId);
    }
  });

  // Pagination Show More button
  document.getElementById("btn-show-more").addEventListener("click", () => {
    loadMoreStories();
  });

  // Search input
  const searchInput = document.getElementById("search-input");
  const searchClear = document.getElementById("search-clear");
  let searchTimer = null;

  searchInput.addEventListener("input", e => {
    const val = e.target.value;
    searchClear.style.display = val ? "block" : "none";
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.query = val;
      refreshFeed();
    }, 250);
  });

  searchClear.addEventListener("click", () => {
    searchInput.value = "";
    searchClear.style.display = "none";
    state.query = "";
    refreshFeed();
  });

  // Bar filter clear pill
  document.getElementById("bar-filter-badge").addEventListener("click", () => {
    state.bar = -1;
    document.getElementById("bar-filter-indicator").style.display = "none";
    refreshFeed();
  });

  // Quick filters (All, Security, Interests, Rising)
  document.querySelectorAll("#quick-filters-row .chip").forEach(chip => {
    chip.addEventListener("click", () => {
      document.querySelectorAll("#quick-filters-row .chip").forEach(c => c.classList.remove("active"));
      chip.classList.add("active");
      state.only = chip.getAttribute("data-filter") || "";
      refreshFeed();
    });
  });

  // Theme toggle button
  document.getElementById("btn-theme").addEventListener("click", () => {
    const current = document.body.getAttribute("data-theme") || "dark";
    const next = current === "dark" ? "light" : current === "light" ? "black" : "dark";
    state.settings.themeMode = next;
    applyTheme(next, state.settings.palette);
    saveSettingsToServer();
  });

  // Saved Stories Modal
  const savedModal = document.getElementById("saved-modal");
  document.getElementById("btn-saved").addEventListener("click", () => {
    renderSavedModal();
    savedModal.classList.add("open");
  });
  document.getElementById("saved-close").addEventListener("click", () => {
    savedModal.classList.remove("open");
  });

  // Saved Stories Export / Import
  document.getElementById("btn-export-saved").addEventListener("click", () => {
    exportSavedStories();
  });

  const fileInput = document.getElementById("saved-import-file");
  document.getElementById("btn-import-saved").addEventListener("click", () => {
    fileInput.click();
  });
  fileInput.addEventListener("change", e => {
    const file = e.target.files[0];
    if (file) {
      importSavedStories(file);
      e.target.value = "";
    }
  });

  // Live Alerts Modal
  const alertsModal = document.getElementById("alerts-modal");
  document.getElementById("btn-alerts").addEventListener("click", () => {
    state.alertFilter = "all";
    document.querySelectorAll("#alerts-filter-row .chip").forEach(c => {
      c.classList.toggle("active", c.getAttribute("data-alert-filter") === "all");
    });
    renderAlertsList();
    updateNotificationBanner();
    alertsModal.classList.add("open");
  });
  document.getElementById("alerts-close").addEventListener("click", () => {
    alertsModal.classList.remove("open");
  });

  // Alerts filter chips
  document.querySelectorAll("#alerts-filter-row .chip").forEach(chip => {
    chip.addEventListener("click", () => {
      document.querySelectorAll("#alerts-filter-row .chip").forEach(c => c.classList.remove("active"));
      chip.classList.add("active");
      state.alertFilter = chip.getAttribute("data-alert-filter") || "all";
      renderAlertsList();
    });
  });

  // Desktop notification permission request
  document.getElementById("btn-request-notif").addEventListener("click", async () => {
    if (!("Notification" in window)) {
      showToast("Notifications not supported in your browser", "warn");
      return;
    }
    try {
      const res = await Notification.requestPermission();
      updateNotificationBanner();
      if (res === "granted") {
        showToast("Desktop notifications enabled! 🔔");
        checkDesktopNotifications(state.alerts);
      } else {
        showToast("Notification permission not granted", "warn");
      }
    } catch (e) {
      showToast("Could not request notification permission", "error");
    }
  });

  // Keyboard Shortcuts Modal
  const shortcutsModal = document.getElementById("shortcuts-modal");
  document.getElementById("btn-shortcuts").addEventListener("click", () => {
    shortcutsModal.classList.add("open");
  });
  document.getElementById("shortcuts-close").addEventListener("click", () => {
    shortcutsModal.classList.remove("open");
  });
  document.getElementById("footer-shortcuts-trigger").addEventListener("click", () => {
    shortcutsModal.classList.add("open");
  });

  // Discussions Modal Close
  document.getElementById("discuss-close").addEventListener("click", () => {
    document.getElementById("discuss-modal").classList.remove("open");
  });

  // Settings Modal
  const settingsModal = document.getElementById("settings-modal");
  document.getElementById("btn-settings").addEventListener("click", () => {
    document.getElementById("settings-theme-mode").value = state.settings.themeMode || "dark";
    document.getElementById("settings-interests").value = (state.settings.interests || []).join(", ");
    document.getElementById("settings-hide-spam").checked = state.settings.hideSpam ?? true;
    document.getElementById("settings-sensitivity").value = state.settings.sensitivity ?? 1;
    document.getElementById("settings-github-token").value = state.settings.githubToken || "";
    loadApiStatus();
    settingsModal.classList.add("open");
  });
  document.getElementById("settings-close").addEventListener("click", () => {
    settingsModal.classList.remove("open");
  });

  // Refresh status button in Settings
  document.getElementById("btn-refresh-status").addEventListener("click", async () => {
    await loadApiStatus();
    showToast("GitHub API status updated ↻");
  });

  // Footer status pill click to refresh
  document.getElementById("footer-status-box").addEventListener("click", async () => {
    await loadApiStatus();
    showToast("GitHub API status updated ↻");
  });

  // Palette dots in settings
  document.querySelectorAll(".palette-dot").forEach(dot => {
    dot.addEventListener("click", () => {
      const idx = parseInt(dot.getAttribute("data-idx"), 10);
      state.settings.palette = idx;
      applyTheme(state.settings.themeMode, idx);
    });
  });

  // Save Settings button
  document.getElementById("btn-save-settings").addEventListener("click", async () => {
    state.settings.themeMode = document.getElementById("settings-theme-mode").value;
    state.settings.interests = document.getElementById("settings-interests").value
      .split(",")
      .map(s => s.trim())
      .filter(Boolean);
    state.settings.hideSpam = document.getElementById("settings-hide-spam").checked;
    state.settings.sensitivity = parseInt(document.getElementById("settings-sensitivity").value, 10);
    state.settings.githubToken = document.getElementById("settings-github-token").value.trim();

    await saveSettingsToServer();
    applyTheme(state.settings.themeMode, state.settings.palette);
    settingsModal.classList.remove("open");
    await loadApiStatus();
    refreshFeed();
    showToast("Settings saved successfully");
  });

  // Keyboard navigation & Shortcuts
  window.addEventListener("keydown", e => {
    // If typing in input / textarea / select, ignore shortcuts except Escape
    if (e.target.tagName === "INPUT" || e.target.tagName === "SELECT" || e.target.tagName === "TEXTAREA" || e.target.isContentEditable) {
      if (e.key === "Escape") e.target.blur();
      return;
    }

    const openModal = document.querySelector(".modal-overlay.open");

    // Escape closes open modals
    if (e.key === "Escape") {
      if (openModal) {
        openModal.classList.remove("open");
      } else if (state.selectedIndex >= 0) {
        const prevId = state.currentStories[state.selectedIndex]?.id;
        document.getElementById(`story-${prevId}`)?.classList.remove("selected");
        state.selectedIndex = -1;
      }
      return;
    }

    // Question mark (?) toggles Shortcuts cheatsheet modal
    if (e.key === "?") {
      e.preventDefault();
      shortcutsModal.classList.toggle("open");
      return;
    }

    // If any modal is open, prevent other feed shortcuts from firing
    if (openModal) return;

    // Search focus
    if (e.key === "/") {
      e.preventDefault();
      document.getElementById("search-input").focus();
      return;
    }

    // Refresh feed
    if (e.key === "r" || e.key === "R") {
      e.preventDefault();
      refreshFeed(true);
      return;
    }

    // Navigation down: j
    if (e.key === "j" || e.key === "J") {
      e.preventDefault();
      selectStory(state.selectedIndex + 1);
      return;
    }

    // Navigation up: k
    if (e.key === "k" || e.key === "K") {
      e.preventDefault();
      selectStory(state.selectedIndex <= 0 ? 0 : state.selectedIndex - 1);
      return;
    }

    // Open link in new tab: Enter or o
    if (e.key === "Enter" || e.key === "o" || e.key === "O") {
      if (state.selectedIndex >= 0 && state.currentStories[state.selectedIndex]) {
        e.preventDefault();
        const story = state.currentStories[state.selectedIndex];
        markSeen(story.id);
        window.open(story.url, "_blank", "noopener,noreferrer");
      }
      return;
    }

    // Toggle bookmark / save: s
    if (e.key === "s" || e.key === "S") {
      if (state.selectedIndex >= 0 && state.currentStories[state.selectedIndex]) {
        e.preventDefault();
        const story = state.currentStories[state.selectedIndex];
        toggleSaveStory(story.id);
      }
      return;
    }

    // Open discussion modal: d
    if (e.key === "d" || e.key === "D") {
      if (state.selectedIndex >= 0 && state.currentStories[state.selectedIndex]) {
        e.preventDefault();
        const story = state.currentStories[state.selectedIndex];
        openDiscussions(story.id, story.title, story.meta || "");
      }
      return;
    }

    // Toggle read/unread mark: m
    if (e.key === "m" || e.key === "M") {
      if (state.selectedIndex >= 0 && state.currentStories[state.selectedIndex]) {
        e.preventDefault();
        const story = state.currentStories[state.selectedIndex];
        toggleReadStory(story.id);
      }
      return;
    }

    // Copy story URL: c
    if (e.key === "c" || e.key === "C") {
      if (state.selectedIndex >= 0 && state.currentStories[state.selectedIndex]) {
        e.preventDefault();
        const story = state.currentStories[state.selectedIndex];
        copyLink(story.url);
      }
      return;
    }
  });

  // Close modals on backdrop click
  document.querySelectorAll(".modal-overlay").forEach(modal => {
    modal.addEventListener("click", e => {
      if (e.target === modal) modal.classList.remove("open");
    });
  });
}

async function saveSettingsToServer() {
  try {
    await fetch("/api/settings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(state.settings),
    });
  } catch (err) {
    console.warn("Could not save settings:", err);
  }
}

function isSafeUrl(url) {
  if (!url || typeof url !== "string") return false;
  try {
    const parsed = new URL(url, window.location.origin);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function escapeHtml(text) {
  if (!text) return "";
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function escapeAttr(text) {
  if (!text) return "";
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
