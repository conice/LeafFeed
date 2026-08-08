package me.ash.reader.ui.page.settings.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiConnection
import me.ash.reader.domain.model.ai.AiModelProfile
import me.ash.reader.domain.model.ai.AiOutputMode
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.domain.model.ai.DEFAULT_MAX_OUTPUT_TOKENS
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun AiSettingsPage(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val featureSettings by context.dataStore.data
        .collectAsStateWithLifecycle(initialValue = androidx.datastore.preferences.core.emptyPreferences())
    val features = featureSettings.toFeatureSettings()

    var connectionDialog by remember { mutableStateOf(false) }
    var modelDialog by remember { mutableStateOf(false) }
    var promptDialog by remember { mutableStateOf(false) }
    var articleCountDialog by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }
    var providerDialog by remember { mutableStateOf(false) }
    var authDialog by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<SelectionTarget?>(null) }
    var fallbackTask by remember { mutableStateOf<AiTask?>(null) }
    var fallbackDraft by remember { mutableStateOf<Set<String>>(emptySet()) }
    var connectionToDelete by remember { mutableStateOf<AiConnection?>(null) }
    var modelToDelete by remember { mutableStateOf<AiModelProfile?>(null) }
    var promptToDelete by remember { mutableStateOf<AiPrompt?>(null) }
    var restorePromptsDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<AiPrompt?>(null) }
    var editingConnection by remember { mutableStateOf<AiConnection?>(null) }

    var connectionName by remember { mutableStateOf("") }
    var connectionBaseUrl by remember { mutableStateOf("") }
    var connectionApiKey by remember { mutableStateOf("") }
    var connectionModelId by remember { mutableStateOf("") }
    var connectionModelName by remember { mutableStateOf("") }
    var connectionProvider by remember { mutableStateOf(AiProvider.RESPONSES) }
    var connectionAuthType by remember { mutableStateOf(AiAuthType.BEARER) }

    var selectedConnectionId by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var modelMaxTokens by remember { mutableStateOf(DEFAULT_MAX_OUTPUT_TOKENS.toString()) }
    var modelTemperature by remember { mutableStateOf("") }
    var editingModel by remember { mutableStateOf<AiModelProfile?>(null) }

    var promptName by remember { mutableStateOf("") }
    var promptSystem by remember { mutableStateOf("") }
    var promptUser by remember { mutableStateOf("") }
    var promptItem by remember { mutableStateOf("{title} · {index}") }
    var promptTask by remember { mutableStateOf(AiTask.ARTICLE_SUMMARY) }
    var promptOutputMode by remember { mutableStateOf(AiOutputMode.MARKDOWN) }
    var articleCount by remember { mutableStateOf("30") }

    fun resetConnectionDraft(provider: AiProvider = AiProvider.RESPONSES) {
        connectionName = ""
        connectionApiKey = ""
        connectionModelId = ""
        connectionModelName = ""
        connectionProvider = provider
        connectionBaseUrl = provider.defaultBaseUrl()
        connectionAuthType = provider.defaultAuthType()
    }

    fun resetPromptDraft(task: AiTask = AiTask.ARTICLE_SUMMARY) {
        promptName = ""
        promptSystem = ""
        promptUser = ""
        promptItem = "{title} · {index}"
        promptTask = task
        promptOutputMode = AiOutputMode.MARKDOWN
    }

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
                item {
                    DisplayText(text = stringResource(R.string.ai_settings), desc = "")
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "API connections")
                    state.connections.forEach { connection ->
                        val models = state.models.filter { it.connectionId == connection.id }
                        SettingItem(
                            title = connection.name,
                            desc = "${connection.provider.displayName()} · ${connection.baseUrl} · ${models.size} model(s)",
                            onClick = {
                                editingConnection = connection
                                connectionName = connection.name
                                connectionBaseUrl = connection.baseUrl
                                connectionApiKey = ""
                                connectionModelId = ""
                                connectionModelName = ""
                                connectionProvider = connection.provider
                                connectionAuthType = connection.authType
                                connectionDialog = true
                            },
                            action = {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete ${connection.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { connectionToDelete = connection },
                                )
                            },
                        )
                    }
                    SettingItem(
                        title = "Add API connection",
                        desc = "Save another Responses, Gemini or Anthropic endpoint",
                        icon = Icons.Rounded.Add,
                        onClick = {
                            editingConnection = null
                            resetConnectionDraft()
                            connectionDialog = true
                        },
                    ) {}
                    SettingItem(
                        title = "Add model to connection",
                        desc = "Reuse a saved API key with another model",
                        icon = Icons.Rounded.Add,
                        enabled = state.connections.isNotEmpty(),
                        onClick = {
                            editingModel = null
                            selectedConnectionId = state.connections.firstOrNull()?.id.orEmpty()
                            modelId = ""
                            modelName = ""
                            modelMaxTokens = DEFAULT_MAX_OUTPUT_TOKENS.toString()
                            modelTemperature = ""
                            modelDialog = true
                        },
                    ) {}
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Saved models")
                    state.models.forEach { model ->
                        val connection = state.connections.firstOrNull { it.id == model.connectionId }
                        SettingItem(
                            title = model.displayName,
                            desc = "${connection?.name ?: "Missing connection"} · ${model.modelId}",
                            onClick = {
                                editingModel = model
                                selectedConnectionId = model.connectionId
                                modelId = model.modelId
                                modelName = model.displayName
                                modelMaxTokens = model.maxOutputTokens.toString()
                                modelTemperature = model.temperature?.toString().orEmpty()
                                modelDialog = true
                            },
                            action = {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete ${model.displayName}",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { modelToDelete = model },
                                )
                            },
                        )
                    }
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Task routing")
                    val titleModel = state.models.firstOrNull { it.id == state.titleBinding?.primaryModelId }
                    val articleModel = state.models.firstOrNull { it.id == state.articleBinding?.primaryModelId }
                    val titlePrompt = state.prompts.firstOrNull { it.id == state.titleBinding?.promptId }
                    val articlePrompt = state.prompts.firstOrNull { it.id == state.articleBinding?.promptId }
                    SettingItem(
                        title = "Title summary model",
                        desc = titleModel?.displayName ?: "Not configured",
                        onClick = { selection = SelectionTarget(AiTask.TITLE_SUMMARY, SelectionKind.MODEL) },
                    ) {}
                    SettingItem(
                        title = "Title summary prompt",
                        desc = titlePrompt?.name ?: "Not configured",
                        onClick = { selection = SelectionTarget(AiTask.TITLE_SUMMARY, SelectionKind.PROMPT) },
                    ) {}
                    SettingItem(
                        title = "Title summary fallback models",
                        desc = fallbackDescription(state, AiTask.TITLE_SUMMARY),
                        onClick = {
                            fallbackTask = AiTask.TITLE_SUMMARY
                            fallbackDraft = (state.titleBinding?.fallbackModelIds ?: emptyList()).toSet()
                        },
                    ) {}
                    SettingItem(
                        title = "Article summary model",
                        desc = articleModel?.displayName ?: "Not configured",
                        onClick = { selection = SelectionTarget(AiTask.ARTICLE_SUMMARY, SelectionKind.MODEL) },
                    ) {}
                    SettingItem(
                        title = "Article summary fallback models",
                        desc = fallbackDescription(state, AiTask.ARTICLE_SUMMARY),
                        onClick = {
                            fallbackTask = AiTask.ARTICLE_SUMMARY
                            fallbackDraft = (state.articleBinding?.fallbackModelIds ?: emptyList()).toSet()
                        },
                    ) {}
                    SettingItem(
                        title = "Article summary prompt",
                        desc = articlePrompt?.name ?: "Not configured",
                        onClick = { selection = SelectionTarget(AiTask.ARTICLE_SUMMARY, SelectionKind.PROMPT) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_article_count),
                        desc = state.titleBinding?.articleCount?.toString() ?: "30",
                        onClick = {
                            articleCount = (state.titleBinding?.articleCount ?: 30).toString()
                            articleCountDialog = true
                        },
                    ) {}
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Prompt library")
                    state.prompts.forEach { prompt ->
                        SettingItem(
                            title = prompt.name,
                            desc = "${prompt.task.displayName()}${if (prompt.builtIn) " · built-in" else " · custom"}",
                            onClick = {
                                editingPrompt = prompt
                                promptName = prompt.name
                                promptSystem = prompt.systemTemplate
                                promptUser = prompt.userTemplate
                                promptItem = prompt.itemTemplate
                                promptTask = prompt.task
                                promptOutputMode = prompt.outputMode
                                promptDialog = true
                            },
                            action = {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete ${prompt.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = { promptToDelete = prompt },
                                )
                            },
                        )
                    }
                    SettingItem(
                        title = "Add prompt",
                        desc = "Create a reusable prompt for either summary task",
                        icon = Icons.Rounded.Add,
                        onClick = {
                            editingPrompt = null
                            resetPromptDraft()
                            promptDialog = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_restore_builtin_prompts),
                        desc = stringResource(R.string.ai_restore_builtin_prompts_desc),
                        icon = Icons.Rounded.Restore,
                        onClick = { restorePromptsDialog = true },
                    ) {}
                }
                item {
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Request behavior")
                    SettingItem(
                        title = "Stream responses",
                        desc = "Show partial output as it arrives",
                        onClick = {
                            scope.launch {
                                context.dataStore.edit {
                                    it[FeaturePreferenceKeys.aiStreamingEnabled] = !features.aiStreamingEnabled
                                }
                            }
                        },
                    ) {
                        RYSwitch(activated = features.aiStreamingEnabled) {
                            scope.launch {
                                context.dataStore.edit {
                                    it[FeaturePreferenceKeys.aiStreamingEnabled] = !features.aiStreamingEnabled
                                }
                            }
                        }
                    }
                    SettingItem(
                        title = "Request timeout",
                        desc = "${features.aiTimeoutSeconds / 60} minutes",
                        onClick = { timeoutDialog = true },
                    ) {}
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    ConnectionDialog(
        visible = connectionDialog,
        title = if (editingConnection == null) "Add API connection" else "Edit API connection",
        name = connectionName,
        baseUrl = connectionBaseUrl,
        apiKey = connectionApiKey,
        hasExistingSecret = editingConnection?.secretRef != null,
        modelId = connectionModelId,
        modelName = connectionModelName,
        provider = connectionProvider,
        authType = connectionAuthType,
        onNameChange = { connectionName = it },
        onBaseUrlChange = { connectionBaseUrl = it },
        onApiKeyChange = { connectionApiKey = it },
        onModelIdChange = { connectionModelId = it },
        onModelNameChange = { connectionModelName = it },
        onProviderClick = { providerDialog = true },
        onAuthClick = { authDialog = true },
        onDismiss = {
            connectionDialog = false
            editingConnection = null
        },
        onConfirm = {
            viewModel.saveConnection(
                existing = editingConnection,
                name = connectionName,
                provider = connectionProvider,
                baseUrl = connectionBaseUrl,
                authType = connectionAuthType,
                apiKey = connectionApiKey,
                modelId = connectionModelId,
                modelName = connectionModelName,
            )
            connectionDialog = false
            editingConnection = null
        },
    )

    ModelDialog(
        visible = modelDialog,
        title = if (editingModel == null) "Add model" else "Edit model",
        connections = state.connections,
        selectedConnectionId = selectedConnectionId,
        modelId = modelId,
        modelName = modelName,
        maxOutputTokens = modelMaxTokens,
        temperature = modelTemperature,
        onConnectionChange = { selectedConnectionId = it },
        onModelIdChange = { modelId = it },
        onModelNameChange = { modelName = it },
        onMaxOutputTokensChange = { modelMaxTokens = it.filter(Char::isDigit) },
        onTemperatureChange = { modelTemperature = it },
        onDismiss = {
            modelDialog = false
            editingModel = null
        },
        onConfirm = {
            if (selectedConnectionId.isNotBlank() && modelId.isNotBlank()) {
                viewModel.saveModel(
                    (editingModel ?: AiModelProfile(
                        connectionId = selectedConnectionId,
                        modelId = modelId,
                        displayName = modelName.ifBlank { modelId },
                    )).copy(
                        connectionId = selectedConnectionId,
                        modelId = modelId.trim(),
                        displayName = modelName.ifBlank { modelId }.trim(),
                        maxOutputTokens = modelMaxTokens.toIntOrNull()
                            ?: DEFAULT_MAX_OUTPUT_TOKENS,
                        temperature = modelTemperature.toDoubleOrNull(),
                    )
                )
            }
            modelDialog = false
            editingModel = null
        },
    )

    PromptDialog(
        visible = promptDialog,
        title = if (editingPrompt == null) "Add prompt" else "Edit prompt",
        name = promptName,
        system = promptSystem,
        user = promptUser,
        item = promptItem,
        task = promptTask,
        outputMode = promptOutputMode,
        onNameChange = { promptName = it },
        onSystemChange = { promptSystem = it },
        onUserChange = { promptUser = it },
        onItemChange = { promptItem = it },
        onTaskChange = { promptTask = it },
        onOutputModeChange = { promptOutputMode = it },
        onDismiss = {
            promptDialog = false
            editingPrompt = null
        },
        onConfirm = {
            if (promptName.isNotBlank() && promptSystem.isNotBlank() && promptUser.isNotBlank()) {
                viewModel.savePrompt(
                    (editingPrompt ?: AiPrompt(
                        name = promptName.trim(),
                        task = promptTask,
                        systemTemplate = promptSystem,
                        userTemplate = promptUser,
                        itemTemplate = promptItem,
                        outputMode = promptOutputMode,
                    )).copy(
                        name = promptName.trim(),
                        task = promptTask,
                        systemTemplate = promptSystem,
                        userTemplate = promptUser,
                        itemTemplate = promptItem,
                        outputMode = promptOutputMode,
                    )
                )
            }
            promptDialog = false
            editingPrompt = null
        },
    )

    articleCountDialog.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = { articleCountDialog = false },
            title = { Text(stringResource(R.string.ai_article_count)) },
            text = {
                OutlinedTextField(
                    value = articleCount,
                    onValueChange = { articleCount = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveArticleCount(articleCount.toIntOrNull() ?: 30)
                    articleCountDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { articleCountDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    selection?.let { target ->
        val options = when (target.kind) {
            SelectionKind.MODEL -> state.models
                .filter { model ->
                    model.enabled && state.connections.firstOrNull { it.id == model.connectionId }?.enabled == true
                }
                .map { model ->
                    val connection = state.connections.firstOrNull { it.id == model.connectionId }
                    RadioDialogOption(
                        text = "${model.displayName} · ${connection?.name ?: "missing connection"}",
                        selected = model.id == target.currentId(state),
                        onClick = { viewModel.selectModel(target.task, model.id) },
                    )
                }
            SelectionKind.PROMPT -> state.prompts
                .filter { it.task == target.task }
                .map { prompt ->
                    RadioDialogOption(
                        text = prompt.name,
                        selected = prompt.id == target.currentId(state),
                        onClick = { viewModel.selectPrompt(target.task, prompt.id) },
                    )
                }
        }
        RadioDialog(
            visible = true,
            title = if (target.kind == SelectionKind.MODEL) "Select model" else "Select prompt",
            options = options,
            onDismissRequest = { selection = null },
        )
    }

    fallbackTask?.let { task ->
        val binding = if (task == AiTask.TITLE_SUMMARY) state.titleBinding else state.articleBinding
        FallbackModelsDialog(
            visible = true,
            task = task,
            models = state.models,
            primaryModelId = binding?.primaryModelId.orEmpty(),
            selectedModelIds = fallbackDraft,
            onToggle = { modelId ->
                fallbackDraft = if (modelId in fallbackDraft) {
                    fallbackDraft - modelId
                } else {
                    fallbackDraft + modelId
                }
            },
            onDismiss = { fallbackTask = null },
            onConfirm = {
                viewModel.setFallbackModels(task, fallbackDraft.toList())
                fallbackTask = null
            },
        )
    }

    providerDialog.takeIf { it }?.let {
        RadioDialog(
            visible = true,
            title = "Provider protocol",
            options = AiProvider.entries.map { provider ->
                RadioDialogOption(
                    text = provider.displayName(),
                    selected = provider == connectionProvider,
                    onClick = {
                        connectionProvider = provider
                        connectionBaseUrl = provider.defaultBaseUrl()
                        connectionAuthType = provider.defaultAuthType()
                        providerDialog = false
                    },
                )
            },
            onDismissRequest = { providerDialog = false },
        )
    }

    authDialog.takeIf { it }?.let {
        RadioDialog(
            visible = true,
            title = "Authentication",
            options = AiAuthType.entries.map { auth ->
                RadioDialogOption(
                    text = auth.displayName(),
                    selected = auth == connectionAuthType,
                    onClick = { connectionAuthType = auth; authDialog = false },
                )
            },
            onDismissRequest = { authDialog = false },
        )
    }

    RadioDialog(
        visible = timeoutDialog,
        title = "Request timeout",
        options = listOf(120, 300, 600).map { seconds ->
            RadioDialogOption(
                text = "${seconds / 60} minutes",
                selected = features.aiTimeoutSeconds == seconds,
                onClick = {
                    scope.launch {
                        context.dataStore.edit { it[FeaturePreferenceKeys.aiTimeoutSeconds] = seconds }
                    }
                },
            )
        },
        onDismissRequest = { timeoutDialog = false },
    )

    connectionToDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("Delete connection?") },
            text = { Text("${connection.name} and its saved models will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConnection(connection.id)
                    connectionToDelete = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("Delete model?") },
            text = { Text("${model.displayName} will be removed from task routing.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteModel(model.id)
                    modelToDelete = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    promptToDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text("Delete prompt?") },
            text = { Text(prompt.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePrompt(prompt.id)
                    promptToDelete = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { promptToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (restorePromptsDialog) {
        AlertDialog(
            onDismissRequest = { restorePromptsDialog = false },
            title = { Text(stringResource(R.string.ai_restore_builtin_prompts_confirm_title)) },
            text = { Text(stringResource(R.string.ai_restore_builtin_prompts_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreBuiltInPrompts()
                        restorePromptsDialog = false
                    },
                ) {
                    Text(stringResource(R.string.restore_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { restorePromptsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ConnectionDialog(
    visible: Boolean,
    title: String,
    name: String,
    baseUrl: String,
    apiKey: String,
    hasExistingSecret: Boolean,
    modelId: String,
    modelName: String,
    provider: AiProvider,
    authType: AiAuthType,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onProviderClick: () -> Unit,
    onAuthClick: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, onNameChange, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = provider.displayName(),
                    onValueChange = {},
                    label = { Text("Protocol") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Tap the protocol row to choose another provider") },
                )
                TextButton(onClick = onProviderClick) { Text("Choose protocol") }
                OutlinedTextField(baseUrl, onBaseUrlChange, label = { Text("Base URL") }, singleLine = true)
                TextButton(onClick = onAuthClick) { Text("Authentication: ${authType.displayName()}") }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = {
                        if (hasExistingSecret && authType != AiAuthType.NONE) {
                            Text("Leave blank to keep the saved key")
                        }
                    },
                )
                OutlinedTextField(modelId, onModelIdChange, label = { Text("Initial model ID") }, singleLine = true)
                OutlinedTextField(modelName, onModelNameChange, label = { Text("Model display name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                onClick = onConfirm,
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ModelDialog(
    visible: Boolean,
    title: String,
    connections: List<AiConnection>,
    selectedConnectionId: String,
    modelId: String,
    modelName: String,
    maxOutputTokens: String,
    temperature: String,
    onConnectionChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onMaxOutputTokensChange: (String) -> Unit,
    onTemperatureChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Connection", style = MaterialTheme.typography.labelLarge)
                connections.forEach { connection ->
                    TextButton(onClick = { onConnectionChange(connection.id) }) {
                        Text(if (connection.id == selectedConnectionId) "✓ ${connection.name}" else connection.name)
                    }
                }
                OutlinedTextField(modelId, onModelIdChange, label = { Text("Model ID") }, singleLine = true)
                OutlinedTextField(modelName, onModelNameChange, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(
                    value = maxOutputTokens,
                    onValueChange = onMaxOutputTokensChange,
                    label = { Text("Max output tokens") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    label = { Text("Temperature (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PromptDialog(
    visible: Boolean,
    title: String,
    name: String,
    system: String,
    user: String,
    item: String,
    task: AiTask,
    outputMode: AiOutputMode,
    onNameChange: (String) -> Unit,
    onSystemChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onItemChange: (String) -> Unit,
    onTaskChange: (AiTask) -> Unit,
    onOutputModeChange: (AiOutputMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(name, onNameChange, label = { Text("Name") }, singleLine = true)
                Row {
                    AiTask.entries.forEach { candidate ->
                        TextButton(onClick = { onTaskChange(candidate) }) {
                            Text(if (candidate == task) "✓ ${candidate.displayName()}" else candidate.displayName())
                        }
                    }
                }
                Row {
                    AiOutputMode.entries.forEach { candidate ->
                        TextButton(onClick = { onOutputModeChange(candidate) }) {
                            Text(if (candidate == outputMode) "✓ ${candidate.name}" else candidate.name)
                        }
                    }
                }
                OutlinedTextField(
                    value = system,
                    onValueChange = onSystemChange,
                    label = { Text("System prompt") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = onUserChange,
                    label = { Text("User template") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = item,
                    onValueChange = onItemChange,
                    label = { Text("Title item template") },
                    supportingText = { Text("Available: {id}, {title}, {index}, {content}, {items}, {link}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FallbackModelsDialog(
    visible: Boolean,
    task: AiTask,
    models: List<AiModelProfile>,
    primaryModelId: String,
    selectedModelIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    val candidates = models.filter { model ->
        model.enabled && model.id != primaryModelId
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fallback models · ${task.displayName()}") },
        text = {
            if (candidates.isEmpty()) {
                Text("Add at least one other enabled model to use fallback routing.")
            } else {
                Column {
                    candidates.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = model.id in selectedModelIds,
                                onCheckedChange = { onToggle(model.id) },
                            )
                            Text(
                                text = model.displayName,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private enum class SelectionKind { MODEL, PROMPT }

private data class SelectionTarget(
    val task: AiTask,
    val kind: SelectionKind,
) {
    fun currentId(state: AiSettingsUiState): String = when (kind) {
        SelectionKind.MODEL -> if (task == AiTask.TITLE_SUMMARY) {
            state.titleBinding?.primaryModelId.orEmpty()
        } else {
            state.articleBinding?.primaryModelId.orEmpty()
        }
        SelectionKind.PROMPT -> if (task == AiTask.TITLE_SUMMARY) {
            state.titleBinding?.promptId.orEmpty()
        } else {
            state.articleBinding?.promptId.orEmpty()
        }
    }
}

private fun fallbackDescription(state: AiSettingsUiState, task: AiTask): String {
    val binding = if (task == AiTask.TITLE_SUMMARY) state.titleBinding else state.articleBinding
    val names = binding?.fallbackModelIds.orEmpty().mapNotNull { id ->
        state.models.firstOrNull { it.id == id }?.displayName
    }
    return names.takeIf { it.isNotEmpty() }?.joinToString() ?: "None"
}

private fun AiProvider.displayName(): String = when (this) {
    AiProvider.RESPONSES -> "OpenAI Responses"
    AiProvider.GEMINI -> "Google Gemini"
    AiProvider.ANTHROPIC -> "Anthropic Messages"
}

private fun AiAuthType.displayName(): String = when (this) {
    AiAuthType.NONE -> "None"
    AiAuthType.BEARER -> "Bearer token"
    AiAuthType.API_KEY_HEADER -> "API key header"
    AiAuthType.API_KEY_QUERY -> "API key query parameter"
}

private fun AiTask.displayName(): String = when (this) {
    AiTask.TITLE_SUMMARY -> "Title summary"
    AiTask.ARTICLE_SUMMARY -> "Article summary"
}

private fun AiProvider.defaultBaseUrl(): String = when (this) {
    AiProvider.RESPONSES -> "https://api.openai.com/v1"
    AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
    AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1"
}

private fun AiProvider.defaultAuthType(): AiAuthType = when (this) {
    AiProvider.RESPONSES -> AiAuthType.BEARER
    AiProvider.GEMINI,
    AiProvider.ANTHROPIC -> AiAuthType.API_KEY_HEADER
}
