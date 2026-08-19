package me.ash.reader.application.service

import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiError
import me.ash.reader.domain.model.ai.AiFailureKind
import me.ash.reader.domain.model.ai.AiGenerationOptions
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiPromptContext
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiProviderException
import me.ash.reader.domain.model.ai.AiRequest
import me.ash.reader.domain.model.ai.AiStreamEvent
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.infrastructure.ai.AiConfigurationRepository
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.infrastructure.ai.provider.AiProviderRegistry
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.infrastructure.preference.dataStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiExecutionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: AiConfigurationRepository,
    private val providers: AiProviderRegistry,
    private val cache: AiSummaryCache,
) {
    suspend fun execute(
        accountId: Int,
        task: AiTask,
        inputFingerprint: String,
        prompt: AiPrompt,
        promptContext: AiPromptContext,
        forceRefresh: Boolean,
        modelOverrideId: String? = null,
        onUpdate: (String) -> Unit,
    ): String {
        configuration.ensureInitialized()
        val featureSettings = context.dataStore.data.first().toFeatureSettings()
        val binding = configuration.getBinding(task)
        val locale = context.resources.configuration.locales[0]
        val compiled = AiPromptCompiler.compile(prompt, promptContext, locale)
        val candidateIds = (
            listOfNotNull(modelOverrideId ?: binding.primaryModelId) +
                if (modelOverrideId == null) binding.fallbackModelIds else emptyList()
            ).filter(String::isNotBlank).distinct()
        val routeSignature = configuration.routeSignature(candidateIds)
        val requestFingerprint = fingerprint(
            task = task,
            input = inputFingerprint,
            prompt = prompt,
            promptText = compiled,
            routeSignature = routeSignature,
            stream = featureSettings.aiStreamingEnabled,
            timeoutSeconds = featureSettings.aiTimeoutSeconds,
            locale = locale,
        )
        val result = cache.getOrPut(
            accountId = accountId,
            fingerprint = requestFingerprint,
            forceRefresh = forceRefresh,
        ) {
            executeCandidates(
                task = task,
                prompt = compiled,
                candidateIds = candidateIds,
                stream = featureSettings.aiStreamingEnabled,
                timeoutSeconds = featureSettings.aiTimeoutSeconds,
                onUpdate = onUpdate,
            )
        }
        if (result.fromCache) onUpdate(result.content)
        return result.content
    }

    private suspend fun executeCandidates(
        task: AiTask,
        prompt: CompiledAiPrompt,
        candidateIds: List<String>,
        stream: Boolean,
        timeoutSeconds: Int,
        onUpdate: (String) -> Unit,
    ): String {
        if (candidateIds.isEmpty()) throw AiProviderException(
            AiError(
                kind = AiFailureKind.NOT_CONFIGURED,
                message = "No AI model is configured for ${task.name}",
            )
        )
        val distinctCandidates = candidateIds.distinct()
        var lastError: AiProviderException? = null
        for ((index, modelId) in distinctCandidates.withIndex()) {
            val resolved = try {
                configuration.resolveModel(modelId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val normalized = normalize(error, provider = null)
                lastError = normalized
                if (index == distinctCandidates.lastIndex) throw normalized
                continue
            }
            if (resolved == null) {
                lastError = AiProviderException(
                    AiError(
                        kind = AiFailureKind.NOT_CONFIGURED,
                        message = "AI model configuration is missing",
                    )
                )
                continue
            }
            val keyRequired = resolved.connection.authType != AiAuthType.NONE
            if (keyRequired && resolved.apiKey.isNullOrBlank()) {
                lastError = AiProviderException(
                    AiError(
                        kind = AiFailureKind.AUTHENTICATION,
                        provider = resolved.connection.provider,
                        message = "API key is not configured for ${resolved.connection.name}",
                    )
                )
                continue
            }
            if (resolved.connection.baseUrl.isBlank() || resolved.model.modelId.isBlank()) {
                lastError = AiProviderException(
                    AiError(
                        kind = AiFailureKind.INVALID_CONFIGURATION,
                        provider = resolved.connection.provider,
                        message = "AI endpoint or model is not configured",
                    )
                )
                continue
            }
            val request = AiRequest(
                task = task,
                systemInstruction = prompt.systemInstruction,
                userInput = prompt.userInput,
                model = resolved.model,
                connection = resolved.connection,
                apiKey = resolved.apiKey,
                options = AiGenerationOptions(
                    stream = stream,
                    timeoutSeconds = timeoutSeconds,
                    maxOutputTokens = resolved.model.maxOutputTokens,
                    temperature = resolved.model.temperature,
                ),
            )
            val output = StringBuilder()
            var emitted = false
            var lastPublished = ""
            var lastUpdateNanos = 0L

            fun publish(content: String, force: Boolean = false) {
                val now = System.nanoTime()
                if (force || lastUpdateNanos == 0L || now - lastUpdateNanos >= UPDATE_INTERVAL_NANOS) {
                    lastUpdateNanos = now
                    lastPublished = content
                    onUpdate(content)
                }
            }

            try {
                val result = providers.adapter(resolved.connection.provider).execute(request) { event ->
                    when (event) {
                        is AiStreamEvent.TextDelta -> {
                            output.append(event.text)
                            emitted = true
                            publish(output.toString())
                        }
                        else -> Unit
                    }
                }
                if (result.isBlank()) throw AiProviderException(
                    AiError(
                        kind = AiFailureKind.EMPTY_RESPONSE,
                        provider = resolved.connection.provider,
                        message = "AI returned an empty response",
                    )
                )
                if (result != lastPublished) publish(result, force = true)
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val normalized = normalize(error, resolved.connection.provider)
                lastError = normalized
                val canFailOver = !emitted &&
                    index < distinctCandidates.lastIndex &&
                    normalized.error.canFailOver()
                if (!canFailOver) throw normalized
            }
        }
        throw (lastError ?: AiProviderException(
            AiError(
                kind = AiFailureKind.UNKNOWN,
                message = "AI request failed",
            )
        ))
    }

    private fun normalize(error: Throwable, provider: AiProvider?): AiProviderException {
        if (error is AiProviderException) return error
        val kind = when (error) {
            is InterruptedIOException -> AiFailureKind.TIMEOUT
            is IOException -> AiFailureKind.NETWORK
            is IllegalArgumentException -> AiFailureKind.INVALID_CONFIGURATION
            else -> AiFailureKind.UNKNOWN
        }
        return AiProviderException(
            AiError(
                kind = kind,
                provider = provider,
                retryable = kind == AiFailureKind.TIMEOUT || kind == AiFailureKind.NETWORK,
                message = error.message ?: "AI request failed",
                detail = error.javaClass.simpleName,
            )
        )
    }

    private fun AiError.canFailOver(): Boolean = retryable || kind in setOf(
        AiFailureKind.AUTHENTICATION,
        AiFailureKind.ENDPOINT_NOT_FOUND,
        AiFailureKind.MODEL_NOT_FOUND,
        AiFailureKind.RATE_LIMITED,
        AiFailureKind.QUOTA_EXCEEDED,
        AiFailureKind.SERVICE_UNAVAILABLE,
        AiFailureKind.NETWORK,
        AiFailureKind.TIMEOUT,
        AiFailureKind.EMPTY_RESPONSE,
        AiFailureKind.INVALID_RESPONSE,
    )

    private fun fingerprint(
        task: AiTask,
        input: String,
        prompt: AiPrompt,
        promptText: CompiledAiPrompt,
        routeSignature: String,
        stream: Boolean,
        timeoutSeconds: Int,
        locale: Locale,
    ): String {
        val canonical = listOf(
            CACHE_REQUEST_VERSION,
            task.name,
            input,
            prompt.id,
            prompt.revision.toString(),
            promptText.systemInstruction,
            promptText.userInput,
            routeSignature,
            stream.toString(),
            timeoutSeconds.toString(),
            locale.toLanguageTag(),
        ).joinToString("\u0000")
        return sha256(canonical)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHexString()

    private companion object {
        const val CACHE_REQUEST_VERSION = "ai-v3"
        const val UPDATE_INTERVAL_NANOS = 50_000_000L
    }
}
