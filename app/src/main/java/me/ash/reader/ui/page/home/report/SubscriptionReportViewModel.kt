package me.ash.reader.ui.page.home.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.application.data.FilterStateUseCase
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.application.service.AccountService

private const val REPORT_DAYS = 7L
private const val SIGNIFICANT_CHANGE_PERCENT = 25
private const val SIGNIFICANT_GROUP_GAP = .20f
private const val MIN_SIGNAL_ITEMS = 5
private const val PRESSURE_SOURCE_LIMIT = 5
private const val ATTENTION_GROUP_LIMIT = 5

@HiltViewModel
class SubscriptionReportViewModel
@Inject
constructor(
    accountService: AccountService,
    private val articleDao: ArticleDao,
    private val groupDao: GroupDao,
    private val filterStateUseCase: FilterStateUseCase,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    val uiState =
        combine(
            accountService.currentAccountIdFlow.filterNotNull(),
            refresh,
        ) { accountId, _ -> accountId }
            .flatMapLatest { accountId ->
                val window = intakeReportWindow()
                combine(
                    groupDao.queryAllGroupWithFeedAsFlow(accountId),
                    articleDao.queryIntakeFeedStats(
                        accountId = accountId,
                        previousStart = window.previousStart,
                        currentStart = window.currentStart,
                        currentEndExclusive = window.currentEndExclusive,
                    ),
                ) { groupsWithFeeds, stats ->
                    buildIntakeUiState(
                        accountId = accountId,
                        period =
                            IntakeReportPeriod(
                                start = window.currentStart,
                                endInclusive = window.endInclusive,
                            ),
                        groups = groupsWithFeeds.map { it.group },
                        feeds = groupsWithFeeds.flatMap { it.feeds }.distinctBy { it.id },
                        stats = stats,
                    )
                }
                    .catch {
                        emit(
                            SubscriptionReportUiState(
                                accountId = accountId,
                                isLoading = false,
                                loadFailed = true,
                            )
                        )
                    }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SubscriptionReportUiState(),
            )

    fun retry() {
        refresh.value += 1
    }

    fun reviewScope(feedId: String? = null, groupId: String? = null, navigate: () -> Unit) {
        viewModelScope.launch {
            filterStateUseCase.initNow(feedId = feedId, groupId = groupId)
            navigate()
        }
    }
}

private fun buildIntakeUiState(
    accountId: Int,
    period: IntakeReportPeriod,
    groups: List<Group>,
    feeds: List<Feed>,
    stats: List<ArticleDao.IntakeFeedStats>,
): SubscriptionReportUiState {
    val statsByFeed = stats.associateBy { it.feedId }
    val groupsById = groups.associateBy { it.id }
    val sources =
        feeds.map { feed ->
            val value = statsByFeed[feed.id]
            IntakeSourceRow(
                id = feed.id,
                name = feed.name,
                groupId = feed.groupId,
                groupName = groupsById[feed.groupId]?.name.orEmpty(),
                received = value?.currentReceived ?: 0,
                previousReceived = value?.previousReceived ?: 0,
                opened = value?.currentOpened ?: 0,
                previousOpened = value?.previousOpened ?: 0,
                clearedWithoutOpening = value?.clearedWithoutOpening ?: 0,
                saved = value?.saved ?: 0,
                pending = value?.currentPending ?: 0,
                unreadBacklog = value?.unreadBacklog ?: 0,
            )
        }
    val metrics =
        IntakeMetrics(
            received = sources.sumOf { it.received },
            previousReceived = sources.sumOf { it.previousReceived },
            opened = sources.sumOf { it.opened },
            previousOpened = sources.sumOf { it.previousOpened },
            clearedWithoutOpening = sources.sumOf { it.clearedWithoutOpening },
            saved = sources.sumOf { it.saved },
            pending = sources.sumOf { it.pending },
            unreadBacklog = sources.sumOf { it.unreadBacklog },
        )
    val pressureSources =
        sources
            .asSequence()
            .filter { it.received > 0 && it.pending > 0 }
            .sortedWith(
                compareByDescending<IntakeSourceRow> { it.pending }
                    .thenByDescending { it.received }
                    .thenBy { it.name.lowercase() }
            )
            .take(PRESSURE_SOURCE_LIMIT)
            .map { source ->
                source.copy(
                    pendingShare = shareOf(source.pending, metrics.pending),
                )
            }
            .toList()
    val sourcesByGroup = sources.groupBy { it.groupId }
    val attentionGroups =
        groups
            .asSequence()
            .map { group ->
                val groupSources = sourcesByGroup[group.id].orEmpty()
                val received = groupSources.sumOf { it.received }
                val opened = groupSources.sumOf { it.opened }
                IntakeGroupRow(
                    id = group.id,
                    name = group.name,
                    received = received,
                    opened = opened,
                    pending = groupSources.sumOf { it.pending },
                    incomingShare = shareOf(received, metrics.received),
                    openedShare = shareOf(opened, metrics.opened),
                )
            }
            .filter { it.received > 0 || it.opened > 0 }
            .sortedByDescending { maxOf(it.incomingShare, it.openedShare) }
            .take(ATTENTION_GROUP_LIMIT)
            .toList()

    return SubscriptionReportUiState(
        accountId = accountId,
        period = period,
        metrics = metrics,
        pressureSources = pressureSources,
        attentionGroups = attentionGroups,
        summary = classifyIntakeSummary(metrics, attentionGroups),
        recommendation = buildIntakeRecommendation(pressureSources, attentionGroups),
        isLoading = false,
    )
}

