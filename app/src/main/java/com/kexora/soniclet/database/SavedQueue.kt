package com.kexora.soniclet.database

data class SavedQueue(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long = System.currentTimeMillis()
)
