package com.conice.morss.application.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import com.conice.morss.domain.model.ai.AiArticleInput
import com.conice.morss.domain.model.ai.AiPromptContext
import com.conice.morss.domain.model.ai.AiProviderException
import com.conice.morss.domain.model.ai.AiTask
import com.conice.morss.domain.model.ai.AiTitleItem
import com.conice.morss.infrastructure.ai.AiConfigurationRepository
import com.conice.morss.infrastructure.preference.toFeatureSettings
import com.conice.morss.infrastructure.preference.dataStore
import javax.inject.Inject
import javax.inject.Singleton

/** Business facade for summary tasks. Provider wire formats live outside this class. */
@Singleton
class AiSummaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuration: AiConfigurationRepository,
    private val coordinator: AiExecutionCoordinator,
) {
    suspend fun articleCount(): Int =
        configuration.getBinding(AiTask.TITLE_SUMMARY).articleCount

    suspend fun summarizeTitles(
        accountId: Int,
        titles: List<Pair<String, String>>,
        forceRefresh: Boolean = false,
        promptId: String? = null,
        modelId: String? = null,
        onUpdate: (String) -> Unit = {},
    ): String {
        require(titles.isNotEmpty()) { "No articles to summarize" }
        configuration.ensureInitialized()
        val binding = configuration.getBinding(AiTask.TITLE_SUMMARY)
        val prompt = configuration.getPrompt(promptId ?: binding.promptId)
            ?: throw AiProviderException(
                com.conice.morss.domain.model.ai.AiError(
                    kind = com.conice.morss.domain.model.ai.AiFailureKind.NOT_CONFIGURED,
                    message = "Title summary prompt is not configured",
                )
            )
        val identity = titles.joinToString("\u0000") { (id, title) -> "$id\u0001$title" }
        return coordinator.execute(
            accountId = accountId,
            task = AiTask.TITLE_SUMMARY,
            inputFingerprint = identity,
            prompt = prompt,
            promptContext = AiPromptContext(
                task = AiTask.TITLE_SUMMARY,
                items = titles.map { (id, title) -> AiTitleItem(id, title) },
            ),
            forceRefresh = forceRefresh,
            modelOverrideId = modelId,
            onUpdate = onUpdate,
        )
    }

    suspend fun summarizeArticle(
        accountId: Int,
        articleId: String,
        title: String,
        content: String,
        link: String? = null,
        forceRefresh: Boolean = false,
        promptId: String? = null,
        modelId: String? = null,
        onUpdate: (String) -> Unit = {},
    ): String {
        configuration.ensureInitialized()
        val featureSettings = context.dataStore.data.first().toFeatureSettings()
        val selectedContent = when (featureSettings.aiContentScope) {
            0 -> ""
            1 -> content.take(2_000)
            else -> content
        }
        val selectedLink = link?.takeIf {
            featureSettings.aiIncludeArticleLink && it.isNotBlank()
        }
        val binding = configuration.getBinding(AiTask.ARTICLE_SUMMARY)
        val prompt = configuration.getPrompt(promptId ?: binding.promptId)
            ?: throw AiProviderException(
                com.conice.morss.domain.model.ai.AiError(
                    kind = com.conice.morss.domain.model.ai.AiFailureKind.NOT_CONFIGURED,
                    message = "Article summary prompt is not configured",
                )
            )
        val input = AiArticleInput(
            articleId = articleId,
            title = title,
            content = selectedContent +
                if (selectedLink != null && "{link}" !in prompt.userTemplate) {
                    "\n\nLink: $selectedLink"
                } else {
                    ""
                },
            link = selectedLink,
        )
        return coordinator.execute(
            accountId = accountId,
            task = AiTask.ARTICLE_SUMMARY,
            inputFingerprint = listOf(
                input.articleId,
                input.title,
                input.content,
                input.link.orEmpty(),
            ).joinToString("\u0000"),
            prompt = prompt,
            promptContext = AiPromptContext(
                task = AiTask.ARTICLE_SUMMARY,
                title = input.title,
                content = input.content,
                link = input.link,
            ),
            forceRefresh = forceRefresh,
            modelOverrideId = modelId,
            onUpdate = onUpdate,
        )
    }
}
