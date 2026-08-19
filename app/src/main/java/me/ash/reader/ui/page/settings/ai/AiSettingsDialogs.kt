package me.ash.reader.ui.page.settings.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiConnection
import me.ash.reader.domain.model.ai.AiModelProfile
import me.ash.reader.domain.model.ai.AiOutputMode
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiTask

@Composable
internal fun ConnectionDialog(
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
internal fun ModelDialog(
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
internal fun PromptDialog(
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
internal fun FallbackModelsDialog(
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
