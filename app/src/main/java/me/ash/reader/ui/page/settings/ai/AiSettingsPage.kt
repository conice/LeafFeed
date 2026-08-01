package me.ash.reader.ui.page.settings.ai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.AiPreferenceKeys
import me.ash.reader.infrastructure.preference.AiTaskSettings
import me.ash.reader.infrastructure.preference.DEFAULT_AI_ARTICLE_COUNT
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.FeatureSettings
import me.ash.reader.infrastructure.preference.toAiSettings
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun AiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val featureSettings by context.dataStore.data.map { it.toFeatureSettings() }
        .collectAsStateWithLifecycle(initialValue = FeatureSettings())
    val defaultPrompts =
        mapOf(
            AiTask.TitleSummary to stringResource(R.string.ai_default_prompt),
            AiTask.ArticleSummary to stringResource(R.string.ai_default_article_prompt),
        )
    val defaultInputTemplates =
        mapOf(
            AiTask.TitleSummary to stringResource(R.string.ai_default_title_input_template),
            AiTask.ArticleSummary to stringResource(R.string.ai_default_article_input_template),
        )

    var titleSummary by remember {
        mutableStateOf(
            AiTaskSettings(
                prompt = defaultPrompts.getValue(AiTask.TitleSummary),
                inputTemplate = defaultInputTemplates.getValue(AiTask.TitleSummary),
            ),
        )
    }
    var articleSummary by remember {
        mutableStateOf(
            AiTaskSettings(
                prompt = defaultPrompts.getValue(AiTask.ArticleSummary),
                inputTemplate = defaultInputTemplates.getValue(AiTask.ArticleSummary),
            ),
        )
    }
    var articleCount by remember { mutableStateOf(DEFAULT_AI_ARTICLE_COUNT) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var editValue by remember { mutableStateOf("") }
    var timeoutDialogVisible by remember { mutableStateOf(false) }

    fun taskSettings(task: AiTask): AiTaskSettings =
        when (task) {
            AiTask.TitleSummary -> titleSummary
            AiTask.ArticleSummary -> articleSummary
        }

    fun saveTask(task: AiTask, value: AiTaskSettings) {
        when (task) {
            AiTask.TitleSummary -> titleSummary = value
            AiTask.ArticleSummary -> articleSummary = value
        }
        scope.launch {
            context.dataStore.edit { preferences -> preferences.writeTask(task, value) }
        }
    }

    fun openEditor(task: AiTask, field: AiField) {
        editTarget = EditTarget(task, field)
        editValue = taskSettings(task).valueOf(field)
    }

    LaunchedEffect(Unit) {
        val preferences = context.dataStore.data.first()
        val settings =
            preferences.toAiSettings(
                defaultTitlePrompt = defaultPrompts.getValue(AiTask.TitleSummary),
                defaultArticlePrompt = defaultPrompts.getValue(AiTask.ArticleSummary),
                defaultTitleInputTemplate =
                    defaultInputTemplates.getValue(AiTask.TitleSummary),
                defaultArticleInputTemplate =
                    defaultInputTemplates.getValue(AiTask.ArticleSummary),
            )
        titleSummary = settings.titleSummary
        articleSummary = settings.articleSummary
        articleCount = settings.articleCount
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
                item {
                    DisplayText(text = stringResource(R.string.ai_settings), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    AiTaskSection(
                        title = stringResource(R.string.ai_title_summary),
                        settings = titleSummary,
                        defaultPrompt = defaultPrompts.getValue(AiTask.TitleSummary),
                        defaultInputTemplate =
                            defaultInputTemplates.getValue(AiTask.TitleSummary),
                        onEdit = { openEditor(AiTask.TitleSummary, it) },
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_article_count),
                        desc = articleCount.toString(),
                        onClick = {
                            editTarget = EditTarget(AiTask.TitleSummary, AiField.ArticleCount)
                            editValue = articleCount.toString()
                        },
                    ) {}
                }
                item {
                    AiTaskSection(
                        title = stringResource(R.string.ai_article_summary_settings),
                        settings = articleSummary,
                        defaultPrompt = defaultPrompts.getValue(AiTask.ArticleSummary),
                        defaultInputTemplate =
                            defaultInputTemplates.getValue(AiTask.ArticleSummary),
                        onEdit = { openEditor(AiTask.ArticleSummary, it) },
                    )
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = "Request behavior",
                    )
                    SettingItem(
                        title = "Stream responses",
                        desc = "Show partial output as it arrives",
                        onClick = {
                            scope.launch {
                                context.dataStore.edit {
                                    it[FeaturePreferenceKeys.aiStreamingEnabled] =
                                        !featureSettings.aiStreamingEnabled
                                }
                            }
                        },
                    ) {
                        RYSwitch(activated = featureSettings.aiStreamingEnabled) {
                            scope.launch {
                                context.dataStore.edit {
                                    it[FeaturePreferenceKeys.aiStreamingEnabled] =
                                        !featureSettings.aiStreamingEnabled
                                }
                            }
                        }
                    }
                    SettingItem(
                        title = "Request timeout",
                        desc = "${featureSettings.aiTimeoutSeconds / 60} minutes",
                        onClick = { timeoutDialogVisible = true },
                    ) {}
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

    RadioDialog(
        visible = timeoutDialogVisible,
        title = "Request timeout",
        options = listOf(120, 300, 600).map { seconds ->
            RadioDialogOption(
                text = "${seconds / 60} minutes",
                selected = featureSettings.aiTimeoutSeconds == seconds,
                onClick = {
                    scope.launch {
                        context.dataStore.edit {
                            it[FeaturePreferenceKeys.aiTimeoutSeconds] = seconds
                        }
                    }
                },
            )
        },
        onDismissRequest = { timeoutDialogVisible = false },
    )

    editTarget?.let { target ->
        val defaultPrompt = defaultPrompts.getValue(target.task)
        val defaultInputTemplate = defaultInputTemplates.getValue(target.task)
        val restorableValue =
            when (target.field) {
                AiField.Prompt -> defaultPrompt
                AiField.InputTemplate -> defaultInputTemplate
                else -> null
            }
        AiEditDialog(
            task = target.task,
            field = target.field,
            value = editValue,
            onValueChange = { editValue = it },
            onRestoreDefault = restorableValue?.let { value -> { editValue = value } },
            onDismiss = { editTarget = null },
            onConfirm = {
                if (target.field == AiField.ArticleCount) {
                    articleCount =
                        editValue.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_AI_ARTICLE_COUNT
                    scope.launch {
                        context.dataStore.edit {
                            it[AiPreferenceKeys.articleCount] = articleCount
                        }
                    }
                } else {
                    val normalizedValue =
                        when (target.field) {
                            AiField.Prompt -> editValue.ifBlank { defaultPrompt }
                            AiField.InputTemplate ->
                                editValue.ifBlank { defaultInputTemplate }
                            else -> editValue.trim()
                        }
                    saveTask(
                        target.task,
                        taskSettings(target.task).withValue(target.field, normalizedValue),
                    )
                }
                editTarget = null
            },
        )
    }
}

@Composable
private fun AiTaskSection(
    title: String,
    settings: AiTaskSettings,
    defaultPrompt: String,
    defaultInputTemplate: String,
    onEdit: (AiField) -> Unit,
) {
    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = title)
    SettingItem(
        title = stringResource(R.string.ai_url),
        desc = settings.url.ifBlank { stringResource(R.string.none) },
        onClick = { onEdit(AiField.Url) },
    ) {}
    SettingItem(
        title = stringResource(R.string.ai_api_key),
        desc = if (settings.apiKey.isBlank()) stringResource(R.string.none) else "••••••••",
        onClick = { onEdit(AiField.ApiKey) },
    ) {}
    SettingItem(
        title = stringResource(R.string.ai_model),
        desc = settings.model.ifBlank { stringResource(R.string.none) },
        onClick = { onEdit(AiField.Model) },
    ) {}
    SettingItem(
        title = stringResource(R.string.ai_prompt),
        desc =
            stringResource(
                if (settings.prompt == defaultPrompt) {
                    R.string.ai_default_value
                } else {
                    R.string.ai_custom_value
                }
            ),
        onClick = { onEdit(AiField.Prompt) },
    ) {}
    SettingItem(
        title = stringResource(R.string.ai_input_template),
        desc =
            stringResource(
                if (settings.inputTemplate == defaultInputTemplate) {
                    R.string.ai_default_value
                } else {
                    R.string.ai_custom_value
                }
            ),
        onClick = { onEdit(AiField.InputTemplate) },
    ) {}
}

