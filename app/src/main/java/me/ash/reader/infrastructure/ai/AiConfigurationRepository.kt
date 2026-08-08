package me.ash.reader.infrastructure.ai

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.ash.reader.R
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiConnection
import me.ash.reader.domain.model.ai.DEFAULT_ARTICLE_COUNT
import me.ash.reader.domain.model.ai.AiModelProfile
import me.ash.reader.domain.model.ai.AiOutputMode
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiResolvedModel
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.domain.model.ai.AiTaskBinding

/** Owns the complete AI configuration graph and its encrypted secrets. */
@Singleton
class AiConfigurationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AiDatabase,
    private val dao: AiDao,
    private val secretStore: AiSecretStore,
) {
    private val initializationMutex = Mutex()

    suspend fun ensureInitialized() {
        initializationMutex.withLock {
            database.withTransaction {
                defaultPrompts().forEach { defaultPrompt ->
                    val task = defaultPrompt.task
                    if (dao.queryBinding(task.name) == null) {
                        val prompt = dao.queryPrompts(task.name).firstOrNull() ?: run {
                            dao.upsertPrompt(defaultPrompt.toEntity(revision = defaultPrompt.revision))
                            defaultPrompt.toEntity(revision = defaultPrompt.revision)
                        }
                        dao.upsertBinding(
                            AiTaskBindingEntity(
                                task = task.name,
                                promptId = prompt.id,
                                primaryModelId = "",
                                fallbackModelIdsJson = "[]",
                                articleCount = DEFAULT_ARTICLE_COUNT,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            }
        }
    }

    fun observeConnections(): Flow<List<AiConnection>> =
        dao.observeConnections().map { rows -> rows.map { it.toDomain() } }

    fun observePrompts(task: AiTask? = null): Flow<List<AiPrompt>> =
        dao.observePrompts(task?.name).map { rows -> rows.map { it.toDomain() } }

    fun observeModels(connectionId: String): Flow<List<AiModelProfile>> =
        dao.observeModels(connectionId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllModels(): Flow<List<AiModelProfile>> =
        dao.observeAllModels().map { rows -> rows.map { it.toDomain() } }

    suspend fun listConnections(): List<AiConnection> = dao.queryConnections().map { it.toDomain() }

    suspend fun listModels(): List<AiModelProfile> = dao.queryModels().map { it.toDomain() }

    suspend fun listPrompts(task: AiTask? = null): List<AiPrompt> =
        dao.queryPrompts(task?.name).map { it.toDomain() }

    suspend fun getPrompt(id: String): AiPrompt? = dao.queryPrompt(id)?.toDomain()

    suspend fun getBinding(task: AiTask): AiTaskBinding {
        ensureInitialized()
        return dao.queryBinding(task.name)?.toDomain()
            ?: error("AI task binding is not configured: ${task.name}")
    }

    fun observeBinding(task: AiTask): Flow<AiTaskBinding?> =
        dao.observeBinding(task.name).map { it?.toDomain() }

    suspend fun saveBinding(binding: AiTaskBinding) {
        database.withTransaction {
            val prompt = dao.queryPrompt(binding.promptId)
                ?: throw IllegalArgumentException("AI prompt does not exist")
            require(AiTask.valueOf(prompt.task) == binding.task) {
                "AI prompt is not valid for ${binding.task.name}"
            }
            val availableModelIds = dao.queryModels().mapTo(mutableSetOf<String>()) { it.id }
            require(binding.primaryModelId.isBlank() || binding.primaryModelId in availableModelIds) {
                "Primary AI model does not exist"
            }
            val sanitizedFallbacks = binding.fallbackModelIds
                .asSequence()
                .filter(String::isNotBlank)
                .filter { it != binding.primaryModelId }
                .filter { it in availableModelIds }
                .distinct()
                .toList()
            dao.upsertBinding(binding.copy(fallbackModelIds = sanitizedFallbacks).toEntity())
        }
    }

    suspend fun saveConnection(connection: AiConnection, secret: String? = null) {
        require(connection.name.isNotBlank()) { "AI connection name cannot be blank" }
        require(connection.baseUrl.isNotBlank()) { "AI base URL cannot be blank" }
        database.withTransaction {
            val existing = dao.queryConnection(connection.id)
            val now = System.currentTimeMillis()
            val secretRef = if (connection.authType == AiAuthType.NONE) {
                null
            } else {
                connection.secretRef ?: existing?.secretRef ?: "ai-secret-${connection.id}"
            }
            if (secretRef == null) {
                secretStore.remove(existing?.secretRef)
            } else if (!secret.isNullOrBlank()) {
                secretStore.put(secretRef, secret)
            }
            dao.upsertConnection(
                connection.copy(secretRef = secretRef).toEntity(
                    revision = (existing?.revision ?: 0L) + 1L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    suspend fun deleteConnection(connectionId: String) {
        database.withTransaction {
            val connection = dao.queryConnection(connectionId)
            val modelIds = dao.queryModels(connectionId).mapTo(mutableSetOf<String>()) { it.id }
            detachModelsFromBindings(modelIds)
            dao.deleteConnection(connectionId)
            secretStore.remove(connection?.secretRef)
        }
    }

    suspend fun saveModel(model: AiModelProfile) {
        require(model.modelId.isNotBlank()) { "AI model ID cannot be blank" }
        require(dao.queryConnection(model.connectionId) != null) {
            "AI connection does not exist"
        }
        require(
            dao.queryModels(model.connectionId).none {
                it.id != model.id && it.modelId == model.modelId.trim()
            }
        ) { "This model is already saved for the connection" }
        database.withTransaction {
            val existing = dao.queryModel(model.id)
            val now = System.currentTimeMillis()
            dao.upsertModel(
                model.toEntity(
                    revision = (existing?.revision ?: 0L) + 1L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    suspend fun deleteModel(modelId: String) {
        database.withTransaction {
            detachModelsFromBindings(setOf(modelId))
            dao.deleteModel(modelId)
        }
    }

    suspend fun savePrompt(prompt: AiPrompt) {
        require(prompt.name.isNotBlank()) { "AI prompt name cannot be blank" }
        require(prompt.systemTemplate.isNotBlank()) { "AI system prompt cannot be blank" }
        require(prompt.userTemplate.isNotBlank()) { "AI user prompt cannot be blank" }
        database.withTransaction {
            val existing = dao.queryPrompt(prompt.id)
            if (existing != null && existing.task != prompt.task.name) {
                dao.queryBindings()
                    .filter { it.promptId == prompt.id }
                    .forEach { binding ->
                        val replacement = dao.queryPrompts(binding.task)
                            .firstOrNull { it.id != prompt.id }
                        dao.upsertBinding(
                            binding.copy(
                                promptId = replacement?.id.orEmpty(),
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
            }
            val revision = (existing?.revision ?: 0L) + 1L
            dao.upsertPrompt(
                prompt.copy(
                    builtIn = existing?.builtIn ?: false,
                    updatedAt = System.currentTimeMillis(),
                ).toEntity(revision = revision)
            )
        }
    }

    suspend fun deletePrompt(promptId: String) {
        database.withTransaction {
            val prompt = dao.queryPrompt(promptId) ?: return@withTransaction
            val replacement = dao.queryPrompts(prompt.task).firstOrNull { it.id != promptId }
            dao.queryBindings()
                .filter { it.promptId == promptId }
                .forEach { binding ->
                    dao.upsertBinding(
                        binding.copy(
                            promptId = replacement?.id.orEmpty(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            dao.deletePrompt(promptId)
        }
    }

    suspend fun resolveModel(modelId: String): AiResolvedModel? {
        val model = dao.queryModel(modelId)?.toDomain() ?: return null
        val connection = dao.queryConnection(model.connectionId)?.toDomain() ?: return null
        if (!connection.enabled || !model.enabled) return null
        return AiResolvedModel(
            model = model,
            connection = connection,
            apiKey = secretStore.get(connection.secretRef),
        )
    }

    /** Cache signature for routing configuration. It deliberately excludes decrypted API keys. */
    suspend fun routeSignature(modelIds: List<String>): String {
        val models = dao.queryModels().associateBy { it.id }
        val connections = dao.queryConnections().associateBy { it.id }
        return modelIds.distinct().joinToString("\u0000") { modelId ->
            val model = models[modelId] ?: return@joinToString "$modelId:missing-model"
            val connection = connections[model.connectionId]
                ?: return@joinToString "$modelId:missing-connection"
            listOf(
                model.id,
                model.revision,
                model.modelId,
                model.maxOutputTokens,
                model.temperature,
                model.enabled,
                connection.id,
                connection.revision,
                connection.provider,
                connection.baseUrl,
                connection.authType,
                connection.enabled,
            ).joinToString(":")
        }
    }

    private suspend fun detachModelsFromBindings(modelIds: Set<String>) {
        if (modelIds.isEmpty()) return
        dao.queryBindings().forEach { row ->
            val binding = row.toDomain()
            if (binding.primaryModelId !in modelIds &&
                binding.fallbackModelIds.none { it in modelIds }
            ) {
                return@forEach
            }
            val remainingFallbacks = binding.fallbackModelIds.filterNot { it in modelIds }
            val primaryModelId = if (binding.primaryModelId in modelIds) {
                remainingFallbacks.firstOrNull().orEmpty()
            } else {
                binding.primaryModelId
            }
            dao.upsertBinding(
                binding.copy(
                    primaryModelId = primaryModelId,
                    fallbackModelIds = remainingFallbacks.filterNot { it == primaryModelId },
                ).toEntity()
            )
        }
    }

    private fun defaultPrompts(): List<AiPrompt> = listOf(
        AiPrompt(
            id = titlePromptId,
            name = context.getString(R.string.ai_title_summary),
            task = AiTask.TITLE_SUMMARY,
            systemTemplate = context.getString(R.string.ai_default_prompt),
            userTemplate =
                "The following is the complete set of supplied article titles. " +
                    "Identify the main issues they collectively focus on and summarize the set as a whole. " +
                    "Use only information present in these titles and their metadata; do not infer facts from " +
                    "the underlying articles.\n\n{items}",
            itemTemplate = context.getString(R.string.ai_default_title_input_template),
            outputMode = AiOutputMode.MARKDOWN,
            builtIn = true,
        ),
        AiPrompt(
            id = articlePromptId,
            name = context.getString(R.string.ai_article_summary_settings),
            task = AiTask.ARTICLE_SUMMARY,
            systemTemplate = context.getString(R.string.ai_default_article_prompt),
            userTemplate = context.getString(R.string.ai_default_article_input_template),
            outputMode = AiOutputMode.MARKDOWN,
            builtIn = true,
        ),
    )

    private val titlePromptId = "builtin-title-summary-v1"
    private val articlePromptId = "builtin-article-summary-v1"
    private val json = Json { ignoreUnknownKeys = true }

    private fun AiConnectionEntity.toDomain() = AiConnection(
        id = id,
        name = name,
        provider = AiProvider.valueOf(provider),
        baseUrl = baseUrl,
        authType = AiAuthType.valueOf(authType),
        secretRef = secretRef,
        enabled = enabled,
        revision = revision,
    )

    private fun AiConnection.toEntity(
        revision: Long,
        createdAt: Long,
        updatedAt: Long,
    ) = AiConnectionEntity(
        id = id,
        name = name,
        provider = provider.name,
        baseUrl = baseUrl.trim().trimEnd('/'),
        authType = authType.name,
        secretRef = secretRef,
        enabled = enabled,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun AiModelProfileEntity.toDomain() = AiModelProfile(
        id = id,
        connectionId = connectionId,
        modelId = modelId,
        displayName = displayName,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature?.takeIf { it.isFinite() }?.coerceIn(0.0, 2.0),
        enabled = enabled,
        revision = revision,
    )

    private fun AiModelProfile.toEntity(
        revision: Long,
        createdAt: Long,
        updatedAt: Long,
    ) = AiModelProfileEntity(
        id = id,
        connectionId = connectionId,
        modelId = modelId.trim(),
        displayName = displayName.trim().ifBlank { modelId.trim() },
        maxOutputTokens = maxOutputTokens.coerceAtLeast(1),
        temperature = temperature?.takeIf { it.isFinite() }?.coerceIn(0.0, 2.0),
        enabled = enabled,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun AiPromptEntity.toDomain() = AiPrompt(
        id = id,
        name = name,
        task = AiTask.valueOf(task),
        systemTemplate = systemTemplate,
        userTemplate = userTemplate,
        itemTemplate = itemTemplate,
        outputMode = AiOutputMode.valueOf(outputMode),
        builtIn = builtIn,
        revision = revision,
        updatedAt = updatedAt,
    )

    private fun AiPrompt.toEntity(revision: Long) = AiPromptEntity(
        id = id,
        name = name,
        task = task.name,
        systemTemplate = systemTemplate,
        userTemplate = userTemplate,
        itemTemplate = itemTemplate,
        outputMode = outputMode.name,
        builtIn = builtIn,
        revision = revision,
        updatedAt = updatedAt,
    )

    private fun AiTaskBindingEntity.toDomain() = AiTaskBinding(
        task = AiTask.valueOf(task),
        promptId = promptId,
        primaryModelId = primaryModelId,
        fallbackModelIds = runCatching {
            json.decodeFromString<List<String>>(fallbackModelIdsJson)
        }.getOrDefault(emptyList()),
        articleCount = articleCount.coerceAtLeast(1),
    )

    private fun AiTaskBinding.toEntity() = AiTaskBindingEntity(
        task = task.name,
        promptId = promptId,
        primaryModelId = primaryModelId,
        fallbackModelIdsJson = json.encodeToString(fallbackModelIds),
        articleCount = articleCount.coerceAtLeast(1),
        updatedAt = System.currentTimeMillis(),
    )
}
