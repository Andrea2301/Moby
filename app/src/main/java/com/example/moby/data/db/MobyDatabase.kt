package com.example.moby.data.db

import android.content.Context
import androidx.room.*
import com.example.moby.models.BookAnnotation
import com.example.moby.models.Publication

@Database(entities = [Publication::class, BookAnnotation::class], version = 5, exportSchema = false)
@TypeConverters(PublicationConverters::class)
abstract class MobyDatabase : RoomDatabase() {
    abstract fun publicationDao(): PublicationDao

    companion object {
        @Volatile
        private var INSTANCE: MobyDatabase? = null

        fun getDatabase(context: Context): MobyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MobyDatabase::class.java,
                    "moby_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
