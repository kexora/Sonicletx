package com.kexora.soniclet.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.kexora.soniclet.utils.CustomArtworkManager
import com.kexora.soniclet.utils.SmartTitleCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Crash-Free, Scoped-Storage-Safe Video-to-Audio (M4A/AAC) Extractor Engine.
 *
 * Dual-engine pipeline:
 * 1. Direct Stream Copy (AAC source + Original Quality, including trimmed ranges)
 * 2. Universal Hardware MediaCodec Decode -> AAC-LC Encode -> MediaMuxer
 *    (honors exact 128k–320k bitrates and decodes Opus/Vorbis/MP3/PCM/etc.)
 *    with automatic Media3 Transformer fallback.
 */
object VideoToM4aConverterUtil {

    private const val TAG = "VideoToM4aConverter"
    private const val MAX_THUMBNAIL_DIM = 600
    private const val COVER_JPEG_QUALITY = 90

    enum class ConversionMode(val label: String, val description: String) {
        DIRECT_LOSSLESS("Original Quality", "Fastest — keeps the video's exact sound"),
        CUSTOM_QUALITY("Choose Quality", "Pick your preferred sound clarity and file size")
    }

    enum class AudioBitrate(val kbps: Int, val label: String, val desc: String, val approxMbPerMin: Float) {
        BITRATE_128(128, "128 kbps", "Small file size", 0.95f),
        BITRATE_192(192, "192 kbps", "Clear everyday sound", 1.44f),
        BITRATE_256(256, "256 kbps", "High clarity", 1.92f),
        BITRATE_320(320, "320 kbps", "Highest quality", 2.40f)
    }

    data class VideoMetadata(
        val uri: Uri,
        val fileName: String,
        val title: String,
        val durationMs: Long,
        val formattedDuration: String,
        val sizeBytes: Long,
        val formattedSize: String,
        val width: Int,
        val height: Int,
        val hasAudio: Boolean,
        val audioMime: String?,
        val audioSampleRate: Int,
        val audioChannels: Int,
        val isAacDirectCompatible: Boolean,
        val thumbnail: Bitmap?
    )

    data class ConversionConfig(
        val inputUri: Uri,
        val outputTitle: String,
        val outputArtist: String = "Soniclet",
        val outputAlbum: String = "",
        val mode: ConversionMode = ConversionMode.DIRECT_LOSSLESS,
        val bitrate: AudioBitrate = AudioBitrate.BITRATE_256,
        val startMs: Long = 0L,
        val endMs: Long = 0L,
        val embedThumbnail: Boolean = true,
        val thumbnailBitmap: Bitmap? = null
    )

    data class ConversionResult(
        val outputFile: File?,
        val outputUri: Uri?,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val fileSizeFormatted: String,
        val bitrateInfo: String,
        val mediaStoreSongId: Long = -1L,
        val albumArtUri: Uri? = null
    )

