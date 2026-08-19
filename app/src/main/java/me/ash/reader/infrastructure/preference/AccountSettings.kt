package me.ash.reader.infrastructure.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import me.ash.reader.application.service.AccountService
import me.ash.reader.domain.model.account.KeepArchivedPreference
import me.ash.reader.domain.model.account.SyncBlockListPreference
import me.ash.reader.domain.model.account.SyncIntervalPreference
import me.ash.reader.domain.model.account.SyncOnStartPreference
import me.ash.reader.domain.model.account.SyncOnlyOnWiFiPreference
import me.ash.reader.domain.model.account.SyncOnlyWhenChargingPreference
import me.ash.reader.ui.ext.collectAsStateValue

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
