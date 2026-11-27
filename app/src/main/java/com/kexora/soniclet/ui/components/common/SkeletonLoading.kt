package com.kexora.soniclet.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates a synchronized linear gradient shimmer brush for skeleton loading states.
 */
@Composable
fun shimmerBrush(
    targetValue: Float = 1400f
): Brush {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val shimmerColors = listOf(
        baseColor.copy(alpha = 0.05f),
        baseColor.copy(alpha = 0.16f),
        baseColor.copy(alpha = 0.05f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - 400f, y = translateAnimation.value - 400f),
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}

/**
 * Extension modifier applying a shimmer background with specified shape.
 */
fun Modifier.shimmerPlaceholder(
    brush: Brush,
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier = this
    .clip(shape)
    .background(brush)

/**
 * Skeleton placeholder for a single song list row.
 */
@Composable
fun SkeletonSongRow(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .shimmerPlaceholder(brush, RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Title and Artist column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Duration / Actions pill placeholder
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp)
                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
        )
    }
}

/**
 * Skeleton placeholder for a single song grid item.
 */
@Composable
fun SkeletonSongGridItem(
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Album art square placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shimmerPlaceholder(brush, RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(13.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * Skeleton placeholder for the Song screen in list mode.
 */
@Composable
fun SkeletonSongList(
    count: Int = 10,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = 8.dp, bottom = 96.dp)
) {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(count) {
            SkeletonSongRow(brush = brush)
        }
    }
}

/**
 * Skeleton placeholder for the Song screen in grid mode.
 */
@Composable
fun SkeletonSongGrid(
    count: Int = 8,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(count) {
            SkeletonSongGridItem(brush = brush)
        }
    }
}

/**
 * Skeleton placeholder for Playlists screen (Smart Playlists + Custom Playlists).
 */
@Composable
fun SkeletonPlaylistsView(
    isGridMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // Section Header: SMART PLAYLISTS
        item {
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 12.dp, bottom = 10.dp)
                    .width(140.dp)
                    .height(14.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
        }

        // Smart Playlist Rows (Clean flat style)
        items(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shimmerPlaceholder(brush, CircleShape)
                )
            }
        }

        // Section Header: CUSTOM PLAYLISTS
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, bottom = 10.dp)
                    .width(160.dp)
                    .height(14.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
        }

        // Custom playlist items (Clean flat style)
        items(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(11.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shimmerPlaceholder(brush, CircleShape)
                )
            }
        }
    }
}

/**
 * Skeleton placeholder for the Playlist details screen (header + song list).
 */
@Composable
fun SkeletonPlaylistDetailsView(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Header Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big art placeholder
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(20.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Title placeholder
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(18.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Track count placeholder
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    // Play / Shuffle buttons placeholder
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(20.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(20.dp))
                        )
                    }
                }
            }
        }

        // Songs section label
        item {
            Box(
                modifier = Modifier
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                    .width(100.dp)
                    .height(14.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
            )
        }

        // Song rows
        items(6) {
            SkeletonSongRow(brush = brush)
        }
    }
}

/**
 * Skeleton placeholder for Category grids/lists (Albums, Artists, Genres, Moods).
 */
@Composable
fun SkeletonCategoryGrid(
    isGridMode: Boolean = true,
    count: Int = 8,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    if (isGridMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(count) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .shimmerPlaceholder(brush, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        )
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(14.dp)
                                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(10.dp)
                                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(count) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(10.dp)
                                    .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for Folders screen.
 */
@Composable
fun SkeletonFoldersView(
    count: Int = 6,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(count) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shimmerPlaceholder(brush, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(10.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for Lyrics loading in Karaoke / Now Playing view.
 */
@Composable
fun SkeletonLyricsView(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    val widths = listOf(0.6f, 0.85f, 0.45f, 0.9f, 0.7f, 0.8f, 0.5f, 0.75f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        widths.forEachIndexed { index, widthFraction ->
            val isCurrentLine = index == 3
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(if (isCurrentLine) 24.dp else 16.dp)
                    .shimmerPlaceholder(
                        brush,
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

/**
 * Skeleton placeholder for Audio Inspector Bottom Sheet.
 */
@Composable
fun SkeletonAudioSpecsInspector(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Card shimmer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp)
                        .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(28.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(28.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // Tech specs grid shimmer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(12.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for Settings category list view.
 */
@Composable
fun SkeletonSettingsListView(
    count: Int = 8,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shimmerPlaceholder(brush, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .shimmerPlaceholder(brush, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for Audio Folders Settings subpage while scanning or processing.
 */
@Composable
fun SkeletonAudioFoldersSettings(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage insights shimmer card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(16.dp)
                        .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .shimmerPlaceholder(brush, CircleShape)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(10.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(10.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Action rows shimmer
        repeat(5) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shimmerPlaceholder(brush, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(14.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(11.dp)
                                .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for Play Queue screen.
 */
@Composable
fun SkeletonQueueView(
    count: Int = 8,
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(36.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(18.dp))
            )
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(36.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shimmerPlaceholder(brush, CircleShape)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(count) {
                SkeletonSongRow(brush = brush)
            }
        }
    }
}

/**
 * Skeleton placeholder for Song Info & Metadata Editor screen.
 */
@Composable
fun SkeletonSongDetailsScreen(
    modifier: Modifier = Modifier
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmerPlaceholder(brush, RoundedCornerShape(20.dp))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(24.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .shimmerPlaceholder(brush, RoundedCornerShape(24.dp))
            )
        }

        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(16.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(10.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shimmerPlaceholder(brush, RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}
