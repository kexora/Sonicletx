package com.kexora.soniclet.radio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.WebResourceResponse
import com.kexora.soniclet.database.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CachedRadioTrack(
    val id: Long,
    val url: String,
    val title: String,
    val artist: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val filePath: String,
    val savedAt: Long
)

private data class CacheIndexEntry(
    val key: String,
    val url: String,
    val mimeType: String,
    val encoding: String,
    val isAudio: Boolean,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val savedAt: Long
)

/**
 * Persistent local cache engine for the Soniclet Radio tab.
 * Mirrors the loaded page, its static assets (HTML, CSS, JS, fonts, images, JSON),
 * and any played audio tracks to internal app storage so previously loaded sessions
 * and played songs continue working smoothly offline or on slow connections.
 */
class RadioOfflineCacheManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheRoot = File(appContext.filesDir, "soniclet_radio_cache").apply { mkdirs() }
    private val blobsDir = File(cacheRoot, "blobs").apply { mkdirs() }
    private val indexFile = File(cacheRoot, "cache_index.json")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexLock = Any()
    private val entries = ConcurrentHashMap<String, CacheIndexEntry>()
    private val inFlightDownloads = ConcurrentHashMap.newKeySet<String>()
    private val inFlightAssets = ConcurrentHashMap.newKeySet<String>()

    private val _hasCachedPage = MutableStateFlow(prefs.getBoolean(KEY_HAS_CACHED_PAGE, false))
    val hasCachedPage: StateFlow<Boolean> = _hasCachedPage.asStateFlow()

    private val _cachedTracks = MutableStateFlow<List<CachedRadioTrack>>(emptyList())
    val cachedTracks: StateFlow<List<CachedRadioTrack>> = _cachedTracks.asStateFlow()

    init {
        loadIndexFromDisk()
    }

    fun markPageLoadedSuccessfully() {
        if (!_hasCachedPage.value) {
            prefs.edit().putBoolean(KEY_HAS_CACHED_PAGE, true).apply()
            _hasCachedPage.value = true
        }
    }

    fun hasSavedSnapshot(): Boolean {
        if (!_hasCachedPage.value) return false
        val rootKey = hashKey(normalizeUrl(PRIMARY_URL))
        val rootSlashKey = hashKey(normalizeUrl("$PRIMARY_URL/"))
        return blobsFile(rootKey).exists() || blobsFile(rootSlashKey).exists() || _hasCachedPage.value
    }

    fun isFirstPartyHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT)
        return normalized == PRIMARY_HOST ||
            normalized.endsWith(".$PRIMARY_HOST") ||
            normalized == ROOT_HOST ||
            normalized.endsWith(".$ROOT_HOST")
    }

    fun isThirdPartyMediaOrYouTubeHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT)
        return YOUTUBE_AND_MEDIA_HOSTS.any { allowed ->
            normalized == allowed || normalized.endsWith(".$allowed")
        }
    }

    fun isLikelyAudioUrl(url: String, requestHeaders: Map<String, String>? = null): Boolean {
        val parsed = Uri.parse(url)
        val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
        // Never intercept or duplicate YouTube / GoogleVideo streaming requests
        if (isThirdPartyMediaOrYouTubeHost(host)) return false

        val lowerPath = parsed.path?.lowercase(Locale.ROOT).orEmpty()
        if (AUDIO_EXTENSIONS.any { lowerPath.endsWith(it) }) return true
        val accept = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value?.lowercase(Locale.ROOT).orEmpty()
        if (accept.contains("audio/")) return true
        return false
    }

    fun isCacheablePageAsset(url: String): Boolean {
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return false
        if (isThirdPartyMediaOrYouTubeHost(host)) return false
        val path = parsed.path?.lowercase(Locale.ROOT).orEmpty()

        // Skip live websocket/event-stream or telemetry endpoints
        if (path.contains("socket.io") || path.contains("/ws") || path.contains("analytics")) {
            return false
        }

        return isFirstPartyHost(host) && !isLikelyAudioUrl(url)
    }

    /**
     * Quietly mirrors first-party page assets to local storage on a background thread
     * without interfering with Chromium's live network stack or YouTube IFrame headers.
     */
    fun mirrorAssetInBackground(
        rawUrl: String,
        requestHeaders: Map<String, String>? = null
    ) {
        val normalizedUrl = normalizeUrl(rawUrl)
        if (!isCacheablePageAsset(normalizedUrl)) return
        val key = hashKey(normalizedUrl)
        if (!inFlightAssets.add(key)) return

        scope.launch {
            try {
                fetchAndCacheAsset(normalizedUrl, requestHeaders)
            } catch (_: Exception) {
            } finally {
                inFlightAssets.remove(key)
            }
        }
    }

    /**
     * Serves a locally cached page asset or audio file if present on disk.
     * Supports HTTP 206 Partial Content for HTML5 <audio> playback and seeking offline.
     */
    fun getCachedWebResponse(
        rawUrl: String,
        requestHeaders: Map<String, String>? = null
    ): WebResourceResponse? {
        val entry = findEntryForUrl(rawUrl) ?: return null
        val file = blobsFile(entry.key)
        if (!file.exists() || file.length() <= 0L) return null

        val originHeader = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value
        val responseHeaders = mutableMapOf(
            "Cache-Control" to "public, max-age=31536000",
            "Accept-Ranges" to "bytes",
            "Access-Control-Allow-Origin" to (originHeader ?: "*"),
            "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS"
        )

        val rangeHeader = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val totalLength = file.length()
            val rangeSpec = rangeHeader.substringAfter("bytes=").substringBefore(",").trim()
            val parts = rangeSpec.split("-")
            val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val requestedEnd = parts.getOrNull(1)?.toLongOrNull() ?: (totalLength - 1L)
            val safeStart = start.coerceIn(0L, (totalLength - 1L).coerceAtLeast(0L))
            val safeEnd = requestedEnd.coerceIn(safeStart, (totalLength - 1L).coerceAtLeast(safeStart))
            val contentLength = (safeEnd - safeStart + 1L).coerceAtLeast(0L)

            return try {
                val fis = FileInputStream(file)
                var remainingToSkip = safeStart
                while (remainingToSkip > 0L) {
                    val skipped = fis.skip(remainingToSkip)
                    if (skipped <= 0L) break
                    remainingToSkip -= skipped
                }
                val boundedStream = BoundedInputStream(fis, contentLength)
                responseHeaders["Content-Range"] = "bytes $safeStart-$safeEnd/$totalLength"
                responseHeaders["Content-Length"] = contentLength.toString()
                WebResourceResponse(
                    entry.mimeType,
                    entry.encoding.ifBlank { null },
                    206,
                    "Partial Content",
                    responseHeaders,
                    boundedStream
                )
            } catch (_: Exception) {
                null
            }
        }

        return try {
            responseHeaders["Content-Length"] = file.length().toString()
            WebResourceResponse(
                entry.mimeType,
                entry.encoding.ifBlank { null },
                200,
                "OK",
                responseHeaders,
                FileInputStream(file)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Online fetch-and-mirror for cacheable page assets (HTML, JS, CSS, images, fonts, JSON).
     * Falls back seamlessly to local cache if network fails or times out.
     */
    fun fetchAndCacheAsset(
        rawUrl: String,
        requestHeaders: Map<String, String>? = null
    ): WebResourceResponse? {
        val normalizedUrl = normalizeUrl(rawUrl)
        if (!isSafeRemoteHttpsUrl(normalizedUrl)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = ASSET_CONNECT_TIMEOUT_MS
                readTimeout = ASSET_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestHeaders?.forEach { (k, v) ->
                    if (k.equals("User-Agent", ignoreCase = true) ||
                        k.equals("Accept", ignoreCase = true) ||
                        k.equals("Referer", ignoreCase = true)
                    ) {
                        setRequestProperty(k, v)
                    }
                }
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return null
            }

            val rawContentType = connection.contentType.orEmpty()
            val (mimeType, encoding) = parseContentType(rawContentType, normalizedUrl)
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_ASSET_BYTES) {
                return null
            }

            val bytes = readBoundedBytes(connection.inputStream, MAX_ASSET_BYTES) ?: return null
            saveBlob(
                url = normalizedUrl,
                bytes = bytes,
                mimeType = mimeType,
                encoding = encoding,
                isAudio = false,
                title = "",
                artist = "",
                durationMs = 0L
            )

            if (mimeType.contains("html", ignoreCase = true) &&
                isFirstPartyHost(Uri.parse(normalizedUrl).host.orEmpty())
            ) {
                markPageLoadedSuccessfully()
            }

            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && !values.isNullOrEmpty() && !key.equals("Content-Encoding", ignoreCase = true)) {
                    responseHeaders[key] = values.joinToString(", ")
                }
            }
            val originHeader = requestHeaders?.entries
                ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
                ?.value
            if (!responseHeaders.keys.any { it.equals("Access-Control-Allow-Origin", ignoreCase = true) }) {
                responseHeaders["Access-Control-Allow-Origin"] = originHeader ?: "*"
            }

            WebResourceResponse(
                mimeType,
                encoding.ifBlank { null },
                statusCode,
                connection.responseMessage ?: "OK",
                responseHeaders,
                ByteArrayInputStream(bytes)
            )
        } catch (_: Exception) {
            getCachedWebResponse(rawUrl, requestHeaders)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Asynchronously downloads and persists a played audio track to local storage without blocking playback.
     */
    fun cacheAudioInBackground(
        rawUrl: String,
        titleHint: String? = null,
        artistHint: String? = null,
        requestHeaders: Map<String, String>? = null
    ) {
        val normalizedUrl = normalizeUrl(rawUrl)
        if (!isSafeRemoteHttpsUrl(normalizedUrl)) return
        val key = hashKey(normalizedUrl)

        val existing = entries[key]
        if (existing != null && blobsFile(key).exists()) {
            // Update metadata if we discovered a cleaner track title later
            val cleanedTitle = sanitizeMetadata(titleHint)
            val cleanedArtist = sanitizeMetadata(artistHint)
            if (cleanedTitle.isNotBlank() && (existing.title.isBlank() || existing.title.startsWith("Track "))) {
                synchronized(indexLock) {
                    entries[key] = existing.copy(
                        title = cleanedTitle,
                        artist = cleanedArtist.ifBlank { existing.artist }
                    )
                    persistIndexLocked()
                }
                publishCachedTracks()
            }
            return
        }

        if (!inFlightDownloads.add(key)) return

        scope.launch {
            var connection: HttpURLConnection? = null
            var tempFile: File? = null
            try {
                connection = (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = AUDIO_CONNECT_TIMEOUT_MS
                    readTimeout = AUDIO_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    requestHeaders?.forEach { (k, v) ->
                        if (k.equals("User-Agent", ignoreCase = true) ||
                            k.equals("Referer", ignoreCase = true)
                        ) {
                            setRequestProperty(k, v)
                        }
                    }
                }

                val code = connection.responseCode
                if (code !in 200..299) return@launch

                val rawContentType = connection.contentType.orEmpty()
                val (mimeType, _) = parseContentType(rawContentType, normalizedUrl)
                val looksLikeAudio = mimeType.startsWith("audio/", ignoreCase = true) ||
                    mimeType.equals("application/ogg", ignoreCase = true) ||
                    (mimeType.equals("application/octet-stream", ignoreCase = true) && isLikelyAudioUrl(normalizedUrl))
                if (!looksLikeAudio) return@launch

                val declaredLen = connection.contentLengthLong
                if (declaredLen > MAX_AUDIO_TRACK_BYTES) return@launch

                tempFile = File(blobsDir, "$key.tmp")
                var totalRead = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalRead += read
                            if (totalRead > MAX_AUDIO_TRACK_BYTES) {
                                tempFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                if (totalRead < MIN_AUDIO_BYTES) {
                    tempFile.delete()
                    return@launch
                }

                val targetFile = blobsFile(key)
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val (metaTitle, metaArtist, durationMs) = extractAudioMetadata(targetFile)
                val finalTitle = sanitizeMetadata(titleHint)
                    .ifBlank { metaTitle }
                    .ifBlank { deriveTitleFromUrl(normalizedUrl) }
                val finalArtist = sanitizeMetadata(artistHint)
                    .ifBlank { metaArtist }
                    .ifBlank { "Soniclet Radio" }

                synchronized(indexLock) {
                    entries[key] = CacheIndexEntry(
                        key = key,
                        url = normalizedUrl,
                        mimeType = mimeType,
                        encoding = "",
                        isAudio = true,
                        title = finalTitle,
                        artist = finalArtist,
                        durationMs = durationMs,
                        sizeBytes = totalRead,
                        savedAt = System.currentTimeMillis()
                    )
                    enforceCacheLimitsLocked()
                    persistIndexLocked()
                }
                publishCachedTracks()
            } catch (_: Exception) {
                tempFile?.delete()
            } finally {
                connection?.disconnect()
                inFlightDownloads.remove(key)
            }
        }
    }

    fun clearSavedAudioTracks() {
        scope.launch {
            synchronized(indexLock) {
                val audioKeys = entries.values.filter { it.isAudio }.map { it.key }
                audioKeys.forEach { key ->
                    entries.remove(key)
                    blobsFile(key).delete()
                }
                persistIndexLocked()
            }
            publishCachedTracks()
        }
    }

    fun toSonicletSong(track: CachedRadioTrack): Song {
        val file = File(track.filePath)
        return Song(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = "Soniclet Radio (Saved Offline)",
            duration = track.durationMs.coerceAtLeast(1000L),
            contentUri = Uri.fromFile(file),
            albumArtUri = null,
            folderPath = file.parent.orEmpty(),
            folderName = "Saved Radio",
            filePath = track.filePath,
            dateAdded = track.savedAt / 1000L
        )
    }

    private fun saveBlob(
        url: String,
        bytes: ByteArray,
        mimeType: String,
        encoding: String,
        isAudio: Boolean,
        title: String,
        artist: String,
        durationMs: Long
    ) {
        val key = hashKey(url)
        val tmpFile = File(blobsDir, "$key.tmp")
        val targetFile = blobsFile(key)
        try {
            FileOutputStream(tmpFile).use { it.write(bytes) }
            if (targetFile.exists()) targetFile.delete()
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
            synchronized(indexLock) {
                entries[key] = CacheIndexEntry(
                    key = key,
                    url = url,
                    mimeType = mimeType,
                    encoding = encoding,
                    isAudio = isAudio,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    sizeBytes = bytes.size.toLong(),
                    savedAt = System.currentTimeMillis()
                )
                enforceCacheLimitsLocked()
                persistIndexLocked()
            }
            if (isAudio) publishCachedTracks()
        } catch (_: Exception) {
            tmpFile.delete()
        }
    }

    private fun findEntryForUrl(rawUrl: String): CacheIndexEntry? {
        val normalized = normalizeUrl(rawUrl)
        entries[hashKey(normalized)]?.let { return it }
        val alt = if (normalized.endsWith("/")) normalized.dropLast(1) else "$normalized/"
        entries[hashKey(alt)]?.let { return it }
        return null
    }

    private fun enforceCacheLimitsLocked() {
        val assetEntries = entries.values.filter { !it.isAudio }.sortedBy { it.savedAt }
        var totalAssetBytes = assetEntries.sumOf { it.sizeBytes }
        for (entry in assetEntries) {
            if (totalAssetBytes <= MAX_TOTAL_ASSET_CACHE_BYTES) break
            // Never evict the primary landing page document
            if (normalizeUrl(entry.url).trimEnd('/') == PRIMARY_URL) continue
            entries.remove(entry.key)
            blobsFile(entry.key).delete()
            totalAssetBytes -= entry.sizeBytes
        }

        val audioEntries = entries.values.filter { it.isAudio }.sortedBy { it.savedAt }
        var totalAudioBytes = audioEntries.sumOf { it.sizeBytes }
        for (entry in audioEntries) {
            if (totalAudioBytes <= MAX_TOTAL_AUDIO_CACHE_BYTES) break
            entries.remove(entry.key)
            blobsFile(entry.key).delete()
            totalAudioBytes -= entry.sizeBytes
        }
    }

    private fun loadIndexFromDisk() {
        synchronized(indexLock) {
            entries.clear()
            if (indexFile.exists()) {
                try {
                    val array = JSONArray(indexFile.readText())
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val key = obj.optString("key")
                        if (key.isBlank() || !blobsFile(key).exists()) continue
                        entries[key] = CacheIndexEntry(
                            key = key,
                            url = obj.optString("url"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            encoding = obj.optString("encoding", ""),
                            isAudio = obj.optBoolean("isAudio", false),
                            title = obj.optString("title", ""),
                            artist = obj.optString("artist", "Soniclet Radio"),
                            durationMs = obj.optLong("durationMs", 0L),
                            sizeBytes = obj.optLong("sizeBytes", blobsFile(key).length()),
                            savedAt = obj.optLong("savedAt", 0L)
                        )
                    }
                } catch (_: Exception) {
                    // Ignore corrupt index and rebuild on next online visit
                }
            }
        }
        publishCachedTracks()
    }

    private fun persistIndexLocked() {
        try {
            val array = JSONArray()
            entries.values.forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put("key", entry.key)
                        put("url", entry.url)
                        put("mimeType", entry.mimeType)
                        put("encoding", entry.encoding)
                        put("isAudio", entry.isAudio)
                        put("title", entry.title)
                        put("artist", entry.artist)
                        put("durationMs", entry.durationMs)
                        put("sizeBytes", entry.sizeBytes)
                        put("savedAt", entry.savedAt)
                    }
                )
            }
            val tmp = File(cacheRoot, "cache_index.json.tmp")
            tmp.writeText(array.toString())
            if (indexFile.exists()) indexFile.delete()
            if (!tmp.renameTo(indexFile)) {
                tmp.copyTo(indexFile, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            // Non-fatal persistence failure
        }
    }

    private fun publishCachedTracks() {
        val list = entries.values
            .filter { it.isAudio && blobsFile(it.key).exists() }
            .sortedByDescending { it.savedAt }
            .map { entry ->
                val file = blobsFile(entry.key)
                CachedRadioTrack(
                    id = -10_000_000L - (entry.key.hashCode().toLong() and 0x7FFFFFFFL),
                    url = entry.url,
                    title = entry.title.ifBlank { deriveTitleFromUrl(entry.url) },
                    artist = entry.artist.ifBlank { "Soniclet Radio" },
                    mimeType = entry.mimeType,
                    durationMs = entry.durationMs,
                    sizeBytes = file.length(),
                    filePath = file.absolutePath,
                    savedAt = entry.savedAt
                )
            }
        _cachedTracks.value = list
    }

    private fun blobsFile(key: String): File = File(blobsDir, key)

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val parsed = Uri.parse(trimmed)
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
        return if (scheme == "http" && isFirstPartyHost(host)) {
            parsed.buildUpon().scheme("https").build().toString()
        } else {
            trimmed
        }
    }

    private fun isSafeRemoteHttpsUrl(urlString: String): Boolean {
        return try {
            val parsed = Uri.parse(urlString)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false
            if (scheme != "https") return false
            val host = parsed.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local")) {
                return false
            }
            val addresses = InetAddress.getAllByName(host)
            addresses.none { addr ->
                addr.isAnyLocalAddress ||
                    addr.isLoopbackAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseContentType(rawHeader: String, url: String): Pair<String, String> {
        val mime = rawHeader.substringBefore(";").trim().lowercase(Locale.ROOT)
        val charset = rawHeader.split(";")
            .drop(1)
            .map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"')
            .orEmpty()

        val resolvedMime = mime.ifBlank { guessMimeFromUrl(url) }
        val resolvedEncoding = if (charset.isNotBlank()) {
            charset
        } else if (resolvedMime.startsWith("text/") ||
            resolvedMime.contains("javascript") ||
            resolvedMime.contains("json") ||
            resolvedMime.contains("svg")
        ) {
            "UTF-8"
        } else {
            ""
        }
        return resolvedMime to resolvedEncoding
    }

    private fun guessMimeFromUrl(url: String): String {
        val path = Uri.parse(url).path?.lowercase(Locale.ROOT).orEmpty()
        return when {
            path.endsWith(".html") || path.endsWith(".htm") || path.isBlank() || path == "/" -> "text/html"
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".flac") -> "audio/flac"
            else -> "application/octet-stream"
        }
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Long): ByteArray? {
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun extractAudioMetadata(file: File): Triple<String, String, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(title, artist, duration)
        } catch (_: Exception) {
            Triple("", "", 0L)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun deriveTitleFromUrl(url: String): String {
        val segment = Uri.parse(url).lastPathSegment.orEmpty()
            .substringBeforeLast(".")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
        return if (segment.length in 2..60 && !segment.all { it.isDigit() }) {
            segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } else {
            "Saved Radio Track"
        }
    }

    private fun sanitizeMetadata(raw: String?): String {
        return raw.orEmpty()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(80)
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            val value = delegate.read()
            if (value != -1) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val read = delegate.read(b, off, toRead)
            if (read > 0) remaining -= read
            return read
        }

        override fun close() {
            delegate.close()
        }
    }

    companion object {
        const val PRIMARY_URL = "https://soniclet.kexora.in"
        private const val PRIMARY_HOST = "soniclet.kexora.in"
        private const val ROOT_HOST = "kexora.in"
        private const val PREFS_NAME = "soniclet_radio_cache_prefs"
        private const val KEY_HAS_CACHED_PAGE = "has_cached_page"

        private const val ASSET_CONNECT_TIMEOUT_MS = 8_000
        private const val ASSET_READ_TIMEOUT_MS = 12_000
        private const val AUDIO_CONNECT_TIMEOUT_MS = 10_000
        private const val AUDIO_READ_TIMEOUT_MS = 30_000

        private const val MAX_ASSET_BYTES = 5L * 1024 * 1024
        private const val MAX_TOTAL_ASSET_CACHE_BYTES = 40L * 1024 * 1024
        private const val MIN_AUDIO_BYTES = 16L * 1024
        private const val MAX_AUDIO_TRACK_BYTES = 25L * 1024 * 1024
        private const val MAX_TOTAL_AUDIO_CACHE_BYTES = 150L * 1024 * 1024

        private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac")
        private val YOUTUBE_AND_MEDIA_HOSTS = listOf(
            "youtube.com",
            "youtube-nocookie.com",
            "youtu.be",
            "ytimg.com",
            "googlevideo.com",
            "googleapis.com",
            "gstatic.com",
            "google.com",
            "doubleclick.net",
            "ggpht.com"
        )

        @Volatile
        private var instance: RadioOfflineCacheManager? = null

        fun getInstance(context: Context): RadioOfflineCacheManager {
            return instance ?: synchronized(this) {
                instance ?: RadioOfflineCacheManager(context).also { instance = it }
            }
        }
    }
}
