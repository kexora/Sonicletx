package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import java.util.Locale

/**
 * High-performance on-device Semantic Tag & Mood Analyzer for Soniclet.
 * Uses an exhaustive multi-lingual keyword dictionary (Hindi, Urdu, English, Punjabi,
 * Haryanvi, Bhojpuri, Sufi, etc.) across song title, artist, album, genre, and file path.
 */
enum class MusicMood(
    val id: String,
    val displayName: String,
    val shortTag: String,
    val iconName: String
) {
    SAD_EMOTIONAL("sad_emotional", "Sad & Soulful", "DARD & SOULFUL", "HeartBroken"),
    ROMANTIC_LOVE("romantic_love", "Romantic & Ishq", "ROMANTIC VIBE", "Favorite"),
    PARTY_DANCE("party_dance", "Party & Club Hits", "PARTY GROOVE", "Celebration"),
    DEVOTIONAL_SPIRITUAL("devotional_spiritual", "Devotional & Bhakti", "DIVINE & PEACE", "SelfImprovement"),
    GYM_WORKOUT_PHONK("gym_workout_phonk", "Workout & Phonk", "HIGH ENERGY", "Bolt"),
    CHILL_ACOUSTIC("chill_acoustic", "Chill & Acoustic", "CHILL & ACOUSTIC", "Spa"),
    RETRO_CLASSIC("retro_classic", "Retro & Golden Era", "NOSTALGIA", "Album"),
    HIPHOP_RAP("hiphop_rap", "Hip-Hop & Desi Rap", "RHYME & BARS", "Mic"),
    REGIONAL_FOLK("regional_folk", "Regional & Folk", "DESI FOLK", "MusicNote"),
    ENERGETIC_POP("energetic_pop", "Upbeat Pop", "UPBEAT VIBES", "TrendingUp")
}

data class SongMoodScore(
    val song: Song,
    val primaryMood: MusicMood,
    val secondaryMoods: List<MusicMood>,
    val confidence: Float,
    val matchedKeywords: List<String>
)

object SongSemanticTagEngine {

    // 1. Sad / Heartbreak / Emotional / Dard / Judai Keywords
    private val SAD_KEYWORDS = listOf(
        "dard", "judai", "bewafa", "sad", "broken", "alone", "crying", "tears", "dil tod", "tanhai",
        "slowed", "reverb", "lofi", "dukh", "gam", "gham", "virah", "alvida", "rona", "chhod", "yaad",
        "ashq", "bikhre", "dhokha", "heartbreak", "depressed", "melancholy", "grief", "pain", "hurt",
        "sorrow", "tear", "lonely", "darkness", "empty", "distance", "roye", "ansu", "ashru", "judaiyan",
        "marjaani", "dard e dil", "khamoshi", "tanha", "ruswa", "fariyaad", "dard e judai", "separation",
        "broken heart", "sad lofi", "unplugged sad", "pain inside", "crying soul", "missing you", "lost love",
        "khoya", "tujhe bhula diya", "chhod diya", "tanhaai", "saza", "barbaad", "tadap", "bedardi", "bewafai"
    )

