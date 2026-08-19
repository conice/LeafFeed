package com.conice.morss.infrastructure.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.conice.morss.application.service.AccountService
import com.conice.morss.domain.model.account.KeepArchivedPreference
import com.conice.morss.domain.model.account.SyncBlockListPreference
import com.conice.morss.domain.model.account.SyncIntervalPreference
import com.conice.morss.domain.model.account.SyncOnStartPreference
import com.conice.morss.domain.model.account.SyncOnlyOnWiFiPreference
import com.conice.morss.domain.model.account.SyncOnlyWhenChargingPreference
import com.conice.morss.ui.ext.collectAsStateValue

// Accounts
val LocalSyncInterval =
    compositionLocalOf<SyncIntervalPreference> { SyncIntervalPreference.default }
val LocalSyncOnStart = compositionLocalOf<SyncOnStartPreference> { SyncOnStartPreference.default }
val LocalSyncOnlyOnWiFi =
    compositionLocalOf<SyncOnlyOnWiFiPreference> { SyncOnlyOnWiFiPreference.default }
val LocalSyncOnlyWhenCharging =
    compositionLocalOf<SyncOnlyWhenChargingPreference> { SyncOnlyWhenChargingPreference.default }
val LocalKeepArchived =
    compositionLocalOf<KeepArchivedPreference> { KeepArchivedPreference.default }
val LocalSyncBlockList = compositionLocalOf { SyncBlockListPreference.default }

@Composable
fun AccountSettingsProvider(accountService: AccountService, content: @Composable () -> Unit) {
    val currentAccount = accountService.currentAccountFlow.collectAsStateValue(null)

    CompositionLocalProvider(
        // Accounts
        LocalSyncInterval provides (currentAccount?.syncInterval ?: SyncIntervalPreference.default),
        LocalSyncOnStart provides (currentAccount?.syncOnStart ?: SyncOnStartPreference.default),
        LocalSyncOnlyOnWiFi provides
            (currentAccount?.syncOnlyOnWiFi ?: SyncOnlyOnWiFiPreference.default),
        LocalSyncOnlyWhenCharging provides
            (currentAccount?.syncOnlyWhenCharging ?: SyncOnlyWhenChargingPreference.default),
        LocalKeepArchived provides (currentAccount?.keepArchived ?: KeepArchivedPreference.default),
        LocalSyncBlockList provides
            (currentAccount?.syncBlockList ?: SyncBlockListPreference.default),
    ) {
        content()
    }
}
