@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.ash.reader.ui.page.settings.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.AutomationRepository
import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationConditionDraft
import me.ash.reader.domain.model.article.AutomationDraft
import me.ash.reader.domain.model.article.AutomationExecutionStatus
import me.ash.reader.domain.model.article.AutomationExecutionSummary
import me.ash.reader.domain.model.article.AutomationField
import me.ash.reader.domain.model.article.AutomationOperator
import me.ash.reader.domain.model.article.AutomationRule
import me.ash.reader.domain.model.article.AutomationScope
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.page.settings.SettingItem

data class AutomationScopeOption(val id: String, val name: String)

data class AutomationScopeOptions(
    val groups: List<AutomationScopeOption> = emptyList(),
    val feeds: List<AutomationScopeOption> = emptyList(),
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    accountService: AccountService,
    private val repository: AutomationRepository,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
) : ViewModel() {
    private val accountId = accountService.currentAccountIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rules = accountService.currentAccountIdFlow.filterNotNull()
        .flatMapLatest(repository::observeRules)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val executions = accountService.currentAccountIdFlow.filterNotNull()
        .flatMapLatest(repository::observeRecentExecutions)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scopeOptions = accountService.currentAccountIdFlow.mapLatest { id ->
        if (id == null) return@mapLatest AutomationScopeOptions()
        AutomationScopeOptions(
            groups = groupDao.queryAll(id).sortedBy { it.name }.map { AutomationScopeOption(it.id, it.name) },
            feeds = feedDao.queryAll(id).sortedBy { it.name }.map { AutomationScopeOption(it.id, it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutomationScopeOptions())

    fun save(draft: AutomationDraft) {
        val id = accountId.value ?: return
        viewModelScope.launch { repository.save(id, draft) }
    }

    fun setEnabled(rule: AutomationRule, enabled: Boolean) =
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }

    fun delete(rule: AutomationRule) = viewModelScope.launch { repository.delete(rule.id) }
}

@Composable
fun AutomationPage(onBack: () -> Unit, viewModel: AutomationViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val executions by viewModel.executions.collectAsStateWithLifecycle()
    val options by viewModel.scopeOptions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AutomationRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    ManagementScaffold("Automations", onBack) {
        item {
            TextButton(
                onClick = { creating = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add automation")
            }
        }
        if (rules.isEmpty()) item { EmptyManagerRow("No automations") }
        items(rules, key = { it.id }) { rule ->
            SettingItem(
                title = rule.name,
                desc = automationDescription(rule, options),
                descMaxLines = 3,
                onClick = { editing = rule },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rule.enabled,
                        onCheckedChange = { viewModel.setEnabled(rule, it) },
                    )
                    IconButton(onClick = { editing = rule }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit automation")
                    }
                    IconButton(onClick = { viewModel.delete(rule) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete automation")
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Recent executions",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (executions.isEmpty()) item { EmptyManagerRow("No executions yet") }
        items(executions.take(50), key = {
            "${it.execution.articleId}:${it.execution.ruleId}:${it.execution.actionType}"
        }) { item ->
            ExecutionRow(item)
        }
    }

    if (creating || editing != null) {
        AutomationEditorDialog(
            rule = editing,
            options = options,
            onDismiss = { creating = false; editing = null },
            onSave = {
                viewModel.save(it)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun ExecutionRow(item: AutomationExecutionSummary) {
    val status = enumValueOrNull<AutomationExecutionStatus>(item.execution.status)
    val statusText = status?.displayName() ?: item.execution.status
    val actionText = enumValueOrNull<AutomationActionType>(item.execution.actionType)?.displayName()
        ?: item.execution.actionType.displayEnumName()
    SettingItem(
        title = item.articleTitle?.takeIf { it.isNotBlank() } ?: item.execution.articleId,
        desc = buildString {
            append(item.ruleName)
            append(" - ")
            append(actionText)
            item.feedName?.takeIf { it.isNotBlank() }?.let {
                append("\n")
                append(it)
            }
            append("\n")
            append(statusText)
            append(" - ")
            append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(item.execution.executedAt))
            item.execution.message?.takeIf { it.isNotBlank() }?.let { append("\n"); append(it) }
        },
        descMaxLines = 4,
        onClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationEditorDialog(
    rule: AutomationRule?,
    options: AutomationScopeOptions,
    onDismiss: () -> Unit,
    onSave: (AutomationDraft) -> Unit,
) {
    var draft by remember(rule?.id) { mutableStateOf(rule.toDraft()) }
    val targetOptions = if (draft.scope == AutomationScope.GROUP) options.groups else options.feeds
    val valid = draft.name.isNotBlank() &&
        (draft.scope == AutomationScope.GLOBAL || draft.scopeId.isNotBlank()) &&
        draft.groups.isNotEmpty() && draft.groups.all { group ->
            group.isNotEmpty() && group.all { condition ->
                condition.value.isNotBlank() &&
                    condition.operator in condition.field.allowedOperators() &&
                    (!condition.field.isBoolean() || condition.value.toBooleanStrictOrNull() != null) &&
                    (!condition.field.isNumeric() || condition.value.toDoubleOrNull() != null) &&
                    (condition.operator != AutomationOperator.REGEX || runCatching { Regex(condition.value) }.isSuccess)
            }
        } && draft.actions.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "Add automation" else "Edit automation") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("Scope", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutomationScope.entries.forEach { scope ->
                            FilterChip(
                                selected = draft.scope == scope,
                                onClick = { draft = draft.copy(scope = scope, scopeId = "") },
                                label = { Text(scope.displayName()) },
                            )
                        }
                    }
                    if (draft.scope != AutomationScope.GLOBAL) {
                        EnumMenu(
                            label = if (draft.scope == AutomationScope.GROUP) "Group" else "Feed",
                            value = targetOptions.firstOrNull { it.id == draft.scopeId }?.name ?: "Select",
                            values = targetOptions,
                            valueLabel = { it.name },
                            onSelected = { draft = draft.copy(scopeId = it.id) },
                        )
                    }
                }
                draft.groups.forEachIndexed { groupIndex, group ->
                    item(key = "group-$groupIndex") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (groupIndex == 0) "When all conditions match" else "OR when all conditions match",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (draft.groups.size > 1) {
                                    IconButton(onClick = {
                                        draft = draft.copy(groups = draft.groups.filterIndexed { i, _ -> i != groupIndex })
                                    }) { Icon(Icons.Outlined.Delete, "Remove condition group") }
                                }
                            }
                            group.forEachIndexed { conditionIndex, condition ->
                                ConditionEditor(
                                    condition = condition,
                                    canDelete = group.size > 1,
                                    onChange = { updated ->
                                        draft = draft.copy(groups = draft.groups.replaceCondition(groupIndex, conditionIndex, updated))
                                    },
                                    onDelete = {
                                        draft = draft.copy(groups = draft.groups.replaceGroup(groupIndex, group.filterIndexed { i, _ -> i != conditionIndex }))
                                    },
                                )
                            }
                            TextButton(onClick = {
                                draft = draft.copy(groups = draft.groups.replaceGroup(groupIndex, group + defaultCondition()))
                            }) { Icon(Icons.Outlined.Add, null); Text("Add AND condition") }
                        }
                    }
                }
                item {
                    TextButton(onClick = {
                        draft = draft.copy(groups = draft.groups + listOf(listOf(defaultCondition())))
                    }) {
                        Icon(Icons.Outlined.Add, null)
                        Text("Add OR group")
                    }
                }
                item {
                    Text("Actions", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutomationActionType.entries.forEach { action ->
                            FilterChip(
                                selected = action in draft.actions,
                                onClick = {
                                    val withoutConflict = when (action) {
                                        AutomationActionType.MARK_READ -> draft.actions - AutomationActionType.MARK_UNREAD
                                        AutomationActionType.MARK_UNREAD -> draft.actions - AutomationActionType.MARK_READ
                                        else -> draft.actions
                                    }
                                    draft = draft.copy(
                                        actions = if (action in draft.actions) draft.actions - action else withoutConflict + action
                                    )
                                },
                                label = { Text(action.displayName()) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(draft) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConditionEditor(
    condition: AutomationConditionDraft,
    canDelete: Boolean,
    onChange: (AutomationConditionDraft) -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                ?: AutomationOperator.EQUALS,
                            value = if (field.isBoolean() && condition.value !in setOf("true", "false")) {
                                "true"
                            } else condition.value,
                            caseSensitive = condition.caseSensitive && field.isText(),
                        )
                    )
                },
                modifier = Modifier.weight(1f),
            )
            EnumMenu(
                label = "Operator",
                value = condition.operator.displayName(),
                values = condition.field.allowedOperators(),
                valueLabel = { it.displayName() },
                onSelected = { onChange(condition.copy(operator = it)) },
                modifier = Modifier.weight(1f),
            )
            if (canDelete) {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove condition") }
            }
        }
        if (condition.field.isBoolean()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = condition.value.toBooleanStrictOrNull() ?: true,
                    onCheckedChange = { onChange(condition.copy(value = it.toString())) },
                )
                Text("Value is true")
            }
        } else {
            OutlinedTextField(
                value = condition.value,
                onValueChange = { onChange(condition.copy(value = it)) },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (condition.field.isText()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = condition.caseSensitive,
                    onCheckedChange = { onChange(condition.copy(caseSensitive = it)) },
                )
                Text("Case sensitive")
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }) { Text("$label: $value") }
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
        scopeId = scopeId,
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

private fun automationDescription(rule: AutomationRule, options: AutomationScopeOptions): String {
    val target = when (rule.scope) {
        AutomationScope.GLOBAL -> "All feeds"
        AutomationScope.GROUP -> options.groups.firstOrNull { it.id == rule.scopeId }?.name ?: rule.scopeId
        AutomationScope.FEED -> options.feeds.firstOrNull { it.id == rule.scopeId }?.name ?: rule.scopeId
    }
    return "${rule.scope.displayName()}: $target\n${rule.groups.sumOf { it.conditions.size }} conditions - ${rule.actions.joinToString { it.displayName() }}"
}

private fun Enum<*>.displayName(): String = name.displayEnumName()

private fun AutomationActionType.displayName(): String = when (this) {
    AutomationActionType.NOTIFY -> "Local notification"
    else -> name.displayEnumName()
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

private fun String.displayEnumName(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