    /**
     * Inspects a picked video URI safely on Dispatchers.IO without broad storage permissions.
     * Downscales thumbnail frames to <= 512px to prevent OOM on 4K videos.
     */
    suspend fun inspectVideo(context: Context, videoUri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var fileName = "Video_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        try {
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query video file name: ${e.message}")
        }

        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video URI: ${e.message}")
            try { retriever.release() } catch (_: Exception) {}
            return@withContext null
        }

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)

        // Memory-safe downscaled thumbnail frame (<= 512px on all API levels)
        val frameTimeUs = when {
            durationMs > 4000L -> 1_500_000L
            durationMs > 1000L -> 500_000L
            else -> 0L
        }
        val safeThumbnail: Bitmap? = try {
            val rawFrame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && width > 0 && height > 0) {
                val scale = minOf(1f, MAX_THUMBNAIL_DIM.toFloat() / maxOf(width, height).toFloat())
                val targetW = (width * scale).toInt().coerceIn(120, MAX_THUMBNAIL_DIM)
                val targetH = (height * scale).toInt().coerceIn(120, MAX_THUMBNAIL_DIM)
                retriever.getScaledFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
            } else {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            downscaleBitmapSafely(rawFrame, MAX_THUMBNAIL_DIM)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val rawTitle = if (!metaTitle.isNullOrBlank()) metaTitle else fileName.substringBeforeLast(".")
        val cleanTitle = SmartTitleCleaner.cleanTitle(rawTitle, fileName).ifBlank { "Saved Audio" }

        var hasAudio = false
        var audioMime: String? = null
        var sampleRate = 44100
        var channelCount = 2
        var isAacDirectCompatible = false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, videoUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) ||
                        mime.contains("mp4a", ignoreCase = true)
                    ) {
                        isAacDirectCompatible = true
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor audio inspection warning: ${e.message}")
            hasAudio = hasAudioMeta.equals("yes", ignoreCase = true)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        VideoMetadata(
            uri = videoUri,
            fileName = fileName,
            title = cleanTitle,
            durationMs = durationMs,
            formattedDuration = formatDuration(durationMs),
            sizeBytes = sizeBytes,
            formattedSize = formatFileSize(sizeBytes),
            width = width,
            height = height,
            hasAudio = hasAudio,
            audioMime = audioMime,
            audioSampleRate = sampleRate,
            audioChannels = channelCount,
            isAacDirectCompatible = isAacDirectCompatible,
            thumbnail = safeThumbnail
        )
    }

    private fun downscaleBitmapSafely(bitmap: Bitmap?, maxDim: Int): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        return try {
            val scale = maxDim.toFloat() / largest.toFloat()
            val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap) {
                bitmap.recycle()
            }
            scaled
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Extracts audio from any picked video URI to M4A (AAC) and saves it to Music/Soniclet.
     */
    fun convert(
        context: Context,
        config: ConversionConfig,
        isAacDirectCompatible: Boolean,
        onProgress: (Int) -> Unit,
        onSuccess: (ConversionResult) -> Unit,
        onError: (String) -> Unit
    ): TransformerJob {
        val tempDir = File(context.cacheDir, "m4a_exports").apply { mkdirs() }
        val safeFileName = config.outputTitle
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .ifEmpty { "Soniclet_Audio" }
        val tempOutputFile = File(tempDir, "${safeFileName}_${System.currentTimeMillis()}.m4a")

        val parentJob = Job()
        val scope = CoroutineScope(Dispatchers.Main + parentJob)
        val cancelled = AtomicBoolean(false)
        var fallbackTransformerJob: TransformerJob? = null

        val useDirectCopy = config.mode == ConversionMode.DIRECT_LOSSLESS && isAacDirectCompatible

        scope.launch(Dispatchers.IO) {
            try {
                // Verify video actually has an audio track first
                if (!videoHasAudioTrack(context, config.inputUri)) {
                    withContext(Dispatchers.Main) {
                        onError("This video has no sound")
                    }
                    return@launch
                }

                val extracted = if (useDirectCopy) {
                    directDemuxAudio(
                        context = context,
                        inputUri = config.inputUri,
                        outputFile = tempOutputFile,
                        startMs = config.startMs,
                        endMs = config.endMs,
                        cancelled = cancelled
                    ) { p ->
                        if (!cancelled.get()) scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                } else {
                    transcodeAudioToAac(
                        context = context,
                        inputUri = config.inputUri,
                        outputFile = tempOutputFile,
                        targetBitrateBps = config.bitrate.kbps * 1000,
                        startMs = config.startMs,
                        endMs = config.endMs,
                        cancelled = cancelled
                    ) { p ->
                        if (!cancelled.get()) scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                }

                if (cancelled.get()) {
                    safeDelete(tempOutputFile)
                    return@launch
                }

                if (extracted && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                    val embeddedCoverBitmap = embedMetadataAndCover(context, tempOutputFile, config)
                    val qualityLabel = if (config.mode == ConversionMode.DIRECT_LOSSLESS) {
                        "Original Quality"
                    } else {
                        "${config.bitrate.kbps} kbps"
                    }
                    val savedResult = saveToDeviceMusicStorage(
                        context = context,
                        tempFile = tempOutputFile,
                        config = config,
                        bitrateInfo = qualityLabel,
                        coverBitmap = embeddedCoverBitmap
                    )
                    if (embeddedCoverBitmap != null && embeddedCoverBitmap !== config.thumbnailBitmap && !embeddedCoverBitmap.isRecycled) {
                        embeddedCoverBitmap.recycle()
                    }
                    safeDelete(tempOutputFile)
                    withContext(Dispatchers.Main) {
                        onProgress(100)
                        onSuccess(savedResult)
                    }
                } else {
                    // Fallback to Media3 Transformer for unusual containers/codecs
                    safeDelete(tempOutputFile)
                    withContext(Dispatchers.Main) {
                        if (!cancelled.get()) {
                            fallbackTransformerJob = startTransformerConversion(
                                context = context,
                                config = config,
                                tempOutputFile = tempOutputFile,
                                coroutineScope = scope,
                                onProgress = onProgress,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Primary audio extraction failed, trying fallback: ${e.message}")
                safeDelete(tempOutputFile)
                if (!cancelled.get()) {
                    withContext(Dispatchers.Main) {
                        fallbackTransformerJob = startTransformerConversion(
                            context = context,
                            config = config,
                            tempOutputFile = tempOutputFile,
                            coroutineScope = scope,
                            onProgress = onProgress,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    }
                }
            }
        }

        return TransformerJob {
            cancelled.set(true)
            fallbackTransformerJob?.cancelJob()
            parentJob.cancel()
            safeDelete(tempOutputFile)
        }
    }

    private fun videoHasAudioTrack(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) return true
            }
            false
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * ENGINE 1: Fast direct stream copy via MediaExtractor -> MediaMuxer (supports trimming).
     */
    private fun directDemuxAudio(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(context, inputUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)

            val trackDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val startUs = (startMs.coerceAtLeast(0L)) * 1000L
            val endUs = if (endMs > startMs) endMs * 1000L else Long.MAX_VALUE
            val expectedDurationUs = if (endUs != Long.MAX_VALUE) {
                (endUs - startUs).coerceAtLeast(1L)
            } else {
                (trackDurationUs - startUs).coerceAtLeast(1L)
            }

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceIn(65_536, 1_048_576)
            } else {
                262_144
            }

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var basePtsUs = -1L
            var lastPtsUs = -1L
            var wroteAnySample = false
            var lastReportTime = 0L

            while (!cancelled.get()) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                    var rebasedPts = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                    if (rebasedPts <= lastPtsUs) {
                        rebasedPts = lastPtsUs + 1L
                    }
                    lastPtsUs = rebasedPts
                    bufferInfo.presentationTimeUs = rebasedPts
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                    wroteAnySample = true

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 70L) {
                        lastReportTime = now
                        val prog = ((rebasedPts.toFloat() / expectedDurationUs.toFloat()) * 94f)
                            .toInt()
                            .coerceIn(1, 94)
                        onProgress(prog)
                    }
                }
                extractor.advance()
            }

            return wroteAnySample && !cancelled.get()
        } catch (e: Exception) {
            Log.e(TAG, "Direct demux failed: ${e.message}")
            return false
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * ENGINE 2: Universal Hardware MediaCodec Decode -> AAC-LC Encode -> MediaMuxer.
     * Supports any audio codec (Opus, Vorbis, MP3, FLAC, PCM, AAC) + exact target bitrate + trimming.
     */
    private fun transcodeAudioToAac(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetBitrateBps: Int,
        startMs: Long,
        endMs: Long,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(context, inputUri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) return false

            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 48000)
            } else {
                44100
            }
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            } else {
                2
            }
            val trackDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val endUs = if (endMs > startMs) endMs * 1000L else Long.MAX_VALUE
            val expectedDurationUs = if (endUs != Long.MAX_VALUE) {
                (endUs - startUs).coerceAtLeast(1L)
            } else {
                (trackDurationUs - startUs).coerceAtLeast(1L)
            }

            extractor.selectTrack(audioTrackIndex)
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            // Configure decoder
            decoder = MediaCodec.createDecoderByType(inputMime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            // Configure AAC-LC encoder
            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateBps.coerceIn(64_000, 320_000))
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 262_144)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var basePcmPtsUs = -1L
            var lastEncPtsUs = -1L
            var lastReportTime = 0L
            var wroteAnyFrame = false

            val timeoutUs = 10_000L

            while (!encoderDone && !cancelled.get()) {
                // 1. Feed extractor -> decoder
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || sampleTimeUs > endUs) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractorDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain decoder -> feed PCM to encoder
                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, timeoutUs)
                    if (outIdx >= 0) {
                        val decBuf = decoder.getOutputBuffer(outIdx)
                        val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (decBuf != null && decInfo.size > 0 && decInfo.presentationTimeUs >= startUs && decInfo.presentationTimeUs <= endUs) {
                            decBuf.position(decInfo.offset)
                            decBuf.limit(decInfo.offset + decInfo.size)
                            if (basePcmPtsUs < 0L) basePcmPtsUs = decInfo.presentationTimeUs
                            var remaining = decInfo.size
                            var offsetShift = 0
                            val baseFramePts = (decInfo.presentationTimeUs - basePcmPtsUs).coerceAtLeast(0L)

                            while (remaining > 0 && !cancelled.get()) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx >= 0) {
                                    val encInBuf = encoder.getInputBuffer(encInIdx)
                                    if (encInBuf != null) {
                                        encInBuf.clear()
                                        val chunk = minOf(remaining, encInBuf.capacity())
                                        val slice = ByteArray(chunk)
                                        decBuf.position(decInfo.offset + offsetShift)
                                        decBuf.get(slice, 0, chunk)
                                        encInBuf.put(slice)
                                        val chunkPts = baseFramePts + ((offsetShift.toLong() * 1_000_000L) / (sampleRate * channelCount * 2L).coerceAtLeast(1L))
                                        encoder.queueInputBuffer(encInIdx, 0, chunk, chunkPts, 0)
                                        remaining -= chunk
                                        offsetShift += chunk
                                    }
                                } else {
                                    // Drain encoder to avoid deadlock
                                    val drained = drainEncoderStep(
                                        encoder, encInfo, muxer, muxerTrackIndex, muxerStarted, lastEncPtsUs, expectedDurationUs, lastReportTime, onProgress
                                    )
                                    muxerTrackIndex = drained.muxerTrackIndex
                                    muxerStarted = drained.muxerStarted
                                    lastEncPtsUs = drained.lastPtsUs
                                    lastReportTime = drained.lastReportTime
                                    if (drained.wroteFrame) wroteAnyFrame = true
                                    if (drained.eosReached) encoderDone = true
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(outIdx, false)

                        if (isEos) {
                            decoderDone = true
                            // Signal EOS to encoder
                            while (!cancelled.get()) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx >= 0) {
                                    encoder.queueInputBuffer(encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    break
                                } else {
                                    val drained = drainEncoderStep(
                                        encoder, encInfo, muxer, muxerTrackIndex, muxerStarted, lastEncPtsUs, expectedDurationUs, lastReportTime, onProgress
                                    )
                                    muxerTrackIndex = drained.muxerTrackIndex
                                    muxerStarted = drained.muxerStarted
                                    lastEncPtsUs = drained.lastPtsUs
                                    lastReportTime = drained.lastReportTime
                                    if (drained.wroteFrame) wroteAnyFrame = true
                                    if (drained.eosReached) {
                                        encoderDone = true
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Drain encoder -> muxer
                val drained = drainEncoderStep(
                    encoder, encInfo, muxer, muxerTrackIndex, muxerStarted, lastEncPtsUs, expectedDurationUs, lastReportTime, onProgress
                )
                muxerTrackIndex = drained.muxerTrackIndex
                muxerStarted = drained.muxerStarted
                lastEncPtsUs = drained.lastPtsUs
                lastReportTime = drained.lastReportTime
                if (drained.wroteFrame) wroteAnyFrame = true
                if (drained.eosReached) encoderDone = true
            }

            return wroteAnyFrame && !cancelled.get()
        } catch (e: Exception) {
            Log.w(TAG, "Hardware AAC transcode failed: ${e.message}")
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private data class DrainResult(
        val muxerTrackIndex: Int,
        val muxerStarted: Boolean,
        val lastPtsUs: Long,
        val lastReportTime: Long,
        val wroteFrame: Boolean,
        val eosReached: Boolean
    )

    private fun drainEncoderStep(
        encoder: MediaCodec,
        encInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        initialTrackIndex: Int,
        initialMuxerStarted: Boolean,
        initialLastPtsUs: Long,
        expectedDurationUs: Long,
        initialLastReportTime: Long,
        onProgress: (Int) -> Unit
    ): DrainResult {
        var trackIndex = initialTrackIndex
        var started = initialMuxerStarted
        var lastPtsUs = initialLastPtsUs
        var lastReportTime = initialLastReportTime
        var wroteFrame = false
        var eosReached = false

        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(encInfo, 0L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!started) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                }
            } else if (outIdx >= 0) {
                val encBuf = encoder.getOutputBuffer(outIdx)
                val isConfig = (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                val isEos = (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                if (!isConfig && encBuf != null && encInfo.size > 0 && started && trackIndex >= 0) {
                    encBuf.position(encInfo.offset)
                    encBuf.limit(encInfo.offset + encInfo.size)
                    var pts = encInfo.presentationTimeUs.coerceAtLeast(0L)
                    if (pts <= lastPtsUs) pts = lastPtsUs + 1L
                    lastPtsUs = pts
                    encInfo.presentationTimeUs = pts
                    muxer.writeSampleData(trackIndex, encBuf, encInfo)
                    wroteFrame = true

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 80L) {
                        lastReportTime = now
                        val prog = ((pts.toFloat() / expectedDurationUs.toFloat()) * 94f)
                            .toInt()
                            .coerceIn(1, 94)
                        onProgress(prog)
                    }
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if (isEos) {
                    eosReached = true
                    break
                }
            }
        }
        return DrainResult(trackIndex, started, lastPtsUs, lastReportTime, wroteFrame, eosReached)
    }

    /**
     * Fallback Engine: Media3 Transformer for edge-case container formats.
     */
    private fun startTransformerConversion(
        context: Context,
        config: ConversionConfig,
        tempOutputFile: File,
        coroutineScope: CoroutineScope,
        onProgress: (Int) -> Unit,
        onSuccess: (ConversionResult) -> Unit,
        onError: (String) -> Unit
    ): TransformerJob {
        val mediaItemBuilder = MediaItem.Builder().setUri(config.inputUri)

        if (config.startMs > 0L || (config.endMs > config.startMs)) {
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(config.startMs)
            if (config.endMs > config.startMs) {
                clippingBuilder.setEndPositionMs(config.endMs)
            }
            mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveVideo(true)
            .build()

        var pollingJob: Job? = null

        val transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    pollingJob?.cancel()
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val embeddedCoverBitmap = embedMetadataAndCover(context, tempOutputFile, config)
                            val bitrateLabel = if (config.mode == ConversionMode.CUSTOM_QUALITY) {
                                "${config.bitrate.kbps} kbps"
                            } else {
                                "Original Quality"
                            }
                            val savedResult = saveToDeviceMusicStorage(
                                context = context,
                                tempFile = tempOutputFile,
                                config = config,
                                bitrateInfo = bitrateLabel,
                                coverBitmap = embeddedCoverBitmap
                            )
                            if (embeddedCoverBitmap != null && embeddedCoverBitmap !== config.thumbnailBitmap && !embeddedCoverBitmap.isRecycled) {
                                embeddedCoverBitmap.recycle()
                            }
                            safeDelete(tempOutputFile)
                            withContext(Dispatchers.Main) {
                                onProgress(100)
                                onSuccess(savedResult)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error finalizing exported audio: ${e.message}", e)
                            safeDelete(tempOutputFile)
                            withContext(Dispatchers.Main) {
                                onError("Couldn't save the audio file. Please try again.")
                            }
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    pollingJob?.cancel()
                    safeDelete(tempOutputFile)
                    Log.e(TAG, "Transformer export error: ${exportException.message}", exportException)
                    coroutineScope.launch(Dispatchers.Main) {
                        onError("Couldn't save audio from this video. Please try another video.")
                    }
                }
            })
            .build()

        try {
            transformer.start(editedMediaItem, tempOutputFile.absolutePath)

            val progressHolder = ProgressHolder()
            pollingJob = coroutineScope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(120)
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val currentProgress = progressHolder.progress.coerceIn(1, 96)
                        withContext(Dispatchers.Main) { onProgress(currentProgress) }
                    }
                }
            }
        } catch (e: Exception) {
            pollingJob?.cancel()
            safeDelete(tempOutputFile)
            Log.e(TAG, "Failed to start fallback transformer: ${e.message}", e)
            onError("Couldn't read this video file. Please try another one.")
        }

        return TransformerJob {
            pollingJob?.cancel()
            try { transformer.cancel() } catch (_: Exception) {}
            safeDelete(tempOutputFile)
        }
    }

    /**
     * Resolves a unique per-track album tag so Android MediaStore NEVER merges separate
     * video-to-audio tracks under a shared albumId ("Saved from Video" / "Soniclet").
     */
    private fun resolveUniqueTrackAlbum(config: ConversionConfig): String {
        val candidate = config.outputAlbum.trim()
        val isSharedGeneric = candidate.isBlank() ||
            candidate.equals("Saved from Video", ignoreCase = true) ||
            candidate.equals("Soniclet", ignoreCase = true) ||
            candidate.equals("Unknown Album", ignoreCase = true)
        return if (isSharedGeneric) {
            config.outputTitle.trim()
        } else {
            candidate
        }
    }

    /**
     * Extracts a crisp 600x600 video frame from the picked video on IO thread,
     * always releasing MediaMetadataRetriever inside finally.
     */
    private fun extractCrispVideoCoverFrame(context: Context, videoUri: Uri, preferredStartMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameTimeUs = when {
                preferredStartMs > 0L -> preferredStartMs * 1000L
                durationMs > 4_000L -> 1_500_000L
                durationMs > 1_000L -> 500_000L
                else -> 0L
            }
            val rawFrame = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            downscaleBitmapSafely(rawFrame, MAX_THUMBNAIL_DIM)
        } catch (e: Exception) {
            Log.w(TAG, "Video frame extraction fallback warning: ${e.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Writes clean MP4 tags + real 600x600 JPEG video frame into the `.m4a` `covr` atom
     * BEFORE publishing to MediaStore. When `embedThumbnail` is OFF, explicitly clears `covr`.
     * Returns the 600x600 cover Bitmap when embedded (null otherwise) so MediaStore save can bind it per-song.
     */
    private fun embedMetadataAndCover(
        context: Context,
        file: File,
        config: ConversionConfig
    ): Bitmap? {
        val uniqueAlbum = resolveUniqueTrackAlbum(config)
        var coverBitmap: Bitmap? = null
        var coverJpegBytes: ByteArray? = null

        if (config.embedThumbnail) {
            coverBitmap = extractCrispVideoCoverFrame(context, config.inputUri, config.startMs)
                ?: config.thumbnailBitmap?.takeIf { !it.isRecycled }?.let { fallbackThumb ->
                    val largest = maxOf(fallbackThumb.width, fallbackThumb.height)
                    if (largest > MAX_THUMBNAIL_DIM) {
                        val scale = MAX_THUMBNAIL_DIM.toFloat() / largest.toFloat()
                        Bitmap.createScaledBitmap(
                            fallbackThumb,
                            (fallbackThumb.width * scale).toInt().coerceAtLeast(1),
                            (fallbackThumb.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        fallbackThumb
                    }
                }

            if (coverBitmap != null && !coverBitmap.isRecycled) {
                try {
                    val baos = ByteArrayOutputStream()
                    coverBitmap.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, baos)
                    coverJpegBytes = baos.toByteArray()
                } catch (e: Exception) {
                    Log.w(TAG, "Cover JPEG compression warning: ${e.message}")
                }
            }
        }

        TagEditorUtil.embedM4aMetadataAndCover(
            file = file,
            title = config.outputTitle.ifBlank { "Saved Audio" },
            artist = config.outputArtist.ifBlank { "Soniclet" },
            album = uniqueAlbum,
            coverJpegBytes = if (config.embedThumbnail) coverJpegBytes else null
        )

        return if (config.embedThumbnail) coverBitmap else null
    }

    private fun saveToDeviceMusicStorage(
        context: Context,
        tempFile: File,
        config: ConversionConfig,
        bitrateInfo: String,
        coverBitmap: Bitmap?
    ): ConversionResult {
        val safeName = config.outputTitle
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .ifEmpty { "Soniclet_Audio" }
        val displayName = "$safeName.m4a"
        val fileBytes = tempFile.length()
        val uniqueAlbum = resolveUniqueTrackAlbum(config)

        var finalFile: File? = null
        var finalUri: Uri? = null
        var resolvedSongId = -1L
        var savedCustomArtUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped Storage safe save into Music/Soniclet
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.TITLE, config.outputTitle)
                    put(MediaStore.Audio.Media.ARTIST, config.outputArtist)
                    put(MediaStore.Audio.Media.ALBUM, uniqueAlbum)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Soniclet")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val insertedUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (insertedUri != null) {
                    context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                        FileInputStream(tempFile).use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    context.contentResolver.update(insertedUri, values, null, null)
                    finalUri = insertedUri
                    resolvedSongId = try {
                        ContentUris.parseId(insertedUri)
                    } catch (_: Exception) {
                        -1L
                    }

                    // Bind per-song cover art immediately to this exact MediaStore songId
                    if (resolvedSongId > 0L) {
                        if (config.embedThumbnail && coverBitmap != null && !coverBitmap.isRecycled) {
                            savedCustomArtUri = CustomArtworkManager.saveCustomArtworkBitmap(
                                context = context,
                                songId = resolvedSongId,
                                bitmap = coverBitmap,
                                maxDim = MAX_THUMBNAIL_DIM,
                                jpegQuality = COVER_JPEG_QUALITY
                            )
                        } else {
                            CustomArtworkManager.clearCustomArtwork(context, resolvedSongId)
                        }
                        CustomArtworkManager.invalidateSongArtCache(context, resolvedSongId)
                    }

                    // Trigger MediaScannerConnection on resolved filesystem path for instant indexing
                    queryPathForUri(context, insertedUri)?.let { realPath ->
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(realPath),
                            arrayOf("audio/mp4"),
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore insert failed: ${e.message}")
            }
        } else {
            // Legacy Android 9 and below: save to public Music/Soniclet & scan
            try {
                @Suppress("DEPRECATION")
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val sonicletDir = File(musicDir, "Soniclet").apply { if (!exists()) mkdirs() }
                val targetFile = uniqueTargetFile(sonicletDir, safeName, "m4a")
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                }
                finalFile = targetFile
                val coverCopy = if (config.embedThumbnail && coverBitmap != null && !coverBitmap.isRecycled) {
                    coverBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mp4")
                ) { _, uri ->
                    if (uri != null) {
                        if (finalUri == null) finalUri = uri
                        val scannedId = try { ContentUris.parseId(uri) } catch (_: Exception) { -1L }
                        if (scannedId > 0L) {
                            if (config.embedThumbnail && coverCopy != null && !coverCopy.isRecycled) {
                                CustomArtworkManager.saveCustomArtworkBitmap(
                                    context = context,
                                    songId = scannedId,
                                    bitmap = coverCopy,
                                    maxDim = MAX_THUMBNAIL_DIM,
                                    jpegQuality = COVER_JPEG_QUALITY
                                )
                            } else {
                                CustomArtworkManager.clearCustomArtwork(context, scannedId)
                            }
                            CustomArtworkManager.invalidateSongArtCache(context, scannedId)
                        }
                    }
                    if (coverCopy != null && !coverCopy.isRecycled) {
                        coverCopy.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Legacy public Music save failed: ${e.message}")
            }
        }

        // Fallback to app-scoped Music directory ONLY if MediaStore & legacy both failed
        if (finalUri == null && finalFile == null) {
            val appMusicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Soniclet").apply {
                if (!exists()) mkdirs()
            }
            val appTargetFile = uniqueTargetFile(appMusicDir, safeName, "m4a")
            FileInputStream(tempFile).use { input ->
                FileOutputStream(appTargetFile).use { output -> input.copyTo(output) }
            }
            finalFile = appTargetFile
        }

        val durationMs = if (config.endMs > config.startMs) {
            config.endMs - config.startMs
        } else {
            0L
        }

        val resolvedArtUri = when {
            !config.embedThumbnail -> null
            savedCustomArtUri != null -> savedCustomArtUri
            finalUri != null -> finalUri
            finalFile != null -> Uri.fromFile(finalFile)
            else -> null
        }

        return ConversionResult(
            outputFile = finalFile,
            outputUri = finalUri,
            title = config.outputTitle,
            artist = config.outputArtist,
            album = uniqueAlbum,
            durationMs = durationMs,
            fileSizeFormatted = formatFileSize(finalFile?.length() ?: fileBytes),
            bitrateInfo = bitrateInfo,
            mediaStoreSongId = resolvedSongId,
            albumArtUri = resolvedArtUri
        )
    }

    private fun queryPathForUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun uniqueTargetFile(dir: File, baseName: String, ext: String): File {
        var candidate = File(dir, "$baseName.$ext")
        var idx = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$idx.$ext")
            idx++
        }
        return candidate
    }

    private fun safeDelete(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    fun calculateEstimatedSize(durationMs: Long, bitrate: AudioBitrate): String {
        if (durationMs <= 0L) return "~0.1 MB"
        val minutes = durationMs / 60_000.0
        val sizeMb = (minutes * bitrate.approxMbPerMin).coerceAtLeast(0.1)
        return String.format(Locale.getDefault(), "~%.1f MB", sizeMb)
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSecs = ms / 1000L
        val hours = totalSecs / 3600L
        val mins = (totalSecs % 3600L) / 60L
        val secs = totalSecs % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    class TransformerJob(private val cancel: () -> Unit) {
        fun cancelJob() = cancel()
    }
}
