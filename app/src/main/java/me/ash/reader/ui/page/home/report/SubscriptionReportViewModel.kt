package me.ash.reader.ui.page.home.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.AiSummaryService

@HiltViewModel
class SubscriptionReportViewModel
@Inject
constructor(
    accountService: AccountService,
    private val articleDao: ArticleDao,
    private val groupDao: GroupDao,
    private val filterStateUseCase: FilterStateUseCase,
    private val aiSummaryService: AiSummaryService,
    private val workManager: WorkManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now()
    private val selectedRange = MutableStateFlow(
        SubscriptionReportRange(
            start = savedStateHandle.get<Long>(START_DATE_KEY)?.let(LocalDate::ofEpochDay)
                ?: today.minusDays(DEFAULT_RANGE_DAYS - 1),
            end = savedStateHandle.get<Long>(END_DATE_KEY)?.let(LocalDate::ofEpochDay) ?: today,
        )
    )
    private val sortMode = MutableStateFlow(ReportSortMode.PUBLISHED)
    private val aiState = MutableStateFlow(ReportAiState())
    private val reminderEnabled = MutableStateFlow(SubscriptionReportReminder.isEnabled(workManager))

    val uiState = accountService.currentAccountIdFlow
        .filterNotNull()
        .combine(selectedRange) { accountId, range -> accountId to range }
        .flatMapLatest { (accountId, range) ->
            val zone = ZoneId.systemDefault()
            val start = Date.from(range.start.atStartOfDay(zone).toInstant())
            val endExclusive = Date.from(range.end.plusDays(1).atStartOfDay(zone).toInstant())
            val dayCount = ChronoUnit.DAYS.between(range.start, range.end) + 1
            val previousEnd = range.start
            val previousStart = previousEnd.minusDays(dayCount)
            val previousStartDate = Date.from(previousStart.atStartOfDay(zone).toInstant())
            val previousEndDate = Date.from(previousEnd.atStartOfDay(zone).toInstant())

            combine(
                groupDao.queryAllGroupWithFeedAsFlow(accountId),
                articleDao.queryReportFeedStats(accountId, start, endExclusive),
                articleDao.queryReportFeedStats(accountId, previousStartDate, previousEndDate),
                articleDao.queryReportDayStats(accountId, start, endExclusive),
                articleDao.queryReportFeedLatest(accountId),
                sortMode,
                aiState,
                reminderEnabled,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val groups = values[0] as List<me.ash.reader.domain.model.group.GroupWithFeed>
                val current = values[1] as List<ArticleDao.ReportFeedStats>
                val previous = values[2] as List<ArticleDao.ReportFeedStats>
                val days = values[3] as List<ArticleDao.ReportDayStats>
                val latest = values[4] as List<ArticleDao.ReportFeedLatest>
                val selectedSort = values[5] as ReportSortMode
                val currentAi = values[6] as ReportAiState
                val currentReminder = values[7] as Boolean
                buildUiState(
                    range, groups, current, previous, days, latest,
                    selectedSort, currentAi, currentReminder,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SubscriptionReportUiState(range = selectedRange.value),
        )

    private fun buildUiState(
        range: SubscriptionReportRange,
        groups: List<me.ash.reader.domain.model.group.GroupWithFeed>,
        current: List<ArticleDao.ReportFeedStats>,
        previous: List<ArticleDao.ReportFeedStats>,
        days: List<ArticleDao.ReportDayStats>,
        latest: List<ArticleDao.ReportFeedLatest>,
        selectedSort: ReportSortMode,
        currentAi: ReportAiState,
        currentReminder: Boolean,
    ): SubscriptionReportUiState {
        val feeds = groups.flatMap { it.feeds }.distinctBy { it.id }
        val stats = current.associateBy { it.feedId }
        val latestByFeed = latest.associate { it.feedId to it.latestDate }
        val rows = feeds.map { feed ->
            val value = stats[feed.id]
            SubscriptionReportRow(
                id = feed.id,
                name = feed.name,
                articleCount = value?.total ?: 0,
                readCount = (value?.total ?: 0) - (value?.unread ?: 0),
                starredCount = value?.starred ?: 0,
                readLaterCount = value?.readLater ?: 0,
                latestDate = latestByFeed[feed.id],
            )
        }
        val sortedRows = rows.filter { it.articleCount > 0 }.sortedWith(
            compareByDescending<SubscriptionReportRow> {
                when (selectedSort) {
                    ReportSortMode.PUBLISHED -> it.articleCount.toDouble()
                    ReportSortMode.READ -> it.readCount.toDouble()
                    ReportSortMode.READ_RATE -> it.readRate
                    ReportSortMode.STARRED -> it.starredCount.toDouble()
                }
            }.thenBy { it.name.lowercase() }
        )
        val duplicateIds = feeds.groupBy { normalizeUrl(it.url) }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
            .values.flatten().mapTo(mutableSetOf()) { it.id }
        val staleBefore = today.minusDays(STALE_DAYS)
        val healthRows = rows.mapNotNull { row ->
            val latestDate = row.latestDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            val issue = when {
                row.id in duplicateIds -> ReportHealthIssue.DUPLICATE
                latestDate == null || latestDate.isBefore(staleBefore) -> ReportHealthIssue.STALE
                row.articleCount >= HIGH_VOLUME_THRESHOLD && row.readRate < LOW_READ_RATE ->
                    ReportHealthIssue.HIGH_VOLUME_LOW_READ
                else -> null
            }
            issue?.let { ReportHealthRow(row.id, row.name, it, row.articleCount, row.readRate) }
        }.sortedBy { it.issue.ordinal }
        val groupRows = groups.map { group ->
            val feedRows = group.feeds.distinctBy { it.id }.mapNotNull { feed -> rows.find { it.id == feed.id } }
            SubscriptionReportRow(
                id = group.group.id,
                name = group.group.name,
                articleCount = feedRows.sumOf { it.articleCount },
                readCount = feedRows.sumOf { it.readCount },
                starredCount = feedRows.sumOf { it.starredCount },
                readLaterCount = feedRows.sumOf { it.readLaterCount },
                isGroup = true,
            )
        }.filter { it.articleCount > 0 }.sortedByDescending { it.articleCount }
        return SubscriptionReportUiState(
            range = range,
            metrics = metrics(current),
            previousMetrics = metrics(previous),
            activeFeeds = rows.count { it.articleCount > 0 },
            inactiveFeeds = rows.count { it.articleCount == 0 },
            groupRows = groupRows,
            feedRows = sortedRows,
            trend = days.mapNotNull {
                runCatching { ReportTrendPoint(LocalDate.parse(it.day), it.total, it.read) }.getOrNull()
            },
            healthRows = healthRows,
            sortMode = selectedSort,
            aiState = currentAi,
            reminderEnabled = currentReminder,
            isLoading = false,
        )
    }

    private fun metrics(stats: List<ArticleDao.ReportFeedStats>): ReportMetrics {
        val total = stats.sumOf { it.total }
        val unread = stats.sumOf { it.unread }
        return ReportMetrics(
            published = total,
            read = total - unread,
            unread = unread,
            starred = stats.sumOf { it.starred },
            readLater = stats.sumOf { it.readLater },
        )
    }

    fun selectRecentDays(days: Long) = selectRange(today.minusDays(days - 1), today)
    fun selectRecentMonths(months: Long) = selectRange(today.minusMonths(months).plusDays(1), today)
    fun selectRecentYear() = selectRange(today.minusYears(1).plusDays(1), today)
    fun selectSortMode(mode: ReportSortMode) { sortMode.value = mode }

    fun selectRange(start: LocalDate, end: LocalDate): Boolean {
        if (end.isBefore(start) || end.isAfter(today) || start.isBefore(end.minusYears(1).plusDays(1))) return false
        savedStateHandle[START_DATE_KEY] = start.toEpochDay()
        savedStateHandle[END_DATE_KEY] = end.toEpochDay()
        selectedRange.value = SubscriptionReportRange(start, end)
        aiState.value = ReportAiState()
        return true
    }

    fun openFeed(feedId: String, navigate: () -> Unit) {
        viewModelScope.launch {
            filterStateUseCase.initNow(feedId = feedId, groupId = null)
            navigate()
        }
    }

    fun openGroup(groupId: String, navigate: () -> Unit) {
        viewModelScope.launch {
            filterStateUseCase.initNow(feedId = null, groupId = groupId)
            navigate()
        }
    }

    fun toggleReminder() {
        val enabled = !reminderEnabled.value
        SubscriptionReportReminder.setEnabled(workManager, enabled)
        reminderEnabled.value = enabled
    }

    fun summarizeReport(forceRefresh: Boolean = false) {
        val state = uiState.value
        if (state.metrics.published == 0 || aiState.value.loading) return
        aiState.value = aiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val input = exportText(state)
            runCatching {
                aiSummaryService.summarizeReport(input, forceRefresh) { partial ->
                    aiState.value = ReportAiState(summary = partial, loading = true)
                }
            }.onSuccess { aiState.value = ReportAiState(summary = it) }
                .onFailure { aiState.value = ReportAiState(error = it.message ?: "AI summary failed") }
        }
    }

    fun exportText(state: SubscriptionReportUiState = uiState.value): String = buildString {
        appendLine("Subscription report: ${state.range.start} - ${state.range.end}")
        appendLine("Published: ${state.metrics.published}; read: ${state.metrics.read}; unread: ${state.metrics.unread}; starred: ${state.metrics.starred}; read later: ${state.metrics.readLater}")
        appendLine("Previous period: ${state.previousMetrics.published} published, ${state.previousMetrics.read} read")
        appendLine("Active feeds: ${state.activeFeeds}; inactive feeds: ${state.inactiveFeeds}")
        if (state.healthRows.isNotEmpty()) {
            appendLine("Health flags: ${state.healthRows.groupingBy { it.issue.name }.eachCount()}")
        }
        if (state.feedRows.isNotEmpty()) {
            appendLine("Top feeds:")
            state.feedRows.take(20).forEach { appendLine("- ${it.name}: ${it.articleCount} published, ${it.readCount} read") }
        }
    }.trim()

    private fun normalizeUrl(url: String) = url.trim().lowercase().removeSuffix("/")

    private companion object {
        const val DEFAULT_RANGE_DAYS = 30L
        const val START_DATE_KEY = "subscriptionReportStartDate"
        const val END_DATE_KEY = "subscriptionReportEndDate"
        const val STALE_DAYS = 90L
        const val HIGH_VOLUME_THRESHOLD = 30
        const val LOW_READ_RATE = .2
    }
}

data class SubscriptionReportRange(val start: LocalDate, val end: LocalDate)
data class ReportMetrics(val published: Int = 0, val read: Int = 0, val unread: Int = 0, val starred: Int = 0, val readLater: Int = 0) {
    val readRate: Double get() = if (published == 0) 0.0 else read.toDouble() / published
}
data class SubscriptionReportRow(
    val id: String,
    val name: String,
    val articleCount: Int,
    val readCount: Int = 0,
    val starredCount: Int = 0,
    val readLaterCount: Int = 0,
    val latestDate: Date? = null,
    val isGroup: Boolean = false,
) { val readRate: Double get() = if (articleCount == 0) 0.0 else readCount.toDouble() / articleCount }
data class ReportTrendPoint(val date: LocalDate, val published: Int, val read: Int)
data class ReportHealthRow(val feedId: String, val name: String, val issue: ReportHealthIssue, val articleCount: Int, val readRate: Double)
enum class ReportHealthIssue { DUPLICATE, STALE, HIGH_VOLUME_LOW_READ }
enum class ReportSortMode { PUBLISHED, READ, READ_RATE, STARRED }
data class ReportAiState(val summary: String = "", val loading: Boolean = false, val error: String? = null)
data class SubscriptionReportUiState(
    val range: SubscriptionReportRange,
    val metrics: ReportMetrics = ReportMetrics(),
    val previousMetrics: ReportMetrics = ReportMetrics(),
    val activeFeeds: Int = 0,
    val inactiveFeeds: Int = 0,
    val groupRows: List<SubscriptionReportRow> = emptyList(),
    val feedRows: List<SubscriptionReportRow> = emptyList(),
    val trend: List<ReportTrendPoint> = emptyList(),
    val healthRows: List<ReportHealthRow> = emptyList(),
    val sortMode: ReportSortMode = ReportSortMode.PUBLISHED,
    val aiState: ReportAiState = ReportAiState(),
    val reminderEnabled: Boolean = false,
    val isLoading: Boolean = true,
) { val totalArticles: Int get() = metrics.published }
