package me.ash.reader.domain.model.account

/** Describes whether a reader service can persist a behavior across devices. */
enum class CapabilitySupport {
    SYNCED,
    LOCAL_ONLY,
    UNSUPPORTED,
}

enum class SyncCapability {
    SUBSCRIPTIONS,
    GROUPS,
    READ_STATE,
    STARRED_STATE,
    READ_LATER,
    TAGS_AND_NOTES,
}

/**
 * A single source of truth for service differences. Unknown services default to no support instead
 * of making optimistic promises that could lose user state.
 */
object SyncServiceCapabilities {
    fun support(accountType: AccountType, capability: SyncCapability): CapabilitySupport =
        matrix[accountType.id]?.get(capability) ?: CapabilitySupport.UNSUPPORTED

    fun all(accountType: AccountType): Map<SyncCapability, CapabilitySupport> =
        SyncCapability.entries.associateWith { support(accountType, it) }

    private val matrix = mapOf(
        AccountType.Local.id to capabilities(
            synced = emptySet(),
            localOnly = setOf(
                SyncCapability.SUBSCRIPTIONS,
                SyncCapability.GROUPS,
                SyncCapability.READ_STATE,
                SyncCapability.STARRED_STATE,
                SyncCapability.READ_LATER,
                SyncCapability.TAGS_AND_NOTES,
            ),
        ),
        AccountType.Fever.id to capabilities(
            synced = setOf(
                SyncCapability.SUBSCRIPTIONS,
                SyncCapability.GROUPS,
                SyncCapability.READ_STATE,
                SyncCapability.STARRED_STATE,
            ),
            localOnly = setOf(SyncCapability.READ_LATER, SyncCapability.TAGS_AND_NOTES),
        ),
        AccountType.GoogleReader.id to readerApiCapabilities(),
        AccountType.FreshRSS.id to readerApiCapabilities(),
        // These account types are retained for data compatibility, but the current runtime does
        // not provide a remote implementation. Never present them as synchronized.
        AccountType.Inoreader.id to unsupportedCapabilities(),
        AccountType.Feedly.id to unsupportedCapabilities(),
    )

    private fun readerApiCapabilities() = capabilities(
        synced = setOf(
            SyncCapability.SUBSCRIPTIONS,
            SyncCapability.GROUPS,
            SyncCapability.READ_STATE,
            SyncCapability.STARRED_STATE,
        ),
        localOnly = setOf(SyncCapability.READ_LATER, SyncCapability.TAGS_AND_NOTES),
    )

    private fun unsupportedCapabilities() =
        SyncCapability.entries.associateWith { CapabilitySupport.UNSUPPORTED }

    private fun capabilities(
        synced: Set<SyncCapability>,
        localOnly: Set<SyncCapability> = emptySet(),
    ): Map<SyncCapability, CapabilitySupport> =
        SyncCapability.entries.associateWith { capability ->
            when (capability) {
                in synced -> CapabilitySupport.SYNCED
                in localOnly -> CapabilitySupport.LOCAL_ONLY
                else -> CapabilitySupport.UNSUPPORTED
            }
        }
}
