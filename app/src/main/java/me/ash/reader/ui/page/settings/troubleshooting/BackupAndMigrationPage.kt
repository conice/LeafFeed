@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.settings.troubleshooting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Date
import me.ash.reader.R
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.infrastructure.android.DateFormat
import me.ash.reader.infrastructure.android.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.infrastructure.android.toString
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun BackupAndMigrationPage(
    onBack: () -> Unit,
    viewModel: TroubleshootingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState = viewModel.troubleshootingUiState.collectAsStateValue()
    var importedPreferences by remember { mutableStateOf(ByteArray(0)) }
    var exportOptionsVisible by remember { mutableStateOf(false) }
    var includeSensitivePreferences by remember { mutableStateOf(false) }

    val exportPreferences =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.JSON)) {
            uri ->
            viewModel.exportPreferencesAsJSON(context, includeSensitivePreferences) { data ->
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(data)
                    }
                }
            }
        }
    val importPreferences =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { input ->
                    importedPreferences = input.readBytes()
                    viewModel.tryImport(context, importedPreferences) { result ->
                        val message =
                            result.fold(
                                onSuccess = { imported ->
                                    resources.getString(
                                        R.string.preferences_imported,
                                        imported.importedCount,
                                        imported.skippedCount,
                                    )
                                },
                                onFailure = { resources.getString(R.string.import_failed) },
                            )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val exportReadingData =
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
    val importReadingData =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { input ->
                    viewModel.importReadingData(input.readBytes()) { result ->
                        val message =
                            result.fold(
                                onSuccess = { imported ->
                                    resources.getString(
                                        R.string.reading_data_imported,
                                        imported.tags,
                                        imported.tagRefs,
                                        imported.notes,
                                        imported.savedSearches,
                                        imported.readingStates,
                                        imported.automations,
                                        imported.skipped,
                                    )
                                },
                                onFailure = { resources.getString(R.string.import_failed) },
                            )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    val exportAutomations =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.JSON)) {
            uri ->
            uri?.let {
                viewModel.exportAutomations { data ->
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(data)
                    }
                }
            }
        }
    val importAutomations =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { input ->
                    viewModel.importAutomations(input.readBytes()) { result ->
                        val message =
                            result.fold(
                                onSuccess = { imported ->
                                    resources.getString(
                                        R.string.automations_imported,
                                        imported.imported,
                                        imported.skipped,
                                    )
                                },
                                onFailure = { resources.getString(R.string.import_failed) },
                            )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

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
                item { DisplayText(text = "Backup and migration", desc = "") }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Preferences")
                    SettingItem(
                        title = stringResource(R.string.import_from_json),
                        onClick = { importPreferences.launch(arrayOf(MimeType.ANY)) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.export_as_json),
                        onClick = { exportOptionsVisible = true },
                    ) {}
                    Spacer(Modifier.height(24.dp))
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Reading data")
                    SettingItem(
                        title = stringResource(R.string.import_reading_data),
                        onClick = { importReadingData.launch(arrayOf(MimeType.JSON)) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.export_reading_data),
                        onClick = {
                            exportReadingData.launch(
                                "LeafFeed-reading-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
                            )
                        },
                    ) {}
                    Spacer(Modifier.height(24.dp))
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Automations")
                    SettingItem(
                        title = stringResource(R.string.import_automations),
                        onClick = { importAutomations.launch(arrayOf(MimeType.JSON)) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.export_automations),
                        onClick = {
                            exportAutomations.launch(
                                "LeafFeed-automations-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
                            )
                        },
                    ) {}
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    RYDialog(
        visible = exportOptionsVisible,
        onDismissRequest = { exportOptionsVisible = false },
        title = { Text(stringResource(R.string.export_as_json)) },
        text = { Text(stringResource(R.string.export_preferences_sensitive_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    exportOptionsVisible = false
                    includeSensitivePreferences = false
                    exportPreferences.launch(
                        "LeafFeed-settings-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
                    )
                }
            ) {
                Text(stringResource(R.string.export_without_api_keys))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    exportOptionsVisible = false
                    includeSensitivePreferences = true
                    exportPreferences.launch(
                        "LeafFeed-settings-${Date().toString(DateFormat.YYYYMMDD_DASH_HHMM)}.json"
                    )
                }
            ) {
                Text(stringResource(R.string.export_with_api_keys))
            }
        },
    )

    RYDialog(
        visible = uiState.warningDialogVisible,
        onDismissRequest = viewModel::hideWarningDialog,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ReportGmailerrorred,
                contentDescription = stringResource(R.string.import_from_json),
            )
        },
        title = { Text(stringResource(R.string.import_from_json)) },
        text = { Text(stringResource(R.string.invalid_json_file_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.hideWarningDialog()
                    viewModel.importPreferencesFromJSON(context, importedPreferences)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::hideWarningDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
