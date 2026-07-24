@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.report

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import me.ash.reader.R
import me.ash.reader.ui.component.base.AnimatedIcon
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.theme.Shape32

private const val COLLAPSED_FEED_COUNT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionReportPage(
    onBack: () -> Unit,
    onOpenReading: () -> Unit = {},
    viewModel: SubscriptionReportViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateValue()
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val isSameYear = uiState.range.start.year == uiState.range.end.year
    val dateFormatter =
        remember(locale, isSameYear) {
            val skeleton = if (isSameYear) "MMMd" else "yMMMd"
            DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(locale, skeleton),
                locale,
            )
        }
    val rangeText =
        remember(uiState.range, dateFormatter) {
            "${uiState.range.start.format(dateFormatter)} → ${uiState.range.end.format(dateFormatter)}"
        }

    RYScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    DisplayText(
                        text = stringResource(R.string.subscription_report),
                        desc = "",
                    )
                }

                item {
                    ReportDateRangeSection(
                        rangeText = rangeText,
                        range = uiState.range,
                        onRecentDays = viewModel::selectRecentDays,
                        onRecentMonths = viewModel::selectRecentMonths,
                        onRecentYear = viewModel::selectRecentYear,
                        onCustom = { showDatePicker = true },
                    )
                }

                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    item {
                        ReportSummary(
                            metrics = uiState.metrics,
                            activeFeeds = uiState.activeFeeds,
                            inactiveFeeds = uiState.inactiveFeeds,
                        )
                    }

                    item { ReportMetricsSection(metrics = uiState.metrics, previous = uiState.previousMetrics) }
                    if (uiState.trend.isNotEmpty()) item { ReportTrendSection(uiState.trend) }
                    if (uiState.healthRows.isNotEmpty()) item {
                        ReportHealthSection(uiState.healthRows) { feedId ->
                            viewModel.openFeed(feedId, onOpenReading)
                        }
                    }
                    item {
                        ReportActions(
                            reminderEnabled = uiState.reminderEnabled,
                            aiState = uiState.aiState,
                            onReminder = viewModel::toggleReminder,
                            onAi = { viewModel.summarizeReport(uiState.aiState.summary.isNotBlank()) },
                            onShare = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.exportText(uiState))
                                }, context.getString(R.string.share)))
                            },
                        )
                    }

                    if (uiState.inactiveFeeds > 0) {
                        item {
                            Text(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                                text = stringResource(R.string.report_inactive_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (uiState.totalArticles == 0) {
                        item {
                            Text(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 48.dp),
                                text = stringResource(R.string.report_no_data),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item {
                            ProgressTable(
                                title = stringResource(R.string.report_group_distribution),
                                rows = uiState.groupRows,
                                totalArticles = uiState.totalArticles,
                                onRowClick = { row -> viewModel.openGroup(row.id, onOpenReading) },
                            )
                        }
                        item { ReportSortSection(uiState.sortMode, viewModel::selectSortMode) }
                        item {
                            ProgressTable(
                                title = stringResource(R.string.report_feed_distribution),
                                rows = uiState.feedRows,
                                totalArticles = uiState.totalArticles,
                                collapsible = true,
                                onRowClick = { row -> viewModel.openFeed(row.id, onOpenReading) },
                            )
                        }
                    }

                    item {
                        Text(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                            text = stringResource(R.string.report_publish_date_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    if (showDatePicker) {
        ReportDateRangeDialog(
            range = uiState.range,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                if (viewModel.selectRange(start, end)) showDatePicker = false
            },
        )
    }
}

@Composable
private fun ReportDateRangeSection(
    rangeText: String,
    range: SubscriptionReportRange,
    onRecentDays: (Long) -> Unit,
    onRecentMonths: (Long) -> Unit,
    onRecentYear: () -> Unit,
    onCustom: () -> Unit,
) {
    val today = LocalDate.now()
    val preset =
        when {
            range.end != today -> null
            range.start == today.minusDays(6) -> "7"
            range.start == today.minusDays(29) -> "30"
            range.start == today.minusDays(89) -> "90"
            range.start == today.minusMonths(6).plusDays(1) -> "6m"
            range.start == today.minusYears(1).plusDays(1) -> "1y"
            else -> null
        }

    Surface(
        modifier =
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(8.dp).selectableGroup()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(Shape32)
                        .clickable(onClick = onCustom)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.report_date_range),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rangeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_last_7_days),
                    selected = preset == "7",
                    onClick = { onRecentDays(7) },
                )
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_last_30_days),
                    selected = preset == "30",
                    onClick = { onRecentDays(30) },
                )
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_last_90_days),
                    selected = preset == "90",
                    onClick = { onRecentDays(90) },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_last_6_months),
                    selected = preset == "6m",
                    onClick = { onRecentMonths(6) },
                )
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_last_year),
                    selected = preset == "1y",
                    onClick = onRecentYear,
                )
                ReportRangeChip(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.report_custom_range),
                    selected = preset == null,
                    onClick = onCustom,
                )
            }
        }
    }
}

