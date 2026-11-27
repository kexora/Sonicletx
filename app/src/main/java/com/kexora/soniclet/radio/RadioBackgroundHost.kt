package com.kexora.soniclet.radio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import com.kexora.soniclet.audio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale

enum class RadioErrorKind {
    OFFLINE_FIRST_TIME,
    SLOW_OR_UNREACHABLE
}

data class RadioHostState(
    val isRadioConnected: Boolean = false,
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    val hasRenderedContent: Boolean = false,
    val isSlowConnection: Boolean = false,
    val errorKind: RadioErrorKind? = null,
    val isViewingOfflineCache: Boolean = false,
    val canGoBack: Boolean = false,
    val isRadioPlaying: Boolean = false,
    val isRadioSessionActive: Boolean = false,
    val currentTitle: String = "Soniclet Radio",
    val currentArtist: String = "",
    val stationName: String = "Soniclet Radio",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkUrl: String = ""
)

/**
 * Custom WebView that keeps Chromium's media/YouTube IFrame decoder alive
 * even when switching tabs, locking the screen, or swiping the app away from Recents.
 */
class BackgroundAudioWebView(context: Context) : WebView(context) {
    @Volatile
    var keepAliveInBackground: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepAliveInBackground) View.VISIBLE else visibility)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(if (keepAliveInBackground) View.VISIBLE else visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, if (keepAliveInBackground) View.VISIBLE else visibility)
    }
}

/**
 * Application-scoped persistent host for Soniclet Radio.
 *
 * Keeps a single WebView alive across tab switches, Activity recreation, screen lock,
 * and Recents task removal, backed by [MusicPlaybackService] foreground notification + WakeLock
 * so YouTube IFrame audio streams never stop or get killed in the background.
 */
class RadioBackgroundHost private constructor(context: Context) : RadioControlListener {

    private val appContext = context.applicationContext
    private val webViewContextWrapper = MutableContextWrapper(appContext)
    private val cacheManager = RadioOfflineCacheManager.getInstance(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        RadioHostState(
            isRadioConnected = false,
            isOnline = checkNetworkAvailable(),
            isLoading = false,
            isViewingOfflineCache = false
        )
    )
    val state: StateFlow<RadioHostState> = _state.asStateFlow()

    private var webView: BackgroundAudioWebView? = null
    private var slowTimerJob: Job? = null
    private var audioMonitorJob: Job? = null
    private var artworkFetchJob: Job? = null
    private var networkCallbackRegistered = false
    @Volatile
    private var userExplicitlyStartedRadio = false
    private var pausedByTransientFocusLoss = false
    private var isBackgroundServiceHeldByRadio = false
    private var hasRadioNotificationBeenShown = false
    private var hasRadioAudioFocus = false
    private var radioAudioFocusRequest: AudioFocusRequest? = null
    private var renderCrashCount = 0
    private var lastBgArgb: Int = android.graphics.Color.BLACK

    // Cached artwork & notification throttle state
    private var cachedArtworkUrl: String = ""
    private var cachedArtworkBitmap: Bitmap? = null
    private var lastNotifiedTitle: String = ""
    private var lastNotifiedArtist: String = ""
    private var lastNotifiedPlaying: Boolean = false
    private var lastNotifiedDurationMs: Long = -1L
    private var lastNotifiedPosMs: Long = 0L
    private var lastNotifiedElapsedRealtime: Long = 0L

