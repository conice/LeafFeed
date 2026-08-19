package com.conice.morss.ui.page.home.report

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.NumberFormat
import kotlin.math.roundToInt
import com.conice.morss.R
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.ext.collectAsStateValue
import com.conice.morss.ui.theme.LayoutTokens
import com.conice.morss.ui.theme.ShapeTokens

@Composable
fun SubscriptionReportPage(
    onBack: () -> Unit,
    onOpenReading: () -> Unit = {},
    viewModel: SubscriptionReportViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateValue()
    val listState = rememberLazyListState()

    RYScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = LayoutTokens.SectionSpacing),
        ) {
            item {
                DisplayText(
                    text = stringResource(R.string.subscription_report),
                    desc = reportPeriodDescription(state.period),
                )
            }
            when {
                state.isLoading -> item { LoadingReport() }
                state.loadFailed ->
                    item {
                        ReportMessage(
                            title = stringResource(R.string.intake_load_failed),
                            body = stringResource(R.string.intake_load_failed_desc),
                            actionLabel = stringResource(R.string.retry),
                            onAction = viewModel::retry,
                        )
                    }
                state.metrics.received == 0 &&
                    state.metrics.opened == 0 &&
                    state.metrics.unreadBacklog == 0 ->
                    item {
                        ReportMessage(
                            title = stringResource(R.string.intake_empty),
                            body = stringResource(R.string.intake_empty_desc),
                        )
                    }
                else -> {
                    item { IntakeSummarySection(state.summary, state.metrics) }
                    item { InformationFlowSection(state.metrics) }
                    item {
                        ReportSectionHeader(
                            title = stringResource(R.string.intake_pressure_sources),
                            description = stringResource(R.string.intake_pressure_sources_desc),
                        )
                    }
                    if (state.pressureSources.isEmpty()) {
                        item { InlineMessage(stringResource(R.string.intake_pressure_sources_empty)) }
                    } else {
                        items(state.pressureSources, key = { "pressure-${it.id}" }) { source ->
                            PressureSourceItem(
                                source = source,
                                onClick = {
                                    viewModel.reviewScope(
                                        feedId = source.id,
                                        navigate = onOpenReading,
                                    )
                                },
                            )
                        }
                    }
                    item {
                        ReportSectionHeader(
                            title = stringResource(R.string.intake_attention_allocation),
                            description = stringResource(R.string.intake_attention_allocation_desc),
                        )
                    }
                    if (state.attentionGroups.isEmpty()) {
                        item { InlineMessage(stringResource(R.string.intake_attention_allocation_empty)) }
                    } else {
                        items(state.attentionGroups, key = { "attention-${it.id}" }) { group ->
                            AttentionGroupItem(group)
                        }
                    }
                    item {
                        RecommendationSection(
                            recommendation = state.recommendation,
                            onReviewSource = { source ->
                                viewModel.reviewScope(
                                    feedId = source.id,
                                    navigate = onOpenReading,
                                )
                            },
                            onReviewGroup = { group ->
                                viewModel.reviewScope(
                                    groupId = group.id,
                                    navigate = onOpenReading,
                                )
                            },
                        )
                    }
                    item { MethodologyNote() }
                }
            }
        }
    }
}

@Composable
private fun reportPeriodDescription(period: IntakeReportPeriod?): String {
    if (period == null) return stringResource(R.string.intake_last_seven_days)
    val context = LocalContext.current
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }
    return stringResource(
        R.string.intake_report_period,
        dateFormat.format(period.start),
        dateFormat.format(period.endInclusive),
    )
}

