@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.ash.reader.ui.page.settings.features

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
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

    fun clearHistory() {
        val id = accountId.value ?: return
        viewModelScope.launch { repository.clearHistory(id) }
    }
}

@Composable
fun AutomationPage(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    initialFeedId: String? = null,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val executions by viewModel.executions.collectAsStateWithLifecycle()
    val options by viewModel.scopeOptions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AutomationRule?>(null) }
    var pendingInitialFeedId by remember(initialFeedId) { mutableStateOf(initialFeedId) }
    var creating by remember(initialFeedId) { mutableStateOf(initialFeedId != null) }
    var failedActivityOnly by remember { mutableStateOf(false) }
    var showAllActivity by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val activityGroups = remember(executions, failedActivityOnly) {
        executions.toActivityGroups().filter { group ->
            !failedActivityOnly || group.executions.any {
                it.execution.status == AutomationExecutionStatus.FAILED.name ||
                    it.execution.status == AutomationExecutionStatus.INTERRUPTED.name
            }
        }
    }

    if (creating || editing != null) {
        val closeEditor = {
            creating = false
            editing = null
            pendingInitialFeedId = null
        }
        AutomationEditorPage(
            rule = editing,
            initialFeedId = pendingInitialFeedId.takeIf { creating },
            options = options,
            onDismiss = closeEditor,
            onSave = {
                viewModel.save(it)
                closeEditor()
            },
        )
        return
    }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (executions.isNotEmpty()) {
                    TextButton(onClick = { confirmClearHistory = true }) { Text("Clear") }
                }
            }
        }
        if (executions.isNotEmpty()) {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !failedActivityOnly,
                        onClick = { failedActivityOnly = false },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = failedActivityOnly,
                        onClick = { failedActivityOnly = true },
                        label = { Text("Failed") },
                    )
                }
            }
        }
        if (activityGroups.isEmpty()) {
            item { EmptyManagerRow(if (failedActivityOnly) "No failed activity" else "No activity yet") }
        }
        items(
            if (showAllActivity) activityGroups else activityGroups.take(10),
            key = { it.key },
        ) { group ->
            ActivityRow(group, onOpenArticle)
        }
        if (activityGroups.size > 10) {
            item {
                TextButton(
                    onClick = { showAllActivity = !showAllActivity },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(if (showAllActivity) "Show less" else "Show more") }
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear activity history?") },
            text = { Text("This removes activity logs only. It will not run actions again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    viewModel.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
private fun ActivityRow(
    group: AutomationActivityGroup,
    onOpenArticle: (String) -> Unit,
) {
    val item = group.executions.first()
    val articleExists = item.articleTitle != null
    SettingItem(
        enabled = articleExists,
        title = item.articleTitle?.takeIf { it.isNotBlank() } ?: item.execution.articleId,
        desc = buildString {
            append(item.ruleName)
            item.feedName?.takeIf { it.isNotBlank() }?.let {
                append(" - ")
                append(it)
            }
            append("\n")
            append(
                group.executions.joinToString(" - ") { execution ->
                    val action = enumValueOrNull<AutomationActionType>(execution.execution.actionType)
                        ?.displayName() ?: execution.execution.actionType.displayEnumName()
                    val status = enumValueOrNull<AutomationExecutionStatus>(execution.execution.status)
                        ?.displayName() ?: execution.execution.status.displayEnumName()
                    val attempt = execution.execution.attempt
                    "$action: $status${if (attempt > 1) " (attempt $attempt)" else ""}"
                }
            )
            append("\n")
            append(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(group.startedAt))
            )
            group.executions.firstNotNullOfOrNull { it.execution.message?.takeIf(String::isNotBlank) }
                ?.let { append("\n"); append(it) }
        },
        descMaxLines = 4,
        onClick = { if (articleExists) onOpenArticle(item.execution.articleId) },
    )
}

private data class AutomationActivityGroup(
    val key: String,
    val startedAt: Long,
    val executions: List<AutomationExecutionSummary>,
)

private fun List<AutomationExecutionSummary>.toActivityGroups(): List<AutomationActivityGroup> =
    groupBy { summary ->
        val execution = summary.execution
        Triple(
            execution.articleId,
            execution.ruleId,
            execution.startedAt / ACTIVITY_GROUP_WINDOW_MILLIS,
        )
    }.map { (_, executions) ->
        AutomationActivityGroup(
            key = executions.minOf { it.execution.id },
            startedAt = executions.maxOf { it.execution.startedAt },
            executions = executions.sortedBy { it.execution.actionType },
        )
    }.sortedByDescending { it.startedAt }

private const val ACTIVITY_GROUP_WINDOW_MILLIS = 60_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AutomationEditorPage(
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

private fun automationDescription(rule: AutomationRule, options: AutomationScopeOptions): String {
    val target = when (rule.scope) {
        AutomationScope.GLOBAL -> "All feeds"
        AutomationScope.GROUP -> rule.scopeIds.targetSummary(options.groups)
        AutomationScope.FEED -> rule.scopeIds.targetSummary(options.feeds)
    }
    return "${rule.scope.displayName()}: $target\n${rule.groups.sumOf { it.conditions.size }} conditions - ${rule.actions.joinToString { it.displayName() }}"
}

private fun List<String>.targetSummary(options: List<AutomationScopeOption>): String {
    val names = map { id -> options.firstOrNull { it.id == id }?.name ?: id }
    return if (names.size <= 3) {
        names.joinToString()
    } else {
        "${names.take(3).joinToString()} +${names.size - 3} more"
    }
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