    // 2. Romantic / Love / Ishq / Pyar / Mohabbat Keywords (Hindi, Bhojpuri, English)
    private val ROMANTIC_KEYWORDS = listOf(
        "love", "ishq", "pyar", "pyaar", "mohabbat", "romantic", "dil", "humsafar", "jaan", "tum hi ho",
        "dhadkan", "valentine", "crush", "deewana", "sanam", "romance", "sweet", "baatein", "tere bina",
        "mehboob", "aashiq", "chahat", "saath", "janam", "naina", "aankhein", "tujhe", "pyaar lafzon",
        "pehla nasha", "forever", "passion", "couple", "darling", "kiss", "heartbeat", "soulmate", "deewani",
        "pashmina", "kesariya", "sajde", "mast magan", "raabta", "sukoon", "zindagi", "haseen", "khwaab",
        "ishqbaazi", "ishq vala", "love story", "slow love", "romantic mashup", "pehli nazar", "deewanapan",
        "meri jaan", "sweetheart", "lovesick", "in love", "falling for you", "together", "romance mix", "ishqan",
        "raja ji", "piya", "sajan", "sajna", "balma", "balam", "dhadkela", "kareja", "jaaneman", "babu", "shona",
        "humdard", "shayad", "agar tum saath", "tera ban jaunga", "apna bana le", "heeriye", "satranga", "ve kamleya",
        "hamar", "tohar", "tohara", "dilwa", "balam ji", "sainya", "saiyaan", "piya ji", "jaan ho", "love song",
        "perfect", "all of me", "thousand years", "thinking out loud", "until i found you", "lover", "my love"
    )

    // 3. Party / Dance / Club / DJ / Remix / EDM Keywords (Hindi, Bhojpuri, Punjabi, English)
    private val PARTY_KEYWORDS = listOf(
        "remix", "bass boost", "club", "party", "dance", "dhol", "bhangra", "dj", "mashup", "daru",
        "electronic", "nach", "thumka", "sharaab", "dhamaka", "celebration", "banger", "festival",
        "edm", "disco", "rave", "pegg", "patiala", "sharab", "masti", "groove", "loud", "drop", "beat",
        "daru badnaam", "hookah", "daaru", "hangover", "tequila", "vodka", "party mix", "club mix",
        "dance mix", "dj remix", "bass boosted", "high bass", "electro", "house", "techno", "dubstep",
        "twerk", "clubbing", "saturday night", "friday night", "boom", "dj drop", "wedding dance", "dhol mix",
        "lollipop", "kamariya", "lahanga", "lehenga", "ghagra", "choli", "arkestra", "orchestra", "holi",
        "barati", "baraat", "shaadi", "sangeet", "kala chashma", "abhi toh party", "ghungroo", "nachde",
        "kamar", "patli kamariya", "nagin", "djsong", "bhojpuri dj", "stage show", "item", "dhinchak",
        "party anthem", "uptown funk", "blinding lights", "levitating", "titanium", "turn down for what"
    )

    // 4. Devotional / Spiritual / Bhajan / Aarti / Mantra Keywords (Hindi, Bhojpuri, Sanskrit)
    private val DEVOTIONAL_KEYWORDS = listOf(
        "bhajan", "aarti", "mantra", "shiv", "krishna", "ram", "prarthana", "chalisa", "hanuman",
        "mahadev", "ganesh", "radhe", "mata", "sufi", "qawwali", "bhakti", "kirtan", "shloka",
        "devotional", "god", "guru", "waheguru", "shabad", "allah", "khwaja", "sai", "govind",
        "har har", "jai", "om", "peaceful prayer", "bhole", "shambhu", "rudra", "shivaay", "kailash",
        "ganpati", "morya", "laxmi", "saraswati", "durga", "kali", "stotram", "jaap", "naam", "gurbani",
        "simran", "shyam", "vrindavan", "barsana", "govinda", "gopala", "ramayana", "sundarkand",
        "chhath", "chhathi maiya", "bolbam", "bol bam", "kanwar", "kawad", "devi geet", "navratri",
        "pachra", "bholenath", "mahakal", "khatu shyam", "bajrangbali", "sankatmochan", "amritwani",
        "sita ram", "radha", "maiya", "sherawali", "kedarnath", "ayodhya", "prabhu", "ishwar", "vandana",
        "gayatri", "mahamrityunjaya", "worship", "gospel", "hymn", "spiritual", "prayer"
    )

