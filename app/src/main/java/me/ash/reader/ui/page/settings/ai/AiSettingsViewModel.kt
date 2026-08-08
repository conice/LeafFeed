package me.ash.reader.ui.page.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiConnection
import me.ash.reader.domain.model.ai.AiModelProfile
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.domain.model.ai.AiTaskBinding
import me.ash.reader.infrastructure.ai.AiConfigurationRepository

data class AiSettingsUiState(
    val connections: List<AiConnection> = emptyList(),
    val models: List<AiModelProfile> = emptyList(),
    val prompts: List<AiPrompt> = emptyList(),
    val titleBinding: AiTaskBinding? = null,
    val articleBinding: AiTaskBinding? = null,
    val initialized: Boolean = false,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val configuration: AiConfigurationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AiSettingsUiState())
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            configuration.ensureInitialized()
            combine(
                configuration.observeConnections(),
                configuration.observeAllModels(),
                configuration.observePrompts(),
                configuration.observeBinding(AiTask.TITLE_SUMMARY),
                configuration.observeBinding(AiTask.ARTICLE_SUMMARY),
            ) { connections, models, prompts, titleBinding, articleBinding ->
                AiSettingsUiState(
                    connections = connections,
                    models = models,
                    prompts = prompts,
                    titleBinding = titleBinding,
                    articleBinding = articleBinding,
                    initialized = true,
                )
            }.collect { _state.value = it }
        }
    }

    fun saveConnection(
        existing: AiConnection? = null,
        name: String,
        provider: me.ash.reader.domain.model.ai.AiProvider,
        baseUrl: String,
        authType: AiAuthType,
        apiKey: String,
        modelId: String,
        modelName: String,
    ) {
        viewModelScope.launch {
            val connection = (existing ?: AiConnection(
                name = name.trim(),
                provider = provider,
                baseUrl = baseUrl.trim(),
                authType = authType,
            )).copy(
                name = name.trim(),
                provider = provider,
                baseUrl = baseUrl.trim(),
                authType = authType,
            )
            configuration.saveConnection(connection, apiKey.trim().ifBlank { null })
            if (modelId.isNotBlank()) {
                val alreadyExists = configuration.listModels().any {
                    it.connectionId == connection.id && it.modelId == modelId.trim()
                }
                if (!alreadyExists) {
                    configuration.saveModel(
                        AiModelProfile(
                            connectionId = connection.id,
                            modelId = modelId.trim(),
                            displayName = modelName.trim().ifBlank { modelId.trim() },
                        )
                    )
                }
            }
        }
    }

    fun saveModel(model: AiModelProfile) {
        viewModelScope.launch { configuration.saveModel(model) }
    }

    fun deleteConnection(connectionId: String) {
        viewModelScope.launch { configuration.deleteConnection(connectionId) }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch { configuration.deleteModel(modelId) }
    }

    fun savePrompt(prompt: AiPrompt) {
        viewModelScope.launch { configuration.savePrompt(prompt) }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch { configuration.deletePrompt(promptId) }
    }

    fun selectPrompt(task: AiTask, promptId: String) {
        viewModelScope.launch {
            val current = configuration.getBinding(task)
            configuration.saveBinding(current.copy(promptId = promptId))
        }
    }

    fun selectModel(task: AiTask, modelId: String) {
        viewModelScope.launch {
            val current = configuration.getBinding(task)
            configuration.saveBinding(current.copy(primaryModelId = modelId))
        }
    }

    fun setFallbackModels(task: AiTask, modelIds: List<String>) {
        viewModelScope.launch {
            val current = configuration.getBinding(task)
            configuration.saveBinding(
                current.copy(
                    fallbackModelIds = modelIds.filter { it.isNotBlank() && it != current.primaryModelId }
                )
            )
        }
    }

    fun saveArticleCount(count: Int) {
        viewModelScope.launch {
            val current = configuration.getBinding(AiTask.TITLE_SUMMARY)
            configuration.saveBinding(current.copy(articleCount = count.coerceAtLeast(1)))
        }
    }
}
