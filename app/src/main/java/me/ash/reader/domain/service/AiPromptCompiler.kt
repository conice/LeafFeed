package me.ash.reader.domain.service

import java.util.Locale
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiPromptContext
import me.ash.reader.domain.model.ai.AiOutputMode
import me.ash.reader.domain.model.ai.AiTask

data class CompiledAiPrompt(
    val systemInstruction: String,
    val userInput: String,
)

/** Provider-neutral prompt renderer. Source content is inserted only into the user input. */
object AiPromptCompiler {
    private val fieldPattern = Regex("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}")

    fun compile(
        prompt: AiPrompt,
        context: AiPromptContext,
        locale: Locale,
    ): CompiledAiPrompt {
        require(prompt.task == context.task) {
            "Prompt ${prompt.id} is not valid for ${context.task.name}"
        }
        val itemText = context.items.mapIndexed { index, item ->
            render(
                prompt.itemTemplate,
                mapOf(
                    "id" to item.id,
                    "index" to (index + 1).toString(),
                    "title" to item.title,
                    "content" to "",
                ),
            )
        }.joinToString("\n")
        val values = mapOf(
            "title" to context.title.orEmpty(),
            "content" to context.content.orEmpty(),
            "link" to context.link.orEmpty(),
            "index" to (context.index ?: 1).toString(),
            "items" to itemText,
        )
        val system = listOf(
            languageInstruction(locale),
            outputInstruction(prompt.outputMode),
            prompt.systemTemplate.trim(),
        ).filter(String::isNotBlank).joinToString("\n\n")
        return CompiledAiPrompt(
            // Article/title source fields always stay in the lower-priority user input.
            systemInstruction = system,
            userInput = render(prompt.userTemplate, values),
        )
    }

    private fun render(template: String, values: Map<String, String>): String =
        fieldPattern.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }

    private fun languageInstruction(locale: Locale): String =
        "Default output language: ${locale.getDisplayLanguage(Locale.ENGLISH)} (${locale.toLanguageTag()}). " +
            "Use this language only when the task prompt does not specify another output language."

    private fun outputInstruction(mode: AiOutputMode): String = when (mode) {
        AiOutputMode.TEXT -> "Return plain text without Markdown table syntax."
        AiOutputMode.MARKDOWN -> "Use Markdown when it improves readability."
    }
}