@Composable
private fun AiEditDialog(
    task: AiTask,
    field: AiField,
    value: String,
    onValueChange: (String) -> Unit,
    onRestoreDefault: (() -> Unit)?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isMultiline = field == AiField.Prompt || field == AiField.InputTemplate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = field.title()) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(
                        if (field == AiField.ArticleCount) it.filter(Char::isDigit) else it
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = !isMultiline,
                minLines = if (isMultiline) 6 else 1,
                supportingText =
                    if (field == AiField.InputTemplate) {
                        {
                            Text(
                                stringResource(
                                    when (task) {
                                        AiTask.TitleSummary ->
                                            R.string.ai_title_input_template_fields
                                        AiTask.ArticleSummary ->
                                            R.string.ai_article_input_template_fields
                                    }
                                )
                            )
                        }
                    } else {
                        null
                    },
                visualTransformation =
                    if (field == AiField.ApiKey) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            if (field == AiField.ArticleCount) {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Text
                            }
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            Row {
                onRestoreDefault?.let {
                    TextButton(onClick = it) {
                        Text(stringResource(R.string.restore_default))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun AiField.title(): String =
    stringResource(
        when (this) {
            AiField.Url -> R.string.ai_url
            AiField.ApiKey -> R.string.ai_api_key
            AiField.Model -> R.string.ai_model
            AiField.Prompt -> R.string.ai_prompt
            AiField.InputTemplate -> R.string.ai_input_template
            AiField.ArticleCount -> R.string.ai_article_count
        }
    )

private fun AiTaskSettings.valueOf(field: AiField): String =
    when (field) {
        AiField.Url -> url
        AiField.ApiKey -> apiKey
        AiField.Model -> model
        AiField.Prompt -> prompt
        AiField.InputTemplate -> inputTemplate
        AiField.ArticleCount -> ""
    }

private fun AiTaskSettings.withValue(field: AiField, value: String): AiTaskSettings =
    when (field) {
        AiField.Url -> copy(url = value)
        AiField.ApiKey -> copy(apiKey = value)
        AiField.Model -> copy(model = value)
        AiField.Prompt -> copy(prompt = value)
        AiField.InputTemplate -> copy(inputTemplate = value)
        AiField.ArticleCount -> this
    }

private fun MutablePreferences.writeTask(task: AiTask, value: AiTaskSettings) {
    when (task) {
        AiTask.TitleSummary -> {
            this[AiPreferenceKeys.url] = value.url
            this[AiPreferenceKeys.apiKey] = value.apiKey
            this[AiPreferenceKeys.model] = value.model
            this[AiPreferenceKeys.titlePrompt] = value.prompt
            this[AiPreferenceKeys.titleInputTemplate] = value.inputTemplate
        }
        AiTask.ArticleSummary -> {
            this[AiPreferenceKeys.articleUrl] = value.url
            this[AiPreferenceKeys.articleApiKey] = value.apiKey
            this[AiPreferenceKeys.articleModel] = value.model
            this[AiPreferenceKeys.articlePrompt] = value.prompt
            this[AiPreferenceKeys.articleInputTemplate] = value.inputTemplate
        }
    }
}

private data class EditTarget(
    val task: AiTask,
    val field: AiField,
)

private enum class AiTask {
    TitleSummary,
    ArticleSummary,
}

private enum class AiField {
    Url,
    ApiKey,
    Model,
    Prompt,
    InputTemplate,
    ArticleCount,
}