private fun intakeReportWindow(
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    zoneId: ZoneId = ZoneId.systemDefault(),
): IntakeReportWindow {
    val currentStartDate = today.minusDays(REPORT_DAYS - 1)
    val currentEndDate = today.plusDays(1)
    return IntakeReportWindow(
        previousStart =
            Date.from(currentStartDate.minusDays(REPORT_DAYS).atStartOfDay(zoneId).toInstant()),
        currentStart = Date.from(currentStartDate.atStartOfDay(zoneId).toInstant()),
        currentEndExclusive = Date.from(currentEndDate.atStartOfDay(zoneId).toInstant()),
        endInclusive = Date.from(today.atStartOfDay(zoneId).toInstant()),
    )
}

private fun shareOf(value: Int, total: Int): Float =
    if (total <= 0) 0f else value.toFloat() / total

internal fun calculateIntakeTrend(current: Int, previous: Int): IntakeTrend {
    if (previous == 0) {
        return IntakeTrend(
            direction = if (current == 0) IntakeTrendDirection.FLAT else IntakeTrendDirection.NEW,
            percent = null,
        )
    }
    val signedPercent = ((current - previous) * 100.0 / previous).roundToInt()
    return IntakeTrend(
        direction =
            when {
                signedPercent > 0 -> IntakeTrendDirection.UP
                signedPercent < 0 -> IntakeTrendDirection.DOWN
                else -> IntakeTrendDirection.FLAT
            },
        percent = kotlin.math.abs(signedPercent),
    )
}

internal fun classifyIntakeSummary(
    metrics: IntakeMetrics,
    groups: List<IntakeGroupRow>,
): IntakeSummary {
    val receivedChange = metrics.receivedTrend.signedPercent
    val openedChange = metrics.openedTrend.signedPercent
    val focusGroup = groups.maxByOrNull { it.openedShare }
    val kind =
        when {
            metrics.received == 0 && metrics.opened == 0 -> IntakeSummaryKind.QUIET
            metrics.previousReceived >= MIN_SIGNAL_ITEMS &&
                receivedChange != null &&
                receivedChange >= SIGNIFICANT_CHANGE_PERCENT &&
                (openedChange ?: 0) < SIGNIFICANT_CHANGE_PERCENT ->
                IntakeSummaryKind.INTAKE_RISING
            metrics.received >= MIN_SIGNAL_ITEMS && metrics.pending * 2 >= metrics.received ->
                IntakeSummaryKind.MOST_NEW_ITEMS_WAITING
            metrics.previousReceived >= MIN_SIGNAL_ITEMS &&
                receivedChange != null &&
                receivedChange <= -SIGNIFICANT_CHANGE_PERCENT ->
                IntakeSummaryKind.INTAKE_EASING
            metrics.previousOpened >= MIN_SIGNAL_ITEMS &&
                openedChange != null &&
                openedChange >= SIGNIFICANT_CHANGE_PERCENT ->
                IntakeSummaryKind.MORE_ATTENTION
            groups.size > 1 &&
                metrics.opened >= MIN_SIGNAL_ITEMS &&
                focusGroup != null &&
                focusGroup.openedShare >= .60f ->
                IntakeSummaryKind.ATTENTION_CONCENTRATED
            else -> IntakeSummaryKind.STEADY
        }
    return IntakeSummary(
        kind = kind,
        focusGroupName =
            if (kind == IntakeSummaryKind.ATTENTION_CONCENTRATED) focusGroup?.name else null,
        focusGroupShare = focusGroup?.openedShare ?: 0f,
    )
}

