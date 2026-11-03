package app.omalauncher.trending.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omalauncher.trending.core.TrendingCore
import app.omalauncher.trending.logic.Briefing
import app.omalauncher.trending.logic.Flags
import app.omalauncher.trending.logic.Story

/** "Since you last looked" Personal Radar Briefing. */
@Composable
fun RadarScreen(
    briefing: Briefing?,
    nowSec: Long,
    seen: List<String> = emptyList(),
    onBack: () -> Unit,
    onOpen: (Story) -> Unit,
    onOpenThread: (Story) -> Unit,
    onToggleSave: (Story) -> Unit,
    isSaved: (String) -> Boolean,
    onMarkCaughtUp: () -> Unit,
    onRefresh: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "Daily Radar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    val timeText = if (briefing != null && briefing.periodStart > 0) {
                        "Since ${TrendingCore.fmtAgo(briefing.periodStart, nowSec)} · ${briefing.readTimeMins} min read"
                    } else {
                        "Since you last looked · 2 min read"
                    }
                    Text(
                        timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                }
                IconButton(onClick = onMarkCaughtUp) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Mark all as read", tint = colors.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = colors.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

            if (briefing == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Gathering radar signals…", color = colors.onSurfaceVariant)
                }
                return@Column
            }

            val isRead = { id: String -> id in seen }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Section: Affects your stack
                if (briefing.affectsYou.isNotEmpty()) {
                    item {
                        RadarSectionHeader(
                            title = "AFFECTS YOUR STACK",
                            desc = "Dependencies in your stack with security advisories or breakouts",
                            badgeColor = colors.error,
                        )
                    }
                    items(briefing.affectsYou, key = { "affects_${it.id}" }) { story ->
                        RadarStoryCard(
                            story = story,
                            nowSec = nowSec,
                            read = isRead(story.id),
                            onOpen = onOpen,
                            onOpenThread = onOpenThread,
                            onToggleSave = onToggleSave,
                            saved = isSaved(story.id),
                            highlight = story.affectsStack.ifEmpty { "stack dependency" },
                        )
                    }
                }

                // Section: Broke out
                if (briefing.brokeOut.isNotEmpty()) {
                    item {
                        RadarSectionHeader(
                            title = "BROKE OUT",
                            desc = "Gaining velocity rapidly in the last few hours",
                            badgeColor = colors.primary,
                        )
                    }
                    items(briefing.brokeOut, key = { "broke_${it.id}" }) { story ->
                        RadarStoryCard(
                            story = story,
                            nowSec = nowSec,
                            read = isRead(story.id),
                            onOpen = onOpen,
                            onOpenThread = onOpenThread,
                            onToggleSave = onToggleSave,
                            saved = isSaved(story.id),
                        )
                    }
                }

                // Section: Cross-Source
                if (briefing.crossSource.isNotEmpty()) {
                    item {
                        RadarSectionHeader(
                            title = "CROSS-SOURCE PROJECT",
                            desc = "Trending on both Hacker News and GitHub simultaneously",
                            badgeColor = colors.tertiary,
                        )
                    }
                    items(briefing.crossSource, key = { "cross_${it.id}" }) { story ->
                        RadarStoryCard(
                            story = story,
                            nowSec = nowSec,
                            read = isRead(story.id),
                            onOpen = onOpen,
                            onOpenThread = onOpenThread,
                            onToggleSave = onToggleSave,
                            saved = isSaved(story.id),
                            badgeText = "On HN & GitHub",
                        )
                    }
                }

                // Section: For you
                if (briefing.forYou.isNotEmpty()) {
                    item {
                        RadarSectionHeader(
                            title = "FOR YOUR INTERESTS",
                            desc = "Matched your selected topics and keywords",
                            badgeColor = colors.secondary,
                        )
                    }
                    items(briefing.forYou, key = { "for_${it.id}" }) { story ->
                        RadarStoryCard(
                            story = story,
                            nowSec = nowSec,
                            read = isRead(story.id),
                            onOpen = onOpen,
                            onOpenThread = onOpenThread,
                            onToggleSave = onToggleSave,
                            saved = isSaved(story.id),
                        )
                    }
                }

                // Caught up card
                item {
                    CaughtUpCard(
                        count = briefing.caughtUpCount,
                        onMarkCaughtUp = onMarkCaughtUp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarSectionHeader(title: String, desc: String, badgeColor: androidx.compose.ui.graphics.Color) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(badgeColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = colors.onBackground,
            )
        }
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
    }
}

