@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.conice.morss.ui.page.settings.troubleshooting

import android.content.ClipData
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import java.util.Date
import kotlinx.coroutines.launch
import com.conice.morss.R
import com.conice.morss.application.data.Log
import com.conice.morss.application.service.SyncWorker.Companion.ONETIME_WORK_TAG
import com.conice.morss.application.service.SyncWorker.Companion.PERIODIC_WORK_TAG
import com.conice.morss.infrastructure.preference.OpenLinkPreference
import com.conice.morss.ui.component.base.Banner
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.component.base.Subtitle
import com.conice.morss.ui.ext.collectAsStateValue
import com.conice.morss.infrastructure.android.openURL
import com.conice.morss.infrastructure.preference.SyncSummary
import com.conice.morss.ui.theme.palette.onLight

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TroubleshootingPage(onBack: () -> Unit, viewModel: TroubleshootingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val hapticFeedback = LocalHapticFeedback.current
    val syncLogList = remember { mutableStateListOf<Log>() }
    var syncSummary by remember { mutableStateOf<SyncSummary?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.getSyncLogs().let { syncLogList.addAll(it) }
        syncSummary = viewModel.getCurrentSyncSummary()
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
                    DisplayText(text = "Diagnostic details", desc = "")
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
                            resources.getString(R.string.issue_tracer_url),
                            OpenLinkPreference.AutoPreferCustomTabs,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
