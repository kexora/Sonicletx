package com.kexora.soniclet.ui.components.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Playlist

/**
 * Universal Modern Floating Pill Bar for Multiple Selection.
 * Slides up smoothly from the bottom with rich haptics & fluid spring physics.
 * Features fixed left controls (dismiss, count, select all) and a scrollable row of labelled action icons.
 */
@Composable
fun MultiSelectFloatingPillBar(
    selectedCount: Int,
    isAllSelected: Boolean = false,
    onClearSelection: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onPlaySelected: () -> Unit,
    onInsertNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareSelected: (() -> Unit)? = null,
    onRemoveFromContext: (() -> Unit)? = null,
    removeContextLabel: String? = null,
    removeContextIcon: ImageVector? = null,
    onExcludeSelected: () -> Unit,
    onDeleteSelected: (() -> Unit)? = null,
    showPlay: Boolean = true,
    showRemoveFromContext: Boolean = true,
    showInsertNext: Boolean = true,
    showAddToQueue: Boolean = true,
    showAddToPlaylist: Boolean = true,
    showFavorite: Boolean = true,
    showShare: Boolean = true,
    showExclude: Boolean = true,
    showDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            ),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        tonalElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fixed Left Control Section (Dismiss, Count Badge, Select All)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Close / Deselect Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearSelection()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear Selection",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 2. Count Badge with smooth number animation (balanced 36dp capsule)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .widthIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = selectedCount,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                                } else {
                                    (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                                }
                            },
                            label = "selected_count_anim"
                        ) { count ->
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // 3. Select All / Deselect All Toggle Button
                    if (onSelectAll != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectAll()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (isAllSelected) "Deselect All" else "Select All",
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Vertical Divider separating fixed controls from scrollable actions
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )

                // Scrollable Actions Row (Contextually filtered)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Play Now
                    if (showPlay) {
                        PillBarActionItem(
                            icon = Icons.Rounded.PlayArrow,
                            label = "Play",
                            contentDescription = "Play Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onPlaySelected
                        )
                    }

                    // Context-specific Primary Action (e.g. Remove from Queue or Remove from Playlist right next to Play)
                    if (showRemoveFromContext && onRemoveFromContext != null) {
                        PillBarActionItem(
                            icon = removeContextIcon ?: Icons.Rounded.RemoveCircleOutline,
                            label = removeContextLabel ?: "Remove",
                            contentDescription = removeContextLabel ?: "Remove",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.90f),
                            onClick = onRemoveFromContext
                        )
                    }

                    // 2. Play Next (Insert Next)
                    if (showInsertNext) {
                        PillBarActionItem(
                            icon = Icons.Rounded.PlaylistPlay,
                            label = "Next",
                            contentDescription = "Play Next",
                            onClick = onInsertNext
                        )
                    }

                    // 3. Add to Queue
                    if (showAddToQueue) {
                        PillBarActionItem(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            label = "Queue",
                            contentDescription = "Add to Queue",
                            onClick = onAddToQueue
                        )
                    }

                    // 4. Add to Playlist
                    if (showAddToPlaylist) {
                        PillBarActionItem(
                            icon = Icons.Rounded.PlaylistAdd,
                            label = "Playlist",
                            contentDescription = "Add to Playlist",
                            onClick = onAddToPlaylist
                        )
                    }

                    // 5. Favorite / Heart All
                    if (showFavorite) {
                        PillBarActionItem(
                            icon = Icons.Rounded.Favorite,
                            label = "Fav",
                            contentDescription = "Favorite Selected",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onToggleFavorite
                        )
                    }

                    // 6. Share Audio Files
                    if (showShare && onShareSelected != null) {
                        PillBarActionItem(
                            icon = Icons.Rounded.Share,
                            label = "Share",
                            contentDescription = "Share Selected Tracks",
                            onClick = onShareSelected
                        )
                    }

                    // 7. Hide / Exclude
                    if (showExclude) {
                        PillBarActionItem(
                            icon = Icons.Rounded.VisibilityOff,
                            label = "Hide",
                            contentDescription = "Hide Selected",
                            onClick = onExcludeSelected
                        )
                    }

                    // 8. Delete Physical File
                    if (showDelete && onDeleteSelected != null) {
                        PillBarActionItem(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "Delete",
                            contentDescription = "Delete Selected Tracks",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onDeleteSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillBarActionItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minWidth = 46.dp, minHeight = 44.dp)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp
                ),
                color = tint.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Modern Bottom Sheet for Adding Selected Songs to a Playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    playlists: List<Playlist>,
    selectedCount: Int,
    onPlaylistSelected: (String) -> Unit,
    onCreateNewPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
    validSongIds: Set<Long>? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$selectedCount tracks selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists found. Create one above!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onPlaylistSelected(playlist.id)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val validCount = if (validSongIds != null) {
                                        playlist.songIds.count { id -> validSongIds.contains(id) }
                                    } else {
                                        playlist.songIds.size
                                    }
                                    Text(
                                        text = "$validCount songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreateNewPlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                            newPlaylistName = ""
                            onDismiss()
                        }
                    }
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
