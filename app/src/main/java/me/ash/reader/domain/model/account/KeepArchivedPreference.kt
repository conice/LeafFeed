package me.ash.reader.domain.model.account

import java.util.concurrent.TimeUnit

data class KeepArchivedPreference(val days: Int) {
    init {
        require(days >= 0) { "Archived article retention days cannot be negative" }
    }

    val value: Long
        get() = TimeUnit.DAYS.toMillis(days.toLong())

    val keepForever: Boolean
        get() = days == 0

    companion object {
        private val millisPerDay = TimeUnit.DAYS.toMillis(1)
        val default = KeepArchivedPreference(30)

        fun fromStoredValue(value: Long): KeepArchivedPreference {
            if (value < 0 || value % millisPerDay != 0L) return default
            val days = TimeUnit.MILLISECONDS.toDays(value)
            if (days > Int.MAX_VALUE) return default
            return KeepArchivedPreference(days.toInt())
        }
    }
}
