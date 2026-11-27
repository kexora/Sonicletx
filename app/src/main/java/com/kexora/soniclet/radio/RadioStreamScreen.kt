package com.kexora.soniclet.radio

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalWifiConnectedNoInternet4
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kexora.soniclet.R
import com.kexora.soniclet.player.MusicViewModel
import java.util.Locale
import kotlin.math.abs

private fun formatRadioFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.getDefault(), "%d KB", bytes / 1024L)
        else -> "$bytes B"
    }
}

private fun formatRadioDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.getDefault(), "%d:%02d", min, sec)
}

/**
 * FrameLayout container whose touch dispatch can be toggled cleanly when the Radio host stays
 * persistently attached behind other tabs, while cleanly recognizing horizontal tab-switch swipes
 * (Songs <-> Radio <-> Playlists) without breaking vertical WebView scrolling or Play button taps.
 */
private class TouchableRadioContainer(context: Context) : FrameLayout(context) {
    @Volatile
    var allowTouches: Boolean = true

    var onSwipeToPreviousTab: (() -> Unit)? = null
    var onSwipeToNextTab: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var swipeConsumed = false
    private val swipeThresholdPx = 68f * context.resources.displayMetrics.density

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!allowTouches || ev == null) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swipeConsumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swipeConsumed && ev.pointerCount == 1) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > swipeThresholdPx && abs(dx) > abs(dy) * 1.55f) {
                        val child = getChildAt(0)
                        val canWebViewScrollHoriz = child?.canScrollHorizontally(if (dx > 0f) -1 else 1) == true
                        if (!canWebViewScrollHoriz) {
                            swipeConsumed = true
                            val cancelEvent = MotionEvent.obtain(ev).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            if (dx > 0f) {
                                onSwipeToPreviousTab?.invoke()
                            } else {
                                onSwipeToNextTab?.invoke()
                            }
                            return true
                        }
                    }
                } else if (swipeConsumed) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeConsumed) {
                    swipeConsumed = false
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (!allowTouches) return false
        return super.onInterceptTouchEvent(ev)
    }
}

private fun Modifier.radioHorizontalTabSwipe(
    enabled: Boolean,
    onSwipeToPreviousTab: () -> Unit,
    onSwipeToNextTab: () -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(onSwipeToPreviousTab, onSwipeToNextTab) {
        var accumulatedDx = 0f
        var triggered = false
        val thresholdPx = 68.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = {
                accumulatedDx = 0f
                triggered = false
            },
            onHorizontalDrag = { _, dragAmount ->
                accumulatedDx += dragAmount
                if (!triggered && abs(accumulatedDx) > thresholdPx) {
                    triggered = true
                    if (accumulatedDx > 0f) {
                        onSwipeToPreviousTab()
                    } else {
                        onSwipeToNextTab()
                    }
                }
            },
            onDragEnd = {
                accumulatedDx = 0f
                triggered = false
            },
            onDragCancel = {
                accumulatedDx = 0f
                triggered = false
            }
        )
    }
}