    // 5. Gym / Workout / Phonk / High Energy / Motivation Keywords
    private val GYM_PHONK_KEYWORDS = listOf(
        "workout", "gym", "phonk", "drift", "hardcore", "energy", "beast", "sigma", "brazilian phonk",
        "hyper", "motivation", "speed", "power", "aggressive", "rock", "metal", "heavy", "lift",
        "training", "adrenaline", "beast mode", "fast", "drill", "rage", "raw", "deadlift", "pump",
        "pre workout", "cardio", "pushup", "muscle", "fitness", "bodybuilding", "montagem", "funk",
        "gym phonk", "sigma phonk", "speed up", "nightcore", "intense", "heavy metal", "heavy bass workout",
        "unstoppable", "believer", "till i collapse", "lose yourself", "zinda", "chak de", "sultan",
        "dangal", "kar har maidaan", "brothers anthem", "apna time aayega", "sher", "warrior", "hustle",
        "aarambh", "bhaag milkha", "lakshya", "josh", "badass", "eye of the tiger", "stronger", "thunder",
        "hall of fame", "remember the name", "run", "sprint", "hype"
    )

    // 6. Chill / Acoustic / Sleep / Calm / Nature / Unplugged Keywords
    private val CHILL_ACOUSTIC_KEYWORDS = listOf(
        "acoustic", "unplugged", "peace", "calm", "rain", "morning", "coffee", "chill", "relax", "sleep",
        "meditation", "nature", "ambient", "study", "soothing", "soft", "serene", "breeze", "quiet", "zen",
        "lo-fi", "lofi", "slowed", "reverb", "mellow", "gentle", "smooth", "guitar", "piano", "flute",
        "sunset", "clouds", "waves", "deep sleep", "mindfulness", "restful", "binaural", "healing",
        "spa", "chillout", "slow morning", "cozy", "fireside", "camp", "warmth", "gentle breeze",
        "acoustic version", "piano cover", "late night", "midnight", "drive", "sukoon", "instrumental",
        "bansuri", "sitar", "night drive", "soft hindi", "slow reverb", "vibes", "indie", "breezy"
    )

    // 7. Retro / Golden Era / 90s / 80s / Classic / Ghazal Keywords
    private val RETRO_KEYWORDS = listOf(
        "retro", "90s", "80s", "70s", "60s", "classic", "evergreen", "vintage", "golden era", "purane",
        "old is gold", "ghazal", "jagjit", "kishore", "rafi", "lata", "mukesh", "asha", "pankaj", "mehdi",
        "nazm", "shaayari", "old hindi", "nostalgia", "memories", "yaadein", "mohammed rafi", "kishore kumar",
        "lata mangeshkar", "mukesh kumar", "asha bhosle", "rd burman", "sd burman", "hemant kumar", "mushaira"
    )

    // 8. Hip-Hop / Rap / Desi Hip Hop Keywords
    private val HIPHOP_KEYWORDS = listOf(
        "rap", "hip hop", "desi hip hop", "dhh", "flow", "rhyme", "bars", "bohemia", "emiway", "raftaar",
        "krishna", "krsna", "mc stan", "sidhu", "moosewala", "karan aujla", "shubh", "freestyle", "cypher",
        "underground", "trap", "drill", "diss", "cypher", "beef", "gangsta", "gully", "divine", "naezy",
        "badshah rap", "yo yo rap", "cypher mix", "808", "boom bap"
    )

    // 9. Regional / Folk Keywords
    private val REGIONAL_FOLK_KEYWORDS = listOf(
        "punjabi", "haryanvi", "bhojpuri", "garba", "dandiya", "rajasthani", "marwadi", "bhojpuriya",
        "dehati", "folk", "desi", "ragni", "boliyan", "dholak", "shehnai", "chhattisgarhi", "bengali folk",
        "marathi", "koli", "lavani", "odia", "gujarati", "tamizha", "telugu", "kannada", "malayalam"
    )

