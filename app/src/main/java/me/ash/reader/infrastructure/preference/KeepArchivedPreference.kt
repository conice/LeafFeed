package me.ash.reader.infrastructure.preference

import android.content.Context
import java.util.concurrent.TimeUnit
import me.ash.reader.R
import me.ash.reader.ui.page.settings.accounts.AccountViewModel

data class KeepArchivedPreference(val days: Int) {
    init {
        require(days >= 0) { "Archived article retention days cannot be negative" }
    }

    val value: Long
        get() = TimeUnit.DAYS.toMillis(days.toLong())

    val keepForever: Boolean
        get() = days == 0

    fun put(accountId: Int, viewModel: AccountViewModel) {
        viewModel.update(accountId) { copy(keepArchived = this@KeepArchivedPreference) }
    }

    fun toDesc(context: Context): String =
        if (keepForever) {
            context.getString(R.string.always)
        } else {
            context.resources.getQuantityString(R.plurals.days, days, days)
        }

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
