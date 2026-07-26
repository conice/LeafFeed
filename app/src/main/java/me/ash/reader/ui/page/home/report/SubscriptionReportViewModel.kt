package me.ash.reader.ui.page.home.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.preference.SyncStatusStore

@HiltViewModel
class SubscriptionReportViewModel
@Inject
constructor(
    accountService: AccountService,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val filterStateUseCase: FilterStateUseCase,
    private val rssService: RssService,
    private val syncStatusStore: SyncStatusStore,
) : ViewModel() {
    private val accountId =
        accountService.currentAccountIdFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val range = MutableStateFlow(ReportRange.DAYS_30)
    private val filter = MutableStateFlow(ReportFilter.ALL)
    private val sort = MutableStateFlow(ReportSort.ATTENTION)
    private val operationMessage = MutableStateFlow<ReportOperationMessage?>(null)

    val uiState =
        accountService.currentAccountIdFlow
            .filterNotNull()
            .combine(range) { id, selectedRange -> id to selectedRange }
            .flatMapLatest { (id, selectedRange) ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val start =
                    Date.from(today.minusDays(selectedRange.days - 1).atStartOfDay(zone).toInstant())
                val end = Date.from(today.plusDays(1).atStartOfDay(zone).toInstant())
                val controls = combine(filter, sort, operationMessage) { selectedFilter, selectedSort, message ->
                    ReportControlsState(selectedFilter, selectedSort, message)
                }
                combine(
                    groupDao.queryAllGroupWithFeedAsFlow(id),
                    articleDao.queryReportFeedStats(id, start, end),
                    syncStatusStore.observe(id),
                    controls,
                ) { groups, stats, sync, selectedControls ->
                    buildUiState(
                        accountId = id,
                        range = selectedRange,
                        groups = groups.map { it.group },
                        feeds = groups.flatMap { it.feeds }.distinctBy { it.id },
                        stats = stats,
                        failedFeedIds = sync?.failedFeedIds.orEmpty().toSet(),
                        filter = selectedControls.filter,
                        sort = selectedControls.sort,
                        operationMessage = selectedControls.operationMessage,
                    )
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SubscriptionReportUiState(),
            )

    fun selectRange(value: ReportRange) {
        range.value = value
    }

    fun selectFilter(value: ReportFilter) {
        filter.value = value
    }

    fun selectSort(value: ReportSort) {
        sort.value = value
    }

    fun dismissOperationMessage() {
        operationMessage.value = null
    }

    fun openFeed(feedId: String, navigate: () -> Unit) {
        viewModelScope.launch {
            filterStateUseCase.initNow(feedId = feedId, groupId = null)
            navigate()
        }
    }

    fun retry(feedId: String) {
        val id = accountId.value ?: return
        runCatching { rssService.get().doSyncOneTime(accountId = id, feedId = feedId) }
            .onSuccess { operationMessage.value = ReportOperationMessage("Update queued") }
            .onFailure {
                operationMessage.value =
                    ReportOperationMessage(it.message ?: "Could not queue the update", true)
            }
    }

    fun markRead(feedIds: Set<String>) {
        launchOperation("Articles marked as read") {
            feedIds.forEach { feedId ->
                rssService.get().markAsRead(
                    groupId = null,
                    feedId = feedId,
                    articleId = null,
                    before = null,
                    isUnread = false,
                )
            }
        }
    }

    fun muteNotifications(feedIds: Set<String>) {
        launchOperation("Notifications muted") {
            val feeds = feedDao.queryByIds(feedIds.toList())
            feedDao.updateAll(feeds.map { it.copy(isNotification = false) })
        }
    }

    fun move(feedIds: Set<String>, targetGroupId: String) {
        launchOperation("Subscription moved") {
            feedDao.queryByIds(feedIds.toList()).forEach { feed ->
                if (feed.groupId != targetGroupId) {
                    rssService.get().moveFeed(feed.groupId, feed.copy(groupId = targetGroupId))
                }
            }
        }
    }

    fun updateFeed(feedId: String, name: String, url: String) {
        launchOperation("Subscription updated") {
            var feed = requireNotNull(feedDao.queryById(feedId)) {
                "Subscription no longer exists"
            }
            val normalizedName = name.trim()
            val normalizedUrl = url.trim()
            if (normalizedName.isNotEmpty() && normalizedName != feed.name) {
                feed = feed.copy(name = normalizedName)
                rssService.get().renameFeed(feed)
            }
            if (normalizedUrl.isNotEmpty() && normalizedUrl != feed.url) {
                feed = feed.copy(url = normalizedUrl)
                rssService.get().changeFeedUrl(feed)
            }
        }
    }

    fun delete(feedIds: Set<String>) {
        launchOperation("Unsubscribed") {
            feedDao.queryByIds(feedIds.toList()).forEach { rssService.get().deleteFeed(it) }
        }
    }

    private fun launchOperation(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { operationMessage.value = ReportOperationMessage(successMessage) }
                .onFailure {
                    operationMessage.value =
                        ReportOperationMessage(it.message ?: "The operation failed", true)
                }
        }
    }

    private fun buildUiState(
        accountId: Int,
        range: ReportRange,
        groups: List<Group>,
        feeds: List<Feed>,
        stats: List<ArticleDao.ReportFeedStats>,
        failedFeedIds: Set<String>,
        filter: ReportFilter,
        sort: ReportSort,
        operationMessage: ReportOperationMessage?,
    ): SubscriptionReportUiState {
        val statsByFeed = stats.associateBy { it.feedId }
        val groupsById = groups.associateBy { it.id }
        val duplicateIds =
            feeds
                .groupBy { normalizeUrl(it.url) }
                .filter { it.key.isNotEmpty() && it.value.size > 1 }
                .values
                .flatten()
                .mapTo(mutableSetOf()) { it.id }
        val today = LocalDate.now()
        val rows =
            feeds.map { feed ->
                val value = statsByFeed[feed.id]
                val firstDate = value?.firstDate?.toLocalDate()
                val latestDate = value?.latestDate?.toLocalDate()
                val lifetimeTotal = value?.lifetimeTotal ?: 0
                val isNew = firstDate?.isAfter(today.minusDays(30)) == true
                val expectedGap =
                    if (firstDate != null && latestDate != null && lifetimeTotal > 1) {
                        val observedDays = max(1L, ChronoUnit.DAYS.between(firstDate, latestDate))
                        (observedDays.toDouble() / (lifetimeTotal - 1) * 4).toLong()
                            .coerceIn(14L, 180L)
                    } else {
                        90L
                    }
                val articleCount = value?.total ?: 0
                val engagedCount = value?.engaged ?: 0
                val issues =
                    classifySubscription(
                        ReportClassificationInput(
                            updateFailed = feed.id in failedFeedIds,
                            duplicate = feed.id in duplicateIds,
                            isNew = isNew,
                            latestDate = latestDate,
                            staleAfterDays = expectedGap,
                            articleCount = articleCount,
                            engagedCount = engagedCount,
                            wasEverOpened = value?.lastOpenedAt != null,
                            periodUnread = value?.periodUnread ?: 0,
                            unreadBacklog = value?.unreadBacklog ?: 0,
                            rangeDays = range.days,
                            today = today,
                        )
                    )
                SubscriptionReportRow(
                    id = feed.id,
                    name = feed.name,
                    url = feed.url,
                    groupId = feed.groupId,
                    groupName = groupsById[feed.groupId]?.name.orEmpty(),
                    notificationsEnabled = feed.isNotification,
                    articleCount = articleCount,
                    openedCount = value?.opened ?: 0,
                    engagedCount = engagedCount,
                    unreadCount = value?.periodUnread ?: 0,
                    unreadBacklog = value?.unreadBacklog ?: 0,
                    starredCount = value?.starred ?: 0,
                    readLaterCount = value?.readLater ?: 0,
                    latestDate = value?.latestDate,
                    lastOpenedAt = value?.lastOpenedAt,
                    issues = issues,
                )
            }
        val attentionRows =
            rows.filter { row -> row.issues.any { it.requiresAttention } }
                .sortedWith(attentionComparator())
        val visibleRows =
            rows.filter { it.matches(filter) }
                .sortedWith(
                    when (sort) {
                        ReportSort.ATTENTION -> attentionComparator()
                        ReportSort.RECEIVED -> compareByDescending<SubscriptionReportRow> { it.articleCount }
                        ReportSort.OPENED -> compareByDescending<SubscriptionReportRow> { it.openedCount }
                        ReportSort.UNREAD -> compareByDescending<SubscriptionReportRow> { it.unreadBacklog }
                        ReportSort.NAME -> compareBy { it.name.lowercase() }
                    }.thenBy { it.name.lowercase() }
                )
        return SubscriptionReportUiState(
            accountId = accountId,
            range = range,
            filter = filter,
            sort = sort,
            totalSubscriptions = rows.size,
            activeSubscriptions = rows.count { it.articleCount > 0 },
            articleCount = rows.sumOf { it.articleCount },
            attentionRows = attentionRows,
            rows = visibleRows,
            groups = groups.sortedBy { it.name.lowercase() },
            operationMessage = operationMessage,
            isLoading = false,
        )
    }

    private fun attentionComparator(): Comparator<SubscriptionReportRow> =
        compareByDescending<SubscriptionReportRow> { row ->
            row.issues.maxOfOrNull { it.priority } ?: 0
        }.thenByDescending { it.unreadBacklog }

    private fun SubscriptionReportRow.matches(filter: ReportFilter): Boolean =
        when (filter) {
            ReportFilter.ALL -> true
            ReportFilter.NEEDS_ATTENTION -> issues.any { it.requiresAttention }
            ReportFilter.UPDATE_FAILED -> ReportIssue.UPDATE_FAILED in issues
            ReportFilter.INACTIVE -> ReportIssue.POSSIBLY_INACTIVE in issues
            ReportFilter.HIGH_VOLUME -> ReportIssue.HIGH_VOLUME in issues
            ReportFilter.RARELY_OPENED ->
                ReportIssue.RARELY_OPENED in issues || ReportIssue.NEVER_OPENED in issues
            ReportFilter.FREQUENTLY_OPENED -> ReportIssue.FREQUENTLY_OPENED in issues
            ReportFilter.UNREAD_BUILDUP -> ReportIssue.UNREAD_BUILDUP in issues
        }

    private fun Date.toLocalDate(): LocalDate =
        toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

    private fun normalizeUrl(url: String): String =
        runCatching {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            val port =
                if ((scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) {
                    -1
                } else {
                    uri.port
                }
            URI(
                scheme,
                uri.userInfo,
                uri.host?.lowercase(),
                port,
                uri.path?.trimEnd('/'),
                uri.query,
                null,
            ).toString()
        }.getOrDefault(url.trim().lowercase().trimEnd('/'))
}

enum class ReportRange(val days: Long) { DAYS_7(7), DAYS_30(30), DAYS_90(90) }

enum class ReportFilter {
    ALL,
    NEEDS_ATTENTION,
    UPDATE_FAILED,
    INACTIVE,
    HIGH_VOLUME,
    RARELY_OPENED,
    FREQUENTLY_OPENED,
    UNREAD_BUILDUP,
}

enum class ReportSort { ATTENTION, RECEIVED, OPENED, UNREAD, NAME }

enum class ReportIssue(val priority: Int, val requiresAttention: Boolean = true) {
    UPDATE_FAILED(7),
    DUPLICATE(6),
    UNREAD_BUILDUP(5),
    HIGH_VOLUME(4),
    NEVER_OPENED(3),
    RARELY_OPENED(2),
    POSSIBLY_INACTIVE(1),
    NEW_SUBSCRIPTION(0, false),
    NO_ARTICLES_YET(0, false),
    FREQUENTLY_OPENED(0, false),
    HEALTHY(0, false),
}

internal data class ReportClassificationInput(
    val updateFailed: Boolean = false,
    val duplicate: Boolean = false,
    val isNew: Boolean = false,
    val latestDate: LocalDate? = null,
    val staleAfterDays: Long = 90,
    val articleCount: Int = 0,
    val engagedCount: Int = 0,
    val wasEverOpened: Boolean = false,
    val periodUnread: Int = 0,
    val unreadBacklog: Int = 0,
    val rangeDays: Long = 30,
    val today: LocalDate = LocalDate.now(),
)

internal fun classifySubscription(input: ReportClassificationInput): Set<ReportIssue> =
    buildSet {
        if (input.updateFailed) add(ReportIssue.UPDATE_FAILED)
        if (input.duplicate) add(ReportIssue.DUPLICATE)
        if (input.isNew) add(ReportIssue.NEW_SUBSCRIPTION)
        if (input.latestDate == null) add(ReportIssue.NO_ARTICLES_YET)
        if (
            !input.isNew &&
                input.latestDate?.isBefore(input.today.minusDays(input.staleAfterDays)) == true
        ) {
            add(ReportIssue.POSSIBLY_INACTIVE)
        }
        val interactionRate =
            if (input.articleCount == 0) 0.0
            else input.engagedCount.toDouble() / input.articleCount
        if (input.articleCount >= max(20, input.rangeDays.toInt() * 2) && interactionRate < .15) {
            add(ReportIssue.HIGH_VOLUME)
        }
        if (!input.isNew && input.articleCount >= 5 && input.engagedCount == 0) {
            add(
                if (input.wasEverOpened) ReportIssue.RARELY_OPENED
                else ReportIssue.NEVER_OPENED
            )
        }
        if (input.articleCount >= 5 && interactionRate >= .5) {
            add(ReportIssue.FREQUENTLY_OPENED)
        }
        if (
            (input.periodUnread >= 20 && input.periodUnread * 5 >= input.articleCount * 4) ||
                (input.unreadBacklog >= 100 && interactionRate < .2)
        ) {
            add(ReportIssue.UNREAD_BUILDUP)
        }
        if (
            none { it.requiresAttention } &&
                ReportIssue.NEW_SUBSCRIPTION !in this &&
                ReportIssue.NO_ARTICLES_YET !in this
        ) {
            add(ReportIssue.HEALTHY)
        }
    }

data class SubscriptionReportRow(
    val id: String,
    val name: String,
    val url: String,
    val groupId: String,
    val groupName: String,
    val notificationsEnabled: Boolean,
    val articleCount: Int,
    val openedCount: Int,
    val engagedCount: Int,
    val unreadCount: Int,
    val unreadBacklog: Int,
    val starredCount: Int,
    val readLaterCount: Int,
    val latestDate: Date?,
    val lastOpenedAt: Date?,
    val issues: Set<ReportIssue>,
) {
    val interactionRate: Double
        get() = if (articleCount == 0) 0.0 else engagedCount.toDouble() / articleCount
}

data class SubscriptionReportUiState(
    val accountId: Int? = null,
    val range: ReportRange = ReportRange.DAYS_30,
    val filter: ReportFilter = ReportFilter.ALL,
    val sort: ReportSort = ReportSort.ATTENTION,
    val totalSubscriptions: Int = 0,
    val activeSubscriptions: Int = 0,
    val articleCount: Int = 0,
    val attentionRows: List<SubscriptionReportRow> = emptyList(),
    val rows: List<SubscriptionReportRow> = emptyList(),
    val groups: List<Group> = emptyList(),
    val operationMessage: ReportOperationMessage? = null,
    val isLoading: Boolean = true,
)

private data class ReportControlsState(
    val filter: ReportFilter,
    val sort: ReportSort,
    val operationMessage: ReportOperationMessage?,
)

data class ReportOperationMessage(val text: String, val isError: Boolean = false)