/**
 * Seamless, native-feeling Soniclet Radio tab integrated directly alongside Home, Songs, Playlists, and Folders.
 *
 * - Defaults to Disconnected (`isRadioConnected = false`) on app startup so zero background WebView
 *   or stream audio loads until the user explicitly switches on **Connect Radio**.
 * - Turning Connect Radio OFF immediately stops audio, releases audio focus, and unloads/destroys the WebView.
 * - Supports smooth horizontal tab swiping (`Songs` <-> `Radio` <-> `Playlists`) in both disconnected
 *   and connected states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioStreamScreen(
    viewModel: MusicViewModel,
    isActiveTab: Boolean = true,
    onSwipeToPreviousTab: () -> Unit = {},
    onSwipeToNextTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cacheManager = remember(context) { RadioOfflineCacheManager.getInstance(context) }
    val radioHost = remember(context) { RadioBackgroundHost.getInstance(context) }

    val hasCachedPage by cacheManager.hasCachedPage.collectAsStateWithLifecycle()
    val cachedTracks by cacheManager.cachedTracks.collectAsStateWithLifecycle()
    val hostState by radioHost.state.collectAsStateWithLifecycle()

    val surfaceBgColor = MaterialTheme.colorScheme.background
    val webViewBgArgb = surfaceBgColor.toArgb()

    var showSavedTracksSheet by remember { mutableStateOf(false) }

    // Keep persistent Radio host alive when Activity backgrounds or foregrounds (never pause explicit audio)
    DisposableEffect(lifecycleOwner, radioHost) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> radioHost.onHostForegrounded()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> radioHost.onHostBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isActiveTab && hostState.isRadioConnected && hostState.canGoBack) {
        radioHost.goBack()
    }

    val shouldBlockFirstOfflineLoad = hostState.isRadioConnected &&
        !hostState.isOnline &&
        !hasCachedPage &&
        !cacheManager.hasSavedSnapshot() &&
        !hostState.hasRenderedContent

    val retryLoading: () -> Unit = {
        radioHost.reload(forceOnline = false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceBgColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("radio_stream_screen")
    ) {
        // Dedicated Native Top Control Bar with Connect Radio Pill Toggle + Play/Pause & Stop Controls
        if (isActiveTab) {
            RadioStreamControlBar(
                hostState = hostState,
                onToggleConnected = { connected -> radioHost.setRadioConnected(connected) },
                onTogglePlayPause = { radioHost.onRadioTogglePlayPause() },
                onStopRadio = { radioHost.stopBackgroundPlayback() },
                modifier = Modifier.radioHorizontalTabSwipe(
                    enabled = isActiveTab,
                    onSwipeToPreviousTab = onSwipeToPreviousTab,
                    onSwipeToNextTab = onSwipeToNextTab
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!hostState.isRadioConnected) {
                if (isActiveTab) {
                    RadioDisconnectedCard(
                        savedTracksCount = cachedTracks.size,
                        onConnect = { radioHost.setRadioConnected(true) },
                        onOpenSavedTracks = { showSavedTracksSheet = true },
                        modifier = Modifier.radioHorizontalTabSwipe(
                            enabled = isActiveTab,
                            onSwipeToPreviousTab = onSwipeToPreviousTab,
                            onSwipeToNextTab = onSwipeToNextTab
                        )
                    )
                }
            } else {
                if (!shouldBlockFirstOfflineLoad) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("radio_stream_content"),
                        factory = { ctx ->
                            TouchableRadioContainer(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                allowTouches = isActiveTab
                                this.onSwipeToPreviousTab = onSwipeToPreviousTab
                                this.onSwipeToNextTab = onSwipeToNextTab
                                alpha = if (isActiveTab) 1f else 0f
                                setBackgroundColor(webViewBgArgb)
                                radioHost.attachToContainer(this, webViewBgArgb)
                            }
                        },
                        update = { container ->
                            container.allowTouches = isActiveTab
                            container.onSwipeToPreviousTab = onSwipeToPreviousTab
                            container.onSwipeToNextTab = onSwipeToNextTab
                            container.alpha = if (isActiveTab) 1f else 0f
                            container.setBackgroundColor(webViewBgArgb)
                            radioHost.updateBackgroundColor(webViewBgArgb)
                            if (container.childCount == 0 && !shouldBlockFirstOfflineLoad && hostState.isRadioConnected) {
                                radioHost.attachToContainer(container, webViewBgArgb)
                            }
                        },
                        onRelease = { container ->
                            radioHost.detachFromContainer(container)
                        }
                    )
                }

                // Subtle Native Offline / Reconnect / Saved Tracks Pill Bar
                val showOfflineBanner = isActiveTab && !hostState.isOnline && hostState.hasRenderedContent
                val showBackOnlineBanner = isActiveTab && hostState.isOnline && hostState.isViewingOfflineCache && hostState.hasRenderedContent
                val showSavedTracksChip = cachedTracks.isNotEmpty() && (!hostState.isOnline || hostState.isViewingOfflineCache)

        androidx.compose.animation.AnimatedVisibility(
            visible = showOfflineBanner || showBackOnlineBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    if (showBackOnlineBanner) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    }
                ),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            if (showBackOnlineBanner) {
                                radioHost.reload(forceOnline = true)
                            } else if (cachedTracks.isNotEmpty()) {
                                showSavedTracksSheet = true
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (showBackOnlineBanner) Icons.Rounded.Wifi else Icons.Rounded.OfflinePin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(
                            if (showBackOnlineBanner) R.string.radio_back_online_banner
                            else R.string.radio_offline_saved_banner
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (showSavedTracksChip) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showSavedTracksSheet = true }
                                .testTag("radio_saved_tracks_chip")
                        ) {
                            Text(
                                text = stringResource(R.string.radio_offline_saved_tracks_chip, cachedTracks.size),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

            // Native-Style Pulsing Loading Screen (never exposes URLs or web copy)
            val showLoadingOverlay = isActiveTab &&
                !shouldBlockFirstOfflineLoad &&
                !hostState.hasRenderedContent &&
                hostState.isLoading &&
                hostState.errorKind == null

            androidx.compose.animation.AnimatedVisibility(
                visible = showLoadingOverlay,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(260)),
                modifier = Modifier
                    .fillMaxSize()
                    .radioHorizontalTabSwipe(
                        enabled = isActiveTab,
                        onSwipeToPreviousTab = onSwipeToPreviousTab,
                        onSwipeToNextTab = onSwipeToNextTab
                    )
            ) {
                RadioNativeLoadingState(
                    isSlowConnection = hostState.isSlowConnection,
                    onRetry = retryLoading
                )
            }

            // Clear, Friendly Offline / Slow Network Error State
            val activeErrorKind = when {
                !isActiveTab -> null
                shouldBlockFirstOfflineLoad -> RadioErrorKind.OFFLINE_FIRST_TIME
                hostState.errorKind != null && !hostState.hasRenderedContent -> hostState.errorKind
                else -> null
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = activeErrorKind != null,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .fillMaxSize()
                    .radioHorizontalTabSwipe(
                        enabled = isActiveTab,
                        onSwipeToPreviousTab = onSwipeToPreviousTab,
                        onSwipeToNextTab = onSwipeToNextTab
                    )
            ) {
                if (activeErrorKind != null) {
                    RadioFriendlyOfflineOrErrorState(
                        kind = activeErrorKind,
                        savedTracksCount = cachedTracks.size,
                        onRetry = retryLoading,
                        onOpenSavedTracks = { showSavedTracksSheet = true }
                    )
                }
            }
            }
        }
    }

    // Native Bottom Sheet for Replaying Locally Saved Played Songs Offline
    if (isActiveTab && showSavedTracksSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val playingOfflineMsg = stringResource(R.string.radio_playing_offline_toast)

        ModalBottomSheet(
            onDismissRequest = { showSavedTracksSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.radio_saved_tracks_sheet_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.radio_saved_tracks_sheet_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                if (cachedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.radio_saved_tracks_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val songs = cachedTracks.map { cacheManager.toSonicletSong(it) }
                                if (songs.isNotEmpty()) {
                                    viewModel.playQueue(songs, 0, "Saved Radio Tracks")
                                    Toast.makeText(context, playingOfflineMsg, Toast.LENGTH_SHORT).show()
                                    showSavedTracksSheet = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("radio_play_all_saved_btn")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.radio_play_all_saved),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { cacheManager.clearSavedAudioTracks() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("radio_clear_saved_btn")
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.radio_clear_saved_cache))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(cachedTracks, key = { _, track -> track.id }) { index, track ->
                            SavedOfflineTrackRow(
                                track = track,
                                onClick = {
                                    val songs = cachedTracks.map { cacheManager.toSonicletSong(it) }
                                    viewModel.playQueue(songs, index, "Saved Radio Tracks")
                                    Toast.makeText(context, playingOfflineMsg, Toast.LENGTH_SHORT).show()
                                    showSavedTracksSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioNativeLoadingState(
    isSlowConnection: Boolean,
    onRetry: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radio_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radio_pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(26.dp)
            )

            Text(
                text = stringResource(
                    if (isSlowConnection) R.string.radio_slow_connection_title
                    else R.string.radio_loading_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(
                    if (isSlowConnection) R.string.radio_slow_connection_subtitle
                    else R.string.radio_loading_subtitle
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isSlowConnection) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("radio_slow_retry_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.radio_try_again),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioFriendlyOfflineOrErrorState(
    kind: RadioErrorKind,
    savedTracksCount: Int,
    onRetry: () -> Unit,
    onOpenSavedTracks: () -> Unit
) {
    val isOfflineFirstTime = kind == RadioErrorKind.OFFLINE_FIRST_TIME

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOfflineFirstTime) {
                        Icons.Rounded.WifiOff
                    } else {
                        Icons.Rounded.SignalWifiConnectedNoInternet4
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    if (isOfflineFirstTime) R.string.radio_offline_never_loaded_title
                    else R.string.radio_connection_error_title
                ),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(
                    if (isOfflineFirstTime) R.string.radio_offline_never_loaded_body
                    else R.string.radio_connection_error_body
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRetry,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("radio_retry_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.radio_try_again),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (savedTracksCount > 0) {
                TextButton(
                    onClick = onOpenSavedTracks,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("radio_open_saved_tracks_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OfflinePin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.radio_saved_tracks_sheet_title)} ($savedTracksCount)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedOfflineTrackRow(
    track: CachedRadioTrack,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val durationLabel = formatRadioDuration(track.durationMs)
                val metaLine = buildString {
                    append(track.artist)
                    if (durationLabel.isNotBlank()) {
                        append(" • ")
                        append(durationLabel)
                    }
                    append(" • ")
                    append(formatRadioFileSize(track.sizeBytes))
                }
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.radio_cd_offline_tracks),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Clean Disconnected Card shown when Connect Radio is OFF (default on app startup).
 * Loads zero WebView or background network resources until the user explicitly turns on Connect Radio.
 */