@Composable
private fun ReportRangeChip(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(Shape32)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ReportSummary(metrics: ReportMetrics, activeFeeds: Int, inactiveFeeds: Int) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = Triple(metrics.published, activeFeeds, inactiveFeeds),
        transitionSpec = {
            (fadeIn(motionScheme.slowEffectsSpec()) + slideInVertically(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialOffsetY = { it / 3 },
            )) togetherWith
                (fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    targetOffsetY = { -it / 3 },
                ))
        },
        label = "reportSummary",
    ) { (articles, active, inactive) ->
        Text(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            text =
                stringResource(
                    R.string.report_summary,
                    numberFormat.format(articles),
                    numberFormat.format(active),
                    numberFormat.format(inactive),
                ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReportMetricsSection(metrics: ReportMetrics, previous: ReportMetrics) {
    val format = remember { NumberFormat.getIntegerInstance() }
    val values = listOf(
        R.string.report_published to metrics.published,
        R.string.report_read to metrics.read,
        R.string.report_unread to metrics.unread,
        R.string.report_starred to metrics.starred,
        R.string.report_read_later to metrics.readLater,
    )
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.report_reading_overview), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            values.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(format.format(value), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }
            val delta = metrics.published - previous.published
            val rateDelta = ((metrics.readRate - previous.readRate) * 100).toInt()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = stringResource(R.string.report_comparison, signed(delta), signed(rateDelta)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

@Composable
private fun ReportTrendSection(points: List<ReportTrendPoint>) {
    val buckets = remember(points) {
        val size = ((points.size + 11) / 12).coerceAtLeast(1)
        points.chunked(size).map { chunk ->
            Triple(chunk.first().date, chunk.sumOf { it.published }, chunk.sumOf { it.read })
        }
    }
    val max = buckets.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.report_trend), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.report_trend_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                buckets.forEach { (_, published, read) ->
                    Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).fillMaxHeight(published.toFloat() / max).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Box(Modifier.weight(1f).fillMaxHeight(read.toFloat() / max).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportHealthSection(rows: List<ReportHealthRow>, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.report_health), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            rows.take(8).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onClick(row.feedId) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(when (row.issue) {
                            ReportHealthIssue.DUPLICATE -> R.string.report_issue_duplicate
                            ReportHealthIssue.STALE -> R.string.report_issue_stale
                            ReportHealthIssue.HIGH_VOLUME_LOW_READ -> R.string.report_issue_high_volume
                        }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportActions(
    reminderEnabled: Boolean,
    aiState: ReportAiState,
    onReminder: () -> Unit,
    onAi: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(modifier = Modifier.weight(1f), onClick = onShare) {
                    Icon(Icons.Rounded.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.report_export))
                }
                TextButton(modifier = Modifier.weight(1f), onClick = onReminder) {
                    Icon(Icons.Rounded.Notifications, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (reminderEnabled) R.string.report_reminder_on
                            else R.string.report_reminder_off
                        )
                    )
                }
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAi,
                enabled = !aiState.loading,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.report_ai_insight))
            }
            if (aiState.loading) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            if (aiState.summary.isNotBlank()) Text(aiState.summary, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            aiState.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ReportSortSection(selected: ReportSortMode, onSelect: (ReportSortMode) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(stringResource(R.string.report_sort), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                ReportSortMode.PUBLISHED to R.string.report_published,
                ReportSortMode.READ to R.string.report_read,
                ReportSortMode.READ_RATE to R.string.report_read_rate,
                ReportSortMode.STARRED to R.string.report_starred,
            ).forEach { (mode, label) ->
                ReportRangeChip(Modifier.weight(1f), stringResource(label), selected == mode) { onSelect(mode) }
            }
        }
    }
}

@Composable
private fun ProgressTable(
    title: String,
    rows: List<SubscriptionReportRow>,
    totalArticles: Int,
    collapsible: Boolean = false,
    onRowClick: ((SubscriptionReportRow) -> Unit)? = null,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val displayedRows =
        if (collapsible && !expanded) rows.take(COLLAPSED_FEED_COUNT) else rows

    Surface(
        modifier =
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ),
        shape = Shape32,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(18.dp))
            displayedRows.forEachIndexed { index, row ->
                key(row.id) {
                    ReportProgressRow(row = row, totalArticles = totalArticles, onClick = onRowClick)
                    if (index != displayedRows.lastIndex) Spacer(modifier = Modifier.height(18.dp))
                }
            }
            if (collapsible && rows.size > COLLAPSED_FEED_COUNT) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = { expanded = !expanded },
                ) {
                    Text(
                        stringResource(
                            if (expanded) R.string.report_show_less else R.string.report_show_all
                        )
                    )
                    AnimatedIcon(
                        imageVector =
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportProgressRow(
    row: SubscriptionReportRow,
    totalArticles: Int,
    onClick: ((SubscriptionReportRow) -> Unit)? = null,
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val motionScheme = MaterialTheme.motionScheme
    val targetProgress =
        if (totalArticles == 0) 0f
        else (row.articleCount.toFloat() / totalArticles.toFloat()).coerceIn(0f, 1f)
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(targetProgress) {
        animatedProgress.animateTo(
            targetValue = targetProgress,
            animationSpec = motionScheme.defaultEffectsSpec(),
        )
    }
    val percentage = (targetProgress * 100).toInt()
    val rowDescription =
        stringResource(
            R.string.report_row_description,
            row.name,
            numberFormat.format(row.articleCount),
            percentage,
        )

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick(row) } else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                modifier = Modifier.width(48.dp),
                targetState = row.articleCount,
                transitionSpec = {
                    (fadeIn(motionScheme.slowEffectsSpec()) + slideInVertically(
                        animationSpec = motionScheme.defaultSpatialSpec(),
                        initialOffsetY = { it },
                    )) togetherWith
                        (fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            targetOffsetY = { -it },
                        ))
                },
                contentAlignment = Alignment.CenterEnd,
                label = "reportCount",
            ) { count ->
                Text(
                    text = numberFormat.format(count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics {
                            progressBarRangeInfo =
                                ProgressBarRangeInfo(animatedProgress.value, 0f..1f)
                        }
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(animatedProgress.value)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                modifier = Modifier.width(48.dp),
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDateRangeDialog(
    range: SubscriptionReportRange,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val selectableDates =
        remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayUtcMillis
            }
        }
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = range.start.toUtcMillis(),
            initialSelectedEndDateMillis = range.end.toUtcMillis(),
            yearRange = 1970..today.year,
            selectableDates = selectableDates,
        )
    val start = state.selectedStartDateMillis?.toUtcLocalDate()
    val end = state.selectedEndDateMillis?.toUtcLocalDate()
    val locale = Locale.getDefault()
    val isSameYear = start != null && end != null && start.year == end.year
    val headlineDateFormatter =
        remember(locale, isSameYear) {
            val skeleton = if (isSameYear) "MMMd" else "yMMMd"
            DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(locale, skeleton),
                locale,
            )
        }
    val selectedRangeText =
        when {
            start != null && end != null ->
                "${start.format(headlineDateFormatter)} → ${end.format(headlineDateFormatter)}"
            start != null -> "${start.format(headlineDateFormatter)} → …"
            else -> stringResource(R.string.report_date_range)
        }
    val isValid =
        start != null &&
            end != null &&
            !end.isBefore(start) &&
            !start.isBefore(end.minusYears(1).plusDays(1))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier.widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                shape = Shape32,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 6.dp,
            ) {
                Column {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.report_date_range),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedRangeText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (
                        start != null &&
                            end != null &&
                            start.isBefore(end.minusYears(1).plusDays(1))
                    ) {
                        Text(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            text = stringResource(R.string.report_range_too_long),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DateRangePicker(
                        state = state,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        title = null,
                        headline = null,
                        showModeToggle = true,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            enabled = isValid,
                            onClick = {
                                if (start != null && end != null) onConfirm(start, end)
                            },
                        ) {
                            Text(stringResource(R.string.apply))
                        }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
