package com.kexora.soniclet.equalizer

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.PlaybackParameters
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.kexora.soniclet.audio.*
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import com.kexora.soniclet.lyrics.LyricsUtil
import com.kexora.soniclet.player.*
import com.kexora.soniclet.utils.AudioInspectorUtil
import com.kexora.soniclet.utils.AudioSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 10-band equalizer, bass boost, virtualizer, reverb, 8D audio, sleep timer,
 * lyrics engine, audio codec inspector, and Kari/theme settings for [MusicViewModel].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AudioFxViewModel(
    private val vm: MusicViewModel,
    private val scope: CoroutineScope
) {
    private var lastSetupSessionId: Int = -1
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var sleepTimerJob: Job? = null

    fun releaseEffects() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        try { bassBoost?.release() } catch (_: Exception) {}
        bassBoost = null
        try { virtualizer?.release() } catch (_: Exception) {}
        virtualizer = null
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        try { reverb?.release() } catch (_: Exception) {}
        reverb = null
        lastSetupSessionId = -1
    }

    fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0) return
        if (sessionId == lastSetupSessionId && equalizer != null) return
        lastSetupSessionId = sessionId
        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = vm._uiState.value.isEqEnabled
            }
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            val levels = (0 until numBands).map { band ->
                equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
            }
            val freqs = (0 until numBands).map { band ->
                val freqMilliHertz = equalizer?.getCenterFreq(band.toShort()) ?: 0
                if (freqMilliHertz >= 1000000) {
                    "${freqMilliHertz / 1000000} kHz"
                } else if (freqMilliHertz >= 100000) {
                    "${String.format("%.1f", freqMilliHertz / 1000000f)} kHz"
                } else {
                    "${freqMilliHertz / 1000} Hz"
                }
            }
            vm._uiState.update { it.copy(eqBandLevels = levels, eqBandFrequencies = if (freqs.isNotEmpty()) freqs else it.eqBandFrequencies) }
        } catch (_: Exception) {}

        try {
            bassBoost?.release()
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = vm._uiState.value.isBassBoostEnabled
                if (strengthSupported) {
                    setStrength(vm._uiState.value.bassBoostLevel.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {}

        try {
            virtualizer?.release()
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = vm._uiState.value.isVirtualizerEnabled
                if (strengthSupported) {
                    setStrength(vm._uiState.value.virtualizerLevel.coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {}

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                val boostPercentage = vm._uiState.value.volumeBoostLevel.coerceIn(100, 300)
                val targetGainMb = ((boostPercentage - 100) / 200f * 6000).toInt()
                setTargetGain(targetGainMb)
                enabled = vm._uiState.value.isVolumeBoostEnabled && (targetGainMb > 0)
            }
        } catch (_: Exception) {}

        try {
            reverb?.release()
            reverb = PresetReverb(0, sessionId).apply {
                enabled = vm._uiState.value.isReverbEnabled
                preset = vm._uiState.value.reverbPreset
            }
            vm.exoPlayer.setAuxEffectInfo(AuxEffectInfo(reverb!!.id, 1f))
        } catch (_: Exception) {}
    }

    fun setEqEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isEqEnabled = enabled) }
        try { equalizer?.enabled = enabled } catch (_: Exception) {}
    }

    fun setReverbEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isReverbEnabled = enabled) }
        try {
            val sessionId = vm._uiState.value.audioSessionId
            if (enabled) {
                if (reverb == null && sessionId > 0) {
                    reverb = PresetReverb(0, sessionId)
                }
                reverb?.enabled = true
                reverb?.preset = vm._uiState.value.reverbPreset
                reverb?.let {
                    vm.exoPlayer.setAuxEffectInfo(AuxEffectInfo(it.id, 1f))
                }
            } else {
                reverb?.enabled = false
                vm.exoPlayer.setAuxEffectInfo(AuxEffectInfo(0, 0f))
            }
        } catch (_: Exception) {}
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isBassBoostEnabled = enabled) }
        try {
            bassBoost?.enabled = enabled
            if (enabled && bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(vm._uiState.value.bassBoostLevel.coerceIn(0, 1000).toShort())
            }
        } catch (_: Exception) {}
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isVirtualizerEnabled = enabled) }
        try {
            virtualizer?.enabled = enabled
            if (enabled && virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(vm._uiState.value.virtualizerLevel.coerceIn(0, 1000).toShort())
            }
        } catch (_: Exception) {}
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isVolumeBoostEnabled = enabled) }
        try {
            if (enabled) {
                val boostPercentage = vm._uiState.value.volumeBoostLevel.coerceIn(100, 300)
                loudnessEnhancer?.enabled = (boostPercentage > 100)
            } else {
                loudnessEnhancer?.enabled = false
            }
        } catch (_: Exception) {}
    }

    fun setDolbyEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isDolbyEnabled = enabled) }
        try {
            if (enabled && virtualizer?.strengthSupported == true && !vm._uiState.value.isVirtualizerEnabled) {
                virtualizer?.enabled = true
                virtualizer?.setStrength(750.toShort())
            } else if (!enabled && !vm._uiState.value.isVirtualizerEnabled) {
                virtualizer?.enabled = false
            }
        } catch (_: Exception) {}
    }

    fun setLosslessEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isLosslessEnabled = enabled) }
    }

    fun setBassVibrationEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(isBassVibrationEnabled = enabled) }
        vm.prefs.edit().putBoolean("bass_vibration_enabled", enabled).apply()
        vm.bassVibrationManager.updateSettings(
            enabled = enabled,
            strength = vm._uiState.value.bassVibrationStrength,
            mode = vm._uiState.value.bassVibrationMode,
            isPlaying = vm.exoPlayer.isPlaying,
            audioSessionId = vm._uiState.value.audioSessionId
        )
        if (enabled) {
            vm.bassVibrationManager.testVibrate(vm._uiState.value.bassVibrationStrength, vm._uiState.value.bassVibrationMode)
        }
    }

    fun setBassVibrationStrength(strength: Int) {
        val safe = strength.coerceIn(1, 100)
        vm._uiState.update { it.copy(bassVibrationStrength = safe) }
        vm.prefs.edit().putInt("bass_vibration_strength", safe).apply()
        vm.bassVibrationManager.updateSettings(
            enabled = vm._uiState.value.isBassVibrationEnabled,
            strength = safe,
            mode = vm._uiState.value.bassVibrationMode,
            isPlaying = vm.exoPlayer.isPlaying,
            audioSessionId = vm._uiState.value.audioSessionId
        )
    }

    fun setBassVibrationMode(mode: String) {
        vm._uiState.update { it.copy(bassVibrationMode = mode) }
        vm.prefs.edit().putString("bass_vibration_mode", mode).apply()
        vm.bassVibrationManager.updateSettings(
            enabled = vm._uiState.value.isBassVibrationEnabled,
            strength = vm._uiState.value.bassVibrationStrength,
            mode = mode,
            isPlaying = vm.exoPlayer.isPlaying,
            audioSessionId = vm._uiState.value.audioSessionId
        )
        vm.bassVibrationManager.testVibrate(vm._uiState.value.bassVibrationStrength, mode)
    }

    fun testBassVibration() {
        vm.bassVibrationManager.testVibrate(vm._uiState.value.bassVibrationStrength, vm._uiState.value.bassVibrationMode)
    }

    fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())
            vm._uiState.update { currentState ->
                val newLevels = currentState.eqBandLevels.toMutableList()
                if (bandIndex in newLevels.indices) {
                    newLevels[bandIndex] = level
                }
                currentState.copy(eqBandLevels = newLevels, selectedEqPresetName = "Custom")
            }
        } catch (_: Exception) {}
    }

    fun setBassBoostLevel(level: Int) {
        val safeLvl = level.coerceIn(0, 1000)
        val autoEnable = safeLvl > 0 || vm._uiState.value.isBassBoostEnabled
        vm._uiState.update { it.copy(bassBoostLevel = safeLvl, isBassBoostEnabled = autoEnable, selectedEqPresetName = "Custom") }
        try {
            bassBoost?.enabled = autoEnable
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(safeLvl.toShort())
            }
        } catch (_: Exception) {}
    }

    fun setVocalBoostLevel(level: Int) {
        val safeLvl = level.coerceIn(-1000, 1000)
        vm._uiState.update { it.copy(vocalBoostLevel = safeLvl, isEqEnabled = true, selectedEqPresetName = "Custom") }
        try {
            equalizer?.enabled = true
            val bandIndex = 2
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            if (bandIndex in 0 until numBands) {
                val dbLevel = (safeLvl * 1.5f).toInt().coerceIn(-1500, 1500)
                equalizer?.setBandLevel(bandIndex.toShort(), dbLevel.toShort())
            }
        } catch (_: Exception) {}
    }

    fun setTrebleBoostLevel(level: Int) {
        val safeLvl = level.coerceIn(0, 1000)
        vm._uiState.update { it.copy(trebleBoostLevel = safeLvl, isEqEnabled = true, selectedEqPresetName = "Custom") }
        try {
            equalizer?.enabled = true
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            if (numBands > 0) {
                val topBand = (numBands - 1).toShort()
                val dbLevel = (safeLvl * 1.5f).toInt().coerceIn(0, 1500)
                equalizer?.setBandLevel(topBand, dbLevel.toShort())
            }
        } catch (_: Exception) {}
    }

    fun setVirtualizerLevel(level: Int) {
        val safeLvl = level.coerceIn(0, 1000)
        val autoEnable = safeLvl > 0 || vm._uiState.value.isVirtualizerEnabled
        vm._uiState.update { it.copy(virtualizerLevel = safeLvl, isVirtualizerEnabled = autoEnable, selectedEqPresetName = "Custom") }
        try {
            virtualizer?.enabled = autoEnable
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(safeLvl.toShort())
            }
        } catch (_: Exception) {}
    }

    fun setVolumeBoostLevel(level: Int) {
        val safeLvl = level.coerceIn(100, 300)
        val autoEnable = safeLvl > 100
        vm._uiState.update { it.copy(volumeBoostLevel = safeLvl, isVolumeBoostEnabled = autoEnable) }
        val targetGainMb = ((safeLvl - 100) / 200f * 6000).toInt()
        try {
            if (loudnessEnhancer == null && vm._uiState.value.audioSessionId > 0) {
                loudnessEnhancer = LoudnessEnhancer(vm._uiState.value.audioSessionId)
            }
            loudnessEnhancer?.apply {
                setTargetGain(targetGainMb)
                enabled = autoEnable
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setReverbPreset(preset: Short) {
        val autoEnable = (preset > 0)
        vm._uiState.update { it.copy(reverbPreset = preset, isReverbEnabled = autoEnable) }
        try {
            val sessionId = vm._uiState.value.audioSessionId
            if (reverb == null && sessionId > 0) {
                reverb = PresetReverb(0, sessionId)
            }
            reverb?.enabled = autoEnable
            if (autoEnable && reverb != null) {
                reverb?.preset = preset
                reverb?.let {
                    vm.exoPlayer.setAuxEffectInfo(AuxEffectInfo(it.id, 1f))
                }
            } else {
                vm.exoPlayer.setAuxEffectInfo(AuxEffectInfo(0, 0f))
            }
        } catch (_: Exception) {}
    }

    fun applyPreset(name: String, levels: List<Int>, bass: Int, vocal: Int, treble: Int, virt: Int) {
        val numBands = equalizer?.numberOfBands?.toInt() ?: levels.size
        val safeLevels = if (levels.size < numBands) {
            levels + List(numBands - levels.size) { 0 }
        } else {
            levels.take(numBands)
        }
        val shouldEnableBass = bass > 0 || vm._uiState.value.isBassBoostEnabled
        val shouldEnableVirt = virt > 0 || vm._uiState.value.isVirtualizerEnabled
        vm._uiState.update {
            it.copy(
                isEqEnabled = true,
                selectedEqPresetName = name,
                eqBandLevels = safeLevels,
                isBassBoostEnabled = shouldEnableBass,
                isVirtualizerEnabled = shouldEnableVirt,
                bassBoostLevel = bass.coerceIn(0, 1000),
                vocalBoostLevel = vocal.coerceIn(-1000, 1000),
                trebleBoostLevel = treble.coerceIn(0, 1000),
                virtualizerLevel = virt.coerceIn(0, 1000)
            )
        }
        try {
            equalizer?.enabled = true
            safeLevels.forEachIndexed { index, lvl ->
                equalizer?.setBandLevel(index.toShort(), lvl.toShort())
            }
            if (shouldEnableBass) {
                bassBoost?.enabled = true
                if (bassBoost?.strengthSupported == true) {
                    bassBoost?.setStrength(bass.coerceIn(0, 1000).toShort())
                }
            }
            if (shouldEnableVirt) {
                virtualizer?.enabled = true
                if (virtualizer?.strengthSupported == true) {
                    virtualizer?.setStrength(virt.coerceIn(0, 1000).toShort())
                }
            }
            if (name.contains("Bhais", ignoreCase = true) || bass > 500) {
                try {
                    val vibrator = vm.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(120)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun set8dAudioEnabled(enabled: Boolean) {
        vm._uiState.update { it.copy(is8dAudioEnabled = enabled) }
        vm.eightDProcessor.is8dEnabled = enabled
        if (enabled) {
            setVirtualizerLevel(1000)
        } else {
            setVirtualizerLevel(0)
        }
    }

    fun resetAllAudioFx() {
        val numBands = equalizer?.numberOfBands?.toInt() ?: vm._uiState.value.eqBandLevels.size.coerceAtLeast(5)
        val flatLevels = List(numBands) { 0 }
        applyPreset("Flat / Normal", flatLevels, 0, 0, 0, 0)
        setBassBoostEnabled(false)
        setBassBoostLevel(0)
        setVirtualizerEnabled(false)
        setVirtualizerLevel(0)
        set8dAudioEnabled(false)
        setDolbyEnabled(false)
        setReverbEnabled(false)
        setReverbPreset(0.toShort())
        setVolumeBoostEnabled(false)
        setVolumeBoostLevel(100)
        setBassVibrationEnabled(false)
    }

    fun resetEqualizerToDefault() = resetAllAudioFx()

    fun saveCustomPreset(name: String) {
        if (name.isBlank()) return
        val current = vm._uiState.value
        val newPreset = CustomEqPreset(
            name = name.trim(),
            levels = current.eqBandLevels,
            bass = current.bassBoostLevel,
            vocal = current.vocalBoostLevel,
            treble = current.trebleBoostLevel,
            virtualizer = current.virtualizerLevel
        )
        val filtered = current.customEqPresets.filterNot { it.name.equals(name.trim(), ignoreCase = true) }
        val updated = filtered + newPreset
        vm._uiState.update { it.copy(customEqPresets = updated, selectedEqPresetName = name.trim()) }
    }

    fun deleteCustomPreset(name: String) {
        vm._uiState.update { currentState ->
            val updated = currentState.customEqPresets.filterNot { it.name.equals(name, ignoreCase = true) }
            val nextPreset = if (currentState.selectedEqPresetName.equals(name, ignoreCase = true)) "Flat / Normal" else currentState.selectedEqPresetName
            currentState.copy(customEqPresets = updated, selectedEqPresetName = nextPreset)
        }
    }

    // SMART SLEEP TIMER ENGINE
    fun setFinishTrackBeforeSleep(enabled: Boolean) {
        vm._uiState.update { it.copy(finishTrackBeforeSleep = enabled) }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        vm.exoPlayer.volume = 1.0f

        if (option == SleepTimerOption.OFF) {
            vm._uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L) }
            return
        }

        if (option == SleepTimerOption.END_OF_SONG) {
            vm._uiState.update { it.copy(sleepTimerOption = SleepTimerOption.END_OF_SONG, sleepTimerRemainingMs = 0L) }
            return
        }

        val totalMs = if (option == SleepTimerOption.CUSTOM) {
            vm._uiState.value.customSleepTimerMinutes * 60 * 1000L
        } else {
            option.minutes * 60 * 1000L
        }

        vm._uiState.update { it.copy(sleepTimerOption = option, sleepTimerRemainingMs = totalMs) }

        sleepTimerJob = scope.launch {
            var remaining = totalMs
            val step = 1000L
            while (remaining > 0L) {
                delay(step)
                if (vm.exoPlayer.isPlaying) {
                    remaining -= step
                    val currentRem = remaining.coerceAtLeast(0L)
                    vm._uiState.update { it.copy(sleepTimerRemainingMs = currentRem) }

                    if (currentRem in 1L..60000L && !vm._uiState.value.finishTrackBeforeSleep) {
                        val fadeRatio = (currentRem.toFloat() / 60000f).coerceIn(0f, 1f)
                        vm.exoPlayer.volume = fadeRatio
                    }
                }
            }

            if (remaining <= 0L) {
                if (vm._uiState.value.finishTrackBeforeSleep) {
                    vm.exoPlayer.volume = 1.0f
                    vm._uiState.update { it.copy(sleepTimerOption = SleepTimerOption.END_OF_SONG, sleepTimerRemainingMs = 0L) }
                } else {
                    if (vm.exoPlayer.isPlaying) {
                        vm.exoPlayer.pause()
                    }
                    vm.exoPlayer.volume = 1.0f
                    vm._uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L) }
                }
            }
        }
    }

    fun setCustomSleepTimer(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 240)
        vm._uiState.update { it.copy(customSleepTimerMinutes = safeMinutes) }
        setSleepTimer(SleepTimerOption.CUSTOM)
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = (Math.round(speed * 100f) / 100f).coerceIn(0.25f, 3.0f)
        vm._uiState.update { it.copy(playbackSpeed = safeSpeed) }
        try {
            vm.exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, vm._uiState.value.playbackPitch)
        } catch (_: Exception) {}
    }

    fun setPlaybackPitch(pitch: Float) {
        val safePitch = (Math.round(pitch * 100f) / 100f).coerceIn(0.25f, 3.0f)
        vm._uiState.update { it.copy(playbackPitch = safePitch) }
        try {
            vm.exoPlayer.playbackParameters = PlaybackParameters(vm._uiState.value.playbackSpeed, safePitch)
        } catch (_: Exception) {}
    }

    // OFFLINE LYRICS & TIME-SYNCED KARAOKE ENGINE
    fun loadLyricsForSong(song: Song) {
        scope.launch(Dispatchers.IO) {
            vm._uiState.update { it.copy(isLyricsLoading = true) }
            val result = LyricsUtil.getLyrics(vm.context, song)
            if (result != null && result.lines.isNotEmpty()) {
                val curPos = vm.currentPosition.value + vm._uiState.value.lyricsSyncOffsetMs
                var activeIdx = -1
                for (i in result.lines.indices) {
                    if (result.lines[i].timeMs <= curPos) {
                        activeIdx = i
                    } else break
                }
                vm._uiState.update {
                    it.copy(
                        currentLyrics = result.lines,
                        lyricsSourceType = result.source,
                        isLyricsLoading = false,
                        activeLyricIndex = activeIdx.coerceAtLeast(0)
                    )
                }
            } else {
                vm._uiState.update {
                    it.copy(
                        currentLyrics = emptyList(),
                        lyricsSourceType = "",
                        isLyricsLoading = false,
                        activeLyricIndex = -1
                    )
                }
            }
        }
    }

    fun importLyricsFromUri(song: Song, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = vm.context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    LyricsUtil.saveLyrics(vm.context, song.id, text)
                    loadLyricsForSong(song)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        vm._uiState.update {
            val newOffset = (it.lyricsSyncOffsetMs + deltaMs).coerceIn(-15000L, 15000L)
            it.copy(lyricsSyncOffsetMs = newOffset)
        }
    }

    fun resetLyricsOffset() {
        vm._uiState.update { it.copy(lyricsSyncOffsetMs = 0L) }
    }

    fun toggleKaraokeMode() {
        vm._uiState.update { it.copy(isKaraokeModeEnabled = !it.isKaraokeModeEnabled) }
    }

    fun clearImportedLyrics(song: Song) {
        scope.launch(Dispatchers.IO) {
            LyricsUtil.clearCustomLyrics(vm.context, song.id)
            loadLyricsForSong(song)
        }
    }

    // AUDIO CODEC & BITRATE INSPECTOR
    fun inspectAudioSpecsForSong(song: Song) {
        scope.launch(Dispatchers.IO) {
            val specs = AudioInspectorUtil.getAudioSpecs(vm.context, song)
            withContext(Dispatchers.Main) {
                vm._uiState.update { current ->
                    val newCache = current.songSpecsCache.toMutableMap()
                    newCache[song.id] = specs
                    if (current.currentSong?.id == song.id) {
                        current.copy(
                            currentSongAudioSpecs = specs,
                            songSpecsCache = newCache
                        )
                    } else {
                        current.copy(songSpecsCache = newCache)
                    }
                }
            }
        }
    }

    fun getAudioSpecs(song: Song): AudioSpecs {
        val cached = vm._uiState.value.songSpecsCache[song.id]
        if (cached != null) return cached
        inspectAudioSpecsForSong(song)
        return AudioInspectorUtil.getAudioSpecs(vm.context, song)
    }

    fun extractKariPalette(song: Song) {
        if (song.albumArtUri == null) {
            vm._uiState.update { it.copy(kariDominantColor = null, kariVibrantColor = null, kariDarkMutedColor = null) }
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(vm.context)
                    .data(song.albumArtUri)
                    .allowHardware(false)
                    .size(64, 64)
                    .build()
                val result = vm.context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val dom = palette.getDominantColor(Color.TRANSPARENT)
                        val vib = palette.getVibrantColor(palette.getMutedColor(dom))
                        val darkMuted = palette.getDarkMutedColor(palette.getDarkVibrantColor(dom))

                        val dominantColor = if (dom != Color.TRANSPARENT) dom.toLong() and 0xFFFFFFFF else null
                        val vibrantColor = if (vib != Color.TRANSPARENT) vib.toLong() and 0xFFFFFFFF else null
                        val darkMutedColor = if (darkMuted != Color.TRANSPARENT) darkMuted.toLong() and 0xFFFFFFFF else null

                        vm._uiState.update {
                            it.copy(
                                kariDominantColor = dominantColor,
                                kariVibrantColor = vibrantColor,
                                kariDarkMutedColor = darkMutedColor
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                vm._uiState.update { it.copy(kariDominantColor = null, kariVibrantColor = null, kariDarkMutedColor = null) }
            }
        }
    }

    // THEME, KARI, GESTURE & PLAYBACK PREFERENCE SETTERS
    fun setAccentColor(colorValue: Long) = vm.audioRepository.setAccentColor(colorValue)
    fun setCustomBackgroundUri(uri: String?) = vm.audioRepository.setCustomBackgroundUri(uri)
    fun setBackgroundMode(mode: String) = vm.audioRepository.setBackgroundMode(mode)
    fun setBgImageBlur(blur: Float) = vm.audioRepository.setBgImageBlur(blur)
    fun setBgImageDim(dim: Float) = vm.audioRepository.setBgImageDim(dim)
    fun setSolidBgColor(color: Long) = vm.audioRepository.setSolidBgColor(color)
    fun setSolidBgOpacity(opacity: Float) = vm.audioRepository.setSolidBgOpacity(opacity)
    fun setGradientPresetIndex(index: Int) = vm.audioRepository.setGradientPresetIndex(index)
    fun setCustomGradientColors(colors: List<Long>) = vm.audioRepository.setCustomGradientColors(colors)
    fun setGradientIntensity(intensity: Float) = vm.audioRepository.setGradientIntensity(intensity)
    fun setNowPlayingLayout(layout: String) = vm.audioRepository.setNowPlayingLayout(layout)
    fun setCornerRadiusScale(scale: Float) = vm.audioRepository.setCornerRadiusScale(scale)
    fun setFontStyle(style: String) = vm.audioRepository.setFontStyle(style)
    fun setMotionLevel(level: String) = vm.audioRepository.setMotionLevel(level)
    fun setGradientBlur(blur: Float) = vm.audioRepository.setGradientBlur(blur)
    fun setAppTheme(theme: AppTheme) = vm.audioRepository.setAppThemeName(theme.name)
    fun setKariModeEnabled(enabled: Boolean) = vm.audioRepository.setKariModeEnabled(enabled)
    fun setKariBlur(blur: Float) = vm.audioRepository.setKariBlur(blur)
    fun setKariOpacity(opacity: Float) = vm.audioRepository.setKariOpacity(opacity)
    fun setShakeEnabled(enabled: Boolean) = vm.audioRepository.setShakeEnabled(enabled)
    fun setTwoFingerTapEnabled(enabled: Boolean) = vm.audioRepository.setTwoFingerTapEnabled(enabled)
    fun setTwoFingerTapAction(action: String) = vm.audioRepository.setTwoFingerTapAction(action)
    fun setThreeFingerSwipeEnabled(enabled: Boolean) = vm.audioRepository.setThreeFingerSwipeEnabled(enabled)
    fun setThreeFingerSwipeAction(action: String) = vm.audioRepository.setThreeFingerSwipeAction(action)
    fun setVolumeEdgeSwipeEnabled(enabled: Boolean) = vm.audioRepository.setVolumeEdgeSwipeEnabled(enabled)
    fun setLongPressDragSeekEnabled(enabled: Boolean) = vm.audioRepository.setLongPressDragSeekEnabled(enabled)
    fun setPlayTogetherEnabled(enabled: Boolean) = vm.audioRepository.setPlayTogetherEnabled(enabled)
    fun setPlayTogetherFadeEnabled(enabled: Boolean) = vm.audioRepository.setPlayTogetherFadeEnabled(enabled)
    fun setOneFingerSwipeBackEnabled(enabled: Boolean) = vm.audioRepository.setOneFingerSwipeBackEnabled(enabled)
    fun setTwoFingerSwipeHomeEnabled(enabled: Boolean) = vm.audioRepository.setTwoFingerSwipeHomeEnabled(enabled)
    fun setMiniPlayerGesturesEnabled(enabled: Boolean) = vm.audioRepository.setMiniPlayerGesturesEnabled(enabled)
    fun setFlipToPauseEnabled(enabled: Boolean) = vm.audioRepository.setFlipToPauseEnabled(enabled)
    fun setFlipToPauseAction(action: String) = vm.audioRepository.setFlipToPauseAction(action)
    fun setWristTwistEnabled(enabled: Boolean) = vm.audioRepository.setWristTwistEnabled(enabled)
    fun setWristTwistAction(action: String) = vm.audioRepository.setWristTwistAction(action)
    fun setPocketModeEnabled(enabled: Boolean) = vm.audioRepository.setPocketModeEnabled(enabled)
    fun setPocketModeAction(action: String) = vm.audioRepository.setPocketModeAction(action)
    fun setShakeAction(action: String) = vm.audioRepository.setShakeAction(action)
    fun setTiltToFastForwardEnabled(enabled: Boolean) = vm.audioRepository.setTiltToFastForwardEnabled(enabled)
    fun setAirWaveEnabled(enabled: Boolean) = vm.audioRepository.setAirWaveEnabled(enabled)
    fun setAirWaveAction(action: String) = vm.audioRepository.setAirWaveAction(action)
    fun setPickUpToPlayEnabled(enabled: Boolean) = vm.audioRepository.setPickUpToPlayEnabled(enabled)
    fun setCrossfadeEnabled(enabled: Boolean) = vm.audioRepository.setCrossfadeEnabled(enabled)
    fun setAutoResumeOnPlugEnabled(enabled: Boolean) = vm.audioRepository.setAutoResumeOnPlugEnabled(enabled)
    fun setAutoPlayOnBluetoothEnabled(enabled: Boolean) = vm.audioRepository.setAutoPlayOnBluetoothEnabled(enabled)
    fun setSingleClickAction(action: String) = vm.audioRepository.setSingleClickAction(action)
    fun setDoubleClickAction(action: String) = vm.audioRepository.setDoubleClickAction(action)
    fun setTripleClickAction(action: String) = vm.audioRepository.setTripleClickAction(action)
    fun setHearingSafetyEnabled(enabled: Boolean) = vm.audioRepository.setHearingSafetyEnabled(enabled)
    fun setGaplessPlaybackEnabled(enabled: Boolean) = vm.audioRepository.setGaplessPlaybackEnabled(enabled)
    fun setCrossfadeDuration(seconds: Int) = vm.audioRepository.setCrossfadeDuration(seconds)
    fun setResumeLastPositionEnabled(enabled: Boolean) = vm.audioRepository.setResumeLastPositionEnabled(enabled)
    fun setFirstLaunch(isFirst: Boolean) = vm.audioRepository.setFirstLaunch(isFirst)
    fun setMohModeActivated(activated: Boolean) = vm.audioRepository.setMohModeActivated(activated)

    fun refreshSpecialCelebrationDate() {
        val special = if (vm._uiState.value.isMohModeActivated) MuhLoveRegistry.getTodaySpecialDate() else null
        vm._uiState.update { it.copy(todaySpecialDate = special) }
    }
}
