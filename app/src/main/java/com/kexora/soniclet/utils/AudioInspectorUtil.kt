package com.kexora.soniclet.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.kexora.soniclet.database.Song
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Detailed technical audio specifications inspected from audio file headers and streams.
 */
data class AudioSpecs(
    val format: String,               // e.g. "FLAC", "MP3", "AAC", "WAV", "OGG", "ALAC", "OPUS"
    val fullCodecName: String,       // e.g. "Free Lossless Audio Codec", "MPEG-1 Audio Layer III"
    val bitrateKbps: Int,            // e.g. 320, 128, 256, 1411, 2814
    val sampleRateHz: Int,           // e.g. 44100, 48000, 96000, 192000
    val bitDepth: Int,               // e.g. 24, 16, 32
    val channels: Int,               // e.g. 2, 1, 6
    val channelLayout: String,       // e.g. "Stereo (2.0 ch)", "Mono (1.0 ch)"
    val isLossless: Boolean,         // true for FLAC, WAV, ALAC, DSD
    val isHiRes: Boolean,            // true if 24-bit, sampleRate >= 48kHz, or Lossless Hi-Fi
    val fileSizeFormatted: String,   // e.g. "28.4 MB"
    val fileSizeBytes: Long,         // e.g. 29779558
    val isVariableBitrate: Boolean,  // CBR vs VBR
    val containerExtension: String   // e.g. "flac", "mp3", "m4a"
) {
    val sampleRateFormatted: String
        get() {
            if (sampleRateHz <= 0) return "44.1 kHz"
            return if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000} kHz"
            } else {
                String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000f)
            }
        }

    val bitrateFormatted: String
        get() {
            if (bitrateKbps <= 0) return "320 kbps"
            return if (bitrateKbps >= 10000) {
                String.format(Locale.US, "%.1f Mbps", bitrateKbps / 1000f)
            } else {
                "$bitrateKbps kbps"
            }
        }

    val bitDepthFormatted: String
        get() {
            return when {
                bitDepth > 0 -> "$bitDepth-bit"
                isLossless -> "16-bit"
                else -> "16-bit"
            }
        }

    val qualityBadgeText: String
        get() {
            return when {
                isHiRes -> "Hi-Res AUDIO"
                isLossless -> "LOSSLESS"
                format == "MP3" && bitrateKbps >= 300 -> "320 KBPS HQ"
                format == "MP3" && bitrateKbps in 115..145 -> "128 KBPS"
                bitrateKbps >= 256 -> "$bitrateKbps kbps HQ"
                bitrateKbps > 0 -> "$bitrateKbps kbps"
                else -> format
            }
        }

    val summaryPillText: String
        get() {
            return if (isHiRes) {
                "$format • $bitDepthFormatted / $sampleRateFormatted • $bitrateFormatted"
            } else if (isLossless) {
                "$format • $sampleRateFormatted • $bitrateFormatted"
            } else {
                "$format • $bitrateFormatted • $sampleRateFormatted"
            }
        }
}

/**
 * Audio Codec & Bitrate Inspector Utility.
 * Performs multi-layer inspection:
 * 1. jaudiotagger ID3/FLAC header inspection (deep bit-depth, VBR, sample rate).
 * 2. MediaMetadataRetriever fallback for Android content URIs.
 * 3. MediaExtractor track parsing for hardware-reported codecs.
 * 4. Precise mathematical file-size / duration verification.
 */
object AudioInspectorUtil {

    private val specsCache = ConcurrentHashMap<Long, AudioSpecs>()

    fun getCachedSpecs(songId: Long): AudioSpecs? = specsCache[songId]

