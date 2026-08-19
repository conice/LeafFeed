@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conice.morss.ui.page.settings.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.conice.morss.domain.model.article.AutomationActionType
import com.conice.morss.domain.model.article.AutomationConditionDraft
import com.conice.morss.domain.model.article.AutomationDraft
import com.conice.morss.domain.model.article.AutomationField
import com.conice.morss.domain.model.article.AutomationOperator
import com.conice.morss.domain.model.article.AutomationRule
import com.conice.morss.domain.model.article.AutomationScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AutomationEditorPage(
    rule: AutomationRule?,
    initialFeedId: String? = null,
    options: AutomationScopeOptions,
    onDismiss: () -> Unit,
    onSave: (AutomationDraft) -> Unit,
) {
    var draft by remember(rule?.id, initialFeedId) {
        mutableStateOf(
            rule.toDraft().let { value ->
                if (rule == null && initialFeedId != null) {
                    value.copy(scope = AutomationScope.FEED, scopeIds = listOf(initialFeedId))
                } else value
            }
        )
    }
    val validationError = draft.validationError()
    val targetOptions = if (draft.scope == AutomationScope.GROUP) options.groups else options.feeds
    val toggleAction: (AutomationActionType) -> Unit = { action ->
        val withoutConflict = when (action) {
            AutomationActionType.MARK_READ -> draft.actions - AutomationActionType.MARK_UNREAD
            AutomationActionType.MARK_UNREAD -> draft.actions - AutomationActionType.MARK_READ
            else -> draft.actions
        }
        draft = draft.copy(
            actions = if (action in draft.actions) draft.actions - action else withoutConflict + action
        )
    }

    BackHandler(onBack = onDismiss)
    ManagementScaffold(
        title = if (rule == null) "Add automation" else "Edit automation",
        onBack = onDismiss,
        actions = {
            TextButton(
                enabled = validationError == null,
                onClick = { onSave(draft) },
            ) { Text("Save") }
        },
    ) {
        item {
            EditorSection("Name") {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Automation name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                validationError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            EditorSection("Applies to") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AutomationScope.entries.forEachIndexed { index, scope ->
                        SegmentedButton(
                            selected = draft.scope == scope,
                            onClick = {
                                if (draft.scope != scope) {
                                    draft = draft.copy(scope = scope, scopeIds = emptyList())
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AutomationScope.entries.size,
                            ),
                            modifier = Modifier.weight(1f),
                        ) { Text(scope.scopeLabel(), maxLines = 1) }
                    }
                }
                if (draft.scope != AutomationScope.GLOBAL) {
                    ScopeTargetSelector(
                        label = if (draft.scope == AutomationScope.GROUP) "Groups" else "Feeds",
                        selectedIds = draft.scopeIds,
                        options = targetOptions,
                        onSelectionChanged = { draft = draft.copy(scopeIds = it) },
                    )
                }
            }
        }
        item {
            Text(
                "When",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        draft.groups.forEachIndexed { groupIndex, group ->
            item(key = "group-$groupIndex") {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Condition group ${groupIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (draft.groups.size > 1) {
                            IconButton(onClick = {
                                draft = draft.copy(
                                    groups = draft.groups.filterIndexed { index, _ -> index != groupIndex }
                                )
                            }) {
                                Icon(Icons.Outlined.Delete, "Remove condition group")
                            }
                        }
                    }
                    group.forEachIndexed { conditionIndex, condition ->
                        if (conditionIndex > 0) {
                            LogicDivider("AND")
                        }
                        ConditionEditor(
                            condition = condition,
                            canDelete = group.size > 1,
                            onChange = { updated ->
                                draft = draft.copy(
                                    groups = draft.groups.replaceCondition(
                                        groupIndex,
                                        conditionIndex,
                                        updated,
                                    )
                                )
                            },
                            onDelete = {
                                draft = draft.copy(
                                    groups = draft.groups.replaceGroup(
                                        groupIndex,
                                        group.filterIndexed { index, _ -> index != conditionIndex },
                                    )
                                )
                            },
                        )
                    }
                    TextButton(onClick = {
                        draft = draft.copy(
                            groups = draft.groups.replaceGroup(groupIndex, group + defaultCondition())
                        )
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("Add AND condition")
                    }
                    if (groupIndex < draft.groups.lastIndex) {
                        LogicDivider("OR")
                    }
                }
            }
        }
        item {
            TextButton(
                onClick = {
                    draft = draft.copy(
                        groups = draft.groups + listOf(listOf(defaultCondition()))
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add OR group")
            }
        }
        item {
            EditorSection("Then") {
                ActionGroup(
                    title = "Article display",
                    actions = listOf(AutomationActionType.FILTER, AutomationActionType.HIGHLIGHT),
                    selected = draft.actions,
                    onToggle = toggleAction,
                )
                ActionGroup(
                    title = "Reading state",
                    actions = listOf(
                        AutomationActionType.STAR,
                        AutomationActionType.READ_LATER,
                        AutomationActionType.MARK_READ,
                        AutomationActionType.MARK_UNREAD,
                    ),
                    selected = draft.actions,
                    onToggle = toggleAction,
                )
                ActionGroup(
                    title = "Content and device",
                    actions = listOf(
                        AutomationActionType.NOTIFY,
                        AutomationActionType.DOWNLOAD_PODCAST,
                        AutomationActionType.FETCH_FULL_CONTENT,
                    ),
                    selected = draft.actions,
                    onToggle = toggleAction,
                )
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ActionGroup(
    title: String,
    actions: List<AutomationActionType>,
    selected: Set<AutomationActionType>,
    onToggle: (AutomationActionType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action ->
                FilterChip(
                    selected = action in selected,
                    onClick = { onToggle(action) },
                    label = { Text(action.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun LogicDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ConditionEditor(
    condition: AutomationConditionDraft,
    canDelete: Boolean,
    onChange: (AutomationConditionDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val valueError = condition.valueError()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Condition", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Remove condition")
                }
            }
        }
        EnumMenu(
            label = "Field",
            value = condition.field.displayName(),
            values = AutomationField.entries,
            valueLabel = { it.displayName() },
            onSelected = { field ->
                onChange(
                    condition.copy(
                        field = field,
                        operator = condition.operator.takeIf { it in field.allowedOperators() }
                            ?: field.allowedOperators().first(),
                        value = if (field.isBoolean()) "true" else condition.value,
                        caseSensitive = condition.caseSensitive && field.isText(),
                    )
                )
            },
        )
        EnumMenu(
            label = "Operator",
            value = condition.operator.displayName(),
            values = condition.field.allowedOperators(),
            valueLabel = { it.displayName() },
            onSelected = { onChange(condition.copy(operator = it)) },
        )
        if (condition.field.isBoolean()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val checked = condition.value.toBooleanStrictOrNull() ?: true
                Text("Value: ${if (checked) "True" else "False"}", modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = { onChange(condition.copy(value = it.toString())) },
                )
            }
        } else {
            OutlinedTextField(
                value = condition.value,
                onValueChange = { onChange(condition.copy(value = it)) },
                label = { Text(condition.valueLabel()) },
                singleLine = true,
                isError = valueError != null,
                supportingText = valueError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (condition.field.isNumeric()) KeyboardType.Decimal else KeyboardType.Text
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (condition.field.isText()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Case sensitive", modifier = Modifier.weight(1f))
                Switch(
                    checked = condition.caseSensitive,
                    onCheckedChange = { onChange(condition.copy(caseSensitive = it)) },
                )
            }
        }
    }
}

@Composable
private fun <T> EnumMenu(
    label: String,
    value: String,
    values: List<T>,
    valueLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $value", modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(valueLabel(option)) },
                    onClick = { expanded = false; onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun ScopeTargetSelector(
    label: String,
    selectedIds: List<String>,
    options: List<AutomationScopeOption>,
    onSelectionChanged: (List<String>) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pendingIds by remember { mutableStateOf(selectedIds) }
    val selectedOptions = selectedIds.map { id ->
        options.firstOrNull { it.id == id } ?: AutomationScopeOption(id, id)
    }
    if (selectedOptions.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            selectedOptions.take(5).forEach { option ->
                FilterChip(
                    selected = true,
                    onClick = { onSelectionChanged(selectedIds - option.id) },
                    label = { Text(option.name, maxLines = 1) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove ${option.name}")
                    },
                )
            }
            if (selectedOptions.size > 5) {
                Text(
                    "+${selectedOptions.size - 5} more",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    OutlinedButton(
        onClick = {
            query = ""
            pendingIds = selectedIds
            visible = true
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (selectedIds.isEmpty()) "Select $label" else "$label: ${selectedIds.size} selected",
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (visible) {
        val filtered = options.filter { it.name.contains(query, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("Select $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "No $label found",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(filtered, key = { it.id }) { option ->
                            TextButton(
                                onClick = {
                                    pendingIds =
                                        if (option.id in pendingIds) pendingIds - option.id
                                        else pendingIds + option.id
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = option.id in pendingIds,
                                    onCheckedChange = null,
                                )
                                Text(option.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingIds = emptyList() }) { Text("Clear all") }
                    TextButton(onClick = { visible = false }) { Text("Cancel") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectionChanged(pendingIds)
                    visible = false
                }) { Text("Done") }
            },
        )
    }
}

private fun AutomationDraft.validationError(): String? {
    if (name.isBlank()) return "Enter a name"
    if (scope != AutomationScope.GLOBAL && scopeIds.isEmpty()) {
        return if (scope == AutomationScope.GROUP) {
            "Select at least one group"
        } else {
            "Select at least one feed"
        }
    }
    if (groups.isEmpty() || groups.any { it.isEmpty() }) return "Add at least one condition"
    groups.forEachIndexed { groupIndex, group ->
        group.forEachIndexed { conditionIndex, condition ->
            if (condition.operator !in condition.field.allowedOperators()) {
                return "Condition ${groupIndex + 1}.${conditionIndex + 1} has an invalid operator"
            }
            condition.valueError()?.let { error ->
                return "Condition ${groupIndex + 1}.${conditionIndex + 1}: $error"
            }
        }
    }
    if (actions.isEmpty()) return "Select at least one action"
    return null
}

private fun AutomationConditionDraft.valueError(): String? = when {
    value.isBlank() -> "Enter a value"
    field.isBoolean() && value.toBooleanStrictOrNull() == null -> "Select true or false"
    field.isNumeric() && value.toDoubleOrNull() == null -> "Enter a valid number"
    operator == AutomationOperator.REGEX && runCatching { Regex(value) }.isFailure ->
        "Invalid regular expression"
    else -> null
}

private fun AutomationConditionDraft.valueLabel(): String = when (field) {
    AutomationField.MEDIA_SIZE -> "Value (bytes)"
    AutomationField.MEDIA_DURATION -> "Value (seconds)"
    else -> "Value"
}

private fun AutomationField.displayName(): String = when (this) {
    AutomationField.SITE_URL -> "Site domain"
    AutomationField.MEDIA_SIZE -> "Media size (bytes)"
    AutomationField.MEDIA_DURATION -> "Media duration (seconds)"
    else -> name.displayEnumName()
}

private fun AutomationField.isBoolean(): Boolean = this in setOf(
    AutomationField.HAS_AUDIO,
    AutomationField.HAS_VIDEO,
    AutomationField.IS_UNREAD,
    AutomationField.IS_STARRED,
    AutomationField.IS_READ_LATER,
)

private fun AutomationField.isNumeric(): Boolean =
    this == AutomationField.MEDIA_SIZE || this == AutomationField.MEDIA_DURATION

private fun AutomationField.isText(): Boolean = !isBoolean() && !isNumeric()

private fun AutomationField.allowedOperators(): List<AutomationOperator> = when {
    isBoolean() -> listOf(AutomationOperator.EQUALS, AutomationOperator.NOT_EQUALS)
    isNumeric() -> listOf(
        AutomationOperator.EQUALS,
        AutomationOperator.NOT_EQUALS,
        AutomationOperator.GREATER_THAN,
        AutomationOperator.LESS_THAN,
    )
    else -> listOf(
        AutomationOperator.CONTAINS,
        AutomationOperator.NOT_CONTAINS,
        AutomationOperator.EQUALS,
        AutomationOperator.NOT_EQUALS,
        AutomationOperator.REGEX,
    )
}

private fun AutomationScope.scopeLabel(): String = when (this) {
    AutomationScope.GLOBAL -> "All feeds"
    AutomationScope.GROUP -> "Groups"
    AutomationScope.FEED -> "Feeds"
}

private fun AutomationRule?.toDraft(): AutomationDraft = if (this == null) {
    AutomationDraft(
        name = "",
        groups = listOf(listOf(defaultCondition())),
        actions = emptySet(),
    )
} else {
    AutomationDraft(
        id = id,
        name = name,
        enabled = enabled,
        scope = scope,
        scopeIds = scopeIds,
        groups = groups.map { group -> group.conditions.map { AutomationConditionDraft(it.field, it.operator, it.value, it.caseSensitive) } },
        actions = actions.toSet(),
    )
}

private fun defaultCondition() = AutomationConditionDraft(
    field = AutomationField.TITLE,
    operator = AutomationOperator.CONTAINS,
    value = "",
)

private fun List<List<AutomationConditionDraft>>.replaceCondition(
    groupIndex: Int,
    conditionIndex: Int,
    condition: AutomationConditionDraft,
) = mapIndexed { index, group ->
    if (index == groupIndex) group.mapIndexed { i, item -> if (i == conditionIndex) condition else item } else group
}

private fun List<List<AutomationConditionDraft>>.replaceGroup(
    groupIndex: Int,
    group: List<AutomationConditionDraft>,
) = mapIndexed { index, item -> if (index == groupIndex) group else item }
