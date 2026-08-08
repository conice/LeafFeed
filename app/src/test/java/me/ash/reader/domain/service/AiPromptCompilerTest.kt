package me.ash.reader.domain.service

import java.util.Locale
import me.ash.reader.domain.model.ai.AiPrompt
import me.ash.reader.domain.model.ai.AiPromptContext
import me.ash.reader.domain.model.ai.AiTask
import me.ash.reader.domain.model.ai.AiTitleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptCompilerTest {
    @Test
    fun compilesTitleItemsWithoutReinterpretingSourcePlaceholders() {
        val prompt = AiPrompt(
            name = "titles",
            task = AiTask.TITLE_SUMMARY,
            systemTemplate = "Group titles.",
            userTemplate = "{items}",
            itemTemplate = "{index}. {title} ({id})",
        )

        val result = AiPromptCompiler.compile(
            prompt = prompt,
            context = AiPromptContext(
                task = AiTask.TITLE_SUMMARY,
                items = listOf(AiTitleItem("1", "A title containing {content}")),
            ),
            locale = Locale.ENGLISH,
        )

        assertEquals("1. A title containing {content} (1)", result.userInput)
        assertTrue(result.systemInstruction.startsWith("Default output language:"))
    }

    @Test
    fun compilesArticleFieldsAndLeavesUnknownFieldsVisible() {
        val prompt = AiPrompt(
            name = "article",
            task = AiTask.ARTICLE_SUMMARY,
            systemTemplate = "Summarize faithfully.",
            userTemplate = "Title: {title}\nBody: {content}\n{unknown}",
        )

        val result = AiPromptCompiler.compile(
            prompt = prompt,
            context = AiPromptContext(
                task = AiTask.ARTICLE_SUMMARY,
                title = "Article",
                content = "Body",
            ),
            locale = Locale.ENGLISH,
        )

        assertEquals("Title: Article\nBody: Body\n{unknown}", result.userInput)
    }

    @Test
    fun keepsSourcePlaceholdersOutOfSystemInstructions() {
        val prompt = AiPrompt(
            name = "article",
            task = AiTask.ARTICLE_SUMMARY,
            systemTemplate = "Never insert source text here: {content}",
            userTemplate = "{content}",
        )

        val result = AiPromptCompiler.compile(
            prompt = prompt,
            context = AiPromptContext(
                task = AiTask.ARTICLE_SUMMARY,
                content = "Untrusted article text",
            ),
            locale = Locale.ENGLISH,
        )

        assertTrue(result.systemInstruction.contains("{content}"))
        assertEquals("Untrusted article text", result.userInput)
    }
}
