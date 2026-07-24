package me.ash.reader.domain.model.account

import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import me.ash.reader.infrastructure.preference.KeepArchivedPreference

/**
 * Provide [TypeConverter] of [KeepArchivedPreference] for [RoomDatabase].
 */
class KeepArchivedConverters {

    @TypeConverter
    fun toKeepArchived(keepArchived: Long): KeepArchivedPreference {
        return KeepArchivedPreference.fromStoredValue(keepArchived)
    }

    @TypeConverter
    fun fromKeepArchived(keepArchived: KeepArchivedPreference): Long {
        return keepArchived.value
    }
}
