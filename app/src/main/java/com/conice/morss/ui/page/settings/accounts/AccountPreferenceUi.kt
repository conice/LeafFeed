package com.conice.morss.ui.page.settings.accounts

import android.content.Context
import com.conice.morss.R
import com.conice.morss.domain.model.account.KeepArchivedPreference
import com.conice.morss.domain.model.account.SyncBlockList
import com.conice.morss.domain.model.account.SyncIntervalPreference
import com.conice.morss.domain.model.account.SyncOnStartPreference
import com.conice.morss.domain.model.account.SyncOnlyOnWiFiPreference
import com.conice.morss.domain.model.account.SyncOnlyWhenChargingPreference

internal fun SyncIntervalPreference.put(accountId: Int, viewModel: AccountViewModel) {
    viewModel.update(accountId) { copy(syncInterval = this@put) }
}

internal fun SyncOnStartPreference.put(accountId: Int, viewModel: AccountViewModel) {
    viewModel.update(accountId) { copy(syncOnStart = this@put) }
}

internal fun SyncOnlyOnWiFiPreference.put(accountId: Int, viewModel: AccountViewModel) {
    viewModel.update(accountId) { copy(syncOnlyOnWiFi = this@put) }
}

internal fun SyncOnlyWhenChargingPreference.put(accountId: Int, viewModel: AccountViewModel) {
    viewModel.update(accountId) { copy(syncOnlyWhenCharging = this@put) }
}

internal fun KeepArchivedPreference.put(accountId: Int, viewModel: AccountViewModel) {
    viewModel.update(accountId) { copy(keepArchived = this@put) }
}

internal fun putSyncBlockList(
    accountId: Int,
    viewModel: AccountViewModel,
    syncBlockList: SyncBlockList,
) {
    viewModel.update(accountId) { copy(syncBlockList = syncBlockList) }
}

internal fun SyncIntervalPreference.toDesc(context: Context): String =
    context.getString(
        when (this) {
            SyncIntervalPreference.Manually -> R.string.manually
            SyncIntervalPreference.Every15Minutes -> R.string.every_15_minutes
            SyncIntervalPreference.Every30Minutes -> R.string.every_30_minutes
            SyncIntervalPreference.Every1Hour -> R.string.every_1_hour
            SyncIntervalPreference.Every2Hours -> R.string.every_2_hours
            SyncIntervalPreference.Every3Hours -> R.string.every_3_hours
            SyncIntervalPreference.Every6Hours -> R.string.every_6_hours
            SyncIntervalPreference.Every12Hours -> R.string.every_12_hours
            SyncIntervalPreference.Every1Day -> R.string.every_1_day
        }
    )

internal fun KeepArchivedPreference.toDesc(context: Context): String =
    if (keepForever) context.getString(R.string.always)
    else context.resources.getQuantityString(R.plurals.days, days, days)