    // Artist to Mood Heuristics (Hindi, Bhojpuri, Punjabi, English)
    private val ARTIST_MOOD_MAP: Map<String, List<MusicMood>> = mapOf(
        "arijit singh" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.SAD_EMOTIONAL),
        "atif aslam" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.SAD_EMOTIONAL),
        "shreya ghoshal" to listOf(MusicMood.ROMANTIC_LOVE),
        "sonu nigam" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.RETRO_CLASSIC),
        "udit narayan" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.RETRO_CLASSIC),
        "kumar sanu" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.RETRO_CLASSIC),
        "alka yagnik" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.RETRO_CLASSIC),
        "kk" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.RETRO_CLASSIC),
        "jagjit singh" to listOf(MusicMood.RETRO_CLASSIC, MusicMood.CHILL_ACOUSTIC),
        "kishore kumar" to listOf(MusicMood.RETRO_CLASSIC, MusicMood.ROMANTIC_LOVE),
        "mohammed rafi" to listOf(MusicMood.RETRO_CLASSIC, MusicMood.ROMANTIC_LOVE),
        "lata mangeshkar" to listOf(MusicMood.RETRO_CLASSIC, MusicMood.DEVOTIONAL_SPIRITUAL),
        "prateek kuhad" to listOf(MusicMood.CHILL_ACOUSTIC, MusicMood.ROMANTIC_LOVE),
        "anuv jain" to listOf(MusicMood.CHILL_ACOUSTIC, MusicMood.ROMANTIC_LOVE),
        "sidhu moosewala" to listOf(MusicMood.GYM_WORKOUT_PHONK, MusicMood.HIPHOP_RAP),
        "karan aujla" to listOf(MusicMood.PARTY_DANCE, MusicMood.GYM_WORKOUT_PHONK),
        "shubh" to listOf(MusicMood.GYM_WORKOUT_PHONK, MusicMood.CHILL_ACOUSTIC),
        "ap dhillon" to listOf(MusicMood.CHILL_ACOUSTIC, MusicMood.PARTY_DANCE),
        "diljit dosanjh" to listOf(MusicMood.PARTY_DANCE, MusicMood.ROMANTIC_LOVE),
        "badshah" to listOf(MusicMood.PARTY_DANCE, MusicMood.HIPHOP_RAP),
        "yo yo honey singh" to listOf(MusicMood.PARTY_DANCE, MusicMood.HIPHOP_RAP),
        "neha kakkar" to listOf(MusicMood.PARTY_DANCE, MusicMood.ROMANTIC_LOVE),
        "pawan singh" to listOf(MusicMood.PARTY_DANCE, MusicMood.DEVOTIONAL_SPIRITUAL),
        "khesari lal" to listOf(MusicMood.PARTY_DANCE, MusicMood.ROMANTIC_LOVE),
        "neelkamal singh" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.PARTY_DANCE),
        "shilpi raj" to listOf(MusicMood.PARTY_DANCE, MusicMood.REGIONAL_FOLK),
        "akshara singh" to listOf(MusicMood.PARTY_DANCE, MusicMood.DEVOTIONAL_SPIRITUAL),
        "ritesh pandey" to listOf(MusicMood.PARTY_DANCE, MusicMood.ROMANTIC_LOVE),
        "pramod premi" to listOf(MusicMood.PARTY_DANCE, MusicMood.REGIONAL_FOLK),
        "kalpana patowary" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL, MusicMood.REGIONAL_FOLK),
        "sharda sinha" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL, MusicMood.REGIONAL_FOLK),
        "manoj tiwari" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL, MusicMood.REGIONAL_FOLK),
        "kordhell" to listOf(MusicMood.GYM_WORKOUT_PHONK),
        "dvrst" to listOf(MusicMood.GYM_WORKOUT_PHONK),
        "emin" to listOf(MusicMood.GYM_WORKOUT_PHONK),
        "eminem" to listOf(MusicMood.GYM_WORKOUT_PHONK, MusicMood.HIPHOP_RAP),
        "imagine dragons" to listOf(MusicMood.GYM_WORKOUT_PHONK),
        "linkin park" to listOf(MusicMood.GYM_WORKOUT_PHONK),
        "alan walker" to listOf(MusicMood.PARTY_DANCE, MusicMood.GYM_WORKOUT_PHONK),
        "ed sheeran" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.CHILL_ACOUSTIC),
        "the weeknd" to listOf(MusicMood.PARTY_DANCE, MusicMood.CHILL_ACOUSTIC),
        "jubin nautiyal" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.DEVOTIONAL_SPIRITUAL),
        "hansraj raghuwanshi" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL),
        "lakhbir singh lakkha" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL),
        "gulshan kumar" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL),
        "anuradha paudwal" to listOf(MusicMood.DEVOTIONAL_SPIRITUAL, MusicMood.ROMANTIC_LOVE),
        "armaan malik" to listOf(MusicMood.ROMANTIC_LOVE, MusicMood.ENERGETIC_POP)
    )

    /**
     * Inspects a song's metadata (Title, Artist, Album, Genre, Path) and extracts mood scores.
     */
    fun analyzeSong(song: Song): SongMoodScore {
        val titleNorm = song.title.lowercase(Locale.ROOT)
        val artistNorm = song.artist.lowercase(Locale.ROOT)
        val albumNorm = song.album.lowercase(Locale.ROOT)
        val genreNorm = (song.genre ?: "").lowercase(Locale.ROOT)
        val pathNorm = (song.filePath.ifEmpty { song.folderPath }).lowercase(Locale.ROOT)

        val fullText = "$titleNorm $artistNorm $albumNorm $genreNorm $pathNorm"

        val scores = mutableMapOf<MusicMood, Float>()
        val matchedWords = mutableListOf<String>()

        fun scoreKeywords(keywords: List<String>, mood: MusicMood, weight: Float) {
            for (kw in keywords) {
                if (fullText.contains(kw)) {
                    val current = scores[mood] ?: 0f
                    scores[mood] = current + weight
                    if (!matchedWords.contains(kw)) {
                        matchedWords.add(kw)
                    }
                }
            }
        }

        // Apply weights
        scoreKeywords(SAD_KEYWORDS, MusicMood.SAD_EMOTIONAL, 2.5f)
        scoreKeywords(ROMANTIC_KEYWORDS, MusicMood.ROMANTIC_LOVE, 2.2f)
        scoreKeywords(PARTY_KEYWORDS, MusicMood.PARTY_DANCE, 2.4f)
        scoreKeywords(DEVOTIONAL_KEYWORDS, MusicMood.DEVOTIONAL_SPIRITUAL, 3.0f)
        scoreKeywords(GYM_PHONK_KEYWORDS, MusicMood.GYM_WORKOUT_PHONK, 2.8f)
        scoreKeywords(CHILL_ACOUSTIC_KEYWORDS, MusicMood.CHILL_ACOUSTIC, 2.3f)
        scoreKeywords(RETRO_KEYWORDS, MusicMood.RETRO_CLASSIC, 2.5f)
        scoreKeywords(HIPHOP_KEYWORDS, MusicMood.HIPHOP_RAP, 2.6f)
        scoreKeywords(REGIONAL_FOLK_KEYWORDS, MusicMood.REGIONAL_FOLK, 2.2f)

        // Artist specific boosts
        for ((artKey, moods) in ARTIST_MOOD_MAP) {
            if (artistNorm.contains(artKey)) {
                for (m in moods) {
                    scores[m] = (scores[m] ?: 0f) + 3.0f
                }
            }
        }

        // Duration / acoustic heuristic hints
        if (song.duration in 45000L..150000L && scores.containsKey(MusicMood.GYM_WORKOUT_PHONK)) {
            scores[MusicMood.GYM_WORKOUT_PHONK] = (scores[MusicMood.GYM_WORKOUT_PHONK] ?: 0f) + 1.2f
        }
        if (song.duration > 320000L && (scores.containsKey(MusicMood.RETRO_CLASSIC) || scores.containsKey(MusicMood.DEVOTIONAL_SPIRITUAL))) {
            scores[MusicMood.RETRO_CLASSIC] = (scores[MusicMood.RETRO_CLASSIC] ?: 0f) + 1.0f
        }

        // Sort by score
        val sorted = scores.entries.sortedByDescending { it.value }

        return if (sorted.isNotEmpty() && sorted.first().value > 0f) {
            val primary = sorted.first().key
            val secondary = sorted.drop(1).map { it.key }
            SongMoodScore(
                song = song,
                primaryMood = primary,
                secondaryMoods = secondary,
                confidence = sorted.first().value.coerceAtMost(10f) / 10f,
                matchedKeywords = matchedWords
            )
        } else {
            // Default heuristic fallback: based on title length or generic chill/pop
            val fallbackMood = when {
                song.duration < 180000L -> MusicMood.ENERGETIC_POP
                song.title.length > 25 -> MusicMood.RETRO_CLASSIC
                else -> MusicMood.CHILL_ACOUSTIC
            }
            SongMoodScore(
                song = song,
                primaryMood = fallbackMood,
                secondaryMoods = emptyList(),
                confidence = 0.3f,
                matchedKeywords = emptyList()
            )
        }
    }

    /**
     * Classifies and filters songs for the Home screen category chips:
     * "All", "Workout", "Devotional", "Chill", "Romantic", "Party"
     */
    fun filterSongsForHomeCategory(
        category: String,
        songs: List<Song>,
        userMoods: Map<Long, String> = emptyMap()
    ): List<Song> {
        if (category.equals("All", ignoreCase = true) || songs.isEmpty()) return songs

        val targetMoods: Set<MusicMood> = when (category) {
            "Workout" -> setOf(MusicMood.GYM_WORKOUT_PHONK, MusicMood.HIPHOP_RAP, MusicMood.ENERGETIC_POP)
            "Devotional" -> setOf(MusicMood.DEVOTIONAL_SPIRITUAL)
            "Chill" -> setOf(MusicMood.CHILL_ACOUSTIC, MusicMood.SAD_EMOTIONAL, MusicMood.RETRO_CLASSIC)
            "Romantic" -> setOf(MusicMood.ROMANTIC_LOVE)
            "Party" -> setOf(MusicMood.PARTY_DANCE, MusicMood.ENERGETIC_POP, MusicMood.REGIONAL_FOLK)
            else -> emptySet()
        }

        val scoredMatches = mutableListOf<Pair<Song, Float>>()
        for (song in songs) {
            val explicitUserMood = userMoods[song.id]
            if (explicitUserMood != null && explicitUserMood.equals(category, ignoreCase = true)) {
                scoredMatches.add(song to 10f)
                continue
            }
            val analysis = analyzeSong(song)
            if (analysis.matchedKeywords.isNotEmpty() || analysis.confidence > 0.35f) {
                if (analysis.primaryMood in targetMoods) {
                    scoredMatches.add(song to (analysis.confidence + 2f))
                } else if (analysis.secondaryMoods.any { it in targetMoods }) {
                    scoredMatches.add(song to (analysis.confidence + 1f))
                }
            }
        }

        return scoredMatches
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Batch analyze songs and group them by mood with confidence threshold.
     */
    fun classifySongs(songs: List<Song>): Map<MusicMood, List<Song>> {
        val map = mutableMapOf<MusicMood, MutableList<Song>>()

        for (song in songs) {
            val res = analyzeSong(song)
            map.getOrPut(res.primaryMood) { mutableListOf() }.add(song)
            for (sec in res.secondaryMoods.take(1)) {
                val secList = map.getOrPut(sec) { mutableListOf() }
                if (!secList.contains(song)) {
                    secList.add(song)
                }
            }
        }
        return map.filterValues { it.isNotEmpty() }
    }
}