@Composable
private fun RadarStoryCard(
    story: Story,
    nowSec: Long,
    read: Boolean = false,
    onOpen: (Story) -> Unit,
    onOpenThread: (Story) -> Unit,
    onToggleSave: (Story) -> Unit,
    saved: Boolean,
    highlight: String = "",
    badgeText: String = "",
) {
    val colors = MaterialTheme.colorScheme
    val isCritical = !read && (highlight.isNotEmpty() || story.tag.isNotEmpty() || story.has(Flags.SECURITY))
    val critTransition = rememberInfiniteTransition(label = "radarCrit")
    val pulseAlpha by critTransition.animateFloat(
        initialValue = if (isCritical) 0.35f else 1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "radarPulse",
    )
    val activePulse = if (isCritical) pulseAlpha else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(story) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                read -> colors.surfaceContainerLowest
                isCritical -> colors.errorContainer.copy(alpha = 0.14f)
                else -> colors.surfaceContainer
            }
        ),
        border = if (isCritical) BorderStroke(1.dp, colors.error.copy(alpha = activePulse * 0.45f)) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Badges row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (highlight.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (read) colors.errorContainer.copy(alpha = 0.45f) else colors.errorContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(if (read) colors.onErrorContainer.copy(alpha = 0.4f) else colors.onErrorContainer.copy(alpha = activePulse), CircleShape)
                            )
                            Text(
                                "⚡ Affects: $highlight",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (read) colors.onErrorContainer.copy(alpha = 0.6f) else colors.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (badgeText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (read) colors.tertiaryContainer.copy(alpha = 0.45f) else colors.tertiaryContainer,
                    ) {
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (read) colors.onTertiaryContainer.copy(alpha = 0.6f) else colors.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (story.tag.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (read) colors.errorContainer.copy(alpha = 0.45f) else colors.errorContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(if (read) colors.onErrorContainer.copy(alpha = 0.4f) else colors.onErrorContainer.copy(alpha = activePulse), CircleShape)
                            )
                            Text(
                                story.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (read) colors.onErrorContainer.copy(alpha = 0.6f) else colors.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (story.sparkline.isNotEmpty()) {
                    Text(
                        story.sparkline,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (read) colors.onSurfaceVariant.copy(alpha = 0.5f) else colors.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    TrendingCore.fmtAgo(story.t, nowSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (read) 0.6f else 1f),
                )
            }

            // Title
            Text(
                story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (read) colors.onSurfaceVariant.copy(alpha = 0.75f) else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (story.blurb.isNotEmpty()) {
                Text(
                    story.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (read) 0.6f else 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Stats row
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "▲ ${TrendingCore.fmtCompact(story.score)} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (read) 0.6f else 1f),
                )
                if (story.heat >= 10.0) {
                    Text(
                        " · +${story.heat.toInt()}/hr",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (read) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (story.extra > 0) {
                    Text(
                        " · ${story.extra.toInt()} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = if (read) 0.6f else 1f),
                        modifier = Modifier.clickable { onOpenThread(story) },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onToggleSave(story) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (saved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (saved) colors.primary else colors.onSurfaceVariant.copy(alpha = if (read) 0.5f else 1f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaughtUpCard(count: Int, onMarkCaughtUp: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onMarkCaughtUp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHighest.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(36.dp),
            )
            Text(
                "You're all caught up! ✓",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                "Reviewed $count items across Hacker News and GitHub. Your radar is clear.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Button(
                onClick = onMarkCaughtUp,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mark all as read")
            }
        }
    }
}