    /**
     * Ultra-fast in-memory specs estimation based on filename/extension.
     * Takes 0ms and does ZERO disk I/O, preventing any UI lag or jank during LazyColumn scrolling.
     */
    fun getAudioSpecsFast(song: Song): AudioSpecs {
        specsCache[song.id]?.let { return it }

        val format = detectFormatFromExtensionOrPath(song)
        val fullCodecName = getFullCodecName(format)
        val isLossless = isFormatLossless(format)
        val containerExt = detectExtension(song.filePath, format)

        val sampleRate = if (isLossless) 48000 else 44100
        val bitDepth = if (isLossless) 24 else 16
        val bitrate = when (format) {
            "FLAC", "WAV", "ALAC" -> 1411
            "MP3" -> 320
            "AAC", "M4A" -> 256
            "OGG", "OPUS" -> 160
            else -> 320
        }
        val isHiRes = isLossless && (bitDepth >= 24 || sampleRate >= 48000)

        return AudioSpecs(
            format = format,
            fullCodecName = fullCodecName,
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channels = 2,
            channelLayout = "Stereo (2.0 ch)",
            isLossless = isLossless,
            isHiRes = isHiRes,
            fileSizeFormatted = "",
            fileSizeBytes = 0L,
            isVariableBitrate = false,
            containerExtension = containerExt
        )
    }

