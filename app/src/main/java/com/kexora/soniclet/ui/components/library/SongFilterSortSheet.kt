package com.kexora.soniclet.ui.components.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.SortOption
import com.kexora.soniclet.utils.fuzzyMatch

private data class SortChipItem(
    val option: SortOption,
    val label: String,
    val icon: ImageVector
)

private data class AttributeChipItem(
    val code: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector = selectedIcon
)

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

    // Local draft state (committed only when tapping Apply)
    var draftSortOption by remember { mutableStateOf(uiState.sortOption) }
    var draftSortAscending by remember { mutableStateOf(uiState.sortAscending) }
    var draftAttributeFilter by remember { mutableStateOf(uiState.attributeFilter) }
    var draftFormatFilter by remember { mutableStateOf(uiState.formatFilter) }
    var draftDurationCategoryFilter by remember { mutableStateOf(uiState.durationCategoryFilter) }

    // Live matching track count matching LibraryViewModel.filterAndSortSongs
    val draftMatchCount = remember(
        draftFormatFilter,
        draftDurationCategoryFilter,
        draftAttributeFilter,
        uiState.songList,
        uiState.searchQuery,
        uiState.favoriteSongIds,
        uiState.playCounts
    ) {
        var list = if (uiState.searchQuery.isBlank()) uiState.songList else {
            uiState.songList.filter { song ->
                song.title.fuzzyMatch(uiState.searchQuery) ||
                    song.artist.fuzzyMatch(uiState.searchQuery) ||
                    song.album.fuzzyMatch(uiState.searchQuery) ||
                    song.folderName.fuzzyMatch(uiState.searchQuery)
            }
        }

        if (draftFormatFilter != "ALL") {
            list = when (draftFormatFilter.uppercase()) {
                "M4A" -> list.filter { song ->
                    song.filePath.endsWith(".m4a", true) || song.filePath.endsWith(".aac", true) ||
                        song.title.endsWith(".m4a", true) || song.title.endsWith(".aac", true)
                }
                "OGG" -> list.filter { song ->
                    song.filePath.endsWith(".ogg", true) || song.filePath.endsWith(".opus", true) ||
                        song.title.endsWith(".ogg", true) || song.title.endsWith(".opus", true)
                }
                else -> {
                    val ext = draftFormatFilter.lowercase()
                    list.filter { song ->
                        song.filePath.endsWith(".$ext", ignoreCase = true) ||
                            song.title.endsWith(".$ext", ignoreCase = true)
                    }
                }
            }
        }

        list = when (draftDurationCategoryFilter) {
            "SHORT" -> list.filter { it.duration in 1..60000 }
            "MEDIUM" -> list.filter { it.duration in 60001..300000 }
            "LONG" -> list.filter { it.duration > 300000 }
            else -> list
        }

        list = when (draftAttributeFilter) {
            "FAVORITES" -> list.filter { uiState.favoriteSongIds.contains(it.id) }
            "RECENT" -> {
                val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000) - (30L * 24 * 3600)
                list.filter { it.dateAdded >= thirtyDaysAgoSec }
            }
            "MOST_PLAYED" -> list.filter { (uiState.playCounts[it.id] ?: 0) > 0 }
            "UNPLAYED" -> list.filter { (uiState.playCounts[it.id] ?: 0) == 0 }
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

    val sortOptionsList = remember {
        listOf(
            SortChipItem(SortOption.TIME_ADDED, "Date Added", Icons.Rounded.CalendarToday),
            SortChipItem(SortOption.SONG_NAME, "Title", Icons.Rounded.SortByAlpha),
            SortChipItem(SortOption.ARTIST_NAME, "Artist", Icons.Rounded.Person),
            SortChipItem(SortOption.ALBUM_NAME, "Album", Icons.Rounded.Album),
            SortChipItem(SortOption.DURATION, "Duration", Icons.Rounded.Schedule),
            SortChipItem(SortOption.FOLDER_NAME, "Folder", Icons.Rounded.Folder),
            SortChipItem(SortOption.YEAR, "Year", Icons.Rounded.Event),
            SortChipItem(SortOption.FILE_SIZE, "Size", Icons.Rounded.SdCard),
            SortChipItem(SortOption.RANDOM, "Shuffle", Icons.Rounded.Shuffle)
        )
    }

    val attributeOptions = remember {
        listOf(
            AttributeChipItem("ALL", "All Tracks", Icons.Rounded.LibraryMusic),
            AttributeChipItem("FAVORITES", "Favorites", Icons.Rounded.Favorite, Icons.Rounded.FavoriteBorder),
            AttributeChipItem("RECENT", "Added in 30 Days", Icons.Rounded.History),
            AttributeChipItem("MOST_PLAYED", "Most Played", Icons.Rounded.TrendingUp),
            AttributeChipItem("UNPLAYED", "Unplayed", Icons.Rounded.NewReleases),
            AttributeChipItem("LOSSLESS", "Hi-Res / Lossless", Icons.Rounded.HighQuality)
        )
    }

    val formatOptions = remember {
        listOf(
            "ALL" to "All",
            "MP3" to "MP3",
            "FLAC" to "FLAC (Hi-Res)",
            "M4A" to "M4A / AAC",
            "WAV" to "WAV",
            "OGG" to "OGG / OPUS"
        )
    }

    val durationOptions = remember {
        listOf(
            "ALL" to "Any Length",
            "SHORT" to "< 1 min",
            "MEDIUM" to "1 - 5 min",
            "LONG" to "> 5 min"
        )
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null,
        shape = sheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = sheetShape
                )
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("song_filter_sort_sheet")
        ) {
            // Centered 36dp x 4dp Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }

            // Compact High-Contrast Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Filter & Sort",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$draftMatchCount of ${uiState.songList.size} tracks",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Uniform 36dp x 36dp Circular Reset Button
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("filter_sheet_reset_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset filters",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Uniform 36dp x 36dp Circular Close Button
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("filter_sheet_close_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            SheetHairlineDivider()

            // Scrollable Content — True Edge-to-Edge LazyRows separated by Hairline Dividers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
            ) {
                // SECTION 1: SORT BY + INLINE DIRECTION TOGGLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterSectionHeader(
                        icon = Icons.Rounded.Sort,
                        title = "Sort By"
                    )

                    if (draftSortOption != SortOption.RANDOM) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftSortAscending = !draftSortAscending
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("filter_sort_direction_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (draftSortAscending) Icons.Rounded.North else Icons.Rounded.South,
                                    contentDescription = "Toggle sort direction",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = directionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortOptionsList, key = { it.option.name }) { item ->
                        val isSelected = draftSortOption == item.option
                        HighContrastFilterPill(
                            label = item.label,
                            icon = item.icon,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (draftSortOption == item.option) {
                                    draftSortAscending = !draftSortAscending
                                } else {
                                    draftSortOption = item.option
                                    draftSortAscending = item.option != SortOption.TIME_ADDED
                                }
                            },
                            testTag = "filter_sort_${item.option.name.lowercase()}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SheetHairlineDivider()

                // SECTION 2: FILTER ATTRIBUTES
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                    FilterSectionHeader(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Filter Attributes"
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attributeOptions, key = { it.code }) { item ->
                        val isSelected = draftAttributeFilter == item.code
                        HighContrastFilterPill(
                            label = item.label,
                            icon = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftAttributeFilter = item.code
                            },
                            testTag = "filter_attr_${item.code.lowercase()}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SheetHairlineDivider()

                // SECTION 3: AUDIO FORMAT
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                    FilterSectionHeader(
                        icon = Icons.Rounded.AudioFile,
                        title = "Audio Format"
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(formatOptions, key = { it.first }) { (code, label) ->
                        val isSelected = draftFormatFilter == code
                        HighContrastFilterPill(
                            label = label,
                            icon = null,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftFormatFilter = code
                            },
                            testTag = "filter_format_${code.lowercase()}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SheetHairlineDivider()

                // SECTION 4: TRACK LENGTH
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
                    FilterSectionHeader(
                        icon = Icons.Rounded.Timer,
                        title = "Track Length"
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(durationOptions, key = { it.first }) { (code, label) ->
                        val isSelected = draftDurationCategoryFilter == code
                        HighContrastFilterPill(
                            label = label,
                            icon = null,
                            isSelected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draftDurationCategoryFilter = code
                            },
                            testTag = "filter_length_${code.lowercase()}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Full-Pill 48dp Apply CTA
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
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(48.dp)
                    .testTag("filter_sheet_apply_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply ($draftMatchCount tracks)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun SheetHairlineDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun FilterSectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HighContrastFilterPill(
    label: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(160),
        label = "filter_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(160),
        label = "filter_pill_fg"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        border = if (isSelected) {
            null
        } else {
            BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        },
        modifier = Modifier
            .height(36.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
