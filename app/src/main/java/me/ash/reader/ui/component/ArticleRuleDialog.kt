@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.ui.component.base.ExpressiveIconButton

@Composable
fun ArticleRuleDialog(
    visible: Boolean,
    title: String,
    rules: List<ArticleRule>,
    onDismiss: () -> Unit,
    onAdd: (String, Boolean, Boolean, Boolean) -> Unit,
    onEdit: (String, String, Boolean, Boolean, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (!visible) return
    var pattern by remember(visible) { mutableStateOf("") }
    var regex by remember(visible) { mutableStateOf(false) }
    var caseSensitive by remember(visible) { mutableStateOf(false) }
    var global by remember(visible) { mutableStateOf(false) }
    var editingId by remember(visible) { mutableStateOf<String?>(null) }
    var orderedRules by remember(rules) { mutableStateOf(rules) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun clearEditor() {
        editingId = null
        pattern = ""
        regex = false
        caseSensitive = false
        global = false
    }

    fun edit(rule: ArticleRule) {
        editingId = rule.id
        pattern = rule.pattern
        regex = rule.isRegex
        caseSensitive = rule.caseSensitive
        global = rule.scope == RuleScope.GLOBAL
    }

    val trimmedPattern = pattern.trim()
    val validPattern = trimmedPattern.isNotBlank() &&
        (!regex || runCatching { Regex(trimmedPattern) }.isSuccess)
    val duplicate = orderedRules.any { rule ->
        rule.id != editingId &&
            rule.pattern == trimmedPattern &&
            rule.isRegex == regex &&
            rule.caseSensitive == caseSensitive &&
            (rule.scope == RuleScope.GLOBAL) == global
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.keyword_or_regex)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = regex, onCheckedChange = { regex = it })
                    Text(stringResource(R.string.regular_expression))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Text(stringResource(R.string.case_sensitive))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = global, onCheckedChange = { global = it })
                    Text(stringResource(R.string.global_rule))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    items(orderedRules, key = { it.id }) { rule ->
                        Row(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                )
                                .fillMaxWidth()
                                .onSizeChanged { rowHeights[rule.id] = it.height },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = stringResource(R.string.reorder_rule),
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(rule.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragDistance = 0f
                                            },
                                            onDragCancel = {
                                                onReorder(orderedRules.map { it.id })
                                                dragDistance = 0f
                                            },
                                            onDragEnd = {
                                                onReorder(orderedRules.map { it.id })
                                                dragDistance = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragDistance += amount.y
                                                var index = orderedRules.indexOfFirst { it.id == rule.id }
                                                if (index < 0) return@detectDragGesturesAfterLongPress
                                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                                if ((amount.y > 0 && index >= (visibleItems.lastOrNull()?.index ?: Int.MAX_VALUE)) ||
                                                    (amount.y < 0 && index <= (visibleItems.firstOrNull()?.index ?: Int.MIN_VALUE))
                                                ) {
                                                    coroutineScope.launch { listState.scrollBy(amount.y) }
                                                }
                                                val threshold = (rowHeights[rule.id] ?: 48) / 2f
                                                while (dragDistance > threshold && index < orderedRules.lastIndex) {
                                                    orderedRules = orderedRules.toMutableList().apply {
                                                        add(index + 1, removeAt(index))
                                                    }
                                                    index++
                                                    dragDistance -= threshold * 2
                                                }
                                                while (dragDistance < -threshold && index > 0) {
                                                    orderedRules = orderedRules.toMutableList().apply {
                                                        add(index - 1, removeAt(index))
                                                    }
                                                    index--
                                                    dragDistance += threshold * 2
                                                }
                                            },
                                        )
                                    }
                                    .padding(12.dp),
                            )
                            Text(
                                text = buildString {
                                    if (rule.scope == RuleScope.GLOBAL) append("🌐 ")
                                    append(if (rule.isRegex) "/${rule.pattern}/" else rule.pattern)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { edit(rule) },
                                    )
                                    .padding(vertical = 12.dp),
                            )
                            ExpressiveIconButton(
                                onClick = {
                                    if (editingId == rule.id) clearEditor()
                                    onDelete(rule.id)
                                },
                            ) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (editingId != null) {
                    TextButton(onClick = ::clearEditor) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                TextButton(
                    enabled = validPattern && !duplicate,
                    onClick = {
                        val id = editingId
                        if (id == null) {
                            onAdd(trimmedPattern, regex, caseSensitive, global)
                            pattern = ""
                        } else {
                            onEdit(id, trimmedPattern, regex, caseSensitive, global)
                            clearEditor()
                        }
                    },
                ) {
                    Text(stringResource(if (editingId == null) R.string.add else R.string.save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
