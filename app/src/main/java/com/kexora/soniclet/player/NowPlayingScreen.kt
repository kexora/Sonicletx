package com.kexora.soniclet.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.equalizer.*
import com.kexora.soniclet.lyrics.*
import com.kexora.soniclet.tools.*
import com.kexora.soniclet.ui.components.common.FadingMarqueeText
import com.kexora.soniclet.ui.components.common.cleanSongTitle
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.utils.ArtistParserUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Main Now Playing screen layout (NowPlayingScreen / Dhun).
 * Delegates album art & aura visuals to PlayerArtwork.kt and controls/sheets to PlayerControls.kt.
 */
@Composable
fun NowPlayingScreen(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    Dhun(song = song, uiState = uiState, viewModel = viewModel, onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dhun(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val currentPositionState = viewModel.currentPosition.collectAsState()
    val isPlaying = uiState.isPlaying
    val currentPosition = currentPositionState.value
    val totalDuration = uiState.totalDuration
    val repeatMode = uiState.repeatMode.name
    val isFavorite = uiState.favoriteSongIds.contains(song.id)
    val currentLyrics = uiState.currentLyrics
    val activeLyricIndex = uiState.activeLyricIndex
    val isKariModeEnabled = uiState.isKariModeEnabled

    val kariDominantColor = uiState.kariDominantColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val kariVibrantColor = uiState.kariVibrantColor?.let { Color(it) } ?: MaterialTheme.colorScheme.secondary
    val animatedDominant by animateColorAsState(kariDominantColor, label = "dominant_color", animationSpec = tween(400))
    val animatedVibrant by animateColorAsState(kariVibrantColor, label = "vibrant_color", animationSpec = tween(400))

    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isQueueDrawerExpanded by remember { mutableStateOf(false) }
    var queueDrawerTab by remember { mutableIntStateOf(0) } // 0: Up Next, 1: Lyrics
    var showLyricsInLandscape by remember { mutableStateOf(false) }
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    var showAudioInspectorSheet by remember { mutableStateOf(false) }
    var showMoodDialog by remember { mutableStateOf(false) }
    var showQuickAudioFxSheet by remember { mutableStateOf(false) }
    var showMultipleArtistsSheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val sheetOffsetY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        sheetOffsetY.snapTo(0f)
    }

    val dismissSheet: () -> Unit = { onDismiss() }

    BackHandler {
        if (isQueueDrawerExpanded) {
            isQueueDrawerExpanded = false
        } else {
            dismissSheet()
        }
    }

    val sheetDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { },
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0 || sheetOffsetY.value > 0) {
                    change.consume()
                    coroutineScope.launch {
                        val next = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                        sheetOffsetY.snapTo(next)
                    }
                }
            },
            onDragEnd = {
                if (sheetOffsetY.value > 80.dp.toPx()) {
                    coroutineScope.launch {
                        sheetOffsetY.animateTo(
                            targetValue = 600.dp.toPx(),
                            animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                        )
                        onDismiss()
                    }
                } else {
                    coroutineScope.launch {
                        sheetOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                coroutineScope.launch { sheetOffsetY.animateTo(0f) }
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.025f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artwork_play_scale"
    )

    LaunchedEffect(song.id) {
        viewModel.inspectAudioSpecsForSong(song)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val bgScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPlaying) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bg_scale"
    )

    val auraGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (isPlaying) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "aura_glow_scale"
    )

    val auraGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.55f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "aura_glow_alpha"
    )

    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "favorite_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, sheetOffsetY.value.roundToInt()) }
            .background(MaterialTheme.colorScheme.background)
    ) {
        DhunArtBackdrop(
            song = song,
            animatedDominant = animatedDominant,
            animatedVibrant = animatedVibrant,
            bgScale = bgScale
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            val totalWidth = maxWidth
            val totalHeight = maxHeight

            val isWideOrTablet = totalWidth >= 580.dp && (totalWidth / totalHeight) > 0.72f
            val isLandscapeLayout = isLandscape || isWideOrTablet || (totalWidth > totalHeight * 1.15f)
            val isCompactHeight = totalHeight < 680.dp
            val isVeryCompactHeight = totalHeight < 560.dp
            val isTallScreen = totalHeight >= 820.dp && (totalHeight / totalWidth) >= 2.05f

            val targetArtSize = when {
                isVeryCompactHeight -> (totalHeight * 0.28f).coerceIn(130.dp, 190.dp)
                isCompactHeight -> (totalHeight * 0.33f).coerceIn(170.dp, 240.dp)
                totalHeight < 760.dp -> (totalHeight * 0.38f).coerceIn(210.dp, 290.dp)
                isTallScreen -> (minOf(totalWidth * 0.82f, 350.dp)).coerceIn(270.dp, 350.dp)
                else -> (minOf(totalWidth * 0.80f, totalHeight * 0.40f, 330.dp)).coerceIn(220.dp, 330.dp)
            }

            val playButtonSize = when {
                isVeryCompactHeight -> 54.dp
                isCompactHeight -> 62.dp
                else -> 72.dp
            }

            val playIconSize = when {
                isVeryCompactHeight -> 28.dp
                isCompactHeight -> 34.dp
                else -> 38.dp
            }

            val skipButtonSize = when {
                isVeryCompactHeight -> 38.dp
                isCompactHeight -> 44.dp
                else -> 48.dp
            }

            val titleFontSize = when {
                isVeryCompactHeight -> 19.sp
                isCompactHeight -> 22.sp
                else -> 25.sp
            }

            val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(sheetDragModifier)
                        .padding(vertical = 4.dp)
                )

                if (isLandscapeLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showLyricsInLandscape) {
                                LaunchedEffect(song.id, showLyricsInLandscape) {
                                    if (currentLyrics.isEmpty()) {
                                        viewModel.loadLyricsForSong(song)
                                    }
                                }
                                TimeSyncedKaraokeView(
                                    lyrics = currentLyrics,
                                    activeLyricIndex = activeLyricIndex,
                                    currentPosition = currentPosition,
                                    syncOffsetMs = uiState.lyricsSyncOffsetMs,
                                    isKaraokeModeEnabled = uiState.isKaraokeModeEnabled,
                                    lyricsSourceType = uiState.lyricsSourceType,
                                    isLyricsLoading = uiState.isLyricsLoading,
                                    isKariModeEnabled = isKariModeEnabled,
                                    dominantColor = animatedDominant,
                                    vibrantColor = animatedVibrant,
                                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                                    onAdjustOffset = { delta -> viewModel.adjustLyricsOffset(delta) },
                                    onResetOffset = { viewModel.resetLyricsOffset() },
                                    onToggleKaraokeMode = { viewModel.toggleKaraokeMode() },
                                    onImportLyrics = { uri -> viewModel.importLyricsFromUri(song, uri) },
                                    onClearCustomLyrics = { viewModel.clearImportedLyrics(song) }
                                )
                            } else {
                                DhunLandscapeArtCard(
                                    song = song,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    totalWidth = totalWidth,
                                    totalHeight = totalHeight,
                                    artScale = artScale,
                                    auraGlowScale = auraGlowScale,
                                    auraGlowAlpha = auraGlowAlpha,
                                    animatedDominant = animatedDominant,
                                    animatedVibrant = animatedVibrant,
                                    onOpenInspector = { showAudioInspectorSheet = true }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.25f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FadingMarqueeText(
                                text = cleanSongTitle(song),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                delayMillis = 2000L,
                                enableFade = true,
                                edgeGradientWidth = 12.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFavorite) Color(0xFFFF3366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp).scale(favoriteScale)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            SleekYouTubeMusicSeekBar(
                                currentPosition = currentPosition,
                                totalDuration = totalDuration,
                                accentColor = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary,
                                onSeek = { viewModel.seekTo(it) }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            DhunPlaybackButtonsRow(
                                uiState = uiState,
                                viewModel = viewModel,
                                isPlaying = isPlaying,
                                repeatMode = repeatMode,
                                isKariModeEnabled = isKariModeEnabled,
                                animatedDominant = animatedDominant,
                                animatedVibrant = animatedVibrant,
                                playButtonSize = 62.dp,
                                playIconSize = 34.dp,
                                skipButtonSize = 44.dp,
                                isCompactHeight = true,
                                arrangement = Arrangement.SpaceEvenly
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            DhunLandscapeBottomShortcuts(
                                uiState = uiState,
                                viewModel = viewModel,
                                animatedVibrant = animatedVibrant,
                                sheetDragModifier = sheetDragModifier,
                                onOpenQueue = {
                                    queueDrawerTab = 0
                                    isQueueDrawerExpanded = true
                                },
                                onOpenSleepTimer = { showSleepTimerDialog = true },
                                onOpenAudioFx = { showQuickAudioFxSheet = true },
                                onOpenSpeedDialog = { showSpeedDialog = true }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 2.dp)
                            .offset(y = (-10).dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount < -16f) {
                                        change.consume()
                                        queueDrawerTab = 0
                                        isQueueDrawerExpanded = true
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        DhunPortraitArtCard(
                            song = song,
                            targetArtSize = targetArtSize,
                            isCompactHeight = isCompactHeight,
                            artScale = artScale,
                            auraGlowScale = auraGlowScale,
                            auraGlowAlpha = auraGlowAlpha,
                            animatedDominant = animatedDominant,
                            animatedVibrant = animatedVibrant,
                            sheetDragModifier = sheetDragModifier
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            FadingMarqueeText(
                                text = cleanSongTitle(song),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = titleFontSize
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                delayMillis = 2000L,
                                enableFade = true,
                                edgeGradientWidth = 14.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            val parsedArtists = remember(song.artist, song.title) {
                                ArtistParserUtil.splitArtists(song.artist, song.title)
                            }

                            if (parsedArtists.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(parsedArtists, key = { it }) { artistName ->
                                        Surface(
                                            onClick = {
                                                val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                                val matchingSongs = allS.filter { s ->
                                                    ArtistParserUtil.splitArtists(s.artist, s.title).any { it.equals(artistName, ignoreCase = true) }
                                                }
                                                viewModel.openCategoryAndClosePlayer(artistName, "ARTIST", matchingSongs)
                                            },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = artistName,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val artistName = song.artist
                                            val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                            val matchingSongs = allS.filter { s ->
                                                ArtistParserUtil.splitArtists(s.artist, s.title).any { it.equals(artistName, ignoreCase = true) }
                                            }
                                            viewModel.openCategoryAndClosePlayer(artistName, "ARTIST", matchingSongs)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = if (isCompactHeight) 14.sp else 16.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (song.album.isNotBlank() && song.album != "<unknown>") {
                                        Text(
                                            text = " • ${song.album}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = if (isCompactHeight) 13.sp else 15.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        DhunQuickControlsRow(
                            song = song,
                            uiState = uiState,
                            viewModel = viewModel,
                            isFavorite = isFavorite,
                            animatedVibrant = animatedVibrant,
                            onOpenTrackInfo = { showTrackInfoDialog = true },
                            onOpenQueueTab = { tabIndex ->
                                queueDrawerTab = tabIndex
                                isQueueDrawerExpanded = true
                            },
                            onOpenAudioFx = { showQuickAudioFxSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onOpenSpeedDialog = { showSpeedDialog = true },
                            onOpenMoodDialog = { showMoodDialog = true },
                            onOpenMoreOptions = { showMoreOptionsSheet = true }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        SleekYouTubeMusicSeekBar(
                            currentPosition = currentPosition,
                            totalDuration = totalDuration,
                            accentColor = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary,
                            onSeek = { viewModel.seekTo(it) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        DhunPlaybackButtonsRow(
                            uiState = uiState,
                            viewModel = viewModel,
                            isPlaying = isPlaying,
                            repeatMode = repeatMode,
                            isKariModeEnabled = isKariModeEnabled,
                            animatedDominant = animatedDominant,
                            animatedVibrant = animatedVibrant,
                            playButtonSize = playButtonSize,
                            playIconSize = playIconSize,
                            skipButtonSize = skipButtonSize,
                            isCompactHeight = isCompactHeight,
                            arrangement = Arrangement.SpaceBetween
                        )

                        Spacer(modifier = Modifier.height(8.dp + navBarPadding))
                    }
                }
            }
        }

        // Up Next & Lyrics Sliding Drawer
        AnimatedVisibility(
            visible = isQueueDrawerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(150)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            var sheetDragOffsetY by remember { mutableFloatStateOf(0f) }
            val animatedSheetOffsetY by animateFloatAsState(
                targetValue = sheetDragOffsetY,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                label = "queueSheetOffset"
            )

            LaunchedEffect(isQueueDrawerExpanded) {
                if (isQueueDrawerExpanded) sheetDragOffsetY = 0f
            }

            BackHandler(enabled = isQueueDrawerExpanded) {
                isQueueDrawerExpanded = false
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .offset { IntOffset(0, animatedSheetOffsetY.toInt().coerceAtLeast(0)) },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (sheetDragOffsetY > 40f) {
                                            isQueueDrawerExpanded = false
                                            sheetDragOffsetY = 0f
                                        } else {
                                            sheetDragOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = { sheetDragOffsetY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (dragAmount > 0 || sheetDragOffsetY > 0) {
                                            change.consume()
                                            sheetDragOffsetY = (sheetDragOffsetY + dragAmount).coerceAtLeast(0f)
                                        }
                                    }
                                )
                            }
                            .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheetDragOffsetY = 0f
                                    isQueueDrawerExpanded = false
                                }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    sheetDragOffsetY = 0f
                                    isQueueDrawerExpanded = false
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse Queue",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val tabs = listOf("UP NEXT", "LYRICS")
                                    tabs.forEachIndexed { index, title ->
                                        val isSelected = queueDrawerTab == index
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.25f)
                                                    else Color.Transparent
                                                )
                                                .clickable { queueDrawerTab = index }
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 12.5.sp,
                                                    letterSpacing = 0.5.sp
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleShuffle() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle Queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.08f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (queueDrawerTab == 0) {
                            PlayQueue(
                                activeQueue = uiState.activeQueue,
                                queueSourceTitle = uiState.queueSourceTitle,
                                currentSong = song,
                                isPlaying = isPlaying,
                                onPlaySong = { viewModel.playSong(it) },
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                                onMoveQueueItemToNext = { viewModel.moveQueueItemToNext(it) },
                                onMoveQueueItemToBottom = { viewModel.moveQueueItemToBottom(it) },
                                onReorderQueueItem = { from, to -> viewModel.reorderQueueItem(from, to) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onClearQueue = { viewModel.clearQueue() },
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                favoriteSongIds = uiState.favoriteSongIds,
                                actuallyPlayedSongIds = uiState.actuallyPlayedSongIds,
                                playbackPositions = uiState.playbackPositions,
                                onNavigateToArtist = { artistName ->
                                    val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                    val matchingSongs = allS.filter { s ->
                                        ArtistParserUtil.splitArtists(s.artist, s.title).any { it.equals(artistName, ignoreCase = true) }
                                    }
                                    viewModel.openCategory(artistName, "ARTIST", matchingSongs)
                                    dismissSheet()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LaunchedEffect(song.id) {
                                if (currentLyrics.isEmpty()) {
                                    viewModel.loadLyricsForSong(song)
                                }
                            }
                            TimeSyncedKaraokeView(
                                lyrics = currentLyrics,
                                activeLyricIndex = activeLyricIndex,
                                currentPosition = currentPosition,
                                syncOffsetMs = uiState.lyricsSyncOffsetMs,
                                isKaraokeModeEnabled = uiState.isKaraokeModeEnabled,
                                lyricsSourceType = uiState.lyricsSourceType,
                                isLyricsLoading = uiState.isLyricsLoading,
                                isKariModeEnabled = isKariModeEnabled,
                                dominantColor = animatedDominant,
                                vibrantColor = animatedVibrant,
                                onSeekTo = { pos -> viewModel.seekTo(pos) },
                                onAdjustOffset = { delta -> viewModel.adjustLyricsOffset(delta) },
                                onResetOffset = { viewModel.resetLyricsOffset() },
                                onToggleKaraokeMode = { viewModel.toggleKaraokeMode() },
                                onImportLyrics = { uri -> viewModel.importLyricsFromUri(song, uri) },
                                onClearCustomLyrics = { viewModel.clearImportedLyrics(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    DhunDialogsAndSheets(
        song = song,
        uiState = uiState,
        viewModel = viewModel,
        totalDuration = totalDuration,
        showMoreOptionsSheet = showMoreOptionsSheet,
        onShowMoreOptionsChange = { showMoreOptionsSheet = it },
        showTrackInfoDialog = showTrackInfoDialog,
        onShowTrackInfoChange = { showTrackInfoDialog = it },
        showSleepTimerDialog = showSleepTimerDialog,
        onShowSleepTimerChange = { showSleepTimerDialog = it },
        showSpeedDialog = showSpeedDialog,
        onShowSpeedDialogChange = { showSpeedDialog = it },
        showMoodDialog = showMoodDialog,
        onShowMoodDialogChange = { showMoodDialog = it },
        showAudioInspectorSheet = showAudioInspectorSheet,
        onShowAudioInspectorChange = { showAudioInspectorSheet = it },
        showQuickAudioFxSheet = showQuickAudioFxSheet,
        onShowQuickAudioFxChange = { showQuickAudioFxSheet = it },
        showMultipleArtistsSheet = showMultipleArtistsSheet,
        onShowMultipleArtistsChange = { showMultipleArtistsSheet = it },
        onDismissPlayer = dismissSheet
    )
}
