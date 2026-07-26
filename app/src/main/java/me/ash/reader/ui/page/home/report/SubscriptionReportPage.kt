@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.ash.reader.ui.page.home.report

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.NumberFormat
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.collectAsStateValue

private const val ATTENTION_PREVIEW_COUNT = 8

@Composable
fun SubscriptionReportPage(
    onBack: () -> Unit,
    onOpenReading: () -> Unit = {},
    onCreateAutomation: (String) -> Unit = {},
    viewModel: SubscriptionReportViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateValue()
    val uriHandler = LocalUriHandler.current
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }
    var moveIds by remember { mutableStateOf(emptySet<String>()) }
    var deleteIds by remember { mutableStateOf(emptySet<String>()) }
    var editRow by remember { mutableStateOf<SubscriptionReportRow?>(null) }

    BackHandler(selectedIds.isNotEmpty()) { selectedIds = emptySet() }
    LaunchedEffect(state.accountId) {
        selectedIds = emptySet()
        expandedIds = emptySet()
        moveIds = emptySet()
        deleteIds = emptySet()
        editRow = null
    }

    RYScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector =
                    if (selectedIds.isEmpty()) Icons.AutoMirrored.Rounded.ArrowBack
                    else Icons.Outlined.Close,
                contentDescription = if (selectedIds.isEmpty()) "Back" else "Clear selection",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { if (selectedIds.isEmpty()) onBack() else selectedIds = emptySet() },
            )
        },
        actions = {
            if (selectedIds.isNotEmpty()) {
                IconButton(
                    onClick = { selectedIds = state.rows.mapTo(mutableSetOf()) { it.id } },
                ) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = "Select all visible")
                }
                Text(
                    "${selectedIds.size} selected",
                    modifier = Modifier.padding(end = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                BatchActionBar(
                    count = selectedIds.size,
                    onMove = { moveIds = selectedIds },
                    onMute = {
                        viewModel.muteNotifications(selectedIds)
                        selectedIds = emptySet()
                    },
                    onMarkRead = {
                        viewModel.markRead(selectedIds)
                        selectedIds = emptySet()
                    },
                    onDelete = { deleteIds = selectedIds },
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                DisplayText(text = "Subscription report", desc = "")
            }
            state.operationMessage?.let { message ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (message.isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = viewModel::dismissOperationMessage) {
                            Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
            item {
                ReportRangeSelector(state.range, viewModel::selectRange)
            }
            if (state.isLoading) {
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
                    OverviewSection(state)
                }
                item {
                    SectionTitle("Needs attention", state.attentionRows.size.toString())
                }
                if (state.attentionRows.isEmpty()) {
                    item { EmptySection("No subscriptions need attention") }
                } else {
                    items(
                        state.attentionRows.take(ATTENTION_PREVIEW_COUNT),
                        key = { "attention-${it.id}" },
                    ) { row ->
                        AttentionRow(
                            row = row,
                            onClick = {
                                viewModel.selectFilter(ReportFilter.NEEDS_ATTENTION)
                            },
                        )
                    }
                    if (state.attentionRows.size > ATTENTION_PREVIEW_COUNT) {
                        item {
                            TextButton(
                                onClick = {
                                    viewModel.selectFilter(ReportFilter.NEEDS_ATTENTION)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Text("View all ${state.attentionRows.size}")
                            }
                        }
                    }
                }
                item {
                    SectionTitle("All subscriptions", state.rows.size.toString())
                    ReportControls(
                        filter = state.filter,
                        sort = state.sort,
                        onFilter = viewModel::selectFilter,
                        onSort = viewModel::selectSort,
                    )
                }
                if (state.rows.isEmpty()) {
                    item { EmptySection("No subscriptions match this filter") }
                } else {
                    items(state.rows, key = { "feed-${it.id}" }) { row ->
                        SubscriptionRow(
                            row = row,
                            selected = row.id in selectedIds,
                            expanded = row.id in expandedIds,
                            onSelect = {
                                selectedIds =
                                    if (row.id in selectedIds) selectedIds - row.id
                                    else selectedIds + row.id
                            },
                            onExpand = {
                                expandedIds =
                                    if (row.id in expandedIds) expandedIds - row.id
                                    else expandedIds + row.id
                            },
                            onOpenReading = { viewModel.openFeed(row.id, onOpenReading) },
                            onRetry = { viewModel.retry(row.id) },
                            onOpenSource = { runCatching { uriHandler.openUri(row.url) } },
                            onEdit = { editRow = row },
                            onMove = { moveIds = setOf(row.id) },
                            onMute = { viewModel.muteNotifications(setOf(row.id)) },
                            onMarkRead = { viewModel.markRead(setOf(row.id)) },
                            onCreateAutomation = { onCreateAutomation(row.id) },
                            onDelete = { deleteIds = setOf(row.id) },
                        )
                    }
                }
            }
        }
    }

    if (moveIds.isNotEmpty()) {
        MoveDialog(
            count = moveIds.size,
            groups = state.groups.map { it.id to it.name },
            onDismiss = { moveIds = emptySet() },
            onMove = { groupId ->
                viewModel.move(moveIds, groupId)
                selectedIds = emptySet()
                moveIds = emptySet()
            },
        )
    }
    if (deleteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteIds = emptySet() },
            title = { Text("Unsubscribe?") },
            text = {
                Text(
                    if (deleteIds.size == 1) {
                        "This subscription and its local articles will be removed."
                    } else {
                        "${deleteIds.size} subscriptions and their local articles will be removed."
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteIds = emptySet() }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteIds)
                        selectedIds = emptySet()
                        deleteIds = emptySet()
                    }
                ) {
                    Text("Unsubscribe", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    editRow?.let { row ->
        EditSubscriptionDialog(
            row = row,
            onDismiss = { editRow = null },
            onSave = { name, url ->
                viewModel.updateFeed(row.id, name, url)
                editRow = null
            },
        )
    }
}

@Composable
private fun ReportRangeSelector(selected: ReportRange, onSelect: (ReportRange) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Time range", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ReportRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = selected == range,
                    onClick = { onSelect(range) },
                    shape = SegmentedButtonDefaults.itemShape(index, ReportRange.entries.size),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${range.days} days")
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(state: SubscriptionReportUiState) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SectionTitle("Overview")
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Metric("Subscriptions", state.totalSubscriptions, Modifier.weight(1f))
            Metric("Active", state.activeSubscriptions, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Metric("Needs attention", state.attentionRows.size, Modifier.weight(1f))
            Metric("Articles received", state.articleCount, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            NumberFormat.getIntegerInstance().format(value),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SectionTitle(title: String, count: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        count?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline) }
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun AttentionRow(row: SubscriptionReportRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            row.issues.filter { it.requiresAttention }.joinToString(" · ") { it.label() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(row.suggestedAction(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ReportControls(
    filter: ReportFilter,
    sort: ReportSort,
    onFilter: (ReportFilter) -> Unit,
    onSort: (ReportSort) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label()) },
                )
            }
        }
        Box {
            TextButton(onClick = { sortMenu = true }) {
                Icon(Icons.Outlined.Sort, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sort: ${sort.label()}")
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                ReportSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        onClick = {
                            sortMenu = false
                            onSort(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    row: SubscriptionReportRow,
    selected: Boolean,
    expanded: Boolean,
    onSelect: () -> Unit,
    onExpand: () -> Unit,
    onOpenReading: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onMute: () -> Unit,
    onMarkRead: () -> Unit,
    onCreateAutomation: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onExpand, onLongClick = onSelect)
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onSelect() })
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(row.groupName, "${row.articleCount} received", "${row.openedCount} opened")
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.issues.isNotEmpty()) {
                    Text(
                        row.issues.joinToString(" · ") { it.label() },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.issues.any { it.requiresAttention }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuVisible = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Subscription actions")
                }
                SubscriptionActionMenu(
                    expanded = menuVisible,
                    notificationsEnabled = row.notificationsEnabled,
                    onDismiss = { menuVisible = false },
                    onOpenReading = onOpenReading,
                    onRetry = onRetry,
                    onOpenSource = onOpenSource,
                    onEdit = onEdit,
                    onMove = onMove,
                    onMute = onMute,
                    onMarkRead = onMarkRead,
                    onCreateAutomation = onCreateAutomation,
                    onDelete = onDelete,
                )
            }
        }
        if (expanded) {
            SubscriptionDetails(row)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun SubscriptionDetails(row: SubscriptionReportRow) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailLine("Feed URL", row.url)
        DetailLine("Latest article", row.latestDate?.let(dateFormat::format) ?: "No articles received")
        DetailLine("Last opened", row.lastOpenedAt?.let(dateFormat::format) ?: "Never")
        DetailLine("Unread received", NumberFormat.getIntegerInstance().format(row.unreadCount))
        DetailLine("Unread backlog", NumberFormat.getIntegerInstance().format(row.unreadBacklog))
        DetailLine(
            "Interaction rate",
            NumberFormat.getPercentInstance().format(row.interactionRate),
        )
        DetailLine("Starred", row.starredCount.toString())
        DetailLine("Read later", row.readLaterCount.toString())
        DetailLine("Suggested action", row.suggestedAction())
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(112.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SubscriptionActionMenu(
    expanded: Boolean,
    notificationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenReading: () -> Unit,
    onRetry: () -> Unit,
    onOpenSource: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onMute: () -> Unit,
    onMarkRead: () -> Unit,
    onCreateAutomation: () -> Unit,
    onDelete: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("View articles") }, onClick = { run(onOpenReading) })
        DropdownMenuItem(
            text = { Text("Retry update") },
            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            onClick = { run(onRetry) },
        )
        DropdownMenuItem(
            text = { Text("Open source") },
            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
            onClick = { run(onOpenSource) },
        )
        DropdownMenuItem(
            text = { Text("Edit") },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            onClick = { run(onEdit) },
        )
        DropdownMenuItem(
            text = { Text("Move to group") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
            onClick = { run(onMove) },
        )
        DropdownMenuItem(
            text = { Text(if (notificationsEnabled) "Mute notifications" else "Notifications muted") },
            enabled = notificationsEnabled,
            leadingIcon = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null) },
            onClick = { run(onMute) },
        )
        DropdownMenuItem(
            text = { Text("Mark all read") },
            leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
            onClick = { run(onMarkRead) },
        )
        DropdownMenuItem(
            text = { Text("Create automation") },
            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
            onClick = { run(onCreateAutomation) },
        )
        DropdownMenuItem(
            text = { Text("Unsubscribe", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { run(onDelete) },
        )
    }
}

@Composable
private fun BatchActionBar(
    count: Int,
    onMove: () -> Unit,
    onMute: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Move to group")
            }
            IconButton(onClick = onMute) { Icon(Icons.Outlined.NotificationsOff, "Mute notifications") }
            IconButton(onClick = onMarkRead) { Icon(Icons.Outlined.DoneAll, "Mark all read") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Unsubscribe", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MoveDialog(
    count: Int,
    groups: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Move subscription" else "Move $count subscriptions") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(groups, key = { it.first }) { (id, name) ->
                    TextButton(onClick = { onMove(id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditSubscriptionDialog(
    row: SubscriptionReportRow,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(row.id) { mutableStateOf(row.name) }
    var url by remember(row.id) { mutableStateOf(row.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = { onSave(name, url) },
            ) {
                Text("Save")
            }
        },
    )
}

private fun ReportIssue.label(): String =
    when (this) {
        ReportIssue.UPDATE_FAILED -> "Update failed"
        ReportIssue.DUPLICATE -> "Duplicate feed"
        ReportIssue.UNREAD_BUILDUP -> "Unread buildup"
        ReportIssue.HIGH_VOLUME -> "High volume"
        ReportIssue.NEVER_OPENED -> "Never opened"
        ReportIssue.RARELY_OPENED -> "Rarely opened"
        ReportIssue.POSSIBLY_INACTIVE -> "Possibly inactive"
        ReportIssue.NEW_SUBSCRIPTION -> "New subscription"
        ReportIssue.NO_ARTICLES_YET -> "No articles yet"
        ReportIssue.FREQUENTLY_OPENED -> "Frequently opened"
        ReportIssue.HEALTHY -> "Healthy"
    }

private fun ReportFilter.label(): String =
    when (this) {
        ReportFilter.ALL -> "All"
        ReportFilter.NEEDS_ATTENTION -> "Needs attention"
        ReportFilter.UPDATE_FAILED -> "Failed"
        ReportFilter.INACTIVE -> "Inactive"
        ReportFilter.HIGH_VOLUME -> "High volume"
        ReportFilter.RARELY_OPENED -> "Rarely opened"
        ReportFilter.FREQUENTLY_OPENED -> "Frequently opened"
        ReportFilter.UNREAD_BUILDUP -> "Unread buildup"
    }

private fun ReportSort.label(): String =
    when (this) {
        ReportSort.ATTENTION -> "Attention"
        ReportSort.RECEIVED -> "Articles received"
        ReportSort.OPENED -> "Articles opened"
        ReportSort.UNREAD -> "Unread backlog"
        ReportSort.NAME -> "Name"
    }

private fun SubscriptionReportRow.suggestedAction(): String =
    when {
        ReportIssue.UPDATE_FAILED in issues -> "Retry the update or edit the Feed URL"
        ReportIssue.DUPLICATE in issues -> "Review both subscriptions before unsubscribing"
        ReportIssue.UNREAD_BUILDUP in issues -> "Mark older articles read or reduce incoming volume"
        ReportIssue.HIGH_VOLUME in issues -> "Mute notifications, create an automation, or unsubscribe"
        ReportIssue.NEVER_OPENED in issues -> "Review whether this subscription is still useful"
        ReportIssue.RARELY_OPENED in issues -> "Move, mute, or unsubscribe"
        ReportIssue.POSSIBLY_INACTIVE in issues -> "Retry the update and check the source"
        else -> "No action needed"
    }
