package com.example.moby.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "annotations")
data class BookAnnotation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val publicationId: String,
    val chapterPath: String, // e.g. OEBPS/Text/chapter1.html
    val cfiInfo: String, // serialized xpath/range info
    val selectedText: String,
    val colorHex: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
