package me.ash.reader.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.ui.component.base.ExpressiveIconButton
import me.ash.reader.ui.theme.MotionTokens

private val LeadingAiSummaryIndex = Regex(
    "^\\s*(?:[-*+]\\s+)?(?:#\\s*)?(?:\\[\\s*)?(?:\\(\\s*)?(\\d+)(?:\\s*[.、):\\]\\-]|\\s|$)",
)
private val TrailingAiSummaryIndex = Regex("[·•]\\s*(\\d+)\\s*$")
private val AiSummaryTagsLine = Regex("^\\s*(?:tags|标签)\\s*[:：]\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)
private val AiSummaryTagSeparator = Regex("\\s*[,，、;；|]\\s*|\\s{2,}")

@Composable
fun AiSummaryDialog(
    visible: Boolean,
    loading: Boolean,
    summary: String,
    failure: AiSummaryFailure? = null,
    failureDetail: String? = null,
    onDismiss: () -> Unit,
    articleIds: List<String> = emptyList(),
    articleTitles: List<String> = emptyList(),
    initialScrollOffset: Int = 0,
    onArticleClick: (String, Int) -> Unit = { _, _ -> },
    onMarkSummarizedAsRead: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    sourceDescription: String? = null,
    onSuggestedTagsClick: ((List<String>) -> Unit)? = null,
) {
    if (!visible) return
    val scrollState = rememberScrollState(initialScrollOffset)
    LaunchedEffect(initialScrollOffset) {
        if (scrollState.value != initialScrollOffset) {
            scrollState.scrollTo(initialScrollOffset)
        }
    }
    val refreshRotation = if (loading) {
        rememberInfiniteTransition(label = "AI summary refresh").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = MotionTokens.IndeterminateCycleMillis,
                        easing = LinearEasing,
                    ),
            ),
            label = "AI summary refresh rotation",
        ).value
    } else {
        0f
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ai_summary))
                Spacer(Modifier.weight(1f))
                if (onRegenerate != null) {
                    ExpressiveIconButton(
                        enabled = !loading,
                        onClick = onRegenerate,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.rotate(refreshRotation),
                        )
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp),
                contentAlignment = Alignment.Center,
            ) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                        sourceDescription?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        failure?.let {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(it.messageRes),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    failureDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                                        Text(
                                            text =
                                                stringResource(
                                                    R.string.ai_summary_error_details,
                                                    detail,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (summary.isNotEmpty()) {
                            if (failure != null) Spacer(Modifier.height(12.dp))
                            AiMarkdownContent(
                                markdown = summary,
                                loading = loading,
                                articleIds = articleIds,
                                articleTitles = articleTitles,
                                onArticleClick = { articleId ->
                                    onArticleClick(articleId, scrollState.value)
                                },
                                onSuggestedTagsClick = onSuggestedTagsClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            if (!loading && articleIds.isNotEmpty() && onMarkSummarizedAsRead != null) {
                TextButton(onClick = onMarkSummarizedAsRead) {
                    Text(stringResource(R.string.mark_summarized_as_read))
                }
            }
        },
    )
}

internal fun parseAiSummaryTags(line: String): List<String>? =
    AiSummaryTagsLine.matchEntire(line)?.groupValues?.getOrNull(1)
        ?.split(AiSummaryTagSeparator)
        ?.map { it.trim().removePrefix("#").trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }

internal fun findAiSummaryArticleNumber(
    line: String,
    articleTitles: List<String>,
    articleCount: Int,
): Int? {
    val normalizedLine = line.trim()
    val matchingTitleIndexes = articleTitles.indices.filter { index ->
        val title = articleTitles[index].trim()
        val number = index + 1
        normalizedLine == "$title · $number" ||
            normalizedLine == "$title$number" ||
            (title.isNotEmpty() && normalizedLine.contains(title))
    }
    if (matchingTitleIndexes.size == 1) return matchingTitleIndexes.single() + 1

    TrailingAiSummaryIndex.find(normalizedLine)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 1..articleCount }
        ?.let { return it }

    LeadingAiSummaryIndex.find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 1..articleCount }
        ?.let { return it }
    return null
}
