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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.SmartTitleCleaner
import com.kexora.soniclet.utils.StoryShareCardGenerator
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
    val context = LocalContext.current
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
    var isInlineLyricsVisible by remember(song.id) { mutableStateOf(false) }
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

    LaunchedEffect(song.id, isInlineLyricsVisible) {
        if (isInlineLyricsVisible && currentLyrics.isEmpty()) {
            viewModel.loadLyricsForSong(song)
        }
    }

    val dismissSheet: () -> Unit = { onDismiss() }

    BackHandler {
        when {
            isQueueDrawerExpanded -> isQueueDrawerExpanded = false
            isInlineLyricsVisible -> isInlineLyricsVisible = false
            else -> dismissSheet()
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

    // Tactile pause scale-down (0.94f) & spring back to 1.0f when playing
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
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
        targetValue = if (isPlaying) 1.06f else 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "aura_glow_scale"
    )

    val auraGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = if (isPlaying) 0.52f else 0.18f,
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

    val cleanedTitle = remember(song.title, song.filePath) {
        SmartTitleCleaner.cleanTitle(song.title, song.filePath)
    }
    val playingFromLabel = remember(uiState.queueSourceTitle, song.folderName) {
        uiState.queueSourceTitle.ifBlank { song.folderName.ifBlank { "Music Library" } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, sheetOffsetY.value.roundToInt()) }
            .background(Color(0xFF070B09))
            .pointerInput(Unit) {}
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
            val isVeryCompactHeight = totalHeight < 580.dp

            val maxPortraitArtHeight = when {
                isVeryCompactHeight -> (totalHeight * 0.34f).coerceIn(160.dp, 220.dp)
                isCompactHeight -> (totalHeight * 0.40f).coerceIn(220.dp, 290.dp)
                else -> minOf(totalWidth - 32.dp, totalHeight * 0.46f, 410.dp)
            }

            val playButtonSize = when {
                isVeryCompactHeight -> 58.dp
                else -> 64.dp
            }

            val playIconSize = when {
                isVeryCompactHeight -> 30.dp
                else -> 34.dp
            }

            val skipButtonSize = when {
                isVeryCompactHeight -> 48.dp
                else -> 48.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
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
                                    onClearCustomLyrics = { viewModel.clearImportedLyrics(song) },
                                    isPlaying = isPlaying
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
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            FadingMarqueeText(
                                text = cleanedTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 21.sp,
                                    lineHeight = 27.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true)
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
                                    text = SmartTitleCleaner.cleanArtist(song.artist).ifBlank { "Unknown Artist" },
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
                                        modifier = Modifier
                                            .size(26.dp)
                                            .scale(favoriteScale)
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
                                skipButtonSize = 48.dp,
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
                    // FLAGSHIP EDGE-TO-EDGE FULL-SCREEN PORTRAIT NOW PLAYING
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isVeryCompactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount < -22f) {
                                        change.consume()
                                        queueDrawerTab = 0
                                        isQueueDrawerExpanded = true
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Centered swipe-down drag pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(sheetDragModifier)
                                .padding(top = 6.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                        }

                        // Clean Edge-to-Edge Top Navigation Bar (Collapse • PLAYING FROM • More Options Pill)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(sheetDragModifier)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = dismissSheet,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("now_playing_collapse_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse player",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PLAYING FROM",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.1.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.62f),
                                    maxLines = 1
                                )
                                Text(
                                    text = playingFromLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { showMoreOptionsSheet = true }
                                    .testTag("now_playing_more_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.14f)),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Flexible Hero Artwork / Inline Karaoke Lyrics Zone
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (!isVeryCompactHeight) Modifier.weight(1f) else Modifier.padding(vertical = 8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            DhunPortraitArtCard(
                                song = song,
                                uiState = uiState,
                                viewModel = viewModel,
                                currentPosition = currentPosition,
                                maxArtHeight = maxPortraitArtHeight,
                                artScale = artScale,
                                auraGlowScale = auraGlowScale,
                                auraGlowAlpha = auraGlowAlpha,
                                animatedDominant = animatedDominant,
                                animatedVibrant = animatedVibrant,
                                isInlineLyricsVisible = isInlineLyricsVisible,
                                onToggleInlineLyrics = { isInlineLyricsVisible = !isInlineLyricsVisible },
                                onSwipeNext = { viewModel.playNext() },
                                onSwipePrevious = { viewModel.playPrevious() },
                                sheetDragModifier = sheetDragModifier
                            )
                        }

                        // Track Title & Clickable Artist Pills (edge-to-edge 16dp horizontal padding)
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FadingMarqueeText(
                                text = cleanedTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isCompactHeight) 20.sp else 22.sp,
                                    lineHeight = if (isCompactHeight) 26.sp else 28.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true)
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                delayMillis = 2000L,
                                enableFade = true,
                                edgeGradientWidth = 14.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val parsedArtists = remember(song.artist, song.title) {
                                ArtistParserUtil.splitArtists(song.artist, song.title)
                            }

                            if (parsedArtists.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(parsedArtists, key = { it }) { artistName ->
                                        Surface(
                                            onClick = {
                                                val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                                val matchingSongs = allS.filter { s ->
                                                    ArtistParserUtil.splitArtists(s.artist, s.title)
                                                        .any { it.equals(artistName, ignoreCase = true) }
                                                }
                                                viewModel.openCategoryAndClosePlayer(artistName, "ARTIST", matchingSongs)
                                            },
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.14f)),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = artistName,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val artistName = song.artist
                                            val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                            val matchingSongs = allS.filter { s ->
                                                ArtistParserUtil.splitArtists(s.artist, s.title)
                                                    .any { it.equals(artistName, ignoreCase = true) }
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
                                        text = SmartTitleCleaner.cleanArtist(song.artist).ifBlank { "Unknown Artist" },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = if (isCompactHeight) 14.sp else 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            platformStyle = PlatformTextStyle(includeFontPadding = true)
                                        ),
                                        color = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 14.dp))

                        // Balanced Non-Clipped 3-Pill Row (Like • Hi-Res/Codec • Up Next)
                        DhunActionPillRow(
                            song = song,
                            uiState = uiState,
                            viewModel = viewModel,
                            isFavorite = isFavorite,
                            animatedVibrant = animatedVibrant,
                            onOpenAudioInspector = { showAudioInspectorSheet = true },
                            onOpenQueue = {
                                queueDrawerTab = 0
                                isQueueDrawerExpanded = true
                            }
                        )

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                        // Edge-to-Edge Seekbar
                        SleekYouTubeMusicSeekBar(
                            currentPosition = currentPosition,
                            totalDuration = totalDuration,
                            accentColor = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary,
                            onSeek = { viewModel.seekTo(it) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 14.dp))

                        // Edge-to-Edge Main Transport Controls
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
                            arrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 12.dp))

                        // Edge-to-Edge Minimal Borderless Bottom Utility Bar
                        DhunBottomUtilityBar(
                            uiState = uiState,
                            isInlineLyricsVisible = isInlineLyricsVisible,
                            onToggleLyrics = { isInlineLyricsVisible = !isInlineLyricsVisible },
                            onOpenAudioFx = { showQuickAudioFxSheet = true },
                            onOpenSleepTimer = { showSleepTimerDialog = true },
                            onOpenDriveMode = { viewModel.selectTab(NavigationTab.DRIVE_MODE) },
                            onShare = {
                                val currentLine = if (activeLyricIndex in currentLyrics.indices) {
                                    currentLyrics[activeLyricIndex].text
                                } else null
                                StoryShareCardGenerator.generateAndShare(context, song, currentLine)
                            }
                        )
                    }
                }
            }
        }

        // Partial-Height Slide-Up Up Next & Lyrics Sheet (max 74% height, opaque frosted surface)
        PlayerQueueLyricsSheet(
            visible = isQueueDrawerExpanded,
            selectedTab = queueDrawerTab,
            onSelectTab = { queueDrawerTab = it },
            song = song,
            uiState = uiState,
            viewModel = viewModel,
            currentPosition = currentPosition,
            animatedDominant = animatedDominant,
            animatedVibrant = animatedVibrant,
            onDismissSheet = { isQueueDrawerExpanded = false },
            onDismissPlayer = dismissSheet,
            modifier = Modifier.zIndex(10f)
        )
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
