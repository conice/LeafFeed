package com.conice.morss.ui.page.settings.accounts.connection

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conice.morss.R
import com.conice.morss.domain.model.account.Account
import com.conice.morss.domain.model.account.AccountType
import com.conice.morss.domain.model.account.CapabilitySupport
import com.conice.morss.domain.model.account.SyncCapability
import com.conice.morss.domain.model.account.SyncServiceCapabilities
import com.conice.morss.ui.component.base.Subtitle

@Composable
fun LazyItemScope.AccountConnection(
    account: Account,
) {
    if (account.type.id != AccountType.Local.id) {
        Subtitle(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.connection),
        )
    }
    when (account.type.id) {
        AccountType.Fever.id -> FeverConnection(account)
        AccountType.GoogleReader.id -> GoogleReaderConnection(account)
        AccountType.FreshRSS.id -> FreshRSSConnection(account)
        AccountType.Feedly.id -> {}
        AccountType.Inoreader.id -> {}
    }
    if (
        SyncServiceCapabilities.support(account.type, SyncCapability.TAGS_AND_NOTES) ==
            CapabilitySupport.LOCAL_ONLY
    ) {
        Text(
            text = "Tags, notes, saved searches, and read-later state stay on this device.",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (account.type.id != AccountType.Local.id) {
        Spacer(modifier = Modifier.height(24.dp))
    }
}
