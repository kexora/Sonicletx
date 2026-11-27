package com.kexora.soniclet.ui.components.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.BuildConfig
import com.kexora.soniclet.R
import com.kexora.soniclet.player.AppTheme
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.player.SleepTimerOption
import com.kexora.soniclet.settings.SettingsSubPage
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.SonicletLogoBadge
import com.kexora.soniclet.utils.fuzzyMatch

@Composable
fun AppSidebarDrawer(
    currentTab: NavigationTab,
    appTheme: AppTheme,
    onAppThemeChange: (AppTheme) -> Unit,
    isGridMode: Boolean,
    onToggleGridMode: () -> Unit,
    onRefresh: () -> Unit,
    isScanning: Boolean = false,
    songCount: Int = 0,
    playlistCount: Int = 0,
    excludedItemCount: Int = 0,
    sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    sleepTimerRemainingMs: Long = 0L,
    isEqEnabled: Boolean = false,
    selectedEqPresetName: String = "Flat / Normal",
    isReverbEnabled: Boolean = false,
    isGaplessPlaybackEnabled: Boolean = true,
    isCrossfadeEnabled: Boolean = false,
    crossfadeDurationSeconds: Int = 3,
    backgroundMode: String = "DYNAMIC",
    isKariModeEnabled: Boolean = true,
    isShakeEnabled: Boolean = false,
    isFlipToPauseEnabled: Boolean = false,
    isPocketModeEnabled: Boolean = false,
    isOneFingerSwipeBackEnabled: Boolean = true,
    isVolumeEdgeSwipeEnabled: Boolean = false,
    onSetSleepTimer: (SleepTimerOption) -> Unit = {},
    onSetCustomSleepTimer: (Int) -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onOpenExcludedItems: (() -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    onNavigateTab: (NavigationTab) -> Unit = {},
    onOpenSettingsSubPage: (SettingsSubPage) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "drawer_refresh_anim")
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drawer_refresh_rot"
    )

    val scrollState = rememberScrollState()
    val drawerSurface = MaterialTheme.colorScheme.surface
    val drawerBorderColor = MaterialTheme.colorScheme.outlineVariant

    // Flush edge-anchored side sheet: 0dp radius on screen-attached edge, 20dp radius ONLY on inner exposed edge
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.84f)
            .widthIn(max = 312.dp)
            .testTag("app_sidebar_drawer"),
        color = drawerSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerSurface)
                .drawBehind {
                    drawLine(
                        color = drawerBorderColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 0.6.dp.toPx()
                    )
                }
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // ==========================================
            // HEADER: SONICLET HEADPHONE EMBLEM + ACTIONS
            // ==========================================
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(160)) + expandVertically())
                        .togetherWith(fadeOut(animationSpec = tween(120)) + shrinkVertically())
                },
                label = "drawer_header_search_anim"
            ) { expanded ->
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearchExpanded = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search tools & settings...",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateTab(NavigationTab.HOME)
                                    onCloseDrawer()
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        ) {
                            SonicletLogoBadge(
                                badgeSize = 40.dp,
                                logoSize = 24.dp,
                                showBorder = true
                            )

                            Column {
                                Text(
                                    text = "Soniclet",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.5.sp,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Studio Edition",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.3.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isSearchExpanded = true
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("drawer_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search sidebar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val (themeIcon, themeLabel) = when (appTheme) {
                                AppTheme.LIGHT -> Icons.Rounded.LightMode to "Light"
                                AppTheme.DARK -> Icons.Rounded.DarkMode to "Dark"
                                AppTheme.SYSTEM -> Icons.Rounded.SettingsBrightness to "Auto"
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val nextTheme = when (appTheme) {
                                        AppTheme.LIGHT -> AppTheme.DARK
                                        AppTheme.DARK -> AppTheme.SYSTEM
                                        AppTheme.SYSTEM -> AppTheme.LIGHT
                                    }
                                    onAppThemeChange(nextTheme)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("drawer_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Theme: $themeLabel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onCloseDrawer,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("drawer_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close drawer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // QUICK-ACTION CIRCULAR PILLS
            // Queue • Sleep Timer • Drive Mode • Rescan • Equalizer
            // ==========================================
            if (searchQuery.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionPill(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Queue",
                        tint = MaterialTheme.colorScheme.primary,
                        isActive = currentTab == NavigationTab.QUEUE,
                        tag = "drawer_quick_queue",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateTab(NavigationTab.QUEUE)
                            onCloseDrawer()
                        }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.Timer,
                        label = "Timer",
                        tint = Color(0xFF8B5CF6),
                        isActive = sleepTimerOption != SleepTimerOption.OFF,
                        showActiveDot = sleepTimerOption != SleepTimerOption.OFF,
                        tag = "drawer_quick_timer",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSleepTimer()
                        }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.DirectionsCar,
                        label = "Drive",
                        tint = Color(0xFFF59E0B),
                        isActive = currentTab == NavigationTab.DRIVE_MODE,
                        tag = "drawer_quick_drive",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateTab(NavigationTab.DRIVE_MODE)
                            onCloseDrawer()
                        }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.Refresh,
                        label = "Rescan",
                        tint = Color(0xFF10B981),
                        isActive = isScanning,
                        iconRotation = if (isScanning) refreshRotation else 0f,
                        tag = "drawer_quick_rescan",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefresh()
                            onCloseDrawer()
                        }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.Equalizer,
                        label = "EQ",
                        tint = Color(0xFFEC4899),
                        isActive = isEqEnabled,
                        tag = "drawer_quick_eq",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettingsSubPage(SettingsSubPage.EQUALIZER)
                            onCloseDrawer()
                        }
                    )
                }
            }

            // ==========================================
            // CLEAN CATEGORIZED PILL MENU
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val q = searchQuery.trim().lowercase()

                data class DrawerItem(
                    val title: String,
                    val subtitle: String,
                    val icon: ImageVector,
                    val iconTint: Color,
                    val groupTag: String,
                    val badge: String? = null,
                    val badgeColor: Color? = null,
                    val isSelected: Boolean = false,
                    val testTag: String? = null,
                    val onClick: () -> Unit
                )

                val libraryAndTools = listOf(
                    DrawerItem(
                        title = "Home Screen Widgets",
                        subtitle = "Vinyl, Pro & Compact widgets",
                        icon = Icons.Rounded.Widgets,
                        iconTint = Color(0xFFA855F7),
                        groupTag = "LIBRARY & TOOLS",
                        testTag = "drawer_item_widgets",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.WIDGETS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Audio Extractor",
                        subtitle = "Video to M4A converter",
                        icon = Icons.Rounded.MovieFilter,
                        iconTint = Color(0xFFF43F5E),
                        groupTag = "LIBRARY & TOOLS",
                        testTag = "drawer_item_audio_extractor",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.VIDEO_TO_M4A)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Folders & Library",
                        subtitle = "Manage included folders",
                        icon = Icons.Rounded.Folder,
                        iconTint = Color(0xFFEAB308),
                        groupTag = "LIBRARY & TOOLS",
                        testTag = "drawer_item_folders",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.AUDIO_FOLDERS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Duplicate Cleaner",
                        subtitle = "Find & remove duplicate audio",
                        icon = Icons.Rounded.ContentCopy,
                        iconTint = Color(0xFF06B6D4),
                        groupTag = "LIBRARY & TOOLS",
                        isSelected = currentTab == NavigationTab.DUPLICATES,
                        testTag = "drawer_item_duplicates",
                        onClick = {
                            onNavigateTab(NavigationTab.DUPLICATES)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Hidden Items",
                        subtitle = "Excluded folders & tracks",
                        icon = Icons.Rounded.VisibilityOff,
                        iconTint = Color(0xFFEC4899),
                        groupTag = "LIBRARY & TOOLS",
                        badge = if (excludedItemCount > 0) "$excludedItemCount" else null,
                        badgeColor = Color(0xFFEC4899),
                        isSelected = currentTab == NavigationTab.HIDDEN_ITEMS,
                        testTag = "drawer_item_hidden",
                        onClick = {
                            onOpenExcludedItems?.invoke() ?: onNavigateTab(NavigationTab.HIDDEN_ITEMS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Soniclet Wrapped",
                        subtitle = "Listening stats & history",
                        icon = Icons.Rounded.BarChart,
                        iconTint = Color(0xFF6366F1),
                        groupTag = "LIBRARY & TOOLS",
                        isSelected = currentTab == NavigationTab.STATS,
                        testTag = "drawer_item_wrapped",
                        onClick = {
                            onNavigateTab(NavigationTab.STATS)
                            onCloseDrawer()
                        }
                    )
                )

                val radioTitle = stringResource(R.string.radio_drawer_title)
                val radioSubtitle = stringResource(R.string.radio_drawer_subtitle)
                val radioBadge = stringResource(R.string.radio_drawer_badge)

                val audioAndVisuals = listOf(
                    DrawerItem(
                        title = "Equalizer & Sound FX",
                        subtitle = if (isEqEnabled) "$selectedEqPresetName${if (isReverbEnabled) " • Reverb" else ""}" else "Off • 10-band studio EQ",
                        icon = Icons.Rounded.Equalizer,
                        iconTint = Color(0xFFEC4899),
                        groupTag = "AUDIO & VISUALS",
                        badge = if (isEqEnabled) "ON" else null,
                        badgeColor = Color(0xFF10B981),
                        testTag = "drawer_item_equalizer",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.EQUALIZER)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Playback Engine",
                        subtitle = buildString {
                            if (isGaplessPlaybackEnabled) append("Gapless")
                            if (isCrossfadeEnabled) {
                                if (isNotEmpty()) append(" • ")
                                append("Crossfade ${crossfadeDurationSeconds}s")
                            }
                            if (isEmpty()) append("Gapless, crossfade & safety")
                        },
                        icon = Icons.Rounded.PlayCircle,
                        iconTint = Color(0xFF3B82F6),
                        groupTag = "AUDIO & VISUALS",
                        testTag = "drawer_item_playback",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.PLAYBACK_SETTINGS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Appearance & Themes",
                        subtitle = when {
                            isKariModeEnabled -> "Dynamic palette active"
                            backgroundMode == "SOLID" -> "Solid canvas • ${appTheme.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            backgroundMode == "GRADIENT" -> "Gradient • ${appTheme.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            backgroundMode == "CUSTOM" -> "Custom wallpaper canvas"
                            else -> "${appTheme.name.lowercase().replaceFirstChar { it.uppercase() }} • AMOLED"
                        },
                        icon = Icons.Rounded.Palette,
                        iconTint = Color(0xFFF97316),
                        groupTag = "AUDIO & VISUALS",
                        testTag = "drawer_item_appearance",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.APPEARANCE)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Motion & Sensors",
                        subtitle = buildString {
                            val active = mutableListOf<String>()
                            if (isShakeEnabled) active.add("Shake")
                            if (isFlipToPauseEnabled) active.add("Flip")
                            if (isPocketModeEnabled) active.add("Pocket")
                            if (active.isNotEmpty()) append("Active: ${active.joinToString(", ")}")
                            else append("Shake, flip & pocket controls")
                        },
                        icon = Icons.Rounded.ScreenRotation,
                        iconTint = Color(0xFF14B8A6),
                        groupTag = "AUDIO & VISUALS",
                        testTag = "drawer_item_motion",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.MOTION_CONTROLS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "Air & Touch Gestures",
                        subtitle = "Swipe back & volume edges",
                        icon = Icons.Rounded.Swipe,
                        iconTint = Color(0xFF0EA5E9),
                        groupTag = "AUDIO & VISUALS",
                        testTag = "drawer_item_gestures",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.GESTURES_GUIDE)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = radioTitle,
                        subtitle = radioSubtitle,
                        icon = Icons.Rounded.Radio,
                        iconTint = Color(0xFF0EA5E9),
                        groupTag = "AUDIO & VISUALS",
                        badge = radioBadge,
                        badgeColor = Color(0xFF0EA5E9),
                        isSelected = currentTab == NavigationTab.RADIO,
                        testTag = "drawer_item_radio",
                        onClick = {
                            onNavigateTab(NavigationTab.RADIO)
                            onCloseDrawer()
                        }
                    )
                )

                val appAndSupport = listOf(
                    DrawerItem(
                        title = "App Permissions",
                        subtitle = "Audio & notification access",
                        icon = Icons.Rounded.Security,
                        iconTint = Color(0xFF10B981),
                        groupTag = "APP & SUPPORT",
                        testTag = "drawer_item_permissions",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.PERMISSIONS)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "User Guide",
                        subtitle = "Gestures, shortcuts & features",
                        icon = Icons.Rounded.MenuBook,
                        iconTint = Color(0xFF64748B),
                        groupTag = "APP & SUPPORT",
                        testTag = "drawer_item_user_guide",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.MANUAL_ALL)
                            onCloseDrawer()
                        }
                    ),
                    DrawerItem(
                        title = "About Soniclet",
                        subtitle = "Pure Offline Hi-Res Music",
                        icon = Icons.Rounded.Info,
                        iconTint = Color(0xFF6366F1),
                        groupTag = "APP & SUPPORT",
                        badge = "v${BuildConfig.VERSION_NAME}",
                        badgeColor = Color(0xFF10B981),
                        testTag = "drawer_item_about",
                        onClick = {
                            onOpenSettingsSubPage(SettingsSubPage.ABOUT)
                            onCloseDrawer()
                        }
                    )
                )

                if (q.isNotEmpty()) {
                    val isSecretMuhTriggered = q == "muh"
                    val allItems = libraryAndTools + audioAndVisuals + appAndSupport
                    val filtered = allItems.filter {
                        it.title.fuzzyMatch(q) ||
                            it.subtitle.fuzzyMatch(q) ||
                            it.groupTag.fuzzyMatch(q)
                    }

                    if (isSecretMuhTriggered) {
                        PillDrawerItemRow(
                            title = "✨ Secret MUH Studio Beat",
                            subtitle = "Dedicated with love 🐮💖",
                            icon = Icons.Rounded.Favorite,
                            iconTint = Color(0xFFEC4899),
                            badge = "SECRET",
                            badgeColor = Color(0xFFEC4899),
                            onClick = {
                                onOpenSettingsSubPage(SettingsSubPage.ABOUT)
                                onCloseDrawer()
                            }
                        )
                    }

                    if (filtered.isEmpty() && !isSecretMuhTriggered) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        filtered.forEach { item ->
                            PillDrawerItemRow(
                                title = item.title,
                                subtitle = item.subtitle,
                                icon = item.icon,
                                iconTint = item.iconTint,
                                badge = item.badge,
                                badgeColor = item.badgeColor,
                                isSelected = item.isSelected,
                                tag = item.testTag,
                                onClick = item.onClick
                            )
                        }
                    }
                } else {
                    SidebarSectionHeader(title = "LIBRARY & TOOLS")
                    libraryAndTools.forEach { item ->
                        PillDrawerItemRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            iconTint = item.iconTint,
                            badge = item.badge,
                            badgeColor = item.badgeColor,
                            isSelected = item.isSelected,
                            tag = item.testTag,
                            onClick = item.onClick
                        )
                    }

                    SidebarSectionHeader(title = "AUDIO & VISUALS")
                    audioAndVisuals.forEach { item ->
                        PillDrawerItemRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            iconTint = item.iconTint,
                            badge = item.badge,
                            badgeColor = item.badgeColor,
                            isSelected = item.isSelected,
                            tag = item.testTag,
                            onClick = item.onClick
                        )
                    }

                    SidebarSectionHeader(title = "APP & SUPPORT")
                    appAndSupport.forEach { item ->
                        PillDrawerItemRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            iconTint = item.iconTint,
                            badge = item.badge,
                            badgeColor = item.badgeColor,
                            isSelected = item.isSelected,
                            tag = item.testTag,
                            onClick = item.onClick
                        )
                    }
                }

                MadeInIndiaFooter(
                    topPadding = 16.dp,
                    bottomPadding = 8.dp,
                    watermarkAlpha = 0.5f,
                    fontSize = 9.5.sp,
                    logoHeight = 12.dp,
                    logoWidth = 64.dp,
                    heartSize = 9.dp
                )
                Spacer(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    isActive: Boolean = false,
    showActiveDot: Boolean = false,
    iconRotation: Float = 0f,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isActive) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 0.7.dp,
                    color = if (isActive) tint.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .size(44.dp)
                    .testTag(tag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isActive) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(20.dp)
                            .then(if (iconRotation != 0f) Modifier.rotate(iconRotation) else Modifier)
                    )
                }
            }

            if (showActiveDot) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, end = 2.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isActive) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.0.sp
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun PillDrawerItemRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badge: String? = null,
    badgeColor: Color? = null,
    isSelected: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit
) {
    val actualBadgeColor = badgeColor ?: iconTint
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(containerColor)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (badge != null) {
            Surface(
                shape = CircleShape,
                color = actualBadgeColor.copy(alpha = 0.16f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = actualBadgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                    maxLines = 1
                )
            }
        }
    }
}
