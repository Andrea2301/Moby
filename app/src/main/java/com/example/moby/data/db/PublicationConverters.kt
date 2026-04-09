package com.example.moby.data.db

import androidx.room.TypeConverter
import com.example.moby.models.PublicationFormat

class PublicationConverters {
    @TypeConverter
    fun fromFormat(format: PublicationFormat): String = format.name

    @TypeConverter
    fun toFormat(name: String): PublicationFormat = PublicationFormat.valueOf(name)
}
