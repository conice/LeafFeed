package com.conice.morss.domain.model.account

import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import com.conice.morss.domain.model.account.SyncOnStartPreference

/**
 * Provide [TypeConverter] of [SyncOnStartPreference] for [RoomDatabase].
 */
class SyncOnStartConverters {

    @TypeConverter
    fun toSyncOnStart(syncOnStart: Boolean): SyncOnStartPreference {
        return SyncOnStartPreference.values.find { it.value == syncOnStart } ?: SyncOnStartPreference.default
    }

    @TypeConverter
    fun fromSyncOnStart(syncOnStart: SyncOnStartPreference): Boolean {
        return syncOnStart.value
    }
}