internal fun buildIntakeRecommendation(
    pressureSources: List<IntakeSourceRow>,
    groups: List<IntakeGroupRow>,
): IntakeRecommendation {
    val source =
        pressureSources
            .asSequence()
            .filter {
                it.received >= MIN_SIGNAL_ITEMS &&
                    it.pending >= MIN_SIGNAL_ITEMS &&
                    it.pending * 2 >= it.received
            }
            .maxWithOrNull(
                compareBy<IntakeSourceRow> { it.pending }
                    .thenBy { it.received }
            )
    if (source != null) return IntakeRecommendation.ReviewSource(source)

    val group = groups.maxByOrNull { it.incomingShare - it.openedShare }
    if (
        group != null &&
            group.received >= MIN_SIGNAL_ITEMS &&
            group.incomingShare - group.openedShare >= SIGNIFICANT_GROUP_GAP
    ) {
        return IntakeRecommendation.ReviewGroup(group)
    }
    return IntakeRecommendation.NoChange
}

private data class IntakeReportWindow(
    val previousStart: Date,
    val currentStart: Date,
    val currentEndExclusive: Date,
    val endInclusive: Date,
)

data class IntakeReportPeriod(
    val start: Date,
    val endInclusive: Date,
)

data class IntakeMetrics(
    val received: Int = 0,
    val previousReceived: Int = 0,
    val opened: Int = 0,
    val previousOpened: Int = 0,
    val clearedWithoutOpening: Int = 0,
    val saved: Int = 0,
    val pending: Int = 0,
    val unreadBacklog: Int = 0,
) {
    val receivedTrend: IntakeTrend
        get() = calculateIntakeTrend(received, previousReceived)

    val openedTrend: IntakeTrend
        get() = calculateIntakeTrend(opened, previousOpened)
}

data class IntakeSourceRow(
    val id: String,
    val name: String,
    val groupId: String,
    val groupName: String,
    val received: Int,
    val previousReceived: Int,
    val opened: Int,
    val previousOpened: Int,
    val clearedWithoutOpening: Int,
    val saved: Int,
    val pending: Int,
    val unreadBacklog: Int,
    val pendingShare: Float = 0f,
)

data class IntakeGroupRow(
    val id: String,
    val name: String,
    val received: Int,
    val opened: Int,
    val pending: Int,
    val incomingShare: Float,
    val openedShare: Float,
)

data class IntakeTrend(
    val direction: IntakeTrendDirection,
    val percent: Int?,
) {
    val signedPercent: Int?
        get() =
            when (direction) {
                IntakeTrendDirection.UP -> percent
                IntakeTrendDirection.DOWN -> percent?.let { -it }
                IntakeTrendDirection.FLAT -> 0
                IntakeTrendDirection.NEW -> null
            }
}

enum class IntakeTrendDirection { UP, DOWN, FLAT, NEW }

data class IntakeSummary(
    val kind: IntakeSummaryKind = IntakeSummaryKind.STEADY,
    val focusGroupName: String? = null,
    val focusGroupShare: Float = 0f,
)

enum class IntakeSummaryKind {
    QUIET,
    INTAKE_RISING,
    MOST_NEW_ITEMS_WAITING,
    INTAKE_EASING,
    MORE_ATTENTION,
    ATTENTION_CONCENTRATED,
    STEADY,
}

sealed interface IntakeRecommendation {
    data class ReviewSource(val source: IntakeSourceRow) : IntakeRecommendation

    data class ReviewGroup(val group: IntakeGroupRow) : IntakeRecommendation

    data object NoChange : IntakeRecommendation
}

data class SubscriptionReportUiState(
    val accountId: Int? = null,
    val period: IntakeReportPeriod? = null,
    val metrics: IntakeMetrics = IntakeMetrics(),
    val pressureSources: List<IntakeSourceRow> = emptyList(),
    val attentionGroups: List<IntakeGroupRow> = emptyList(),
    val summary: IntakeSummary = IntakeSummary(),
    val recommendation: IntakeRecommendation = IntakeRecommendation.NoChange,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)
