package me.ash.reader.domain.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import me.ash.reader.domain.model.ai.AiArticleInput
import me.ash.reader.domain.model.ai.AiPromptContext
import me.ash.reader.domain.model.ai.AiProviderException
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.domain.model.ai.AiTitleItem
import me.ash.reader.infrastructure.ai.AiConfigurationRepository
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.ui.ext.dataStore
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
                me.ash.reader.domain.model.ai.AiError(
                    kind = me.ash.reader.domain.model.ai.AiFailureKind.NOT_CONFIGURED,
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
                me.ash.reader.domain.model.ai.AiError(
                    kind = me.ash.reader.domain.model.ai.AiFailureKind.NOT_CONFIGURED,
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
