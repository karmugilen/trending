package app.omalauncher.trending.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.data.Saved
import app.omalauncher.trending.logic.Flags
import app.omalauncher.trending.logic.Story
import kotlinx.coroutines.launch

/** Stories kept for later, newest first. */
@Composable
fun SavedScreen(
    saved: List<Saved>,
    nowSec: Long,
    onBack: () -> Unit,
    onOpen: (Story) -> Unit,
    onOpenThread: (Story) -> Unit,
    onShare: (Story) -> Unit,
    onRemove: (Story) -> Unit,
    onRestore: (List<Saved>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    // The Surface sets the text and icon color for everything on the page.
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "top") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text("Saved", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                when (saved.size) {
                                    0 -> "Nothing here yet"
                                    1 -> "1 story"
                                    else -> "${saved.size} stories"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (saved.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(AppIcons.BookmarkBorder, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                            Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap the bookmark on any story or repo to keep it here. Saved items stay after they leave the feed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(saved, key = { it.story.id }) { item ->
                    SavedCard(
                        item = item,
                        nowSec = nowSec,
                        onOpen = { onOpen(item.story) },
                        onOpenThread = { onOpenThread(item.story) },
                        onShare = { onShare(item.story) },
                        onRemove = {
                            val before = saved
                            onRemove(item.story)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar("Removed from saved", actionLabel = "Undo")
                                if (result == SnackbarResult.ActionPerformed) onRestore(before)
                            }
                        },
                        modifier = Modifier.widthIn(max = 720.dp).animateItem(),
                    )
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SavedCard(
    item: Saved,
    nowSec: Long,
    onOpen: () -> Unit,
    onOpenThread: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val story = item.story
    val hn = story.id.startsWith("hn:")
    val shape = RoundedCornerShape(16.dp)
    Surface(modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(
            Modifier.clickable(onClick = onOpen).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                story.title,
                modifier = Modifier.padding(end = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val where = buildList {
                add(if (hn) story.domain.ifEmpty { "Hacker News" } else "GitHub")
                if (!hn && story.meta.isNotEmpty()) add(story.meta)
                if (story.has(Flags.SECURITY) && story.tag.isNotEmpty()) add(story.tag)
                add("saved ${savedAgo(item.savedAt, nowSec)}")
            }.joinToString(" · ")
            Text(where, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (story.blurb.isNotEmpty()) {
                Text(
                    story.blurb,
                    modifier = Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${TrendingCore.fmtCompact(story.score)} ${if (hn) "points" else "stars"} when saved",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Spacer(Modifier.weight(1f))
                if (hn && story.talk.isNotEmpty() && story.talk != story.url) {
                    IconButton(onClick = onOpenThread) {
                        Icon(AppIcons.Comment, contentDescription = "Open discussion", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(AppIcons.Bookmark, contentDescription = "Remove from saved", tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun savedAgo(t: Long, now: Long): String {
    if (t <= 0) return "a while ago"
    val ago = TrendingCore.fmtAgo(t, now)
    return if (ago == "now") "just now" else "$ago ago"
}
