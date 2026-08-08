package me.ash.reader.infrastructure.ai

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
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

    suspend fun restoreBuiltInPrompts() {
        initializationMutex.withLock {
            database.withTransaction {
                defaultPrompts().forEach { defaultPrompt ->
                    val existing = dao.queryPrompt(defaultPrompt.id)
                    dao.upsertPrompt(
                        defaultPrompt.copy(updatedAt = System.currentTimeMillis()).toEntity(
                            revision = (existing?.revision ?: 0L) + 1L,
                        )
                    )

                    val task = defaultPrompt.task
                    val binding = dao.queryBinding(task.name)
                    val selectedPrompt = binding?.promptId?.let { dao.queryPrompt(it) }
                    if (binding == null) {
                        dao.upsertBinding(
                            AiTaskBindingEntity(
                                task = task.name,
                                promptId = defaultPrompt.id,
                                primaryModelId = "",
                                fallbackModelIdsJson = "[]",
                                articleCount = DEFAULT_ARTICLE_COUNT,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    } else if (selectedPrompt == null) {
                        dao.upsertBinding(
                            binding.copy(
                                promptId = defaultPrompt.id,
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

    suspend fun exportBackup(includeSecrets: Boolean): AiConfigurationBackup {
        ensureInitialized()
        val connections = dao.queryConnections()
        return AiConfigurationBackup(
            connections =
                connections.map { connection ->
                    AiConnectionBackup(
                        id = connection.id,
                        name = connection.name,
                        provider = connection.provider,
                        baseUrl = connection.baseUrl,
                        authType = connection.authType,
                        enabled = connection.enabled,
                        secret =
                            if (includeSecrets) secretStore.get(connection.secretRef) else null,
                    )
                },
            models =
                dao.queryModels().map { model ->
                    AiModelBackup(
                        id = model.id,
                        connectionId = model.connectionId,
                        modelId = model.modelId,
                        displayName = model.displayName,
                        maxOutputTokens = model.maxOutputTokens,
                        temperature = model.temperature,
                        enabled = model.enabled,
                    )
                },
            prompts =
                dao.queryPrompts(null).map { prompt ->
                    AiPromptBackup(
                        id = prompt.id,
                        name = prompt.name,
                        task = prompt.task,
                        systemTemplate = prompt.systemTemplate,
                        userTemplate = prompt.userTemplate,
                        itemTemplate = prompt.itemTemplate,
                        outputMode = prompt.outputMode,
                        builtIn = prompt.builtIn,
                    )
                },
            bindings =
                dao.queryBindings().map { binding ->
                    AiBindingBackup(
                        task = binding.task,
                        promptId = binding.promptId,
                        primaryModelId = binding.primaryModelId,
                        fallbackModelIds =
                            json.decodeFromString<List<String>>(binding.fallbackModelIdsJson),
                        articleCount = binding.articleCount,
                    )
                },
        )
    }

    suspend fun importBackup(backup: AiConfigurationBackup): AiConfigurationImportResult {
        backup.validate()
        database.withTransaction {
            val now = System.currentTimeMillis()
            backup.connections.forEach { imported ->
                val existing = dao.queryConnection(imported.id)
                val authType = AiAuthType.valueOf(imported.authType)
                val secretRef =
                    if (authType == AiAuthType.NONE) {
                        null
                    } else {
                        existing?.secretRef ?: "ai-secret-${imported.id}"
                    }
                if (secretRef == null) {
                    secretStore.remove(existing?.secretRef)
                } else if (!imported.secret.isNullOrBlank()) {
                    secretStore.put(secretRef, imported.secret)
                }
                dao.upsertConnection(
                    AiConnectionEntity(
                        id = imported.id,
                        name = imported.name.trim(),
                        provider = imported.provider,
                        baseUrl = imported.baseUrl.trim().trimEnd('/'),
                        authType = imported.authType,
                        secretRef = secretRef,
                        enabled = imported.enabled,
                        revision = (existing?.revision ?: 0L) + 1L,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
            }
            backup.models.forEach { imported ->
                val existing = dao.queryModel(imported.id)
                dao.upsertModel(
                    AiModelProfileEntity(
                        id = imported.id,
                        connectionId = imported.connectionId,
                        modelId = imported.modelId.trim(),
                        displayName =
                            imported.displayName.trim().ifBlank { imported.modelId.trim() },
                        maxOutputTokens = imported.maxOutputTokens,
                        temperature = imported.temperature,
                        enabled = imported.enabled,
                        revision = (existing?.revision ?: 0L) + 1L,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
            }
            backup.prompts.forEach { imported ->
                val existing = dao.queryPrompt(imported.id)
                dao.upsertPrompt(
                    AiPromptEntity(
                        id = imported.id,
                        name = imported.name.trim(),
                        task = imported.task,
                        systemTemplate = imported.systemTemplate,
                        userTemplate = imported.userTemplate,
                        itemTemplate = imported.itemTemplate,
                        outputMode = imported.outputMode,
                        builtIn = imported.builtIn,
                        revision = (existing?.revision ?: 0L) + 1L,
                        updatedAt = now,
                    )
                )
            }
            backup.bindings.forEach { imported ->
                dao.upsertBinding(
                    AiTaskBindingEntity(
                        task = imported.task,
                        promptId = imported.promptId,
                        primaryModelId = imported.primaryModelId,
                        fallbackModelIdsJson = json.encodeToString(imported.fallbackModelIds),
                        articleCount = imported.articleCount,
                        updatedAt = now,
                    )
                )
            }
        }
        return AiConfigurationImportResult(importedCount = backup.entryCount, skippedCount = 0)
    }

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
                "Group the following headlines by topic.\n\n" +
                    "Output format:\n\n" +
                    "### Group name\n" +
                    "- [item number] headline\n\n" +
                    "Use one section for each group. Keep the groups ordered by overall importance or " +
                    "number of headlines.\n\n" +
                    "<HEADLINES>\n{items}\n</HEADLINES>",
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

@Serializable
data class AiConfigurationBackup(
    val version: Int = AI_CONFIGURATION_BACKUP_VERSION,
    val connections: List<AiConnectionBackup> = emptyList(),
    val models: List<AiModelBackup> = emptyList(),
    val prompts: List<AiPromptBackup> = emptyList(),
    val bindings: List<AiBindingBackup> = emptyList(),
) {
    val entryCount: Int
        get() = connections.size + models.size + prompts.size + bindings.size
}

@Serializable
data class AiConnectionBackup(
    val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val authType: String,
    val enabled: Boolean = true,
    val secret: String? = null,
)

@Serializable
data class AiModelBackup(
    val id: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String,
    val maxOutputTokens: Int,
    val temperature: Double? = null,
    val enabled: Boolean = true,
)

@Serializable
data class AiPromptBackup(
    val id: String,
    val name: String,
    val task: String,
    val systemTemplate: String,
    val userTemplate: String,
    val itemTemplate: String,
    val outputMode: String,
    val builtIn: Boolean = false,
)

@Serializable
data class AiBindingBackup(
    val task: String,
    val promptId: String,
    val primaryModelId: String = "",
    val fallbackModelIds: List<String> = emptyList(),
    val articleCount: Int = DEFAULT_ARTICLE_COUNT,
)

data class AiConfigurationImportResult(
    val importedCount: Int,
    val skippedCount: Int,
)

private const val AI_CONFIGURATION_BACKUP_VERSION = 1
private const val MAX_AI_BACKUP_ENTRIES = 10_000

private fun AiConfigurationBackup.validate() {
    require(version == AI_CONFIGURATION_BACKUP_VERSION) {
        "Unsupported AI configuration backup version"
    }
    require(
        connections.size <= MAX_AI_BACKUP_ENTRIES &&
            models.size <= MAX_AI_BACKUP_ENTRIES &&
            prompts.size <= MAX_AI_BACKUP_ENTRIES &&
            bindings.size <= MAX_AI_BACKUP_ENTRIES &&
            entryCount <= MAX_AI_BACKUP_ENTRIES
    ) { "Too many AI configuration entries" }
    require(connections.map { it.id }.distinct().size == connections.size) {
        "AI configuration backup contains duplicate connections"
    }
    require(models.map { it.id }.distinct().size == models.size) {
        "AI configuration backup contains duplicate models"
    }
    require(prompts.map { it.id }.distinct().size == prompts.size) {
        "AI configuration backup contains duplicate prompts"
    }
    require(bindings.map { it.task }.distinct().size == bindings.size) {
        "AI configuration backup contains duplicate bindings"
    }
    val connectionIds = connections.mapTo(mutableSetOf()) { it.id }
    val modelIds = models.mapTo(mutableSetOf()) { it.id }
    val promptsById = prompts.associateBy { it.id }
    require(
        connections.all {
            it.id.isNotBlank() &&
                it.name.isNotBlank() &&
                it.baseUrl.isNotBlank() &&
                runCatching { AiProvider.valueOf(it.provider) }.isSuccess &&
                runCatching { AiAuthType.valueOf(it.authType) }.isSuccess
        }
    ) { "AI configuration backup contains an invalid connection" }
    require(
        models.all {
            it.id.isNotBlank() &&
                it.connectionId in connectionIds &&
                it.modelId.isNotBlank() &&
                it.maxOutputTokens > 0 &&
                (it.temperature == null || it.temperature.isFinite() && it.temperature in 0.0..2.0)
        }
    ) { "AI configuration backup contains an invalid model" }
    require(
        prompts.all {
            it.id.isNotBlank() &&
                it.name.isNotBlank() &&
                it.systemTemplate.isNotBlank() &&
                it.userTemplate.isNotBlank() &&
                runCatching { AiTask.valueOf(it.task) }.isSuccess &&
                runCatching { AiOutputMode.valueOf(it.outputMode) }.isSuccess
        }
    ) { "AI configuration backup contains an invalid prompt" }
    require(
        bindings.all { binding ->
            val task = runCatching { AiTask.valueOf(binding.task) }.getOrNull()
            val prompt = promptsById[binding.promptId]
            task != null &&
                prompt?.task == binding.task &&
                (binding.primaryModelId.isBlank() || binding.primaryModelId in modelIds) &&
                binding.fallbackModelIds.all { it in modelIds } &&
                binding.fallbackModelIds.distinct().size == binding.fallbackModelIds.size &&
                binding.primaryModelId !in binding.fallbackModelIds &&
                binding.articleCount > 0
        }
    ) { "AI configuration backup contains an invalid task binding" }
}
