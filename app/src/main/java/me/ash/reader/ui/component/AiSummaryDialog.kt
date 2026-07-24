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
import androidx.compose.foundation.clickable
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

@Composable
fun AiSummaryDialog(
    visible: Boolean,
    loading: Boolean,
    summary: String,
    failure: AiSummaryFailure? = null,
    onDismiss: () -> Unit,
    articleIds: List<String> = emptyList(),
    initialScrollOffset: Int = 0,
    onArticleClick: (String, Int) -> Unit = { _, _ -> },
    onMarkSummarizedAsRead: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    sourceDescription: String? = null,
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
                                Text(
                                    text = stringResource(it.messageRes),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (summary.isNotEmpty()) {
                            if (failure != null) Spacer(Modifier.height(12.dp))
                            val lines = summary.lines()
                            lines.forEachIndexed { index, line ->
                                val number = Regex("^\\s*(\\d+)[.、)]").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                val articleId = if (loading) null else {
                                    number?.let { articleIds.getOrNull(it - 1) }
                                }
                                Text(
                                    text = if (loading && index == lines.lastIndex) "$line ▍" else line,
                                    modifier = if (articleId != null) Modifier.clickable {
                                        onArticleClick(articleId, scrollState.value)
                                    } else Modifier,
                                )
                            }
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
