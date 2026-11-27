package com.kexora.soniclet.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.hypot
import kotlin.math.max

/**
 * Intelligent Haptic Bass & DJ Vibration Engine.
 *
 * Converts audio bass kicks, sub-bass frequencies, and DJ drop rhythms
 * into physical tactile vibrations through the device's haptic motor.
 * Uses real-time hardware Visualizer FFT analysis when available,
 * seamlessly paired with a rhythmic beat sync engine for consistent,
 * punchy tactile feedback on any device.
 */
class BassVibrationManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var visualizer: Visualizer? = null
    private var isVisualizerActive = false
    private var scope: CoroutineScope? = null
    private var beatSyncJob: Job? = null

    private var isEnabled = false
    private var isPlaying = false
    private var strength = 80 // 1 to 100
    private var mode = "DJ Club Bass" // "DJ Club Bass", "Beat Drop Subwoofer", "Extreme Rave"

    private var lastVibrateTime = 0L
    private var rollingBassAverage = 20.0
    private var currentAudioSessionId: Int = 0

    fun updateSettings(
        enabled: Boolean,
        strength: Int,
        mode: String,
        isPlaying: Boolean,
        audioSessionId: Int
    ) {
        val wasEnabled = this.isEnabled
        val wasPlaying = this.isPlaying
        val oldSessionId = this.currentAudioSessionId

        this.isEnabled = enabled
        this.strength = strength.coerceIn(1, 100)
        this.mode = mode
        this.isPlaying = isPlaying
        this.currentAudioSessionId = audioSessionId

        if (!enabled || !isPlaying) {
            stop()
        } else {
            if (!wasEnabled || !wasPlaying || oldSessionId != audioSessionId || scope == null) {
                start(audioSessionId)
            }
        }
    }

    fun start(audioSessionId: Int) {
        stop()
        if (!isEnabled || !isPlaying) return

        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Try initializing hardware visualizer if session ID is valid
        if (audioSessionId > 0) {
            try {
                setupVisualizer(audioSessionId)
            } catch (e: Exception) {
                Log.w("BassVibration", "Visualizer init fallback to beat-sync engine: ${e.message}")
            }
        }

        // Start beat-sync fallback or hybrid rhythm pulse
        startRhythmEngine()
    }

    private fun setupVisualizer(sessionId: Int) {
        try {
            val captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
            visualizer = Visualizer(sessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || !isEnabled || !isPlaying) return
                        analyzeFftBass(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            isVisualizerActive = true
        } catch (e: Exception) {
            isVisualizerActive = false
            visualizer = null
        }
    }

    private fun analyzeFftBass(fft: ByteArray) {
        if (fft.size < 8) return

        // Extract low-frequency bins (0-150Hz bass kick & sub-bass range)
        val r0 = fft[2].toDouble()
        val i0 = fft[3].toDouble()
        val r1 = fft[4].toDouble()
        val i1 = fft[5].toDouble()
        val r2 = fft[6].toDouble()
        val i2 = fft[7].toDouble()

        val subBass = hypot(r0, i0)
        val kickBass = hypot(r1, i1)
        val lowMid = hypot(r2, i2)

        val currentBassEnergy = (subBass * 1.5) + (kickBass * 1.2) + (lowMid * 0.5)

        // Rolling exponential moving average
        rollingBassAverage = (rollingBassAverage * 0.85) + (currentBassEnergy * 0.15)

        val thresholdMultiplier = when (mode) {
            "Beat Drop Subwoofer" -> 1.7
            "Extreme Rave" -> 1.3
            else -> 1.5 // "DJ Club Bass"
        }

        val dynamicThreshold = max(rollingBassAverage * thresholdMultiplier, 25.0)

        if (currentBassEnergy > dynamicThreshold) {
            val now = System.currentTimeMillis()
            val minCooldown = when (mode) {
                "Extreme Rave" -> 110L
                "Beat Drop Subwoofer" -> 180L
                else -> 140L
            }

            if (now - lastVibrateTime >= minCooldown) {
                lastVibrateTime = now
                val intensity = ((currentBassEnergy / (dynamicThreshold * 1.8)).coerceIn(0.5, 1.2)).toFloat()
                pulseBassKick(intensity)
            }
        }
    }

    private fun startRhythmEngine() {
        beatSyncJob?.cancel()
        beatSyncJob = scope?.launch {
            // If visualizer is active, rhythm engine acts as periodic sub-bass reinforcement on drops
            // If visualizer is inactive, it drives the full DJ beat vibration pattern smoothly
            var step = 0
            while (isActive && isEnabled && isPlaying) {
                val tempoIntervalMs = when (mode) {
                    "Extreme Rave" -> 220L // Fast energetic drops ~136 BPM
                    "Beat Drop Subwoofer" -> 520L // Heavy slower sub-bass drops ~115 BPM
                    else -> 460L // Classic club DJ 4/4 ~130 BPM
                }

                delay(tempoIntervalMs)

                val now = System.currentTimeMillis()
                // Only fire if visualizer hasn't triggered recently
                if (now - lastVibrateTime >= (tempoIntervalMs * 0.75).toLong()) {
                    lastVibrateTime = now
                    step = (step + 1) % 4
                    val isAccentBeat = (step == 0 || step == 2)
                    val factor = if (isAccentBeat) 1.0f else 0.75f
                    pulseBassKick(factor)
                }
            }
        }
    }

    /**
     * Physically triggers a crisp, punchy bass kick vibration.
     */
    fun pulseBassKick(intensityFactor: Float = 1.0f) {
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            val baseDurationMs = when (mode) {
                "Beat Drop Subwoofer" -> 65L
                "Extreme Rave" -> 40L
                else -> 48L
            }
            val duration = (baseDurationMs * intensityFactor).toLong().coerceIn(25L, 85L)

            val calculatedAmplitude = ((strength / 100f) * 255f * intensityFactor.coerceIn(0.6f, 1.0f)).toInt().coerceIn(30, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, calculatedAmplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // Ignore vibration hardware hiccups
        }
    }

    /**
     * Preview vibration for user testing in settings.
     */
    fun testVibrate(testStrength: Int = strength, testMode: String = mode) {
        if (vibrator == null || !vibrator.hasVibrator()) return
        scope?.cancel()
        val testScope = CoroutineScope(Dispatchers.Default)
        testScope.launch {
            val duration = when (testMode) {
                "Beat Drop Subwoofer" -> 60L
                "Extreme Rave" -> 35L
                else -> 45L
            }
            val amplitude = ((testStrength / 100f) * 255f).toInt().coerceIn(40, 255)

            // Pattern: THUMP ... THUMP-THUMP!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                delay(180)
                vibrator.vibrate(VibrationEffect.createOneShot(duration, (amplitude * 0.85f).toInt().coerceIn(30, 255)))
                delay(130)
                vibrator.vibrate(VibrationEffect.createOneShot(duration + 15, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, duration, 180, duration, 130, duration + 15), -1)
            }
        }
    }

    fun stop() {
        beatSyncJob?.cancel()
        beatSyncJob = null
        scope?.cancel()
        scope = null

        try {
            if (isVisualizerActive && visualizer != null) {
                visualizer?.enabled = false
                visualizer?.release()
                visualizer = null
                isVisualizerActive = false
            }
        } catch (e: Exception) {
            visualizer = null
            isVisualizerActive = false
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
    }
}
