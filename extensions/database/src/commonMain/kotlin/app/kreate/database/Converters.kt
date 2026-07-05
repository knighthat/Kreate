package app.kreate.database

import androidx.room.TypeConverter
import androidx.room.TypeConverters

@TypeConverters
object Converters {

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return stringList.joinToString(separator = ",")
    }
}