    private val radioAudioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        mainHandler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasRadioAudioFocus = false
                    pausedByTransientFocusLoss = false
                    if (_state.value.isRadioPlaying) {
                        onRadioPause()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (_state.value.isRadioPlaying && userExplicitlyStartedRadio) {
                        pausedByTransientFocusLoss = true
                        webView?.evaluateJavascript(PAUSE_ALL_MEDIA_JS, null)
                        _state.update { it.copy(isRadioPlaying = false) }
                        if (hasRadioNotificationBeenShown) {
                            syncRadioToMediaControlCenter(force = true)
                        }
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Never enter oscillating duck/recover loops or leave Radio volume permanently ducked;
                    // keep internal stream gain steady at 1.0f (100% of device media volume).
                    webView?.evaluateJavascript(LOCK_VOLUME_FULL_JS, null)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasRadioAudioFocus = true
                    // Ensure Radio gain stays locked at steady 1.0f after any transient notification
                    webView?.evaluateJavascript(LOCK_VOLUME_FULL_JS, null)
                    if (pausedByTransientFocusLoss && userExplicitlyStartedRadio) {
                        pausedByTransientFocusLoss = false
                        ensureWebViewActiveForPlayback()
                        webView?.evaluateJavascript(PLAY_RADIO_JS, null)
                        _state.update { it.copy(isRadioPlaying = true, isRadioSessionActive = true) }
                        syncRadioToMediaControlCenter(force = true)
                    }
                }
            }
        }
    }

    private inner class RadioJsBridge {
        @JavascriptInterface
        fun onUserInitiatedWebPlay() {
            mainHandler.post {
                val localWasPlaying = (appContext as? com.kexora.soniclet.SonicletApplication)
                    ?.musicViewModel?.uiState?.value?.isPlaying == true
                userExplicitlyStartedRadio = true
                pausedByTransientFocusLoss = false
                if (localWasPlaying || (appContext as? com.kexora.soniclet.SonicletApplication)?.musicViewModel?.uiState?.value?.currentSong != null) {
                    stopLocalPlaybackAndHideMiniPlayer()
                }
                requestRadioAudioFocus()
                webView?.let { wv ->
                    wv.settings.mediaPlaybackRequiresUserGesture = false
                    wv.keepAliveInBackground = true
                    wv.evaluateJavascript(LOCK_VOLUME_FULL_JS, null)
                }
                _state.update { it.copy(isRadioSessionActive = true) }
                startAudioMonitorLoop()
                schedulePostControlInspect()
            }
        }

        @JavascriptInterface
        fun onUserInitiatedWebPause() {
            mainHandler.post {
                pausedByTransientFocusLoss = false
                _state.update { it.copy(isRadioPlaying = false) }
                if (hasRadioNotificationBeenShown) {
                    syncRadioToMediaControlCenter(force = true)
                }
            }
        }
    }

    private fun requestRadioAudioFocus(): Boolean {
        return try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(radioAudioFocusListener, mainHandler)
                    .build()
                radioAudioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    radioAudioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            hasRadioAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasRadioAudioFocus
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonRadioAudioFocus() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                radioAudioFocusRequest?.let { req ->
                    audioManager.abandonAudioFocusRequest(req)
                }
                radioAudioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(radioAudioFocusListener)
            }
        } catch (_: Exception) {
        } finally {
            hasRadioAudioFocus = false
        }
    }

    init {
        MediaNotificationManager.registerRadioListener(this)
        registerNetworkMonitor()
    }

    /**
     * Master Connect Radio toggle:
     * - OFF (default on startup): Immediately stops audio, abandons audio focus, dismisses notifications,
     *   unloads and destroys the WebView so zero background network or audio activity runs.
     * - ON: Enables loading the Radio stream interface, while keeping playback strictly paused
     *   (userExplicitlyStartedRadio = false) until the user explicitly taps Play.
     */
    fun setRadioConnected(connected: Boolean) {
        mainHandler.post {
            if (!connected) {
                userExplicitlyStartedRadio = false
                pausedByTransientFocusLoss = false
                isBackgroundServiceHeldByRadio = false
                hasRadioNotificationBeenShown = false
                slowTimerJob?.cancel()
                slowTimerJob = null
                audioMonitorJob?.cancel()
                audioMonitorJob = null
                artworkFetchJob?.cancel()
                artworkFetchJob = null
                abandonRadioAudioFocus()

                webView?.let { wv ->
                    runCatching {
                        wv.keepAliveInBackground = false
                        wv.settings.mediaPlaybackRequiresUserGesture = true
                        wv.evaluateJavascript(STOP_RADIO_AND_LOCK_AUTOPLAY_JS, null)
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.onPause()
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                    }
                }
                webView = null
                webViewContextWrapper.baseContext = appContext

                MediaNotificationManager.dismissRadioNotification(appContext)
                MusicPlaybackService.stopRadioForeground(appContext)

                _state.update {
                    it.copy(
                        isRadioConnected = false,
                        isLoading = false,
                        hasRenderedContent = false,
                        isSlowConnection = false,
                        errorKind = null,
                        isViewingOfflineCache = false,
                        canGoBack = false,
                        isRadioPlaying = false,
                        isRadioSessionActive = false,
                        positionMs = 0L,
                        durationMs = 0L
                    )
                }
            } else {
                if (_state.value.isRadioConnected) return@post
                val onlineNow = checkNetworkAvailable()
                val hasSnapshot = cacheManager.hasSavedSnapshot()
                userExplicitlyStartedRadio = false
                pausedByTransientFocusLoss = false
                _state.update {
                    it.copy(
                        isRadioConnected = true,
                        isOnline = onlineNow,
                        isLoading = onlineNow || hasSnapshot,
                        hasRenderedContent = false,
                        isSlowConnection = false,
                        errorKind = if (!onlineNow && !hasSnapshot) RadioErrorKind.OFFLINE_FIRST_TIME else null,
                        isViewingOfflineCache = !onlineNow && hasSnapshot,
                        isRadioPlaying = false,
                        isRadioSessionActive = false
                    )
                }
            }
        }
    }

    fun attachToContainer(container: ViewGroup, backgroundColorArgb: Int) {
        if (!_state.value.isRadioConnected) return
        lastBgArgb = backgroundColorArgb
        // Bind to the live Activity context while attached so dropdowns, dialogs, and display metrics work properly
        webViewContextWrapper.baseContext = container.context
        val onlineNow = checkNetworkAvailable()
        val hasSnapshot = cacheManager.hasSavedSnapshot()
        _state.update { it.copy(isOnline = onlineNow) }

        if (!onlineNow && !hasSnapshot && !_state.value.hasRenderedContent) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorKind = RadioErrorKind.OFFLINE_FIRST_TIME
                )
            }
            return
        }

        val wv = getOrCreateWebView(backgroundColorArgb) ?: return
        wv.setBackgroundColor(backgroundColorArgb)

        val currentParent = wv.parent as? ViewGroup
        if (currentParent !== container) {
            currentParent?.removeView(wv)
            container.removeAllViews()
            container.addView(
                wv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        wv.keepAliveInBackground = userExplicitlyStartedRadio || _state.value.isRadioPlaying
        wv.onResume()
        wv.resumeTimers()
        if (!userExplicitlyStartedRadio) {
            wv.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
        }
        startAudioMonitorLoop()
    }

    fun detachFromContainer(container: ViewGroup) {
        val wv = webView ?: return
        if (wv.parent === container) {
            container.removeView(wv)
        }
        // Release the Activity reference immediately to prevent leaks on Activity destroy
        webViewContextWrapper.baseContext = appContext

        val shouldStayAlive = userExplicitlyStartedRadio && _state.value.isRadioPlaying
        if (shouldStayAlive) {
            wv.keepAliveInBackground = true
            wv.onResume()
            wv.resumeTimers()
            mainHandler.post {
                webView?.let { activeWv ->
                    if (userExplicitlyStartedRadio && _state.value.isRadioPlaying) {
                        activeWv.keepAliveInBackground = true
                        activeWv.dispatchWindowVisibilityChanged(View.VISIBLE)
                        activeWv.onResume()
                        activeWv.resumeTimers()
                    }
                }
            }
            ensureRadioForegroundAlive(forceNotification = true)
        } else {
            wv.keepAliveInBackground = false
            wv.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
            wv.onPause()
        }
    }

    fun onHostForegrounded() {
        webView?.let { wv ->
            wv.keepAliveInBackground = userExplicitlyStartedRadio || _state.value.isRadioPlaying
            wv.onResume()
            wv.resumeTimers()
        }
    }

    fun onHostBackgrounded() {
        val shouldStayAlive = userExplicitlyStartedRadio && _state.value.isRadioPlaying
        if (shouldStayAlive) {
            webView?.let { wv ->
                wv.keepAliveInBackground = true
                wv.dispatchWindowVisibilityChanged(View.VISIBLE)
                wv.onResume()
                wv.resumeTimers()
            }
            ensureRadioForegroundAlive(forceNotification = true)
        } else {
            webView?.let { wv ->
                wv.keepAliveInBackground = false
                wv.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
            }
        }
    }

    fun updateBackgroundColor(backgroundColorArgb: Int) {
        lastBgArgb = backgroundColorArgb
        webView?.setBackgroundColor(backgroundColorArgb)
    }

    fun goBack(): Boolean {
        val wv = webView ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            true
        } else {
            false
        }
    }

    fun reload(forceOnline: Boolean = false) {
        if (!_state.value.isRadioConnected) return
        val onlineNow = checkNetworkAvailable()
        _state.update { it.copy(isOnline = onlineNow, isSlowConnection = false) }

        if (!onlineNow && !cacheManager.hasSavedSnapshot()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorKind = RadioErrorKind.OFFLINE_FIRST_TIME
                )
            }
            return
        }

        val offlineFallback = !onlineNow && !forceOnline
        _state.update {
            it.copy(
                errorKind = null,
                isLoading = true,
                isViewingOfflineCache = offlineFallback
            )
        }

        val wv = getOrCreateWebView(lastBgArgb) ?: return
        wv.settings.cacheMode = if (onlineNow) {
            WebSettings.LOAD_DEFAULT
        } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        wv.loadUrl(RadioOfflineCacheManager.PRIMARY_URL)
        startSlowConnectionWatchdog()
    }

    /**
     * Completely stops Radio playback, locks autoplay, releases AudioFocus,
     * dismisses the Radio MediaSession notification, and shuts down background service holding.
     */
    fun stopBackgroundPlayback() {
        mainHandler.post {
            userExplicitlyStartedRadio = false
            pausedByTransientFocusLoss = false
            isBackgroundServiceHeldByRadio = false
            hasRadioNotificationBeenShown = false
            audioMonitorJob?.cancel()
            audioMonitorJob = null
            abandonRadioAudioFocus()
            _state.update {
                it.copy(
                    isRadioPlaying = false,
                    isRadioSessionActive = false,
                    positionMs = 0L
                )
            }
            webView?.let { wv ->
                wv.settings.mediaPlaybackRequiresUserGesture = true
                wv.evaluateJavascript(STOP_RADIO_AND_LOCK_AUTOPLAY_JS, null)
                wv.keepAliveInBackground = false
                if (wv.parent == null) {
                    wv.onPause()
                }
            }
            MediaNotificationManager.dismissRadioNotification(appContext)
            MusicPlaybackService.stopRadioForeground(appContext)
        }
    }

    /**
     * Completely stops Radio & releases its AudioFocus when the user starts a local song,
     * while preserving local music's own notification and foreground service.
     */
    fun stopForLocalPlayback() {
        mainHandler.post {
            val wasActive = userExplicitlyStartedRadio ||
                _state.value.isRadioPlaying ||
                _state.value.isRadioSessionActive ||
                hasRadioAudioFocus
            userExplicitlyStartedRadio = false
            pausedByTransientFocusLoss = false
            isBackgroundServiceHeldByRadio = false
            hasRadioNotificationBeenShown = false
            audioMonitorJob?.cancel()
            audioMonitorJob = null
            abandonRadioAudioFocus()
            if (wasActive || _state.value.isRadioPlaying || _state.value.isRadioSessionActive) {
                _state.update {
                    it.copy(
                        isRadioPlaying = false,
                        isRadioSessionActive = false,
                        positionMs = 0L
                    )
                }
            }
            webView?.let { wv ->
                wv.settings.mediaPlaybackRequiresUserGesture = true
                wv.evaluateJavascript(STOP_RADIO_AND_LOCK_AUTOPLAY_JS, null)
                wv.keepAliveInBackground = false
                if (wv.parent == null) {
                    wv.onPause()
                }
            }
        }
    }

    /**
     * Compatibility alias that stops Radio cleanly when local playback begins.
     */
    fun pauseForLocalPlayback() {
        stopForLocalPlayback()
    }

    // --- RadioControlListener: Android Media Control Center, Lockscreen, & Headset Controls ---

    override fun onRadioPlay() {
        mainHandler.post {
            if (!_state.value.isRadioConnected) return@post
            userExplicitlyStartedRadio = true
            pausedByTransientFocusLoss = false
            stopLocalPlaybackAndHideMiniPlayer()
            requestRadioAudioFocus()
            ensureWebViewActiveForPlayback()
            webView?.let { wv ->
                wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.evaluateJavascript(PLAY_RADIO_JS, null)
            }
            _state.update { it.copy(isRadioPlaying = true, isRadioSessionActive = true) }
            startAudioMonitorLoop()
            syncRadioToMediaControlCenter(force = true)
            schedulePostControlInspect()
        }
    }

    override fun onRadioPause() {
        mainHandler.post {
            pausedByTransientFocusLoss = false
            webView?.evaluateJavascript(PAUSE_ALL_MEDIA_JS, null)
            _state.update { it.copy(isRadioPlaying = false) }
            if (hasRadioNotificationBeenShown) {
                syncRadioToMediaControlCenter(force = true)
            }
            schedulePostControlInspect()
        }
    }

    override fun onRadioTogglePlayPause() {
        mainHandler.post {
            if (!_state.value.isRadioConnected) return@post
            if (_state.value.isRadioPlaying) {
                onRadioPause()
            } else {
                onRadioPlay()
            }
        }
    }

    override fun onRadioNext() {
        mainHandler.post {
            if (!_state.value.isRadioConnected) return@post
            userExplicitlyStartedRadio = true
            pausedByTransientFocusLoss = false
            stopLocalPlaybackAndHideMiniPlayer()
            requestRadioAudioFocus()
            ensureWebViewActiveForPlayback()
            webView?.let { wv ->
                wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.evaluateJavascript(NEXT_TRACK_JS, null)
            }
            _state.update { it.copy(isRadioPlaying = true, isRadioSessionActive = true, positionMs = 0L) }
            startAudioMonitorLoop()
            syncRadioToMediaControlCenter(force = true)
            schedulePostControlInspect()
        }
    }

    override fun onRadioPrevious() {
        mainHandler.post {
            if (!_state.value.isRadioConnected) return@post
            userExplicitlyStartedRadio = true
            pausedByTransientFocusLoss = false
            stopLocalPlaybackAndHideMiniPlayer()
            requestRadioAudioFocus()
            ensureWebViewActiveForPlayback()
            webView?.let { wv ->
                wv.settings.mediaPlaybackRequiresUserGesture = false
                wv.evaluateJavascript(PREV_TRACK_JS, null)
            }
            _state.update { it.copy(isRadioPlaying = true, isRadioSessionActive = true, positionMs = 0L) }
            startAudioMonitorLoop()
            syncRadioToMediaControlCenter(force = true)
            schedulePostControlInspect()
        }
    }

    override fun onRadioRewind() {
        mainHandler.post {
            val targetMs = (_state.value.positionMs - 10_000L).coerceAtLeast(0L)
            onRadioSeekTo(targetMs)
        }
    }

    override fun onRadioFastForward() {
        mainHandler.post {
            val dur = _state.value.durationMs
            val targetMs = if (dur > 0L) {
                (_state.value.positionMs + 10_000L).coerceAtMost(dur)
            } else {
                _state.value.positionMs + 10_000L
            }
            onRadioSeekTo(targetMs)
        }
    }

    override fun onRadioSeekTo(positionMs: Long) {
        mainHandler.post {
            val dur = _state.value.durationMs
            val clampedMs = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
            val targetSec = clampedMs / 1000.0
            webView?.evaluateJavascript(buildSeekJs(targetSec), null)
            _state.update { it.copy(positionMs = clampedMs) }
            syncRadioToMediaControlCenter(force = true)
            schedulePostControlInspect()
        }
    }

    override fun onRadioStop() {
        stopBackgroundPlayback()
    }

    private fun ensureWebViewActiveForPlayback() {
        webView?.let { wv ->
            wv.keepAliveInBackground = true
            wv.dispatchWindowVisibilityChanged(View.VISIBLE)
            wv.onResume()
            wv.resumeTimers()
        }
    }

    private fun schedulePostControlInspect() {
        mainHandler.postDelayed({ inspectPlaybackAndKeepAlive() }, 350L)
        mainHandler.postDelayed({ inspectPlaybackAndKeepAlive() }, 1200L)
    }

    private fun stopLocalPlaybackAndHideMiniPlayer() {
        val vm = (appContext as? com.kexora.soniclet.SonicletApplication)?.musicViewModel ?: return
        val localState = vm.uiState.value
        if (localState.isPlaying || localState.currentSong != null) {
            vm.stopAndHideMiniPlayerForRadio()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun getOrCreateWebView(backgroundColorArgb: Int): BackgroundAudioWebView? {
        webView?.let { return it }

        return runCatching {
            val wv = BackgroundAudioWebView(webViewContextWrapper).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                keepAliveInBackground = false
                // Keep Chromium renderer process important when active
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                }
                // Keep default hardware compositing so YouTube IFrame <video>/audio decoding works properly
                setBackgroundColor(backgroundColorArgb)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isLongClickable = false
                setOnLongClickListener { true }

                // Register explicit JS bridge so ONLY genuine Play taps unlock Radio playback
                addJavascriptInterface(RadioJsBridge(), "SonicletRadioBridge")

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    // Block all automatic audio/video playback until user explicitly taps Play
                    mediaPlaybackRequiresUserGesture = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false)
                    // Strip "; wv" marker so YouTube IFrame treats WebView as standard Mobile Chrome
                    val defaultUa = userAgentString.orEmpty()
                    if (defaultUa.isNotBlank()) {
                        userAgentString = defaultUa
                            .replace("; wv", "")
                            .replace("Version/4.0 ", "")
                    }
                    cacheMode = if (checkNetworkAvailable()) {
                        WebSettings.LOAD_DEFAULT
                    } else {
                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                    // Security hardening while preserving HTTPS YouTube IFrame media playback
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                // Critical for YouTube embedded IFrame playback inside soniclet.kexora.in
                runCatching {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress >= 85) {
                            _state.update {
                                it.copy(
                                    hasRenderedContent = true,
                                    isLoading = false,
                                    isSlowConnection = false
                                )
                            }
                        }
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        if (request == null) return
                        // Only allow DRM/EME protected media playback required by YouTube HTML5 streams
                        val protectedMedia = request.resources.filter {
                            it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                        }.toTypedArray()
                        if (protectedMedia.isNotEmpty()) {
                            request.grant(protectedMedia)
                        } else {
                            request.deny()
                        }
                    }

                    override fun getDefaultVideoPoster(): Bitmap {
                        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request == null || !request.method.equals("GET", ignoreCase = true)) {
                            return null
                        }
                        val reqUrl = request.url?.toString() ?: return null
                        val scheme = request.url?.scheme?.lowercase(Locale.ROOT) ?: return null
                        if (scheme != "https" && scheme != "http") return null

                        val reqHeaders = request.requestHeaders
                        val stateNow = _state.value

                        // 1. Offline mode: serve saved page assets & saved audio straight from local disk
                        if (!stateNow.isOnline || stateNow.isViewingOfflineCache) {
                            return cacheManager.getCachedWebResponse(reqUrl, reqHeaders)
                        }

                        // 2. Online mode: NEVER alter Chromium's live response (keeps YouTube IFrame 100% intact).
                        //    Quietly mirror first-party page assets and direct non-YouTube audio in background.
                        if (cacheManager.isLikelyAudioUrl(reqUrl, reqHeaders)) {
                            cacheManager.cacheAudioInBackground(
                                rawUrl = reqUrl,
                                requestHeaders = reqHeaders
                            )
                        } else if (cacheManager.isCacheablePageAsset(reqUrl)) {
                            cacheManager.mirrorAssetInBackground(reqUrl, reqHeaders)
                        }
                        return null
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val targetUri = request?.url ?: return true
                        val scheme = targetUri.scheme?.lowercase(Locale.ROOT) ?: return true
                        val host = targetUri.host?.lowercase(Locale.ROOT) ?: ""

                        // Never hijack YouTube / media iframe subframe navigations!
                        if (!request.isForMainFrame) {
                            return when (scheme) {
                                "https", "about", "blob" -> false
                                else -> true
                            }
                        }

                        return when {
                            scheme == "https" && cacheManager.isFirstPartyHost(host) -> false
                            scheme == "http" && cacheManager.isFirstPartyHost(host) -> {
                                val upgraded = targetUri.buildUpon().scheme("https").build().toString()
                                view?.loadUrl(upgraded)
                                true
                            }
                            scheme == "https" || scheme == "http" || scheme == "mailto" -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    appContext.startActivity(intent)
                                } catch (_: Exception) {
                                }
                                true
                            }
                            else -> true
                        }
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        _state.update { current ->
                            current.copy(
                                isLoading = if (current.hasRenderedContent) current.isLoading else true,
                                errorKind = null,
                                canGoBack = view?.canGoBack() == true
                            )
                        }
                        view?.evaluateJavascript(NATIVE_POLISH_AND_KEEPALIVE_JS, null)
                        if (!userExplicitlyStartedRadio) {
                            view?.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
                        }
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        _state.update {
                            it.copy(
                                hasRenderedContent = true,
                                isLoading = false,
                                isSlowConnection = false
                            )
                        }
                        view?.evaluateJavascript(NATIVE_POLISH_AND_KEEPALIVE_JS, null)
                        if (!userExplicitlyStartedRadio) {
                            view?.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        renderCrashCount = 0
                        view?.evaluateJavascript(NATIVE_POLISH_AND_KEEPALIVE_JS, null)
                        if (!userExplicitlyStartedRadio) {
                            view?.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
                        }
                        val cur = _state.value
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isSlowConnection = false,
                                canGoBack = view?.canGoBack() == true,
                                hasRenderedContent = if (cur.errorKind == null) true else cur.hasRenderedContent
                            )
                        }
                        if (cur.errorKind == null && cur.isOnline && !cur.isViewingOfflineCache) {
                            cacheManager.markPageLoadedSuccessfully()
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            val cur = _state.value
                            if (!cur.isViewingOfflineCache && cacheManager.hasSavedSnapshot()) {
                                _state.update {
                                    it.copy(
                                        isLoading = true,
                                        isSlowConnection = false,
                                        isViewingOfflineCache = true
                                    )
                                }
                                view?.settings?.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                                view?.loadUrl(RadioOfflineCacheManager.PRIMARY_URL)
                            } else if (!cur.hasRenderedContent) {
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        isSlowConnection = false,
                                        errorKind = if (!checkNetworkAvailable()) {
                                            RadioErrorKind.OFFLINE_FIRST_TIME
                                        } else {
                                            RadioErrorKind.SLOW_OR_UNREACHABLE
                                        }
                                    )
                                }
                            }
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.cancel()
                        if (!_state.value.hasRenderedContent) {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    errorKind = RadioErrorKind.SLOW_OR_UNREACHABLE
                                )
                            }
                        }
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        val oldParent = view?.parent as? ViewGroup
                        oldParent?.removeView(view)
                        runCatching { view?.destroy() }
                        webView = null
                        renderCrashCount++
                        if (renderCrashCount <= 2) {
                            _state.update {
                                it.copy(
                                    isLoading = true,
                                    hasRenderedContent = false,
                                    errorKind = null
                                )
                            }
                            if (oldParent != null) {
                                mainHandler.post { attachToContainer(oldParent, lastBgArgb) }
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    hasRenderedContent = false,
                                    errorKind = RadioErrorKind.SLOW_OR_UNREACHABLE
                                )
                            }
                        }
                        return true
                    }
                }
            }

            webView = wv
            val onlineNow = checkNetworkAvailable()
            _state.update {
                it.copy(
                    isOnline = onlineNow,
                    isLoading = true,
                    errorKind = null,
                    isViewingOfflineCache = !onlineNow && cacheManager.hasSavedSnapshot()
                )
            }
            wv.loadUrl(RadioOfflineCacheManager.PRIMARY_URL)
            startSlowConnectionWatchdog()
            wv
        }.getOrElse {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorKind = RadioErrorKind.SLOW_OR_UNREACHABLE
                )
            }
            null
        }
    }

    private fun startSlowConnectionWatchdog() {
        slowTimerJob?.cancel()
        slowTimerJob = scope.launch {
            delay(5_000L)
            if (_state.value.isLoading && !_state.value.hasRenderedContent) {
                _state.update { it.copy(isSlowConnection = true) }
            }
            delay(8_000L)
            if (_state.value.isLoading && !_state.value.hasRenderedContent && cacheManager.hasSavedSnapshot()) {
                _state.update { it.copy(isViewingOfflineCache = true) }
                webView?.let { wv ->
                    wv.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    wv.loadUrl(RadioOfflineCacheManager.PRIMARY_URL)
                }
            }
        }
    }

    private fun startAudioMonitorLoop() {
        if (audioMonitorJob?.isActive == true) return
        audioMonitorJob = scope.launch {
            while (isActive) {
                val intervalMs = if (_state.value.isRadioPlaying || userExplicitlyStartedRadio) 1_500L else 3_500L
                delay(intervalMs)
                if (_state.value.hasRenderedContent && webView != null) {
                    inspectPlaybackAndKeepAlive()
                }
            }
        }
    }

    private fun inspectPlaybackAndKeepAlive() {
        val wv = webView ?: return
        try {
            wv.evaluateJavascript(INSPECT_RADIO_STATE_JS) { rawJson ->
                val prev = _state.value
                var parsedTitle = prev.currentTitle
                var parsedArtist = prev.currentArtist
                var parsedStation = prev.stationName
                var parsedArtworkUrl = prev.artworkUrl
                var parsedPosMs = prev.positionMs
                var parsedDurMs = prev.durationMs
                var detectedPlaying = false
                var detectedExplicitPaused = false

                if (!rawJson.isNullOrBlank() && rawJson != "null" && rawJson != "\"\"") {
                    try {
                        val unescaped = JSONObject("{\"v\":$rawJson}").optString("v")
                        if (unescaped.startsWith("{")) {
                            val obj = JSONObject(unescaped)
                            val src = obj.optString("src").trim()
                            val title = obj.optString("title").trim()
                            val artist = obj.optString("artist").trim()
                            val station = obj.optString("station").trim()
                            val artwork = obj.optString("artwork").trim()
                            val posSec = obj.optDouble("positionSec", -1.0)
                            val durSec = obj.optDouble("durationSec", -1.0)
                            val playingFlag = obj.optBoolean("playing", false)
                            val pausedFlag = obj.optBoolean("explicitPaused", false)

                            if (title.isNotBlank()) parsedTitle = title
                            if (artist.isNotBlank()) parsedArtist = artist
                            if (station.isNotBlank()) parsedStation = station
                            if (artwork.startsWith("https://", ignoreCase = true)) {
                                parsedArtworkUrl = artwork
                            }
                            if (posSec >= 0.0 && posSec.isFinite()) {
                                parsedPosMs = (posSec * 1000.0).toLong()
                            }
                            if (durSec > 0.0 && durSec.isFinite()) {
                                parsedDurMs = (durSec * 1000.0).toLong()
                            }
                            detectedPlaying = playingFlag
                            detectedExplicitPaused = pausedFlag

                            if (src.startsWith("https://", ignoreCase = true) && _state.value.isOnline) {
                                cacheManager.cacheAudioInBackground(
                                    rawUrl = src,
                                    titleHint = title,
                                    artistHint = artist
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val finalTitle = parsedTitle.ifBlank { parsedStation.ifBlank { "Soniclet Radio" } }
                val finalStation = parsedStation.ifBlank { "Soniclet Radio" }

                // Strict user-initiated gate: if the user has NOT explicitly tapped Play,
                // immediately suppress any stray autoplay and keep isRadioPlaying = false!
                if (!userExplicitlyStartedRadio) {
                    if (detectedPlaying) {
                        wv.evaluateJavascript(PAUSE_AND_BLOCK_AUTOPLAY_JS, null)
                    }
                    _state.update {
                        it.copy(
                            isRadioPlaying = false,
                            isRadioSessionActive = false,
                            currentTitle = finalTitle,
                            currentArtist = parsedArtist,
                            stationName = finalStation,
                            artworkUrl = parsedArtworkUrl
                        )
                    }
                    return@evaluateJavascript
                }

                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val systemAudioActive = audioManager?.isMusicActive == true
                val vm = (appContext as? com.kexora.soniclet.SonicletApplication)?.musicViewModel
                val localPlayerPlaying = vm?.uiState?.value?.isPlaying == true
                val localHasCurrentSong = vm?.uiState?.value?.currentSong != null

                val radioIsActivelyPlaying = when {
                    detectedPlaying -> true
                    detectedExplicitPaused -> false
                    systemAudioActive && !localPlayerPlaying -> true
                    else -> false
                }

                if (radioIsActivelyPlaying) {
                    if (localPlayerPlaying || localHasCurrentSong) {
                        stopLocalPlaybackAndHideMiniPlayer()
                    }
                    if (!hasRadioAudioFocus) {
                        requestRadioAudioFocus()
                    }
                }

                _state.update {
                    it.copy(
                        isRadioPlaying = radioIsActivelyPlaying,
                        isRadioSessionActive = userExplicitlyStartedRadio,
                        currentTitle = finalTitle,
                        currentArtist = parsedArtist,
                        stationName = finalStation,
                        positionMs = parsedPosMs,
                        durationMs = parsedDurMs,
                        artworkUrl = parsedArtworkUrl
                    )
                }

                if (parsedArtworkUrl.isNotBlank() && parsedArtworkUrl != cachedArtworkUrl) {
                    fetchArtworkInBackground(parsedArtworkUrl)
                }

                val localStillPlaying = vm?.uiState?.value?.isPlaying == true
                if (radioIsActivelyPlaying && !localStillPlaying) {
                    ensureRadioForegroundAlive(forceNotification = !prev.isRadioPlaying)
                } else if (!radioIsActivelyPlaying && hasRadioNotificationBeenShown && !localStillPlaying) {
                    // Keep paused card synced in Media Control Center so Play/Stop work cleanly
                    syncRadioToMediaControlCenter(force = prev.isRadioPlaying)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureRadioForegroundAlive(forceNotification: Boolean = false) {
        val localPlayerPlaying = (appContext as? com.kexora.soniclet.SonicletApplication)
            ?.musicViewModel?.uiState?.value?.isPlaying == true
        if (localPlayerPlaying) return
        isBackgroundServiceHeldByRadio = true
        syncRadioToMediaControlCenter(force = forceNotification)
    }

    private fun syncRadioToMediaControlCenter(force: Boolean = false) {
        val st = _state.value
        val localPlayerPlaying = (appContext as? com.kexora.soniclet.SonicletApplication)
            ?.musicViewModel?.uiState?.value?.isPlaying == true
        if (localPlayerPlaying) return

        val metadataChanged = st.currentTitle != lastNotifiedTitle ||
            st.isRadioPlaying != lastNotifiedPlaying

        if (!force && hasRadioNotificationBeenShown && !metadataChanged) {
            return
        }

        hasRadioNotificationBeenShown = true
        lastNotifiedTitle = st.currentTitle
        lastNotifiedArtist = ""
        lastNotifiedPlaying = st.isRadioPlaying
        lastNotifiedDurationMs = -1L
        lastNotifiedPosMs = 0L
        lastNotifiedElapsedRealtime = SystemClock.elapsedRealtime()

        val art = if (cachedArtworkUrl == st.artworkUrl && cachedArtworkBitmap?.isRecycled == false) {
            cachedArtworkBitmap
        } else {
            null
        }

        MediaNotificationManager.showRadioNotification(
            context = appContext,
            title = st.currentTitle,
            isPlaying = st.isRadioPlaying,
            artworkBitmap = art
        )
    }

    /**
     * Downloads validated public-host HTTPS track artwork (256x256 RGB_565) for Android's
     * Media Control Center & Lockscreen without risking SSRF or Binder IPC bloat.
     */
    private fun fetchArtworkInBackground(urlString: String) {
        if (!urlString.startsWith("https://", ignoreCase = true)) return
        artworkFetchJob?.cancel()
        artworkFetchJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                downloadValidatedArtworkBitmap(urlString)
            }
            if (bitmap != null) {
                cachedArtworkUrl = urlString
                cachedArtworkBitmap = bitmap
                if (hasRadioNotificationBeenShown || _state.value.isRadioPlaying) {
                    syncRadioToMediaControlCenter(force = true)
                }
            }
        }
    }

    private fun downloadValidatedArtworkBitmap(urlString: String): Bitmap? {
        return try {
            val uri = Uri.parse(urlString)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            val host = uri.host?.lowercase(Locale.ROOT)?.trim().orEmpty()
            if (host.isBlank() || host == "localhost" || host.endsWith(".local")) return null

            // Reject loopback, link-local, or private network addresses (SSRF protection)
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) return null
            for (addr in addresses) {
                if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) {
                    return null
                }
            }

            val conn = (URL(urlString).openConnection() as? HttpURLConnection) ?: return null
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.setRequestProperty("Accept", "image/*")
            conn.connect()

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val maxBytes = 2 * 1024 * 1024
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        conn.disconnect()
                        return null
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
            conn.disconnect()
            if (bytes.isEmpty()) return null

            val targetSize = 256
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while ((bounds.outWidth / 2) / sampleSize >= targetSize &&
                (bounds.outHeight / 2) / sampleSize >= targetSize
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val scaled = Bitmap.createScaledBitmap(decoded, targetSize, targetSize, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }

    private fun registerNetworkMonitor() {
        if (networkCallbackRegistered) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onConnectivityChanged(true)
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    onConnectivityChanged(online)
                }

                override fun onLost(network: Network) {
                    onConnectivityChanged(checkNetworkAvailable())
                }
            })
            networkCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun onConnectivityChanged(online: Boolean) {
        mainHandler.post {
            val prev = _state.value
            _state.update { it.copy(isOnline = online) }
            if (prev.isRadioConnected && online && prev.errorKind != null && !prev.hasRenderedContent) {
                reload(forceOnline = true)
            }
        }
    }

    private fun checkNetworkAvailable(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        private const val NATIVE_POLISH_AND_KEEPALIVE_JS = """
(function() {
  try {
    if (typeof window.__sonicletAllowRadioPlay !== 'boolean') {
      window.__sonicletAllowRadioPlay = false;
    }
    if (!document.getElementById('soniclet-native-polish')) {
      var style = document.createElement('style');
      style.id = 'soniclet-native-polish';
      style.textContent = '* { -webkit-tap-highlight-color: transparent !important; -webkit-touch-callout: none !important; } html, body { overscroll-behavior: none; scrollbar-width: none; } ::-webkit-scrollbar { display: none; }';
      (document.head || document.documentElement).appendChild(style);
    }
    if (!window.__sonicletBgKeepAliveInstalled) {
      window.__sonicletBgKeepAliveInstalled = true;
      try {
        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
        Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
      } catch (e) {}
      document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
      document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
      window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);

      // Lock HTMLMediaElement.prototype.volume at 1.0 (100% of device media volume) so webpage scripts
      // cannot spontaneously attenuate or oscillate volume without user hardware/slider input.
      try {
        var volDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'volume');
        if (volDesc && volDesc.set && volDesc.get) {
          Object.defineProperty(HTMLMediaElement.prototype, 'volume', {
            configurable: true,
            get: function() { return volDesc.get.call(this); },
            set: function() {
              try { volDesc.set.call(this, 1.0); } catch (e) {}
            }
          });
        }
      } catch (e) {}

      // Strict user-initiated play gate: block automatic HTMLMediaElement.play() until user taps Play
      try {
        var origPlay = HTMLMediaElement.prototype.play;
        if (typeof origPlay === 'function') {
          HTMLMediaElement.prototype.play = function() {
            if (!window.__sonicletAllowRadioPlay) {
              try { this.pause(); } catch (e) {}
              return Promise.resolve();
            }
            try {
              this.muted = false;
              this.volume = 1.0;
            } catch (e) {}
            return origPlay.apply(this, arguments);
          };
        }
      } catch (e) {}

      // Neutralize autoplay attributes and iframe autoplay=1 parameters whenever playback is locked
      function stripAutoplay(root) {
        if (window.__sonicletAllowRadioPlay) return;
        try {
          var media = (root || document).querySelectorAll('audio, video');
          for (var i = 0; i < media.length; i++) {
            var el = media[i];
            el.autoplay = false;
            el.removeAttribute('autoplay');
            if (!el.paused) {
              try { el.pause(); } catch (e) {}
            }
          }
          var frames = (root || document).querySelectorAll('iframe');
          for (var j = 0; j < frames.length; j++) {
            var f = frames[j];
            var src = f.getAttribute('src') || '';
            if (/[?&]autoplay=1/i.test(src)) {
              f.setAttribute('src', src.replace(/([?&])autoplay=1/ig, '$1autoplay=0'));
            }
            try { f.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":[]}', '*'); } catch (e) {}
          }
        } catch (e) {}
      }
      stripAutoplay(document);

      try {
        var mo = new MutationObserver(function(mutations) {
          if (window.__sonicletAllowRadioPlay) return;
          for (var m = 0; m < mutations.length; m++) {
            var added = mutations[m].addedNodes;
            for (var n = 0; n < added.length; n++) {
              var node = added[n];
              if (node && node.nodeType === 1) {
                var tag = (node.tagName || '').toUpperCase();
                if (tag === 'AUDIO' || tag === 'VIDEO') {
                  node.autoplay = false;
                  node.removeAttribute('autoplay');
                  try { node.pause(); } catch (e) {}
                } else if (tag === 'IFRAME') {
                  var fSrc = node.getAttribute('src') || '';
                  if (/[?&]autoplay=1/i.test(fSrc)) {
                    node.setAttribute('src', fSrc.replace(/([?&])autoplay=1/ig, '$1autoplay=0'));
                  }
                } else if (node.querySelectorAll) {
                  stripAutoplay(node);
                }
              }
            }
          }
        });
        if (document.documentElement) {
          mo.observe(document.documentElement, { childList: true, subtree: true });
        }
      } catch (e) {}

      // Only unlock Radio playback on genuine trusted user clicks on interactive play/station/track controls
      document.addEventListener('click', function(evt) {
        try {
          if (!evt || !evt.isTrusted) return;
          var target = evt.target;
          var interactive = target && target.closest ? target.closest('button, [role="button"], [data-play], [data-station], [data-track], .station, .track, .song, li') : null;
          if (!interactive) return;
          var desc = ((interactive.getAttribute('aria-label') || '') + ' ' + (interactive.getAttribute('title') || '') + ' ' + (interactive.id || '') + ' ' + (interactive.className || '') + ' ' + (interactive.textContent || '').slice(0, 60)).toLowerCase();
          var svgRects = interactive.querySelectorAll ? interactive.querySelectorAll('svg rect, svg line').length : 0;
          var looksLikePause = /\bpause\b/.test(desc) || (/\bplay\b/.test(desc) && svgRects >= 2);
          var looksLikeNavOrTheme = /\b(menu|close|dismiss|theme|dark|light|share|about|privacy|contact|search|clear|back|mute|volume|fx|rain|timer|sleep)\b/.test(desc);
          if (looksLikeNavOrTheme) return;
          if (looksLikePause) {
            if (window.SonicletRadioBridge && window.SonicletRadioBridge.onUserInitiatedWebPause) {
              window.SonicletRadioBridge.onUserInitiatedWebPause();
            }
            return;
          }
          var looksLikePlayTrigger = /\b(play|listen|tune|live|radio|station|channel|next|prev|previous|skip|track|song|stream)\b/.test(desc) ||
            interactive.tagName === 'LI' ||
            (interactive.tagName === 'BUTTON' && interactive.getBoundingClientRect().top > window.innerHeight * 0.55);
          if (looksLikePlayTrigger) {
            window.__sonicletAllowRadioPlay = true;
            if (window.SonicletRadioBridge && window.SonicletRadioBridge.onUserInitiatedWebPlay) {
              window.SonicletRadioBridge.onUserInitiatedWebPlay();
            }
          }
        } catch (e) {}
      }, true);
    }
  } catch (e) {}
})();
"""

        private const val LOCK_VOLUME_FULL_JS = """
(function() {
  try {
    document.querySelectorAll('audio, video').forEach(function(el) {
      try {
        el.muted = false;
        el.volume = 1.0;
      } catch (e) {}
    });
    document.querySelectorAll('iframe').forEach(function(frame) {
      try {
        frame.contentWindow.postMessage('{"event":"command","func":"unMute","args":[]}', '*');
        frame.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[100]}', '*');
      } catch (e) {}
    });
  } catch (e) {}
})();
"""

        private const val PAUSE_AND_BLOCK_AUTOPLAY_JS = """
(function() {
  try {
    window.__sonicletAllowRadioPlay = false;
    document.querySelectorAll('audio, video').forEach(function(el) {
      try {
        el.autoplay = false;
        el.removeAttribute('autoplay');
        el.pause();
      } catch (e) {}
    });
    document.querySelectorAll('iframe').forEach(function(frame) {
      try {
        var src = frame.getAttribute('src') || '';
        if (/[?&]autoplay=1/i.test(src)) {
          frame.setAttribute('src', src.replace(/([?&])autoplay=1/ig, '$1autoplay=0'));
        }
        frame.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":[]}', '*');
      } catch (e) {}
    });
  } catch (e) {}
})();
"""

        private const val INSPECT_RADIO_STATE_JS = """
(function() {
  try {
    function parseTimeSec(txt) {
      if (!txt) return -1;
      var m = txt.trim().match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
      if (!m) return -1;
      if (m[3] !== undefined) return parseInt(m[1],10)*3600 + parseInt(m[2],10)*60 + parseInt(m[3],10);
      return parseInt(m[1],10)*60 + parseInt(m[2],10);
    }

    var activeSrc = '';
    var isPlaying = false;
    var explicitPaused = false;
    var positionSec = -1;
    var durationSec = -1;

    var audios = document.querySelectorAll('audio, video');
    for (var i = 0; i < audios.length; i++) {
      var el = audios[i];
      var src = el.currentSrc || el.src || '';
      if (!el.paused && !el.ended) {
        isPlaying = true;
        if (src) activeSrc = src;
        if (isFinite(el.currentTime)) positionSec = el.currentTime;
        if (isFinite(el.duration) && el.duration > 0) durationSec = el.duration;
        break;
      } else if (src && !activeSrc) {
        activeSrc = src;
      }
    }

    var ms = navigator.mediaSession;
    if (ms && ms.playbackState === 'playing') isPlaying = true;
    var msTitle = (ms && ms.metadata && ms.metadata.title) ? ms.metadata.title.trim() : '';
    var msArtist = (ms && ms.metadata && ms.metadata.artist) ? ms.metadata.artist.trim() : '';
    var msArtwork = '';
    if (ms && ms.metadata && ms.metadata.artwork && ms.metadata.artwork.length > 0) {
      msArtwork = ms.metadata.artwork[ms.metadata.artwork.length - 1].src || ms.metadata.artwork[0].src || '';
    }

    var stationName = (document.title || '').replace(/\s*[—\-|]\s*Soniclet.*$/i, '').trim();
    if (!stationName) {
      var h1 = document.querySelector('h1, h2');
      if (h1 && h1.textContent) stationName = h1.textContent.trim();
    }

    // Locate the bottom player bar & controls in Soniclet Radio UI
    var buttons = Array.from(document.querySelectorAll('button, [role="button"]')).filter(function(b) {
      var r = b.getBoundingClientRect();
      return r.width >= 20 && r.height >= 20 && r.bottom > window.innerHeight * 0.45;
    });

    var playPauseBtn = null;
    for (var j = 0; j < buttons.length; j++) {
      var btn = buttons[j];
      var label = ((btn.getAttribute('aria-label') || '') + ' ' + (btn.getAttribute('title') || '') + ' ' + (btn.id || '') + ' ' + (btn.className || '')).toLowerCase();
      if (/\b(play|pause|playpause|play-pause|toggle)\b/.test(label) && !/(prev|next|skip|back|forward|replay|shuffle|repeat|mute|sound|fx|rain)/.test(label)) {
        playPauseBtn = btn;
        break;
      }
    }

    // Fallback: locate the center circular Play/Pause button sitting between Prev and Next in the bottom player bar
    if (!playPauseBtn) {
      var bottomBtns = buttons.filter(function(b) {
        var r = b.getBoundingClientRect();
        return r.top > window.innerHeight * 0.65 && r.width >= 32 && r.width <= 76 && r.height >= 32 && r.height <= 76;
      });
      if (bottomBtns.length >= 3) {
        bottomBtns.sort(function(a, b) { return a.getBoundingClientRect().left - b.getBoundingClientRect().left; });
        var mid = bottomBtns[Math.floor(bottomBtns.length / 2)];
        if (mid) playPauseBtn = mid;
      }
    }

    // Detect playing/paused icon state on the Play/Pause button
    if (playPauseBtn) {
      var lbl = ((playPauseBtn.getAttribute('aria-label') || '') + ' ' + (playPauseBtn.getAttribute('title') || '') + ' ' + (playPauseBtn.innerHTML || '')).toLowerCase();
      var svgRects = playPauseBtn.querySelectorAll('svg rect, svg line').length;
      var hasPauseIcon = /pause/.test(lbl) || svgRects >= 2 || /m6\s*4h4v16|m6\s*19h4v5|m14\s*4h4v16|m9\s*4|m15\s*4/.test(lbl);
      var hasPlayIcon = /\bplay\b/.test(lbl) && !/pause/.test(lbl) && svgRects < 2;
      if (hasPauseIcon) {
        isPlaying = true;
      } else if (hasPlayIcon && !isPlaying) {
        explicitPaused = true;
      }
    }

    // Extract player bar container for title, thumbnail, and timestamps
    var playerRoot = playPauseBtn ? playPauseBtn.parentElement : null;
    while (playerRoot && playerRoot !== document.body && playerRoot.getBoundingClientRect().width < window.innerWidth * 0.65) {
      playerRoot = playerRoot.parentElement;
    }

    var domTitle = '';
    var domArtist = '';
    var domArtwork = '';

    if (playerRoot) {
      var imgs = playerRoot.querySelectorAll('img');
      for (var k = 0; k < imgs.length; k++) {
        var im = imgs[k];
        var imSrc = im.currentSrc || im.src || '';
        if (imSrc && imSrc.indexOf('https://') === 0 && (im.naturalWidth >= 24 || im.getBoundingClientRect().width >= 24)) {
          domArtwork = imSrc;
          break;
        }
      }

      var timeVals = [];
      var textCandidates = [];
      var walker = document.createTreeWalker(playerRoot, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {
        var t = (node.nodeValue || '').trim();
        if (!t) continue;
        var sec = parseTimeSec(t);
        if (sec >= 0) {
          timeVals.push(sec);
        } else if (t.length >= 2 && !/^(©|20\d\d|about|contact|privacy|soniclet|kexora)/i.test(t)) {
          textCandidates.push(t);
        }
      }
      if (timeVals.length >= 2) {
        if (positionSec < 0) positionSec = timeVals[0];
        if (durationSec <= 0) durationSec = timeVals[timeVals.length - 1];
      }
      if (textCandidates.length >= 1) domTitle = textCandidates[0];
      if (textCandidates.length >= 2) domArtist = textCandidates[1];

      var range = playerRoot.querySelector('input[type="range"]');
      if (range) {
        var rVal = parseFloat(range.value);
        var rMax = parseFloat(range.max);
        if (isFinite(rMax) && rMax > 10 && durationSec <= 0) durationSec = rMax;
        if (isFinite(rVal) && rMax > 10 && positionSec < 0) positionSec = rVal;
      }
    }

    // Check if elapsed timestamp actually moved forward since previous poll (strongest live proof of playback)
    if (positionSec >= 0) {
      var prevSec = window.__sonicletPrevPosSec;
      var prevTs = window.__sonicletPrevPosTs || 0;
      var nowTs = Date.now();
      if (typeof prevSec === 'number' && positionSec > prevSec && (nowTs - prevTs) < 10000) {
        isPlaying = true;
        explicitPaused = false;
      }
      window.__sonicletPrevPosSec = positionSec;
      window.__sonicletPrevPosTs = nowTs;
    }

    var finalTitle = msTitle || domTitle || stationName || 'Soniclet Radio';
    var finalArtist = msArtist || domArtist || '';
    var finalArt = msArtwork || domArtwork || '';

    return JSON.stringify({
      src: activeSrc,
      title: finalTitle,
      artist: finalArtist,
      station: stationName || 'Soniclet Radio',
      artwork: finalArt,
      positionSec: positionSec,
      durationSec: durationSec,
      playing: isPlaying,
      explicitPaused: explicitPaused
    });
  } catch (e) {
    return '';
  }
})();
"""

        private const val FIND_PLAYER_BUTTONS_HELPER_JS = """
function __sonicletFindControls() {
  var btns = Array.from(document.querySelectorAll('button, [role="button"]')).filter(function(b) {
    var r = b.getBoundingClientRect();
    return r.width >= 18 && r.height >= 18 && r.bottom > window.innerHeight * 0.45;
  });
  var prevBtn = null, playBtn = null, nextBtn = null;
  for (var i = 0; i < btns.length; i++) {
    var b = btns[i];
    var l = ((b.getAttribute('aria-label') || '') + ' ' + (b.getAttribute('title') || '') + ' ' + (b.id || '') + ' ' + (b.className || '')).toLowerCase();
    if (!prevBtn && /(prev|previous|skip-back|back-track)/.test(l)) prevBtn = b;
    else if (!nextBtn && /(next|skip-forward|forward-track)/.test(l)) nextBtn = b;
    else if (!playBtn && /\b(play|pause|playpause|play-pause)\b/.test(l) && !/(prev|next|skip|back|forward|fx|rain)/.test(l)) playBtn = b;
  }
  if (!playBtn || !prevBtn || !nextBtn) {
    var barBtns = btns.filter(function(b) {
      var r = b.getBoundingClientRect();
      return r.top > window.innerHeight * 0.65 && r.width >= 24 && r.width <= 80 && r.height >= 24 && r.height <= 80;
    });
    if (barBtns.length >= 3) {
      barBtns.sort(function(a, b) { return a.getBoundingClientRect().left - b.getBoundingClientRect().left; });
      // Take the last 3 horizontally aligned buttons on the right of the player bar
      var group = barBtns.slice(barBtns.length - 3);
      if (!prevBtn) prevBtn = group[0];
      if (!playBtn) playBtn = group[1];
      if (!nextBtn) nextBtn = group[2];
    }
  }
  return { prev: prevBtn, play: playBtn, next: nextBtn };
}
"""

        private const val PLAY_RADIO_JS = """
(function() {
  try {
    window.__sonicletAllowRadioPlay = true;
    $FIND_PLAYER_BUTTONS_HELPER_JS
    var c = __sonicletFindControls();
    var handled = false;
    if (c.play) {
      var lbl = ((c.play.getAttribute('aria-label') || '') + ' ' + (c.play.getAttribute('title') || '') + ' ' + (c.play.innerHTML || '')).toLowerCase();
      var svgRects = c.play.querySelectorAll('svg rect, svg line').length;
      var alreadyPlaying = /pause/.test(lbl) || svgRects >= 2;
      if (!alreadyPlaying) {
        c.play.click();
        handled = true;
      }
    }
    if (!handled) {
      document.querySelectorAll('iframe').forEach(function(frame) {
        try {
          frame.contentWindow.postMessage('{"event":"command","func":"unMute","args":[]}', '*');
          frame.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[100]}', '*');
          frame.contentWindow.postMessage('{"event":"command","func":"playVideo","args":[]}', '*');
        } catch(e) {}
      });
      document.querySelectorAll('audio, video').forEach(function(el) {
        try {
          el.muted = false;
          el.volume = 1.0;
          el.play();
        } catch(e) {}
      });
    } else {
      document.querySelectorAll('audio, video').forEach(function(el) {
        try { el.muted = false; el.volume = 1.0; } catch(e) {}
      });
      document.querySelectorAll('iframe').forEach(function(frame) {
        try {
          frame.contentWindow.postMessage('{"event":"command","func":"unMute","args":[]}', '*');
          frame.contentWindow.postMessage('{"event":"command","func":"setVolume","args":[100]}', '*');
        } catch(e) {}
      });
    }
  } catch (e) {}
})();
"""

        private const val PAUSE_ALL_MEDIA_JS = """
(function() {
  try {
    $FIND_PLAYER_BUTTONS_HELPER_JS
    var c = __sonicletFindControls();
    if (c.play) {
      var lbl = ((c.play.getAttribute('aria-label') || '') + ' ' + (c.play.getAttribute('title') || '') + ' ' + (c.play.innerHTML || '')).toLowerCase();
      var svgRects = c.play.querySelectorAll('svg rect, svg line').length;
      var currentlyPlaying = /pause/.test(lbl) || svgRects >= 2;
      if (currentlyPlaying) {
        c.play.click();
      }
    }
    document.querySelectorAll('audio, video').forEach(function(el) { try { el.pause(); } catch(e) {} });
    document.querySelectorAll('iframe').forEach(function(frame) {
      try {
        frame.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":[]}', '*');
      } catch(e) {}
    });
  } catch (e) {}
})();
"""

        private const val STOP_RADIO_AND_LOCK_AUTOPLAY_JS = """
(function() {
  try {
    window.__sonicletAllowRadioPlay = false;
    $FIND_PLAYER_BUTTONS_HELPER_JS
    var c = __sonicletFindControls();
    if (c.play) {
      var lbl = ((c.play.getAttribute('aria-label') || '') + ' ' + (c.play.getAttribute('title') || '') + ' ' + (c.play.innerHTML || '')).toLowerCase();
      var svgRects = c.play.querySelectorAll('svg rect, svg line').length;
      var currentlyPlaying = /pause/.test(lbl) || svgRects >= 2;
      if (currentlyPlaying) {
        c.play.click();
      }
    }
    document.querySelectorAll('audio, video').forEach(function(el) {
      try {
        el.autoplay = false;
        el.removeAttribute('autoplay');
        el.pause();
        if (isFinite(el.currentTime)) el.currentTime = 0;
      } catch(e) {}
    });
    document.querySelectorAll('iframe').forEach(function(frame) {
      try {
        frame.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":[]}', '*');
        frame.contentWindow.postMessage('{"event":"command","func":"stopVideo","args":[]}', '*');
      } catch(e) {}
    });
  } catch (e) {}
})();
"""

        private const val NEXT_TRACK_JS = """
(function() {
  try {
    window.__sonicletAllowRadioPlay = true;
    $FIND_PLAYER_BUTTONS_HELPER_JS
    var c = __sonicletFindControls();
    if (c.next) {
      c.next.click();
    } else {
      document.querySelectorAll('iframe').forEach(function(frame) {
        try { frame.contentWindow.postMessage('{"event":"command","func":"nextVideo","args":[]}', '*'); } catch(e) {}
      });
    }
    document.querySelectorAll('audio, video').forEach(function(el) {
      try { el.muted = false; el.volume = 1.0; } catch(e) {}
    });
  } catch (e) {}
})();
"""

        private const val PREV_TRACK_JS = """
(function() {
  try {
    window.__sonicletAllowRadioPlay = true;
    $FIND_PLAYER_BUTTONS_HELPER_JS
    var c = __sonicletFindControls();
    if (c.prev) {
      c.prev.click();
    } else {
      document.querySelectorAll('iframe').forEach(function(frame) {
        try { frame.contentWindow.postMessage('{"event":"command","func":"previousVideo","args":[]}', '*'); } catch(e) {}
      });
    }
    document.querySelectorAll('audio, video').forEach(function(el) {
      try { el.muted = false; el.volume = 1.0; } catch(e) {}
    });
  } catch (e) {}
})();
"""

        private fun buildSeekJs(targetSeconds: Double): String {
            val safeSec = String.format(Locale.US, "%.2f", targetSeconds.coerceAtLeast(0.0))
            return """
(function() {
  try {
    var sec = $safeSec;
    document.querySelectorAll('iframe').forEach(function(frame) {
      try {
        frame.contentWindow.postMessage('{"event":"command","func":"seekTo","args":[' + sec + ',true]}', '*');
      } catch(e) {}
    });
    document.querySelectorAll('audio, video').forEach(function(el) {
      try { if (isFinite(el.duration) && el.duration > 0) el.currentTime = Math.min(sec, el.duration); } catch(e) {}
    });
    var ranges = document.querySelectorAll('input[type="range"]');
    for (var i = 0; i < ranges.length; i++) {
      var r = ranges[i];
      var rect = r.getBoundingClientRect();
      if (rect.top > window.innerHeight * 0.65) {
        var maxVal = parseFloat(r.max);
        var targetVal = (isFinite(maxVal) && maxVal > 0 && maxVal <= 100 && sec > maxVal) ? maxVal : sec;
        var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        if (setter) setter.call(r, String(targetVal));
        else r.value = String(targetVal);
        r.dispatchEvent(new Event('input', { bubbles: true }));
        r.dispatchEvent(new Event('change', { bubbles: true }));
        break;
      }
    }
  } catch (e) {}
})();
"""
        }

        @Volatile
        private var instance: RadioBackgroundHost? = null

        fun getInstance(context: Context): RadioBackgroundHost {
            return instance ?: synchronized(this) {
                instance ?: RadioBackgroundHost(context).also { instance = it }
            }
        }

        fun pauseIfInitialized() {
            instance?.stopBackgroundPlayback()
        }

        fun pauseForLocalPlaybackIfInitialized() {
            instance?.stopForLocalPlayback()
        }

        fun stopForLocalPlaybackIfInitialized() {
            instance?.stopForLocalPlayback()
        }

        fun isRadioPlayingNow(): Boolean {
            val host = instance ?: return false
            return host.userExplicitlyStartedRadio && host._state.value.isRadioPlaying
        }

        fun keepAliveAfterTaskRemovedIfInitialized() {
            val host = instance ?: return
            if (!host.userExplicitlyStartedRadio || !host._state.value.isRadioPlaying) return
            host.mainHandler.post {
                if (host.userExplicitlyStartedRadio && host._state.value.isRadioPlaying) {
                    host.webView?.let { wv ->
                        wv.keepAliveInBackground = true
                        wv.dispatchWindowVisibilityChanged(View.VISIBLE)
                        wv.onResume()
                        wv.resumeTimers()
                    }
                }
            }
        }
    }
}