    /**
     * Suspending version that offloads Jaudiotagger and MediaMetadataRetriever disk I/O to Dispatchers.IO.
     */
    suspend fun getAudioSpecsAsync(context: Context, song: Song): AudioSpecs =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getAudioSpecs(context, song)
        }

    fun getAudioSpecs(context: Context, song: Song): AudioSpecs {
        specsCache[song.id]?.let { return it }

        var format = detectFormatFromExtensionOrPath(song)
        var fullCodecName = getFullCodecName(format)
        var bitrateKbps = 0
        var sampleRateHz = 44100
        var bitDepth = 16
        var channels = 2
        var isLossless = isFormatLossless(format)
        var isVbr = false

        val file = if (song.filePath.isNotBlank()) File(song.filePath) else null
        var fileSizeBytes = if (file != null && file.exists() && file.canRead()) {
            file.length()
        } else {
            getFileSizeFromUri(context, song.contentUri)
        }

        // Layer 1: Jaudiotagger Header Inspection (Most accurate for ID3 & FLAC blocks)
        if (file != null && file.exists() && file.canRead()) {
            try {
                val audioFile = AudioFileIO.read(file)
                val header = audioFile.audioHeader
                if (header != null) {
                    val jFormat = try { header.format?.trim() ?: "" } catch (e: Throwable) { "" }
                    if (jFormat.isNotBlank()) {
                        val parsedFormat = normalizeJaudiotaggerFormat(jFormat)
                        if (parsedFormat.isNotBlank()) {
                            format = parsedFormat
                            fullCodecName = getFullCodecName(format)
                        }
                    }

                    val jBitrate = try { header.bitRateAsNumber.toInt() } catch (e: Throwable) { 0 }
                    if (jBitrate > 0) {
                        bitrateKbps = jBitrate
                    } else {
                        val brStr = try { header.bitRate?.filter { it.isDigit() } ?: "" } catch (e: Throwable) { "" }
                        val brParsed = brStr.toIntOrNull() ?: 0
                        if (brParsed > 0) bitrateKbps = brParsed
                    }

                    val jSampleRate = try { header.sampleRateAsNumber } catch (e: Throwable) { 0 }
                    if (jSampleRate > 0) {
                        sampleRateHz = jSampleRate
                    }

                    val jBits = try { header.bitsPerSample } catch (e: Throwable) { 0 }
                    if (jBits > 0) {
                        bitDepth = jBits
                    }

                    isLossless = try { header.isLossless || isFormatLossless(format) } catch (e: Throwable) { isFormatLossless(format) }
                    isVbr = try { header.isVariableBitRate } catch (e: Throwable) { false }

                    val jChannels = try { header.channels?.lowercase() ?: "" } catch (e: Throwable) { "" }
                    if (jChannels.contains("mono") || jChannels == "1") {
                        channels = 1
                    } else if (jChannels.contains("stereo") || jChannels == "2") {
                        channels = 2
                    } else if (jChannels.contains("5.1") || jChannels == "6") {
                        channels = 6
                    }
                }
            } catch (e: Throwable) {
                // Ignore and fall back to MediaMetadataRetriever & MediaExtractor
            }
        }

        // Layer 2: Android MediaMetadataRetriever
        if (bitrateKbps <= 0 || sampleRateHz <= 0) {
            val mmr = MediaMetadataRetriever()
            try {
                if (file != null && file.exists() && file.canRead()) {
                    mmr.setDataSource(file.absolutePath)
                } else {
                    mmr.setDataSource(context, song.contentUri)
                }

                val mmrBitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                if (mmrBitrate != null && mmrBitrate > 0) {
                    bitrateKbps = (mmrBitrate / 1000).coerceAtLeast(1)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val mmrSampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                    if (mmrSampleRate != null && mmrSampleRate > 0) {
                        sampleRateHz = mmrSampleRate
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val mmrBits = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                    if (mmrBits != null && mmrBits > 0) {
                        bitDepth = mmrBits
                    }
                }

                val mmrMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                if (!mmrMime.isNullOrBlank() && format == "UNKNOWN") {
                    format = normalizeMimeType(mmrMime)
                    fullCodecName = getFullCodecName(format)
                    isLossless = isFormatLossless(format)
                }
            } catch (e: Throwable) {
                // Ignore
            } finally {
                try { mmr.release() } catch (e: Throwable) {}
            }
        }

        // Layer 3: Android MediaExtractor
        if (sampleRateHz <= 0 || bitrateKbps <= 0) {
            val extractor = MediaExtractor()
            try {
                if (file != null && file.exists() && file.canRead()) {
                    extractor.setDataSource(file.absolutePath)
                } else {
                    extractor.setDataSource(context, song.contentUri, null)
                }

                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRateHz = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (trackFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            val br = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE)
                            if (br > 0) bitrateKbps = br / 1000
                        }
                        break
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            } finally {
                try { extractor.release() } catch (e: Throwable) {}
            }
        }

        // Layer 4: Mathematical calculation from file size and song duration
        if (bitrateKbps <= 0 && fileSizeBytes > 0L && song.duration > 1000L) {
            val durationSec = song.duration / 1000f
            val calculatedKbps = ((fileSizeBytes * 8f) / durationSec / 1000f).toInt()
            if (calculatedKbps in 32..9999) {
                bitrateKbps = calculatedKbps
            }
        }

        // Standardize common bitrate rounding (e.g. 318-323 -> 320 kbps; 125-132 -> 128 kbps)
        bitrateKbps = normalizeCommonBitrates(bitrateKbps, format)

        // Determine Hi-Res Audio certification
        val isHiRes = (sampleRateHz >= 48000 && (bitDepth >= 24 || isLossless)) ||
                sampleRateHz >= 88200 ||
                bitDepth >= 24 ||
                (isLossless && sampleRateHz > 44100) ||
                format == "DSD"

        val channelLayout = when (channels) {
            1 -> "Mono (1.0 ch)"
            2 -> "Stereo (2.0 ch)"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> "$channels Channels"
        }

        val containerExt = detectExtension(song.filePath, format)
        val fileSizeFormatted = formatFileSize(fileSizeBytes)

        val specs = AudioSpecs(
            format = format,
            fullCodecName = fullCodecName,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            channels = channels,
            channelLayout = channelLayout,
            isLossless = isLossless,
            isHiRes = isHiRes,
            fileSizeFormatted = fileSizeFormatted,
            fileSizeBytes = fileSizeBytes,
            isVariableBitrate = isVbr,
            containerExtension = containerExt
        )

        specsCache[song.id] = specs
        return specs
    }

    private fun detectFormatFromExtensionOrPath(song: Song): String {
        val path = song.filePath.lowercase(Locale.ROOT)
        val title = song.title.lowercase(Locale.ROOT)
        return when {
            path.endsWith(".flac") || title.endsWith(".flac") -> "FLAC"
            path.endsWith(".mp3") || title.endsWith(".mp3") -> "MP3"
            path.endsWith(".wav") || title.endsWith(".wav") -> "WAV"
            path.endsWith(".m4a") || title.endsWith(".m4a") -> "AAC"
            path.endsWith(".aac") || title.endsWith(".aac") -> "AAC"
            path.endsWith(".ogg") || title.endsWith(".ogg") -> "OGG"
            path.endsWith(".opus") || title.endsWith(".opus") -> "OPUS"
            path.endsWith(".alac") || title.endsWith(".alac") -> "ALAC"
            path.endsWith(".dsd") || path.endsWith(".dsf") || path.endsWith(".dff") -> "DSD"
            path.endsWith(".ape") -> "APE"
            else -> "MP3" // Default standard
        }
    }

    private fun normalizeJaudiotaggerFormat(rawFormat: String): String {
        val lower = rawFormat.lowercase(Locale.ROOT)
        return when {
            lower.contains("flac") -> "FLAC"
            lower.contains("mpeg") || lower.contains("mp3") || lower.contains("layer 3") -> "MP3"
            lower.contains("wav") -> "WAV"
            lower.contains("aac") || lower.contains("mp4") -> "AAC"
            lower.contains("ogg") || lower.contains("vorbis") -> "OGG"
            lower.contains("opus") -> "OPUS"
            lower.contains("alac") -> "ALAC"
            lower.contains("dsd") -> "DSD"
            else -> rawFormat.uppercase(Locale.ROOT)
        }
    }

    private fun normalizeMimeType(mime: String): String {
        val lower = mime.lowercase(Locale.ROOT)
        return when {
            lower.contains("flac") -> "FLAC"
            lower.contains("mpeg") || lower.contains("mp3") -> "MP3"
            lower.contains("wav") -> "WAV"
            lower.contains("aac") || lower.contains("mp4") || lower.contains("m4a") -> "AAC"
            lower.contains("ogg") -> "OGG"
            lower.contains("opus") -> "OPUS"
            lower.contains("alac") -> "ALAC"
            else -> "MP3"
        }
    }

    private fun getFullCodecName(format: String): String {
        return when (format) {
            "FLAC" -> "Free Lossless Audio Codec (FLAC)"
            "MP3" -> "MPEG-1 Audio Layer III (MP3)"
            "WAV" -> "Waveform Audio File Format (PCM)"
            "AAC" -> "Advanced Audio Coding (AAC / LC)"
            "OGG" -> "Ogg Vorbis Audio Codec"
            "OPUS" -> "Opus Interactive Audio Codec"
            "ALAC" -> "Apple Lossless Audio Codec (ALAC)"
            "DSD" -> "Direct Stream Digital (DSD / SACD)"
            "APE" -> "Monkey's Audio Lossless (APE)"
            else -> "$format Audio Codec"
        }
    }

    private fun isFormatLossless(format: String): Boolean {
        return format in listOf("FLAC", "WAV", "ALAC", "DSD", "APE")
    }

    private fun normalizeCommonBitrates(rawKbps: Int, format: String): Int {
        if (rawKbps <= 0) {
            return if (isFormatLossless(format)) 1411 else 320
        }
        // Round standard MP3/AAC targets
        return when (rawKbps) {
            in 315..325 -> 320
            in 250..260 -> 256
            in 188..196 -> 192
            in 155..165 -> 160
            in 124..132 -> 128
            in 92..100 -> 96
            in 1390..1430 -> 1411
            else -> rawKbps
        }
    }

    private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Throwable) {
            0L
        }
    }

    private fun detectExtension(path: String, format: String): String {
        if (path.contains(".")) {
            val ext = path.substringAfterLast(".").lowercase(Locale.ROOT)
            if (ext.length in 2..5) return ext
        }
        return format.lowercase(Locale.ROOT)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0.0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