@Composable
private fun LoadingReport() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReportMessage(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.SectionSpacing,
                ),
        verticalArrangement = Arrangement.spacedBy(LayoutTokens.ContentGap),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun IntakeSummarySection(summary: IntakeSummary, metrics: IntakeMetrics) {
    val title =
        when (summary.kind) {
            IntakeSummaryKind.QUIET -> stringResource(R.string.intake_summary_quiet)
            IntakeSummaryKind.INTAKE_RISING ->
                stringResource(R.string.intake_summary_rising)
            IntakeSummaryKind.MOST_NEW_ITEMS_WAITING ->
                stringResource(R.string.intake_summary_waiting)
            IntakeSummaryKind.INTAKE_EASING ->
                stringResource(R.string.intake_summary_easing)
            IntakeSummaryKind.MORE_ATTENTION ->
                stringResource(R.string.intake_summary_more_attention)
            IntakeSummaryKind.ATTENTION_CONCENTRATED ->
                stringResource(
                    R.string.intake_summary_concentrated,
                    summary.focusGroupName.orEmpty(),
                )
            IntakeSummaryKind.STEADY -> stringResource(R.string.intake_summary_steady)
        }
    val body =
        when (summary.kind) {
            IntakeSummaryKind.QUIET -> stringResource(R.string.intake_summary_quiet_desc)
            IntakeSummaryKind.INTAKE_RISING ->
                stringResource(
                    R.string.intake_summary_rising_desc,
                    metrics.received,
                    metrics.receivedTrend.percent ?: 0,
                    metrics.opened,
                )
            IntakeSummaryKind.MOST_NEW_ITEMS_WAITING ->
                stringResource(
                    R.string.intake_summary_waiting_desc,
                    metrics.pending,
                    metrics.received,
                )
            IntakeSummaryKind.INTAKE_EASING ->
                stringResource(
                    R.string.intake_summary_easing_desc,
                    metrics.receivedTrend.percent ?: 0,
                    metrics.opened,
                )
            IntakeSummaryKind.MORE_ATTENTION ->
                stringResource(
                    R.string.intake_summary_more_attention_desc,
                    metrics.opened,
                    metrics.openedTrend.percent ?: 0,
                )
            IntakeSummaryKind.ATTENTION_CONCENTRATED ->
                stringResource(
                    R.string.intake_summary_concentrated_desc,
                    summary.focusGroupName.orEmpty(),
                    (summary.focusGroupShare * 100).roundToInt(),
                )
            IntakeSummaryKind.STEADY ->
                stringResource(
                    R.string.intake_summary_steady_desc,
                    metrics.received,
                    metrics.opened,
                )
        }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = LayoutTokens.PageHorizontalPadding,
                    end = LayoutTokens.PageHorizontalPadding,
                    bottom = LayoutTokens.SectionSpacing,
                ),
        verticalArrangement = Arrangement.spacedBy(LayoutTokens.ListItemGap),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InformationFlowSection(metrics: IntakeMetrics) {
    ReportSectionHeader(
        title = stringResource(R.string.intake_information_flow),
        description = stringResource(R.string.intake_information_flow_desc),
    )
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = LayoutTokens.PageHorizontalPadding),
        shape = ShapeTokens.Surface,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
    ) {
        Column(
            modifier = Modifier.padding(LayoutTokens.ActionGap),
            verticalArrangement = Arrangement.spacedBy(LayoutTokens.ActionGap),
        ) {
            MetricRow(
                first = {
                    IntakeMetric(
                        value = metrics.received,
                        label = stringResource(R.string.intake_metric_received),
                        supporting = trendLabel(metrics.receivedTrend),
                    )
                },
                second = {
                    IntakeMetric(
                        value = metrics.opened,
                        label = stringResource(R.string.intake_metric_opened),
                        supporting = trendLabel(metrics.openedTrend),
                    )
                },
            )
            MetricRow(
                first = {
                    IntakeMetric(
                        value = metrics.clearedWithoutOpening,
                        label = stringResource(R.string.intake_metric_cleared),
                        supporting = stringResource(R.string.intake_metric_cleared_desc),
                    )
                },
                second = {
                    IntakeMetric(
                        value = metrics.saved,
                        label = stringResource(R.string.intake_metric_saved),
                        supporting = stringResource(R.string.intake_metric_saved_desc),
                    )
                },
            )
            MetricRow(
                first = {
                    IntakeMetric(
                        value = metrics.pending,
                        label = stringResource(R.string.intake_metric_pending),
                        supporting = stringResource(R.string.intake_metric_pending_desc),
                    )
                },
                second = {
                    IntakeMetric(
                        value = metrics.unreadBacklog,
                        label = stringResource(R.string.intake_metric_backlog),
                        supporting = stringResource(R.string.intake_metric_backlog_desc),
                    )
                },
            )
        }
    }
}

