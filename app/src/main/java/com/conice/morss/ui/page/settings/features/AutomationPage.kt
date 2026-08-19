@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conice.morss.ui.page.settings.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.conice.morss.application.data.AutomationRepository
import com.conice.morss.application.service.AccountService
import com.conice.morss.domain.model.article.AutomationActionType
import com.conice.morss.domain.model.article.AutomationDraft
import com.conice.morss.domain.model.article.AutomationExecutionStatus
import com.conice.morss.domain.model.article.AutomationExecutionSummary
import com.conice.morss.domain.model.article.AutomationRule
import com.conice.morss.domain.model.article.AutomationScope
import com.conice.morss.domain.repository.FeedDao
import com.conice.morss.domain.repository.GroupDao
import com.conice.morss.ui.page.settings.SettingItem

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val executions = accountService.currentAccountIdFlow.filterNotNull()
        .flatMapLatest(repository::observeRecentExecutions)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scopeOptions = accountService.currentAccountIdFlow.mapLatest { id ->
        if (id == null) return@mapLatest AutomationScopeOptions()
        AutomationScopeOptions(
            groups = groupDao.queryAll(id).sortedBy { it.name }.map { AutomationScopeOption(it.id, it.name) },
            feeds = feedDao.queryAll(id).sortedBy { it.name }.map { AutomationScopeOption(it.id, it.name) },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AutomationScopeOptions(),
    )

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

internal fun Enum<*>.displayName(): String = name.displayEnumName()

internal fun AutomationActionType.displayName(): String = when (this) {
    AutomationActionType.NOTIFY -> "Local notification"
    else -> name.displayEnumName()
}

internal fun String.displayEnumName(): String =
    lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
