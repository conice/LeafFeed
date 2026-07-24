package me.ash.reader.domain.model.account

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncServiceCapabilitiesTest {
    @Test
    fun `local account keeps every capability on device`() {
        assertEquals(
            SyncCapability.entries.toSet(),
            SyncServiceCapabilities.all(AccountType.Local)
                .filterValues { it == CapabilitySupport.LOCAL_ONLY }
                .keys,
        )
    }

    @Test
    fun `reader services do not promise collection synchronization`() {
        listOf(AccountType.GoogleReader, AccountType.FreshRSS)
            .forEach { type ->
                assertEquals(
                    CapabilitySupport.LOCAL_ONLY,
                    SyncServiceCapabilities.support(type, SyncCapability.TAGS_AND_NOTES),
                )
            }
    }

    @Test
    fun `account types without a remote implementation are unsupported`() {
        listOf(AccountType.Feedly, AccountType.Inoreader).forEach { type ->
            assertEquals(
                setOf(CapabilitySupport.UNSUPPORTED),
                SyncServiceCapabilities.all(type).values.toSet(),
            )
        }
    }
}
