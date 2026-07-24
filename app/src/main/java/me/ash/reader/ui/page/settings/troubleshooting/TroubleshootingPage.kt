@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.settings.troubleshooting

import android.content.ClipData
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import java.util.Date
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.data.Log
import me.ash.reader.domain.service.SyncWorker.Companion.ONETIME_WORK_TAG
import me.ash.reader.domain.service.SyncWorker.Companion.PERIODIC_WORK_TAG
import me.ash.reader.infrastructure.preference.OpenLinkPreference
import me.ash.reader.ui.component.base.Banner
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.DateFormat
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.toString
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.infrastructure.preference.SyncSummary
import me.ash.reader.ui.theme.palette.onLight

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TroubleshootingPage(onBack: () -> Unit, viewModel: TroubleshootingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiState = viewModel.troubleshootingUiState.collectAsStateValue()
    var byteArray by remember { mutableStateOf(ByteArray(0)) }
    var exportOptionsVisible by remember { mutableStateOf(false) }
    var includeSensitivePreferences by remember { mutableStateOf(false) }

    val syncLogList = remember { mutableStateListOf<Log>() }
    var syncSummary by remember { mutableStateOf<SyncSummary?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.getSyncLogs().let { syncLogList.addAll(it) }
        syncSummary = viewModel.getCurrentSyncSummary()
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.JSON)) {
            result ->
            viewModel.exportPreferencesAsJSON(context, includeSensitivePreferences) { byteArray ->
                result?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(byteArray)
                    }
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    byteArray = inputStream.readBytes()
                    viewModel.tryImport(context, byteArray) { result ->
                        val message = result.fold(
                            onSuccess = {
                                context.getString(
                                    R.string.preferences_imported,
                                    it.importedCount,
                                    it.skippedCount,
                                )
                            },
                            onFailure = { context.getString(R.string.import_failed) },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val readingDataExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.JSON)) {
            uri ->
            uri?.let {
                viewModel.exportReadingData { data ->
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(data)
                    }
                }
            }
        }

    val readingDataImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { input ->
                    viewModel.importReadingData(input.readBytes()) { result ->
                        val message = result.fold(
                            onSuccess = { imported ->
                                context.getString(
                                    R.string.reading_data_imported,
                                    imported.tags,
                                    imported.notes,
                                    imported.savedSearches,
                                )
                            },
                            onFailure = { context.getString(R.string.import_failed) },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val onetimeWorkerInfos =
        viewModel.workManager
            .getWorkInfosByTagFlow(ONETIME_WORK_TAG)
            .collectAsStateValue(emptyList())

    val periodicWorkerInfos =
        viewModel.workManager
            .getWorkInfosByTagFlow(PERIODIC_WORK_TAG)
            .collectAsStateValue(emptyList())

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.troubleshooting), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                    Banner(
                        title = stringResource(R.string.bug_report),
                        icon = Icons.Outlined.Info,
                        action = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.go_to),
                            )
                        },
                    ) {
                        context.openURL(
                            context.getString(R.string.issue_tracer_url),
                            OpenLinkPreference.AutoPreferCustomTabs,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = "Backup and recovery",
                    )
                    SettingItem(
                        title = stringResource(R.string.import_from_json),
                        onClick = { importLauncher.launch(arrayOf(MimeType.ANY)) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.export_as_json),
                        onClick = { exportOptionsVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.import_reading_data),
                        onClick = { readingDataImportLauncher.launch(arrayOf(MimeType.JSON)) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.export_reading_data),
                        onClick = {
                            readingDataExportLauncher.launch(
                                "LeafFeed-reading-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
                            )
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Worker infos")
                }
                syncSummary?.let { summary ->
                    item {
                        Text(
                            text = stringResource(
                                R.string.last_sync_summary,
                                summary.state.name,
                                summary.completed,
                                summary.total ?: 0,
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(onetimeWorkerInfos, key = { it.id }) {
                    WorkInfo(
                        workInfo = it,
                        onRetry = viewModel::retrySync,
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    )
                }
                items(periodicWorkerInfos, key = { it.id }) {
                    WorkInfo(
                        workInfo = it,
                        onRetry = viewModel::retrySync,
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    )
                }
                if (syncLogList.isNotEmpty()) {
                    item {
                        Subtitle(
                            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 24.dp),
                            text = "Sync errors",
                        )
                    }
                    items(syncLogList, key = { it.fileName }) {
                        SyncLogItem(
                            log = it,
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            ),
                        )
                    }
                    item {
                        Button(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                            onClick = {
                                viewModel.clearSyncLogs()
                                syncLogList.clear()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(
                        modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                    )
                }
            }
        },
    )

    RYDialog(
        visible = exportOptionsVisible,
        onDismissRequest = { exportOptionsVisible = false },
        title = { Text(text = stringResource(R.string.export_as_json)) },
        text = { Text(text = stringResource(R.string.export_preferences_sensitive_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    exportOptionsVisible = false
                    includeSensitivePreferences = false
                    preferenceFileLauncher(exportLauncher)
                }
            ) {
                Text(text = stringResource(R.string.export_without_api_keys))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    exportOptionsVisible = false
                    includeSensitivePreferences = true
                    preferenceFileLauncher(exportLauncher)
                }
            ) {
                Text(text = stringResource(R.string.export_with_api_keys))
            }
        },
    )

    RYDialog(
        visible = uiState.warningDialogVisible,
        onDismissRequest = { viewModel.hideWarningDialog() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.ReportGmailerrorred,
                contentDescription = stringResource(R.string.import_from_json),
            )
        },
        title = { Text(text = stringResource(R.string.import_from_json)) },
        text = { Text(text = stringResource(R.string.invalid_json_file_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.hideWarningDialog()
                    viewModel.importPreferencesFromJSON(context, byteArray)
                }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideWarningDialog() }) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

private fun preferenceFileLauncher(
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "LeafFeed-settings-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkInfo(
    workInfo: WorkInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember(workInfo.nextScheduleTimeMillis) { Date(workInfo.nextScheduleTimeMillis) }
    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(workInfo.tags.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(workInfo.state.toString(), style = MaterialTheme.typography.bodySmall)
        if (workInfo.runAttemptCount > 0) {
            Text(
                stringResource(R.string.work_attempts, workInfo.runAttemptCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        workInfo.outputData.keyValueMap["error"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
        if (workInfo.tags.contains(PERIODIC_WORK_TAG) && workInfo.state != WorkInfo.State.FAILED) {
            Text(
                stringResource(R.string.work_next_scheduled, date),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (workInfo.state == WorkInfo.State.FAILED) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun SyncLogItem(log: Log, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText(log.fileName, log.content))
                        )
                    }
                }
                .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(log.fileName, style = MaterialTheme.typography.titleMedium)
        Text(
            log.content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
