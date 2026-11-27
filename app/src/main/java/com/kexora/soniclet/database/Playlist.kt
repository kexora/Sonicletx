package com.kexora.soniclet.database


/**
 * Represents a custom user-created audio playlist.
 */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val customCoverUri: String? = null,
    val colorGradientIndex: Int = 0
)
