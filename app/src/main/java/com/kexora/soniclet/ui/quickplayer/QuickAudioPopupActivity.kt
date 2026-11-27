package com.kexora.soniclet.ui.quickplayer

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.ui.theme.SonicletTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.DecimalFormat
import kotlin.math.sin

/**
 * Floating Audio Popup Player Activity.
 *
 * Designed specifically for quick previews from File Managers, WhatsApp, Downloads,
 * Voice Recorders, and Telegram without taking the user away from their current app!
 */
class QuickAudioPopupActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow transparent background overlay over calling app
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        audioUri = intent?.data

        setContent {
            SonicletTheme {
                QuickAudioPopupScreen(
                    uri = audioUri,
                    onClose = { finishAndClose() },
                    onOpenInFullApp = { uriToOpen ->
                        openInMainSonicletApp(uriToOpen)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        audioUri = intent.data
    }

    private fun finishAndClose() {
        releasePlayer()
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun openInMainSonicletApp(uri: Uri?) {
        releasePlayer()
        val fullAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(fullAppIntent)
        finish()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onPause() {
        super.onPause()
        // If activity loses focus completely and is finishing, release
        if (isFinishing) {
            releasePlayer()
        }
    }
}

data class ExternalAudioInfo(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val mimeType: String,
    val formatBadge: String,
    val fileSizeFormatted: String,
    val isVoiceRecording: Boolean
)

@Composable
fun QuickAudioPopupScreen(
    uri: Uri?,
    onClose: () -> Unit,
    onOpenInFullApp: (Uri?) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLooping by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isReady by remember { mutableStateOf(false) }

    // Extract rich metadata from Uri
    val audioInfo = remember(uri) {
        extractAudioDetails(context, uri)
    }

    // Initialize MediaPlayer
    DisposableEffect(uri) {
        if (uri != null) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(context, uri)
                    setOnPreparedListener { mp ->
                        durationMs = if (mp.duration > 0) mp.duration.toLong() else audioInfo.durationMs
                        isReady = true
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener { mp ->
                        if (!mp.isLooping) {
                            isPlaying = false
                            currentPositionMs = durationMs
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        errorMessage = "Cannot playback this file (code: $what, $extra)"
                        false
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                errorMessage = "Error opening file: ${e.localizedMessage}"
            }
        } else {
            errorMessage = "No audio file provided."
        }

        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
        }
    }

    // Position Ticker Coroutine
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isActive && isPlaying && !isUserScrubbing) {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        currentPositionMs = mp.currentPosition.toLong()
                    }
                } catch (e: Exception) {
                    // Ignore state errors
                }
            }
            delay(120)
        }
    }

    // Apply speed
    LaunchedEffect(playbackSpeed, isReady) {
        if (isReady && mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = playbackSpeed
                    mp.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Apply looping
    LaunchedEffect(isLooping, isReady) {
        if (isReady) {
            mediaPlayer?.isLooping = isLooping
        }
    }

    // Apply mute
    LaunchedEffect(isMuted, isReady) {
        if (isReady) {
            val vol = if (isMuted) 0f else 1f
            mediaPlayer?.setVolume(vol, vol)
        }
    }

    BackHandler {
        onClose()
    }

    // Floating Popup Root
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping outside closes the popup directly!
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClose()
            },
        contentAlignment = Alignment.Center
    ) {
        // Floating Card Container (Prevents click propagation to background)
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .padding(vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Consume clicks */ }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Header Row (Badges, Full App Action, Close Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Badge Chip
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (audioInfo.isVoiceRecording) {
                            Color(0xFF00B0FF).copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            0.5.dp,
                            if (audioInfo.isVoiceRecording) Color(0xFF00B0FF).copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (audioInfo.isVoiceRecording) Icons.Rounded.Mic else Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (audioInfo.isVoiceRecording) "Voice Recording" else "Quick Audio Popup",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "• ${audioInfo.formatBadge}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action Icons: Full App & Close
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Open in Full Soniclet App
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onOpenInFullApp(uri)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open in Full App",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close "X" Button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onClose()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Message if unplayable
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    // 2. Audio Title & File Details
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = audioInfo.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "${audioInfo.artist} • ${audioInfo.fileSizeFormatted}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Live Animated Waveform Visualizer
                    QuickPopupWaveformCanvas(
                        isPlaying = isPlaying,
                        progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        isVoiceRecording = audioInfo.isVoiceRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Scrubbing Slider
                    val displayPosition = if (isUserScrubbing) scrubPositionMs else currentPositionMs
                    val safeDuration = if (durationMs > 0) durationMs.toFloat() else 1f

                    Slider(
                        value = displayPosition.toFloat().coerceIn(0f, safeDuration),
                        onValueChange = { newPos ->
                            isUserScrubbing = true
                            scrubPositionMs = newPos.toLong()
                        },
                        onValueChangeFinished = {
                            mediaPlayer?.let { mp ->
                                try {
                                    mp.seekTo(scrubPositionMs.toInt())
                                    currentPositionMs = scrubPositionMs
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            isUserScrubbing = false
                        },
                        valueRange = 0f..safeDuration,
                        colors = SliderDefaults.colors(
                            thumbColor = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    // Time Row: Elapsed vs Total
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(displayPosition),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Main Playback Controls (Rewind, Play/Pause, Fast Forward, Loop, Mute)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Loop Button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isLooping = !isLooping
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isLooping) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Loop",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // -10s Rewind
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                mediaPlayer?.let { mp ->
                                    val target = (mp.currentPosition - 10000).coerceAtLeast(0)
                                    mp.seekTo(target)
                                    currentPositionMs = target.toLong()
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Hero Play / Pause Button
                        val playButtonScale by animateFloatAsState(
                            targetValue = if (isPlaying) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "play_scale"
                        )

                        Surface(
                            shape = CircleShape,
                            color = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(56.dp)
                                .scale(playButtonScale)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
                                )
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    mediaPlayer?.let { mp ->
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            if (currentPositionMs >= durationMs && durationMs > 0) {
                                                mp.seekTo(0)
                                                currentPositionMs = 0L
                                            }
                                            mp.start()
                                            isPlaying = true
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // +10s Fast Forward
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                mediaPlayer?.let { mp ->
                                    val target = (mp.currentPosition + 10000).coerceAtMost(durationMs.toInt())
                                    mp.seekTo(target)
                                    currentPositionMs = target.toLong()
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = "Forward 10s",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Mute Button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isMuted = !isMuted
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. Voice Recording Speed Multiplier Pills (0.75x, 1x, 1.25x, 1.5x, 2x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        speeds.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) {
                                    if (audioInfo.isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        playbackSpeed = speed
                                    }
                            ) {
                                Text(
                                    text = "${if (speed == 1.0f) "1" else speed.toString()}x",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 7. Bottom Exit / Dismiss Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClose()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Done / Tap outside to close",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.4.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Waveform Canvas for Quick Player
 */
@Composable
private fun QuickPopupWaveformCanvas(
    isPlaying: Boolean,
    progress: Float,
    isVoiceRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform_anim")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val baseHeights = remember {
        listOf(
            0.35f, 0.6f, 0.9f, 0.45f, 0.75f, 1.0f, 0.55f, 0.8f, 0.4f, 0.95f,
            0.7f, 0.3f, 0.85f, 0.5f, 0.9f, 0.65f, 0.4f, 0.8f, 1.0f, 0.6f,
            0.45f, 0.75f, 0.9f, 0.5f, 0.35f, 0.7f, 0.95f, 0.6f, 0.4f, 0.8f
        )
    }

    val primaryColor = if (isVoiceRecording) Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val totalBars = baseHeights.size
        val barSpacing = 3.dp.toPx()
        val totalSpacing = barSpacing * (totalBars - 1)
        val barWidth = ((size.width - totalSpacing) / totalBars).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f

        for (i in 0 until totalBars) {
            val barProgress = i.toFloat() / totalBars
            val isBarPlayed = barProgress <= progress

            val dynamicFactor = if (isPlaying) {
                0.7f + 0.3f * sin(wavePhase + i * 0.4f)
            } else {
                0.8f
            }

            val barHeight = (size.height * 0.85f * baseHeights[i] * dynamicFactor).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + barSpacing)
            val y = centerY - (barHeight / 2f)

            drawRoundRect(
                color = if (isBarPlayed) primaryColor else unplayedColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

/**
 * Format Milliseconds to MM:SS
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Extract rich audio metadata from external Content/File Uri
 */
private fun extractAudioDetails(context: Context, uri: Uri?): ExternalAudioInfo {
    if (uri == null) {
        return ExternalAudioInfo(
            title = "External Audio",
            artist = "Unknown Source",
            durationMs = 0L,
            mimeType = "audio/*",
            formatBadge = "AUDIO",
            fileSizeFormatted = "Unknown Size",
            isVoiceRecording = false
        )
    }

    var fileName = uri.lastPathSegment ?: "Audio File"
    var fileSizeFormatted = ""
    var durationMs = 0L
    var artist = "External File"
    var mimeType = context.contentResolver.getType(uri) ?: "audio/*"

    // Query ContentResolver for displayName and size
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIndex != -1) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > 0) {
                            fileSizeFormatted = formatFileSize(size)
                        }
                    }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { p ->
                val f = File(p)
                if (f.exists()) {
                    fileName = f.name
                    fileSizeFormatted = formatFileSize(f.length())
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // MediaMetadataRetriever
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (!extractedTitle.isNullOrBlank()) {
            fileName = extractedTitle
        }
        val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        if (!extractedArtist.isNullOrBlank()) {
            artist = extractedArtist
        }
        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (durStr != null) {
            durationMs = durStr.toLongOrNull() ?: 0L
        }
        val extractedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        if (!extractedMime.isNullOrBlank()) {
            mimeType = extractedMime
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {}
    }

    val lowerName = fileName.lowercase()
    val isVoice = lowerName.contains("rec") ||
            lowerName.contains("voice") ||
            lowerName.contains("ptt") ||
            lowerName.contains("audio_") ||
            lowerName.contains("wa") ||
            lowerName.endsWith(".opus") ||
            lowerName.endsWith(".amr") ||
            lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".aac")

    val formatBadge = when {
        lowerName.endsWith(".mp3") -> "MP3"
        lowerName.endsWith(".m4a") -> "M4A"
        lowerName.endsWith(".aac") -> "AAC"
        lowerName.endsWith(".wav") -> "WAV"
        lowerName.endsWith(".flac") -> "FLAC"
        lowerName.endsWith(".opus") -> "OPUS"
        lowerName.endsWith(".ogg") -> "OGG"
        lowerName.endsWith(".amr") -> "AMR"
        else -> "AUDIO"
    }

    return ExternalAudioInfo(
        title = fileName,
        artist = artist,
        durationMs = durationMs,
        mimeType = mimeType,
        formatBadge = formatBadge,
        fileSizeFormatted = if (fileSizeFormatted.isNotEmpty()) fileSizeFormatted else "Audio File",
        isVoiceRecording = isVoice
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
