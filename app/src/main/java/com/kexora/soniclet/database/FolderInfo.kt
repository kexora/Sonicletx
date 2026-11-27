package com.kexora.soniclet.database


/**
 * Represents a device storage folder containing scanned audio files.
 */
data class FolderInfo(
    val path: String,
    val name: String,
    val songCount: Int,
    val isExcluded: Boolean = false
)