@Composable
private fun MetricRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LayoutTokens.ActionGap),
    ) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun IntakeMetric(value: Int, label: String, supporting: String) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            numberFormat.format(value),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun trendLabel(trend: IntakeTrend): String =
    when (trend.direction) {
        IntakeTrendDirection.UP ->
            stringResource(R.string.intake_trend_more, trend.percent ?: 0)
        IntakeTrendDirection.DOWN ->
            stringResource(R.string.intake_trend_less, trend.percent ?: 0)
        IntakeTrendDirection.FLAT -> stringResource(R.string.intake_trend_same)
        IntakeTrendDirection.NEW -> stringResource(R.string.intake_trend_no_previous)
    }

@Composable
private fun ReportSectionHeader(title: String, description: String) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = LayoutTokens.PageHorizontalPadding,
                    top = LayoutTokens.SectionSpacing,
                    end = LayoutTokens.PageHorizontalPadding,
                    bottom = LayoutTokens.SectionLabelVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(LayoutTokens.ListItemGap),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PressureSourceItem(source: IntakeSourceRow, onClick: () -> Unit) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.ContentGap,
                ),
        verticalArrangement = Arrangement.spacedBy(LayoutTokens.ListItemGap),
    ) {
        Text(
            source.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (source.groupName.isNotBlank()) {
            Text(
                source.groupName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(
                R.string.intake_source_waiting,
                numberFormat.format(source.pending),
                (source.pendingShare * 100).roundToInt(),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                R.string.intake_source_stats,
                numberFormat.format(source.received),
                numberFormat.format(source.opened),
                numberFormat.format(source.unreadBacklog),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { source.pendingShare.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LayoutTokens.PageHorizontalPadding),
        thickness = LayoutTokens.DividerThickness,
    )
}

@Composable
private fun AttentionGroupItem(group: IntakeGroupRow) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.ContentGap,
                ),
        verticalArrangement = Arrangement.spacedBy(LayoutTokens.ContentGap),
    ) {
        Text(
            group.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ShareIndicator(
            label = stringResource(R.string.intake_share_incoming),
            share = group.incomingShare,
            color = MaterialTheme.colorScheme.secondary,
        )
        ShareIndicator(
            label = stringResource(R.string.intake_share_opened),
            share = group.openedShare,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LayoutTokens.PageHorizontalPadding),
        thickness = LayoutTokens.DividerThickness,
    )
}

@Composable
private fun ShareIndicator(
    label: String,
    share: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    val percent = (share * 100).roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(LayoutTokens.ListItemGap)) {
        Row {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.intake_percent, percent),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { share.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
        )
    }
}

@Composable
private fun RecommendationSection(
    recommendation: IntakeRecommendation,
    onReviewSource: (IntakeSourceRow) -> Unit,
    onReviewGroup: (IntakeGroupRow) -> Unit,
) {
    ReportSectionHeader(
        title = stringResource(R.string.intake_recommendation),
        description = stringResource(R.string.intake_recommendation_desc),
    )
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = LayoutTokens.PageHorizontalPadding),
        shape = ShapeTokens.Surface,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
    ) {
        Row(
            modifier = Modifier.padding(LayoutTokens.ActionGap),
            horizontalArrangement = Arrangement.spacedBy(LayoutTokens.ContentGap),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LayoutTokens.ContentGap),
            ) {
                when (recommendation) {
                    is IntakeRecommendation.ReviewSource -> {
                        Text(
                            stringResource(
                                R.string.intake_recommendation_source_title,
                                recommendation.source.name,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(
                                R.string.intake_recommendation_source_body,
                                recommendation.source.received,
                                recommendation.source.pending,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = { onReviewSource(recommendation.source) }) {
                            Text(stringResource(R.string.view_articles))
                        }
                    }
                    is IntakeRecommendation.ReviewGroup -> {
                        Text(
                            stringResource(
                                R.string.intake_recommendation_group_title,
                                recommendation.group.name,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(
                                R.string.intake_recommendation_group_body,
                                (recommendation.group.incomingShare * 100).roundToInt(),
                                (recommendation.group.openedShare * 100).roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = { onReviewGroup(recommendation.group) }) {
                            Text(stringResource(R.string.view_articles))
                        }
                    }
                    IntakeRecommendation.NoChange -> {
                        Text(
                            stringResource(R.string.intake_recommendation_none_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(R.string.intake_recommendation_none_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineMessage(text: String) {
    Text(
        text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.ContentGap,
                ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MethodologyNote() {
    Text(
        text = stringResource(R.string.intake_methodology_note),
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.SectionSpacing,
                ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
