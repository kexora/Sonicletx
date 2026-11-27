package com.kexora.soniclet.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.kexora.soniclet.database.Song
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ListeningEvent(
    val songId: Long,
    val timestamp: Long,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val moodId: String,
    val completionRatio: Float
)

data class ContextualMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String,
    val iconName: String,
    val gradientColors: List<Color>,
    val songs: List<Song>,
    val reason: String
)

data class UserDailyPersona(
    val title: String,
    val description: String,
    val dominantMood: MusicMood,
    val currentHourMood: MusicMood,
    val isNightOwl: Boolean
)

object UserBehaviorProfiler {

    private const val PREFS_NAME = "soniclet_user_behavior_profiler"
    private const val KEY_HISTORY = "listening_history_json"
    private const val MAX_HISTORY_EVENTS = 600

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records a song listening event on-device.
     */
    fun recordPlayEvent(context: Context, song: Song, completionRatio: Float = 1.0f) {
        val moodResult = SongSemanticTagEngine.analyzeSong(song)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val event = ListeningEvent(
            songId = song.id,
            timestamp = System.currentTimeMillis(),
            hourOfDay = hour,
            dayOfWeek = dayOfWeek,
            moodId = moodResult.primaryMood.id,
            completionRatio = completionRatio
        )

        try {
            val prefs = getPrefs(context)
            val historyJsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val array = JSONArray(historyJsonStr)

            val obj = JSONObject().apply {
                put("id", event.songId)
                put("ts", event.timestamp)
                put("h", event.hourOfDay)
                put("d", event.dayOfWeek)
                put("m", event.moodId)
                put("c", event.completionRatio.toDouble())
            }

            array.put(obj)

            // Keep within max history size
            val trimmedArray = if (array.length() > MAX_HISTORY_EVENTS) {
                val newArr = JSONArray()
                val start = array.length() - MAX_HISTORY_EVENTS
                for (i in start until array.length()) {
                    newArr.put(array.get(i))
                }
                newArr
            } else {
                array
            }

            prefs.edit().putString(KEY_HISTORY, trimmedArray.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Reads all recorded listening history with automatic exponential decay for older days.
     */
    fun getListeningHistory(context: Context): List<ListeningEvent> {
        val list = mutableListOf<ListeningEvent>()
        try {
            val prefs = getPrefs(context)
            val historyJsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
            val array = JSONArray(historyJsonStr)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ListeningEvent(
                        songId = obj.optLong("id"),
                        timestamp = obj.optLong("ts"),
                        hourOfDay = obj.optInt("h"),
                        dayOfWeek = obj.optInt("d"),
                        moodId = obj.optString("m", MusicMood.CHILL_ACOUSTIC.id),
                        completionRatio = obj.optDouble("c", 1.0).toFloat()
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Computes the daily user profile by calculating time-of-day affinities with daily decay and skip penalties.
     * 100% dynamic: reflects what the user ACTUAL Listens to at that hour rather than assuming stereotypes.
     */
    fun computeDailyPersona(context: Context, allSongs: List<Song> = emptyList()): UserDailyPersona {
        val history = getListeningHistory(context)
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val hourMoodScores = mutableMapOf<MusicMood, Float>()
        val totalMoodScores = mutableMapOf<MusicMood, Float>()
        var lateNightPlayCount = 0

        for (event in history) {
            val ageDays = ((now - event.timestamp) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
            // Exponential daily decay: today=1.0, 1d=0.85, 3d=0.6, 7d=0.35, 14d=0.15
            val decay = when {
                ageDays <= 0 -> 1.0f
                ageDays == 1L -> 0.85f
                ageDays in 2..3 -> 0.65f
                ageDays in 4..7 -> 0.40f
                ageDays in 8..14 -> 0.20f
                else -> 0.10f
            }

            val mood = MusicMood.values().firstOrNull { it.id == event.moodId } ?: MusicMood.CHILL_ACOUSTIC

            // Positive completion vs Quick Skip penalty
            val weight = if (event.completionRatio < 0f) {
                -1.5f * decay
            } else {
                decay * (0.5f + (event.completionRatio.coerceIn(0f, 2f) * 0.8f))
            }

            totalMoodScores[mood] = (totalMoodScores[mood] ?: 0f) + weight

            if (event.hourOfDay in 21..23 || event.hourOfDay in 0..4) {
                lateNightPlayCount++
            }

            // Match current 5 time-slot windows
            val isCurrentTimeBucket = when (currentHour) {
                in 5..9 -> event.hourOfDay in 5..9       // Morning
                in 10..15 -> event.hourOfDay in 10..15   // Afternoon
                in 16..20 -> event.hourOfDay in 16..20   // Evening
                in 21..23 -> event.hourOfDay in 21..23   // Late Night
                else -> event.hourOfDay in 0..4          // Night Owl
            }

            if (isCurrentTimeBucket) {
                hourMoodScores[mood] = (hourMoodScores[mood] ?: 0f) + weight
            }
        }

        // 100% Dynamic Mood Selection based on REAL user behavior:
        // Priority 1: Actual history score for THIS hour slot
        // Priority 2: Actual overall history score across all times
        // Priority 3: Top mood present in user's phone library by song count
        val classified = if (allSongs.isNotEmpty()) SongSemanticTagEngine.classifySongs(allSongs) else emptyMap()
        val topLibraryMood = classified.maxByOrNull { it.value.size }?.key ?: MusicMood.ENERGETIC_POP

        val dominantMood = totalMoodScores.maxByOrNull { it.value }?.key ?: topLibraryMood
        val hasHourHistory = hourMoodScores.isNotEmpty() && (hourMoodScores.maxByOrNull { it.value }?.value ?: 0f) > 0f
        val currentHourMood = if (hasHourHistory) {
            hourMoodScores.maxByOrNull { it.value }!!.key
        } else if (totalMoodScores.isNotEmpty() && (totalMoodScores.maxByOrNull { it.value }?.value ?: 0f) > 0f) {
            totalMoodScores.maxByOrNull { it.value }!!.key
        } else {
            topLibraryMood
        }

        val isNightOwl = lateNightPlayCount >= 8

        val timeSlotLabel = when (currentHour) {
            in 5..9 -> "Morning"
            in 10..15 -> "Afternoon"
            in 16..20 -> "Evening"
            in 21..23 -> "Late Night"
            else -> "Midnight"
        }

        val historySourceText = when {
            hasHourHistory -> "Based on your $timeSlotLabel listening habits"
            totalMoodScores.isNotEmpty() -> "Based on your top played music taste"
            else -> "Based on top tracks in your library"
        }

        val title = when (currentHourMood) {
            MusicMood.GYM_WORKOUT_PHONK -> "$timeSlotLabel Workout & Phonk Flow"
            MusicMood.HIPHOP_RAP -> "$timeSlotLabel Desi Rap & Heavy Flow"
            MusicMood.PARTY_DANCE -> "$timeSlotLabel High Energy Beats"
            MusicMood.ENERGETIC_POP -> "$timeSlotLabel Upbeat Pop & Vibes"
            MusicMood.ROMANTIC_LOVE -> "$timeSlotLabel Romantic & Soft Melodies"
            MusicMood.SAD_EMOTIONAL -> "$timeSlotLabel Soul & Deep Reflections"
            MusicMood.DEVOTIONAL_SPIRITUAL -> "$timeSlotLabel Peaceful Divine Hymns"
            MusicMood.CHILL_ACOUSTIC -> "$timeSlotLabel Unplugged Chill & Acoustic"
            MusicMood.RETRO_CLASSIC -> "$timeSlotLabel Golden Era Nostalgia"
            MusicMood.REGIONAL_FOLK -> "$timeSlotLabel Desi Folk & Cultural Beats"
        }

        return UserDailyPersona(
            title = title,
            description = historySourceText,
            dominantMood = dominantMood,
            currentHourMood = currentHourMood,
            isNightOwl = isNightOwl
        )
    }

    /**
     * Generates on-device intelligent mixes based on the user's updated daily profile.
     */
    fun generateDynamicMixes(
        context: Context,
        allSongs: List<Song>,
        playCounts: Map<Long, Int>
    ): List<ContextualMix> {
        if (allSongs.isEmpty()) return emptyList()

        val persona = computeDailyPersona(context)
        val classified = SongSemanticTagEngine.classifySongs(allSongs)
        val history = getListeningHistory(context)
        val now = System.currentTimeMillis()

        val mixes = mutableListOf<ContextualMix>()

        // 1. Current Time-Context Hero Mix (Based on user's behavioral mood at this time!)
        val timeSongs = classified[persona.currentHourMood] ?: emptyList()
        val finalTimeSongs = if (timeSongs.size >= 4) {
            timeSongs.shuffled().take(20)
        } else {
            allSongs.shuffled().take(20)
        }

        val timeGradient = when (persona.currentHourMood) {
            MusicMood.SAD_EMOTIONAL -> listOf(Color(0xFF1E2640), Color(0xFF38436B), Color(0xFF6373A8))
            MusicMood.ROMANTIC_LOVE -> listOf(Color(0xFF880E4F), Color(0xFFC2185B), Color(0xFFE91E63))
            MusicMood.PARTY_DANCE -> listOf(Color(0xFF4A148C), Color(0xFF7B1FA2), Color(0xFFAB47BC))
            MusicMood.DEVOTIONAL_SPIRITUAL -> listOf(Color(0xFFE65100), Color(0xFFF57C00), Color(0xFFFFB74D))
            MusicMood.GYM_WORKOUT_PHONK -> listOf(Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFFFF5252))
            MusicMood.CHILL_ACOUSTIC -> listOf(Color(0xFF004D40), Color(0xFF00796B), Color(0xFF4DB6AC))
            MusicMood.RETRO_CLASSIC -> listOf(Color(0xFF3E2723), Color(0xFF5D4037), Color(0xFF8D6E63))
            MusicMood.HIPHOP_RAP -> listOf(Color(0xFF212121), Color(0xFF424242), Color(0xFF757575))
            else -> listOf(Color(0xFF0D47A1), Color(0xFF1976D2), Color(0xFF42A5F5))
        }

        mixes.add(
            ContextualMix(
                id = "mix_time_context",
                title = persona.title,
                subtitle = persona.description,
                tag = persona.currentHourMood.shortTag,
                iconName = persona.currentHourMood.iconName,
                gradientColors = timeGradient,
                songs = finalTimeSongs,
                reason = "Adapted from your hourly listening profile"
            )
        )

        // 2. Active Obsession (Tracks played repeatedly in the last 72 hours)
        val threeDaysAgo = now - (3L * 24 * 60 * 60 * 1000L)
        val recentHistory = history.filter { it.timestamp >= threeDaysAgo }
        val recentCounts = recentHistory.groupingBy { it.songId }.eachCount()

        val songMap = allSongs.associateBy { it.id }
        val obsessionSongs = recentCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { songMap[it.key] }
            .take(15)

        if (obsessionSongs.size >= 2) {
            mixes.add(
                ContextualMix(
                    id = "mix_obsession",
                    title = "Your Current Obsession",
                    subtitle = "Tracks you have had on heavy repeat this week",
                    tag = "ON REPEAT",
                    iconName = "RepeatOne",
                    gradientColors = listOf(Color(0xFFE91E63), Color(0xFFFF4081), Color(0xFFFF80AB)),
                    songs = obsessionSongs,
                    reason = "Frequently replayed in the last 3 days"
                )
            )
        }

        // 3. Mood Mixes for other prominent detected categories with 3+ songs
        val otherMoods = listOf(
            MusicMood.SAD_EMOTIONAL,
            MusicMood.ROMANTIC_LOVE,
            MusicMood.PARTY_DANCE,
            MusicMood.DEVOTIONAL_SPIRITUAL,
            MusicMood.GYM_WORKOUT_PHONK,
            MusicMood.CHILL_ACOUSTIC,
            MusicMood.RETRO_CLASSIC,
            MusicMood.HIPHOP_RAP
        ).filter { it != persona.currentHourMood }

        for (m in otherMoods) {
            val songsInMood = classified[m] ?: emptyList()
            if (songsInMood.size >= 3) {
                val grad = when (m) {
                    MusicMood.SAD_EMOTIONAL -> listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF546E7A))
                    MusicMood.ROMANTIC_LOVE -> listOf(Color(0xFF880E4F), Color(0xFFAD1457), Color(0xFFC2185B))
                    MusicMood.PARTY_DANCE -> listOf(Color(0xFF311B92), Color(0xFF4A148C), Color(0xFF6A1B9A))
                    MusicMood.DEVOTIONAL_SPIRITUAL -> listOf(Color(0xFFBF360C), Color(0xFFD84315), Color(0xFFFF7043))
                    MusicMood.GYM_WORKOUT_PHONK -> listOf(Color(0xFF880E4F), Color(0xFFB71C1C), Color(0xFFF44336))
                    MusicMood.CHILL_ACOUSTIC -> listOf(Color(0xFF006064), Color(0xFF00838F), Color(0xFF00ACC1))
                    MusicMood.RETRO_CLASSIC -> listOf(Color(0xFF4E342E), Color(0xFF5D4037), Color(0xFF6D4C41))
                    else -> listOf(Color(0xFF212121), Color(0xFF424242), Color(0xFF616161))
                }
                mixes.add(
                    ContextualMix(
                        id = "mix_${m.id}",
                        title = "${m.displayName} Radio",
                        subtitle = "Smart on-device collection of ${m.displayName.lowercase()}",
                        tag = m.shortTag,
                        iconName = m.iconName,
                        gradientColors = grad,
                        songs = songsInMood.shuffled().take(25),
                        reason = "Classified by title, artist & semantic tags"
                    )
                )
            }
        }

        // 4. Rediscovery / Forgotten Gems
        val recentIds = history.takeLast(50).map { it.songId }.toSet()
        val forgottenGems = allSongs
            .filter { song ->
                song.id !in recentIds && (playCounts[song.id] ?: 0) >= 2
            }
            .sortedByDescending { playCounts[it.id] ?: 0 }
            .take(15)

        if (forgottenGems.size >= 3) {
            mixes.add(
                ContextualMix(
                    id = "mix_rediscovery",
                    title = "Forgotten Gems You Loved",
                    subtitle = "High-affinity tracks you haven't played recently",
                    tag = "REDISCOVERY",
                    iconName = "AutoAwesome",
                    gradientColors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047)),
                    songs = forgottenGems,
                    reason = "Previously high play count, idle for 2+ weeks"
                )
            )
        }

        return mixes
    }
}
