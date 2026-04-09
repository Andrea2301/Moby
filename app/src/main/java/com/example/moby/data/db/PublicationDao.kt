package com.example.moby.data.db

import androidx.room.*
import com.example.moby.models.BookAnnotation
import com.example.moby.models.Publication
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY lastRead DESC")
    fun getAllPublications(): Flow<List<Publication>>

    @Query("SELECT * FROM publications WHERE id = :id")
    suspend fun getPublicationById(id: String): Publication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPublication(publication: Publication)

    @Delete
    suspend fun deletePublication(publication: Publication)

    @Update
    suspend fun updatePublication(publication: Publication)

    @Query("DELETE FROM publications")
    suspend fun deleteAllPublications()

    // --- ANNOTATIONS ---
    @Query("SELECT * FROM annotations WHERE publicationId = :pubId AND chapterPath = :chapterPath")
    suspend fun getAnnotationsForChapter(pubId: String, chapterPath: String): List<BookAnnotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: BookAnnotation)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: String)
}
