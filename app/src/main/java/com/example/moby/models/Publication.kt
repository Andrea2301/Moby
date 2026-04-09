package com.example.moby.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class PublicationFormat {
    EPUB, CBZ, PDF, OTHER
}

@Entity(tableName = "publications")
data class Publication(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "Unknown Author",
    val format: PublicationFormat,
    val coverUrl: String? = null,
    val totalPages: Int = 0,
    val currentPosition: Int = 0,
    val filePath: String,
    val lastRead: Long = System.currentTimeMillis(),
    val isVerticalMode: Boolean = false
) {
    val progress: Float
        get() = if (totalPages > 0) currentPosition.toFloat() / totalPages else 0f
}