@Composable
private fun RadioDisconnectedCard(
    savedTracksCount: Int,
    onConnect: () -> Unit,
    onOpenSavedTracks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 24.dp, end = 24.dp, bottom = 100.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("radio_disconnected_card")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.radio_disconnected_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.radio_disconnected_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onConnect,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("radio_connect_cta_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.radio_connect_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (savedTracksCount > 0) {
                    TextButton(
                        onClick = onOpenSavedTracks,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("radio_disconnected_saved_tracks_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.radio_saved_tracks_sheet_title)} ($savedTracksCount)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated native top pill control bar for Soniclet Radio featuring the master
 * **Connect Radio** toggle switch (OFF by default) plus explicit Play/Pause and
 * one-tap Stop Radio controls whenever connected.
 */
@Composable
private fun RadioStreamControlBar(
    hostState: RadioHostState,
    onToggleConnected: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = hostState.isRadioConnected
    val isRadioActive = isConnected && (hostState.isRadioPlaying || hostState.isRadioSessionActive)
    val titleText = if (isConnected && hostState.hasRenderedContent) {
        hostState.currentTitle.ifBlank { stringResource(R.string.radio_connect_toggle_label) }
    } else {
        stringResource(R.string.radio_connect_toggle_label)
    }
    val statusText = when {
        !isConnected -> stringResource(R.string.radio_disconnected_status)
        hostState.isRadioPlaying -> stringResource(R.string.radio_status_live)
        hostState.isRadioSessionActive -> stringResource(R.string.radio_status_paused)
        hostState.hasRenderedContent -> stringResource(R.string.radio_status_ready)
        else -> stringResource(R.string.radio_connected_status)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("radio_control_bar"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        ),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (hostState.isRadioPlaying || isConnected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isConnected && hostState.hasRenderedContent) {
                IconButton(
                    onClick = onTogglePlayPause,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("radio_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (hostState.isRadioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (hostState.isRadioPlaying) R.string.radio_cd_pause else R.string.radio_cd_play
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isRadioActive) {
                IconButton(
                    onClick = onStopRadio,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("radio_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.radio_cd_stop),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Switch(
                checked = isConnected,
                onCheckedChange = onToggleConnected,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("radio_connect_toggle")
            )
        }
    }
}

/**
 * Floating mini-banner shown across other tabs whenever Online Radio is active in the background,
 * giving one-tap Play/Pause and Stop Radio (Turn Off) controls.
 */
@Composable
fun RadioMiniBanner(
    hostState: RadioHostState,
    onOpenRadioTab: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenRadioTab,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("radio_mini_banner"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostState.currentTitle.ifBlank { stringResource(R.string.radio_tab_title) },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (hostState.isRadioPlaying) R.string.radio_live_banner_subtitle
                        else R.string.radio_paused_banner_subtitle
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("radio_mini_banner_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (hostState.isRadioPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (hostState.isRadioPlaying) R.string.radio_cd_pause else R.string.radio_cd_play
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            FilledTonalButton(
                onClick = onStopRadio,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("radio_mini_banner_stop_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.radio_cd_stop),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.radio_turn_off),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

