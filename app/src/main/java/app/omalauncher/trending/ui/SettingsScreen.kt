@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.omalauncher.trending.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.omalauncher.trending.data.Settings
import app.omalauncher.trending.data.ThemeMode

private val SectionCardShape = RoundedCornerShape(20.dp)

@Composable
fun SettingsScreen(
    settings: Settings,
    notificationsAllowed: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    onCheckNow: () -> Unit,
    startAtAlerts: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Opened from alerts bell: jump directly to the radar alerts section
    LaunchedEffect(startAtAlerts) {
        if (startAtAlerts) {
            listState.scrollToItem(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 3 else 2)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Appearance Section
            item {
                SettingsCategoryCard(title = "Appearance") {
                    SettingItemContainer(
                        icon = AppIcons.ColorLens,
                        title = "Theme",
                        subtitle = "Select application color theme",
                    ) {
                        val modes = ThemeMode.entries
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            modes.forEachIndexed { index, mode ->
                                val selected = settings.themeMode == mode
                                val label = when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.BLACK -> "OLED"
                                }
                                SegmentedButton(
                                    selected = selected,
                                    onClick = { onChange { it.copy(themeMode = mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = selected) {
                                            val icon = when (mode) {
                                                ThemeMode.SYSTEM -> AppIcons.AutoMode
                                                ThemeMode.LIGHT -> AppIcons.LightMode
                                                ThemeMode.DARK -> AppIcons.DarkMode
                                                ThemeMode.BLACK -> AppIcons.Contrast
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                            )
                                        }
                                    },
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingDivider()
                        SettingSwitchItem(
                            icon = AppIcons.Palette,
                            title = "Match wallpaper colors",
                            subtitle = "Dynamic color palette generated from system theme",
                            checked = settings.dynamicColor,
                            onCheckedChange = { checked -> onChange { it.copy(dynamicColor = checked) } },
                        )
                    }

                    SettingDivider()
                    SettingItemContainer(
                        icon = AppIcons.Palette,
                        title = "Accent color",
                        subtitle = if (settings.dynamicColor) {
                            "Turn off wallpaper colors to pick a custom palette"
                        } else {
                            "Used for charts, highlights, badges and chips"
                        },
                        enabled = !settings.dynamicColor,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .alpha(if (settings.dynamicColor) 0.38f else 1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Palettes.forEachIndexed { index, option ->
                                    val selected = !settings.dynamicColor && settings.palette == index
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(option.seed)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                                },
                                                shape = CircleShape,
                                            )
                                            .clickable(enabled = !settings.dynamicColor) {
                                                onChange { it.copy(palette = index, dynamicColor = false) }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            val checkTint =
                                                if (option.seed.luminance() > 0.5f) Color.Black else Color.White
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = option.name,
                                                tint = checkTint,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (!settings.dynamicColor) {
                                val currentName = Palettes.getOrNull(settings.palette)?.name?.replaceFirstChar { it.uppercase() } ?: ""
                                Text(
                                    text = "Selected: $currentName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // Feed & Reading Section
            item {
                SettingsCategoryCard(title = "Feed & Reading") {
                    SettingSwitchItem(
                        icon = AppIcons.FilterList,
                        title = "Hide likely spam repos",
                        subtitle = "Filter out low-signal and artificial GitHub repositories",
                        checked = settings.hideSpam,
                        onCheckedChange = { checked -> onChange { it.copy(hideSpam = checked) } },
                    )
                    SettingDivider()
                    SettingSwitchItem(
                        icon = AppIcons.BarChart,
                        title = "Full activity charts",
                        subtitle = "Hour-by-hour velocity bars above list. Off shows compact sparkline strip.",
                        checked = settings.chartExpanded,
                        onCheckedChange = { checked -> onChange { it.copy(chartExpanded = checked) } },
                    )
                    SettingDivider()
                    SettingSwitchItem(
                        icon = AppIcons.OpenInNew,
                        title = "Open links in the app",
                        subtitle = "Pages open in Chrome Custom Tabs over Trending so Back returns immediately",
                        checked = settings.openInApp,
                        onCheckedChange = { checked -> onChange { it.copy(openInApp = checked) } },
                    )
                }
            }

            // Radar & Notifications Section
            item {
                SettingsCategoryCard(title = "Radar & Alerts") {
                    SettingSwitchItem(
                        icon = AppIcons.NotificationsActive,
                        title = "Alert me when things trend",
                        subtitle = "Background radar scanning for breakout stories and repositories",
                        checked = settings.notify,
                        onCheckedChange = { enabled ->
                            if (enabled && !notificationsAllowed) {
                                onRequestNotifications()
                            }
                            onChange { it.copy(notify = enabled) }
                        },
                    )

                    AnimatedVisibility(
                        visible = settings.notify && !notificationsAllowed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notifications disabled",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        text = "Grant notification permissions to receive radar alerts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                    )
                                }
                                Button(
                                    onClick = onRequestNotifications,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("Grant", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    SettingDivider()
                    SettingSwitchItem(
                        icon = AppIcons.Bolt,
                        title = "Rising fast",
                        subtitle = "Alert on stories picking up points or stars with steep acceleration",
                        checked = settings.notifyBreaking,
                        enabled = settings.notify,
                        onCheckedChange = { checked -> onChange { it.copy(notifyBreaking = checked) } },
                    )

                    SettingDivider()
                    SettingSwitchItem(
                        icon = AppIcons.Shield,
                        title = "Security alerts",
                        subtitle = "Zero-days, novel vulnerabilities, CVEs and exploits gaining attention",
                        checked = settings.notifySecurity,
                        enabled = settings.notify,
                        onCheckedChange = { checked -> onChange { it.copy(notifySecurity = checked) } },
                    )

                    SettingDivider()
                    SettingItemContainer(
                        icon = AppIcons.Tune,
                        title = "Monitored sources",
                        subtitle = "Select platforms to monitor for trending alerts",
                        enabled = settings.notify,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            FilterChip(
                                selected = settings.notifyHn,
                                enabled = settings.notify,
                                onClick = { onChange { it.copy(notifyHn = !it.notifyHn) } },
                                label = { Text("Hacker News") },
                                leadingIcon = {
                                    if (settings.notifyHn) {
                                        Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                                    }
                                },
                            )
                            FilterChip(
                                selected = settings.notifyGh,
                                enabled = settings.notify,
                                onClick = { onChange { it.copy(notifyGh = !it.notifyGh) } },
                                label = { Text("GitHub") },
                                leadingIcon = {
                                    if (settings.notifyGh) {
                                        Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                                    }
                                },
                            )
                        }
                    }

                    SettingDivider()
                    SettingItemContainer(
                        icon = AppIcons.Tune,
                        title = "Alert sensitivity",
                        subtitle = "Higher sensitivity delivers faster alerts; lower focuses on major breakouts",
                        enabled = settings.notify,
                    ) {
                        val sensitivityLevels = listOf(
                            0 to "Fewer (Quiet)",
                            1 to "Balanced",
                            2 to "More (Aggressive)",
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            sensitivityLevels.forEachIndexed { index, (lvl, label) ->
                                val selected = settings.sensitivity == lvl
                                SegmentedButton(
                                    selected = selected,
                                    enabled = settings.notify,
                                    onClick = { onChange { it.copy(sensitivity = lvl) } },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sensitivityLevels.size),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = selected)
                                    },
                                ) {
                                    Text(label, maxLines = 1)
                                }
                            }
                        }
                    }

                    SettingDivider()
                    SettingItemContainer(
                        icon = AppIcons.Schedule,
                        title = "Background check interval",
                        subtitle = "WorkManager periodically inspects feeds in background",
                        enabled = settings.notify,
                    ) {
                        val intervals = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            for ((mins, label) in intervals) {
                                val selected = settings.intervalMin == mins
                                FilterChip(
                                    selected = selected,
                                    enabled = settings.notify,
                                    onClick = { onChange { it.copy(intervalMin = mins) } },
                                    label = { Text(label) },
                                    leadingIcon = {
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    SettingDivider()
                    SettingItemContainer(
                        icon = AppIcons.Tag,
                        title = "Your keywords & interests",
                        subtitle = "Trigger high-priority alerts whenever stories match these terms",
                        enabled = settings.notify,
                    ) {
                        InterestsEditor(settings = settings, onChange = onChange)
                    }

                    SettingDivider()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .alpha(if (settings.notify) 1f else 0.45f),
                    ) {
                        OutlinedButton(
                            onClick = onTestNotification,
                            enabled = settings.notify,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(AppIcons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test alert")
                        }
                        OutlinedButton(
                            onClick = onCheckNow,
                            enabled = settings.notify,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check now")
                        }
                    }
                }
            }

            // Tech Stack & Dependencies Section
            item {
                SettingsCategoryCard(title = "Tech Stack & Dependencies") {
                    StackDependenciesCard(
                        dependencies = settings.stackDependencies,
                        onUpdate = { deps -> onChange { it.copy(stackDependencies = deps) } },
                    )
                }
            }

            // About Section
            item {
                SettingsCategoryCard(title = "About") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = AppIcons.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Trending",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "v1.5.0",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Personal radar for developers · Hacker News & GitHub signals powered by native Rust core.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun InterestsEditor(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (settings.interests.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (interest in settings.interests) {
                    InputChip(
                        selected = true,
                        onClick = {
                            if (settings.notify) {
                                onChange { s ->
                                    s.copy(interests = s.interests.filterNot { it.equals(interest, ignoreCase = true) })
                                }
                            }
                        },
                        label = { Text(interest) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $interest",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        if (settings.notify) {
                                            onChange { s ->
                                                s.copy(
                                                    interests = s.interests.filterNot {
                                                        it.equals(interest, ignoreCase = true)
                                                    },
                                                )
                                            }
                                        }
                                    },
                            )
                        },
                        enabled = settings.notify,
                    )
                }
            }
        }

        var newInterest by remember { mutableStateOf("") }
        val addInterest = {
            val trimmed = newInterest.trim()
            if (trimmed.isNotEmpty() && settings.interests.none { it.equals(trimmed, ignoreCase = true) }) {
                onChange { s -> s.copy(interests = s.interests + trimmed) }
                newInterest = ""
            }
        }

        OutlinedTextField(
            value = newInterest,
            onValueChange = { newInterest = it },
            placeholder = { Text("Add keyword (e.g. llama, zig, rust)") },
            singleLine = true,
            enabled = settings.notify,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addInterest() }),
            trailingIcon = {
                IconButton(
                    onClick = { addInterest() },
                    enabled = settings.notify && newInterest.trim().isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add keyword")
                }
            },
        )

        val allSuggestions = remember {
            listOf("apk", "game", "android", "rust", "ai", "linux", "privacy", "open source", "self-hosted")
        }
        val remainingSuggestions = allSuggestions.filter { suggestion ->
            settings.interests.none { it.equals(suggestion, ignoreCase = true) }
        }

        if (remainingSuggestions.isNotEmpty()) {
            Text(
                text = "Suggestions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (s in remainingSuggestions) {
                    SuggestionChip(
                        onClick = {
                            if (settings.notify) {
                                onChange { current -> current.copy(interests = current.interests + s) }
                            }
                        },
                        label = { Text(s) },
                        enabled = settings.notify,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SectionCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun SettingItemContainer(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector? = null,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null, // Handled by outer row toggleable
            enabled = enabled,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun StackDependenciesCard(
    dependencies: List<String>,
    onUpdate: (List<String>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Match CVEs, zero-days and breakout security news against your libraries and packages. Paste package.json, Cargo.lock, requirements.txt, or enter package names.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        // Pre-defined quick suggestions
        val presets = listOf("tokio", "react", "fastapi", "postgres", "redis", "nextjs", "sqlite", "linux")
        val unaddedPresets = presets.filter { p -> dependencies.none { it.equals(p, ignoreCase = true) } }
        if (unaddedPresets.isNotEmpty()) {
            Text(
                "Quick Add:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (preset in unaddedPresets.take(6)) {
                    AssistChip(
                        onClick = { onUpdate(dependencies + preset) },
                        label = { Text("+ $preset") },
                    )
                }
            }
        }

        // Active dependencies chips
        if (dependencies.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (dep in dependencies) {
                    InputChip(
                        selected = true,
                        onClick = {
                            onUpdate(dependencies.filterNot { it.equals(dep, ignoreCase = true) })
                        },
                        label = { Text(dep) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $dep",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        onUpdate(dependencies.filterNot { it.equals(dep, ignoreCase = true) })
                                    },
                            )
                        },
                    )
                }
            }
        }

        // Input field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Package name or lockfile") },
                placeholder = { Text("e.g. tokio, react, or paste...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val parsed = app.omalauncher.trending.core.TrendingCore.parseStack(inputText)
                    if (parsed.isNotEmpty()) {
                        val clean = (dependencies + parsed).distinctBy { it.lowercase() }
                        onUpdate(clean)
                        inputText = ""
                    }
                }),
            )
            Button(
                onClick = {
                    val parsed = app.omalauncher.trending.core.TrendingCore.parseStack(inputText)
                    if (parsed.isNotEmpty()) {
                        val clean = (dependencies + parsed).distinctBy { it.lowercase() }
                        onUpdate(clean)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Add")
            }
        }
    }
}
