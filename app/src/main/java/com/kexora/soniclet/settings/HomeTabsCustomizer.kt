package com.kexora.soniclet.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import java.util.Locale

private data class TabPreset(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val tabs: List<NavigationTab>
)

@Composable
fun HomeTabsCustomizerSubPage(
    viewModel: MusicViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val allAvailableTabs = viewModel.defaultMainTabs
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Local state for tabs ordering and active set
    var currentOrderedTabs by remember {
        val active = uiState.activeMainTabs.ifEmpty { viewModel.defaultMainTabs }
        val remaining = allAvailableTabs.filter { it !in active }
        mutableStateOf(active + remaining)
    }

    var activeTabs by remember {
        mutableStateOf(uiState.activeMainTabs.ifEmpty { viewModel.defaultMainTabs }.toSet())
    }

    // Drag-and-drop state management
    var draggingTab by remember { mutableStateOf<NavigationTab?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    // Sync from ViewModel when not actively dragging
    LaunchedEffect(uiState.activeMainTabs) {
        if (draggingTab == null) {
            val active = uiState.activeMainTabs.ifEmpty { viewModel.defaultMainTabs }
            val existingRemaining = currentOrderedTabs.filter { it !in active }
            val missing = allAvailableTabs.filter { it !in active && it !in existingRemaining }
            currentOrderedTabs = active + existingRemaining + missing
            activeTabs = active.toSet()
        }
    }

    val activeList = remember(currentOrderedTabs, activeTabs) {
        currentOrderedTabs.filter { it in activeTabs }
    }
    val hiddenList = remember(currentOrderedTabs, activeTabs) {
        currentOrderedTabs.filter { it !in activeTabs }
    }

    // Direct Move Up / Down with immediate commit
    fun moveActiveTab(tab: NavigationTab, direction: Int) {
        val currentIdx = activeList.indexOf(tab)
        val targetIdx = currentIdx + direction
        if (targetIdx in activeList.indices) {
            val targetTab = activeList[targetIdx]
            val fullIdx = currentOrderedTabs.indexOf(tab)
            val targetFullIdx = currentOrderedTabs.indexOf(targetTab)
            val mutable = currentOrderedTabs.toMutableList()
            mutable[fullIdx] = targetTab
            mutable[targetFullIdx] = tab
            currentOrderedTabs = mutable
            val finalized = mutable.filter { it in activeTabs }
            viewModel.updateActiveMainTabs(finalized)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Toggle active state
    fun toggleTabActive(tab: NavigationTab) {
        val newActive = if (tab in activeTabs) {
            if (activeTabs.size <= 1) return // Preserve at least 1 tab
            activeTabs - tab
        } else {
            activeTabs + tab
        }
        activeTabs = newActive
        val finalized = currentOrderedTabs.filter { it in newActive }
        viewModel.updateActiveMainTabs(finalized)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Drag gesture callbacks
    val onDragStartForTab: (NavigationTab) -> Unit = { tab ->
        draggingTab = tab
        dragAccumulatedY = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onDragDeltaForTab: (NavigationTab, Float) -> Unit = { tab, deltaY ->
        dragAccumulatedY += deltaY
        val slotHeight = if (itemHeightPx > 0f) itemHeightPx else with(density) { 60.dp.toPx() }
        val threshold = slotHeight * 0.65f

        val currentActiveIdx = activeList.indexOf(tab)
        if (currentActiveIdx != -1) {
            // Move Down when dragging past 65% of item height
            if (dragAccumulatedY > threshold && currentActiveIdx < activeList.size - 1) {
                val nextTab = activeList[currentActiveIdx + 1]
                val fullIdx = currentOrderedTabs.indexOf(tab)
                val nextIdx = currentOrderedTabs.indexOf(nextTab)
                if (fullIdx != -1 && nextIdx != -1) {
                    val list = currentOrderedTabs.toMutableList()
                    list[fullIdx] = nextTab
                    list[nextIdx] = tab
                    currentOrderedTabs = list
                    dragAccumulatedY = 0f
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            // Move Up when dragging past 65% of item height upwards
            else if (dragAccumulatedY < -threshold && currentActiveIdx > 0) {
                val prevTab = activeList[currentActiveIdx - 1]
                val fullIdx = currentOrderedTabs.indexOf(tab)
                val prevIdx = currentOrderedTabs.indexOf(prevTab)
                if (fullIdx != -1 && prevIdx != -1) {
                    val list = currentOrderedTabs.toMutableList()
                    list[fullIdx] = prevTab
                    list[prevIdx] = tab
                    currentOrderedTabs = list
                    dragAccumulatedY = 0f
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    val onDragEndForTab: () -> Unit = {
        draggingTab = null
        dragAccumulatedY = 0f
        // Final commit to ViewModel only after drag completes
        val finalized = currentOrderedTabs.filter { it in activeTabs }
        if (finalized.isNotEmpty()) {
            viewModel.updateActiveMainTabs(finalized)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val presets = remember {
        listOf(
            TabPreset(
                id = "default",
                name = "Default (5)",
                icon = Icons.Rounded.Tune,
                tabs = listOf(NavigationTab.HOME, NavigationTab.PLAYER, NavigationTab.RADIO, NavigationTab.PLAYLISTS, NavigationTab.FOLDERS)
            ),
            TabPreset(
                id = "minimal",
                name = "Minimal (2)",
                icon = Icons.Rounded.Speed,
                tabs = listOf(NavigationTab.HOME, NavigationTab.PLAYER)
            ),
            TabPreset(
                id = "library",
                name = "Library (5)",
                icon = Icons.Rounded.LibraryMusic,
                tabs = listOf(NavigationTab.HOME, NavigationTab.PLAYER, NavigationTab.ALBUMS, NavigationTab.ARTISTS, NavigationTab.FOLDERS)
            ),
            TabPreset(
                id = "vibe",
                name = "Vibe & Mood (4)",
                icon = Icons.Rounded.AutoAwesome,
                tabs = listOf(NavigationTab.HOME, NavigationTab.PLAYLISTS, NavigationTab.MOODS, NavigationTab.GENRES)
            ),
            TabPreset(
                id = "studio",
                name = "Studio / DJ (5)",
                icon = Icons.Rounded.Equalizer,
                tabs = listOf(NavigationTab.HOME, NavigationTab.PLAYER, NavigationTab.FOLDERS, NavigationTab.QUEUE, NavigationTab.STATS)
            ),
            TabPreset(
                id = "all",
                name = "All Tabs (11)",
                icon = Icons.Rounded.CheckCircle,
                tabs = allAvailableTabs
            )
        )
    }

    // Scrollable root column allowing fluid scrolling through the entire screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Live Top Bar Preview
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE TAB BAR PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${activeList.size} visible",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontally scrollable preview pill tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeList.forEach { tab ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = getTabIcon(tab),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = getTabTitle(tab),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Quick Presets
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "QUICK PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap to apply layout",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = activeTabs == preset.tabs.toSet() && activeList == preset.tabs
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                val newOrder = preset.tabs + allAvailableTabs.filter { it !in preset.tabs }
                                currentOrderedTabs = newOrder
                                activeTabs = preset.tabs.toSet()
                                viewModel.updateActiveMainTabs(preset.tabs)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Active Tabs (with dedicated drag handle + 1-tap reorder arrows)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ACTIVE TABS (${activeList.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Drag handle or tap arrows to reorder",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeList.forEachIndexed { activeIdx, tab ->
                    key(tab) {
                        val isDragging = draggingTab == tab
                        val translationY = if (isDragging) dragAccumulatedY else 0f

                        ActiveTabRowItem(
                            tab = tab,
                            indexNumber = activeIdx + 1,
                            canMoveUp = activeIdx > 0,
                            canMoveDown = activeIdx < activeList.size - 1,
                            canDisable = activeTabs.size > 1,
                            isDragging = isDragging,
                            translationY = translationY,
                            onMoveUp = { moveActiveTab(tab, -1) },
                            onMoveDown = { moveActiveTab(tab, 1) },
                            onMeasuredHeight = { h ->
                                if (itemHeightPx == 0f) itemHeightPx = h
                            },
                            onDragStart = { onDragStartForTab(tab) },
                            onDragDelta = { deltaY -> onDragDeltaForTab(tab, deltaY) },
                            onDragEnd = onDragEndForTab,
                            onToggle = { toggleTabActive(tab) }
                        )
                    }
                }
            }
        }

        // Section 4: Hidden / Available Tabs
        if (hiddenList.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HIDDEN TABS (${hiddenList.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap switch to enable",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hiddenList.forEach { tab ->
                        key(tab) {
                            HiddenTabRowItem(
                                tab = tab,
                                onToggle = { toggleTabActive(tab) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom space so nothing is clipped by MiniPlayer or Navigation Bar
        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 24.dp))
    }
}

@Composable
private fun ActiveTabRowItem(
    tab: NavigationTab,
    indexNumber: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDisable: Boolean,
    isDragging: Boolean,
    translationY: Float,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMeasuredHeight: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: () -> Unit
) {
    val density = LocalDensity.current

    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "scale_anim"
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "elevation_anim"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = if (isDragging) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                this.translationY = translationY
                scaleX = animatedScale
                scaleY = animatedScale
                shadowElevation = animatedElevation.toPx()
            }
            .onGloballyPositioned { coordinates ->
                onMeasuredHeight(coordinates.size.height.toFloat() + with(density) { 6.dp.toPx() })
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dedicated Drag Handle (Only this handle intercepts vertical drag, so the rest of the row scrolls freely!)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .pointerInput(tab) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Hold and slide to reorder",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Numbering badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d", indexNumber),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tab Icon
            Icon(
                imageVector = getTabIcon(tab),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Subtitle (Touchable for page scroll without blocking)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getTabTitle(tab),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = getTabSubtitle(tab),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Quick Move Arrows (1-tap reordering with easy touch targets)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Switch toggle
            Switch(
                checked = true,
                onCheckedChange = { onToggle() },
                enabled = canDisable
            )
        }
    }
}

@Composable
private fun HiddenTabRowItem(
    tab: NavigationTab,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getTabIcon(tab),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getTabTitle(tab),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Text(
                    text = getTabSubtitle(tab),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    maxLines = 1
                )
            }

            Switch(
                checked = false,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

private fun getTabTitle(tab: NavigationTab): String {
    return when (tab) {
        NavigationTab.HOME -> "Home"
        NavigationTab.PLAYER -> "Songs"
        NavigationTab.RADIO -> "Radio"
        NavigationTab.PLAYLISTS -> "Playlists"
        NavigationTab.FOLDERS -> "Folders"
        NavigationTab.ARTISTS -> "Artists"
        NavigationTab.ALBUMS -> "Albums"
        NavigationTab.GENRES -> "Genres"
        NavigationTab.MOODS -> "Moods"
        NavigationTab.QUEUE -> "Queue"
        NavigationTab.STATS -> "Wrapped"
        else -> tab.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun getTabSubtitle(tab: NavigationTab): String {
    return when (tab) {
        NavigationTab.HOME -> "Dashboard, mix & quick access"
        NavigationTab.PLAYER -> "All audio tracks & sorting"
        NavigationTab.RADIO -> "Live stations & offline saved tracks"
        NavigationTab.PLAYLISTS -> "Smart & custom mixes"
        NavigationTab.FOLDERS -> "Storage file directory browser"
        NavigationTab.ARTISTS -> "Solo & collaboration artists"
        NavigationTab.ALBUMS -> "Album collections & discs"
        NavigationTab.GENRES -> "Music style categories"
        NavigationTab.MOODS -> "Chill, energetic & emotional"
        NavigationTab.QUEUE -> "Now playing order"
        NavigationTab.STATS -> "Listening habits & analytics"
        else -> "Custom category"
    }
}

// Exactly matches the real icons used in the actual library screens and tabs
private fun getTabIcon(tab: NavigationTab): ImageVector {
    return when (tab) {
        NavigationTab.HOME -> Icons.Rounded.Home
        NavigationTab.PLAYER -> Icons.Rounded.MusicNote
        NavigationTab.RADIO -> Icons.Rounded.Radio
        NavigationTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
        NavigationTab.FOLDERS -> Icons.Rounded.Folder
        NavigationTab.ARTISTS -> Icons.Rounded.Person
        NavigationTab.ALBUMS -> Icons.Rounded.Album
        NavigationTab.GENRES -> Icons.Rounded.Category
        NavigationTab.MOODS -> Icons.Rounded.Mood
        NavigationTab.QUEUE -> Icons.AutoMirrored.Rounded.PlaylistPlay
        NavigationTab.STATS -> Icons.Default.BarChart
        else -> Icons.Rounded.Tab
    }
}
