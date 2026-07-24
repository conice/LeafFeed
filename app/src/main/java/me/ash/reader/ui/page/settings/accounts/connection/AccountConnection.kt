package me.ash.reader.ui.page.settings.accounts.connection

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
import me.ash.reader.R
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.account.CapabilitySupport
import me.ash.reader.domain.model.account.SyncCapability
import me.ash.reader.domain.model.account.SyncServiceCapabilities
import me.ash.reader.ui.component.base.Subtitle

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
