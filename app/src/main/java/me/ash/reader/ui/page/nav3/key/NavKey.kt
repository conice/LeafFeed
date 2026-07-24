package me.ash.reader.ui.page.nav3.key

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    // Startup
    @Serializable data object Startup : Route

    // Home
    @Serializable data object Feeds : Route

    @Serializable data object SubscriptionReport : Route

    //    @Serializable data object Flow : Route

    @Serializable enum class ReadingSource { Flow, AiSummary }

    @Serializable
    data class Reading(
        val articleId: String?,
        val source: ReadingSource = ReadingSource.Flow,
    ) : Route {
        companion object {
            val Saver: Saver<Reading, Any> = listSaver(
                save = { listOf(it.articleId.orEmpty(), it.source.name) },
                restore = {
                    Reading(
                        articleId = it.first().ifEmpty { null },
                        source = it.getOrNull(1)?.let { value -> ReadingSource.valueOf(value) }
                            ?: ReadingSource.Flow,
                    )
                },
            )
        }
    }

    // Settings
    @Serializable data object Settings : Route
    @Serializable data object AiSettings : Route
    @Serializable data object ReadingOptions : Route
    @Serializable data object PodcastSettings : Route
    @Serializable data object NotificationSettings : Route
    @Serializable data object DataPrivacySettings : Route
    @Serializable data object SyncStatus : Route
    @Serializable data object PodcastLibrary : Route
    @Serializable data object CollectionManager : Route
    @Serializable data object RuleManager : Route

    // Accounts
    @Serializable data object Accounts : Route

    @Serializable data class AccountDetails(val accountId: Int) : Route

    @Serializable data object AddAccounts : Route

    // Color & Style
    @Serializable data object ColorAndStyle : Route

    @Serializable data object DarkTheme : Route

    @Serializable data object FeedsPageStyle : Route

    @Serializable data object FlowPageStyle : Route

    @Serializable data object ReadingPageStyle : Route

    @Serializable data object ReadingBoldCharacters : Route

    @Serializable data object ReadingPageTitle : Route

    @Serializable data object ReadingPageText : Route

    @Serializable data object ReadingPageImage : Route

    @Serializable data object ReadingPageVideo : Route

    // Interaction
    @Serializable data object Interaction : Route

    // Languages
    @Serializable data object Languages : Route

    // Troubleshooting
    @Serializable data object Troubleshooting : Route

    // Tips & Support
    @Serializable data object TipsAndSupport : Route

    @Serializable data object LicenseList : Route
}
