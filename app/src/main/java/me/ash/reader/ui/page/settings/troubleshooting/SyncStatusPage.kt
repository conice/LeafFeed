package me.ash.reader.ui.page.settings.troubleshooting

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ash.reader.R
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun SyncStatusPage(
    onBack: () -> Unit,
    navigateToLogs: () -> Unit,
    viewModel: TroubleshootingViewModel = hiltViewModel(),
) {
    val summary by viewModel.currentSyncSummary.collectAsStateWithLifecycle()

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
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
                item { DisplayText(text = "Reliability center", desc = "") }
                item { Subtitle(Modifier.padding(horizontal = 24.dp), "Latest synchronization") }
                item {
                    val current = summary
                    if (current == null) {
                        Text("No synchronization has been recorded yet.", Modifier.padding(24.dp))
                    } else {
                        SettingItem(
                            title = current.state.name.lowercase().replaceFirstChar(Char::uppercaseChar),
                            desc = current.total?.let {
                                "${current.completed} of $it feeds completed, " +
                                    "${current.scope.name.lowercase()} scope, attempt ${current.attempt + 1}"
                            } ?: "${current.completed} feeds completed, " +
                                "${current.scope.name.lowercase()} scope, attempt ${current.attempt + 1}",
                            onClick = {},
                        ) {}
                        current.errorMessage?.let {
                            Text(
                                "${current.failureKind?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar(Char::uppercaseChar) ?: "Failure"}: $it",
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (current.failedFeedIds.isNotEmpty()) {
                            Text(
                                "Failed feeds: ${(current.failedFeedNames.ifEmpty { current.failedFeedIds }).joinToString()}",
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item {
                    TextButton(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        onClick = {
                            viewModel.retrySync()
                        },
                    ) { Text("Retry synchronization") }
                }
                item {
                    SettingItem(
                        title = "Logs and worker details",
                        desc = "Open troubleshooting for detailed diagnostics",
                        onClick = navigateToLogs,
                    ) {}
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}
