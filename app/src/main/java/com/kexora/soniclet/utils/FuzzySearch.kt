package com.kexora.soniclet.utils

import kotlin.math.abs

fun String.fuzzyMatch(query: String): Boolean {
    if (query.isBlank()) return true
    if (this.isBlank()) return false
    
    val source = this.lowercase().trim()
    val target = query.lowercase().trim()
    
    // 1. Fast exact path
    if (source.contains(target)) return true
    
    // 2. Tokenized matching (handles "AS" matching "Arijit Singh")
    val sourceTokens = source.split(Regex("[\\s_\\.-]+")).filter { it.isNotBlank() }
    val targetTokens = target.split(Regex("\\s+")).filter { it.isNotBlank() }
    
    if (targetTokens.isEmpty()) return true

    // Check initials
    val initials = sourceTokens.mapNotNull { it.firstOrNull() }.joinToString("")
    if (initials.contains(target) && target.length >= 2) return true

    // 3. Subsequence matching (handles "ajtsnh" for "Arijit Singh")
    if (target.length >= 3) {
        var sIdx = 0
        var tIdx = 0
        while (sIdx < source.length && tIdx < target.length) {
            if (source[sIdx] == target[tIdx]) tIdx++
            sIdx++
        }
        if (tIdx == target.length) return true
    }

    // 4. Word-level similarity with higher typo tolerance
    var matchCount = 0
    for (t in targetTokens) {
        var found = false
        for (s in sourceTokens) {
            // If prefix matches exactly or with 1 typo
            if (s.startsWith(t)) {
                found = true
                break
            }
            
            // Levenshtein check for typos
            val maxDist = when {
                t.length <= 3 -> 0
                t.length <= 5 -> 1
                else -> 2
            }
            
            if (t.length >= 4) {
                // Check substring similarity
                if (s.length >= t.length) {
                    for (i in 0..s.length - t.length) {
                        if (levenshtein(s.substring(i, i + t.length), t) <= maxDist) {
                            found = true
                            break
                        }
                    }
                }
                if (found) break
                
                // Check if target is a typo of source token
                if (levenshtein(s, t) <= maxDist) {
                    found = true
                    break
                }
            }
        }
        if (found) matchCount++
    }
    
    if (matchCount == targetTokens.size) return true

    return false
}

private fun levenshtein(lhs: String, rhs: String): Int {
    val dp = Array(lhs.length + 1) { IntArray(rhs.length + 1) }
    for (i in 0..lhs.length) dp[i][0] = i
    for (j in 0..rhs.length) dp[0][j] = j

    for (i in 1..lhs.length) {
        for (j in 1..rhs.length) {
            val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[lhs.length][rhs.length]
}
