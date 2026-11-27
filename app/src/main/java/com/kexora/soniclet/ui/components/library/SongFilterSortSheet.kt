package com.kexora.soniclet.ui.components.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.SortOption
import com.kexora.soniclet.library.*
import com.kexora.soniclet.utils.fuzzyMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongFilterSortSheet(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    // -------------------------------------------------------------------------
    // LOCAL DRAFT STATE (Only committed when user taps "Apply")
    // -------------------------------------------------------------------------
    var draftSortOption by remember { mutableStateOf(uiState.sortOption) }
    var draftSortAscending by remember { mutableStateOf(uiState.sortAscending) }
    var draftAttributeFilter by remember { mutableStateOf(uiState.attributeFilter) }
    var draftFormatFilter by remember { mutableStateOf(uiState.formatFilter) }
    var draftDurationCategoryFilter by remember { mutableStateOf(uiState.durationCategoryFilter) }

    // Live matching track count for the draft filters
    val draftMatchCount = remember(
        draftFormatFilter,
        draftDurationCategoryFilter,
        draftAttributeFilter,
        uiState.songList,
        uiState.searchQuery,
        uiState.favoriteSongIds
    ) {
        var list = if (uiState.searchQuery.isBlank()) uiState.songList else {
            uiState.songList.filter { song ->
                song.title.fuzzyMatch(uiState.searchQuery) ||
                        song.artist.fuzzyMatch(uiState.searchQuery) ||
                        song.album.fuzzyMatch(uiState.searchQuery) ||
                        song.folderName.fuzzyMatch(uiState.searchQuery)
            }
        }

        // 1. Format Filter
        if (draftFormatFilter != "ALL") {
            val ext = draftFormatFilter.lowercase()
            list = list.filter { song ->
                song.filePath.endsWith(".$ext", ignoreCase = true) || song.title.endsWith(".$ext", ignoreCase = true)
            }
        }

        // 2. Duration Category Filter
        list = when (draftDurationCategoryFilter) {
            "SHORT" -> list.filter { it.duration in 1..60000 }
            "MEDIUM" -> list.filter { it.duration in 60001..300000 }
            "LONG" -> list.filter { it.duration > 300000 }
            else -> list
        }

        // 3. Special Attributes Filter
        list = when (draftAttributeFilter) {
            "FAVORITES" -> list.filter { uiState.favoriteSongIds.contains(it.id) }
            "RECENT" -> {
                val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000) - (30L * 24 * 3600)
                list.filter { it.dateAdded >= thirtyDaysAgoSec }
            }
            "LOSSLESS" -> list.filter {
                it.filePath.endsWith(".flac", true) ||
                        it.filePath.endsWith(".wav", true) ||
                        it.filePath.endsWith(".alac", true) ||
                        it.filePath.endsWith(".aiff", true)
            }
            else -> list
        }
        list.size
    }

    // Direction descriptive text
    val directionLabel = remember(draftSortOption, draftSortAscending) {
        when (draftSortOption) {
            SortOption.SONG_NAME, SortOption.ARTIST_NAME, SortOption.ALBUM_NAME, SortOption.FOLDER_NAME ->
                if (draftSortAscending) "A → Z" else "Z → A"
            SortOption.TIME_ADDED ->
                if (draftSortAscending) "Oldest First" else "Newest First"
            SortOption.DURATION ->
                if (draftSortAscending) "Shortest" else "Longest"
            SortOption.YEAR ->
                if (draftSortAscending) "Earliest" else "Latest"
            SortOption.FILE_SIZE ->
                if (draftSortAscending) "Smallest" else "Largest"
            else ->
                if (draftSortAscending) "Ascending" else "Descending"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 18.dp, bottom = 20.dp)
        ) {
            // Compact Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Filter & Sort",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$draftMatchCount of ${uiState.songList.size} tracks",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Always-present Reset Icon Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            draftSortOption = SortOption.TIME_ADDED
                            draftSortAscending = false
                            draftAttributeFilter = "ALL"
                            draftFormatFilter = "ALL"
                            draftDurationCategoryFilter = "ALL"
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset filters",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Compact Close Icon Button
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // Scrollable Content with Compact Spacing
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ==========================================
                // SECTION 1: SORT BY + DIRECTION TOGGLE
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlimSectionHeader(
                        icon = Icons.Rounded.Sort,
                        title = "Sort By"
                    )

                    if (draftSortOption != SortOption.RANDOM) {
                        // Integrated Compact Order Direction Pill
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftSortAscending = !draftSortAscending
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (draftSortAscending) Icons.Rounded.North else Icons.Rounded.South,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = directionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                val sortOptionsList = listOf(
                    Triple(SortOption.TIME_ADDED, "Date Added", Icons.Rounded.CalendarToday),
                    Triple(SortOption.SONG_NAME, "Title", Icons.Rounded.SortByAlpha),
                    Triple(SortOption.ARTIST_NAME, "Artist", Icons.Rounded.Person),
                    Triple(SortOption.ALBUM_NAME, "Album", Icons.Rounded.Album),
                    Triple(SortOption.DURATION, "Duration", Icons.Rounded.Schedule),
                    Triple(SortOption.FOLDER_NAME, "Folder", Icons.Rounded.Folder),
                    Triple(SortOption.YEAR, "Year", Icons.Rounded.Event),
                    Triple(SortOption.FILE_SIZE, "Size", Icons.Rounded.SdCard),
                    Triple(SortOption.RANDOM, "Shuffle", Icons.Rounded.Shuffle)
                )

                // Compact Horizontal Scroll for Sort Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sortOptionsList.forEach { (option, label, icon) ->
                        val isSelected = draftSortOption == option
                        SlimFilterChip(
                            label = label,
                            icon = icon,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (draftSortOption == option) {
                                    draftSortAscending = !draftSortAscending
                                } else {
                                    draftSortOption = option
                                    draftSortAscending = true
                                }
                            }
                        )
                    }
                }

                // ==========================================
                // SECTION 2: SPECIAL FILTERS
                // ==========================================
                SlimSectionHeader(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Filter Attributes"
                )

                val attributes = listOf(
                    Triple("ALL", "All Tracks", Icons.Rounded.LibraryMusic),
                    Triple("FAVORITES", "Favorites ❤️", Icons.Rounded.Favorite),
                    Triple("RECENT", "Added in 30 Days", Icons.Rounded.History),
                    Triple("LOSSLESS", "Hi-Res / Lossless", Icons.Rounded.HighQuality)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    attributes.forEach { (code, label, icon) ->
                        val isSelected = draftAttributeFilter == code
                        SlimFilterChip(
                            label = label,
                            icon = icon,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftAttributeFilter = code
                            }
                        )
                    }
                }

                // ==========================================
                // SECTION 3: AUDIO FORMAT
                // ==========================================
                SlimSectionHeader(
                    icon = Icons.Rounded.AudioFile,
                    title = "Audio Format"
                )

                val formats = listOf(
                    "ALL" to "All",
                    "MP3" to "MP3",
                    "FLAC" to "FLAC (Hi-Res)",
                    "M4A" to "M4A / AAC",
                    "WAV" to "WAV",
                    "OGG" to "OGG / OPUS"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    formats.forEach { (code, label) ->
                        val isSelected = draftFormatFilter == code
                        SlimFilterChip(
                            label = label,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftFormatFilter = code
                            }
                        )
                    }
                }

                // ==========================================
                // SECTION 4: TRACK LENGTH
                // ==========================================
                SlimSectionHeader(
                    icon = Icons.Rounded.Timer,
                    title = "Track Length"
                )

                val durationCategories = listOf(
                    "ALL" to "Any Length",
                    "SHORT" to "< 1 min",
                    "MEDIUM" to "1 - 5 min",
                    "LONG" to "> 5 min"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durationCategories.forEach { (code, label) ->
                        val isSelected = draftDurationCategoryFilter == code
                        SlimFilterChip(
                            label = label,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftDurationCategoryFilter = code
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Compact Bottom Apply Button (Commits draft state)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.applySongFiltersAndSort(
                            sortOption = draftSortOption,
                            sortAscending = draftSortAscending,
                            formatFilter = draftFormatFilter,
                            durationCategoryFilter = draftDurationCategoryFilter,
                            attributeFilter = draftAttributeFilter
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Apply ($draftMatchCount tracks)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// COMPACT REUSABLE SUB-COMPONENTS
// -------------------------------------------------------------------------

@Composable
private fun SlimSectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SlimFilterChip(
    label: String,
    isSelected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                       else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
