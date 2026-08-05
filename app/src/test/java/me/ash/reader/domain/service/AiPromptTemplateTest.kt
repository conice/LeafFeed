package me.ash.reader.domain.service

import me.ash.reader.infrastructure.preference.DEFAULT_AI_TITLE_INPUT_TEMPLATE
import org.junit.Assert.assertEquals
import org.junit.Test

class AiPromptTemplateTest {
    @Test
    fun defaultTitleTemplatePlacesTitleBeforeIndex() {
        val result =
            renderAiInputTemplate(
                template = DEFAULT_AI_TITLE_INPUT_TEMPLATE,
                index = 3,
                title = "Article title",
                body = "",
            )

        assertEquals("Article title · 3", result)
    }

    @Test
    fun replacesSupportedInputFields() {
        val result =
            renderAiInputTemplate(
                template = "{index}. {title}\n\n{content}",
                index = 3,
                title = "Article title",
                body = "Article body",
            )

        assertEquals("3. Article title\n\nArticle body", result)
    }

    @Test
    fun leavesUnknownFieldsUntouched() {
        val result =
            renderAiInputTemplate(
                template = "{title}\n{unknown}",
                index = 1,
                title = "Article title",
                body = "Article body",
            )

        assertEquals("Article title\n{unknown}", result)
    }

    @Test
    fun doesNotReplaceFieldsInsideSourceText() {
        val result =
            renderAiInputTemplate(
                template = "{title}\n{content}",
                index = 1,
                title = "A title containing {content}",
                body = "Body containing {title}",
            )

        assertEquals("A title containing {content}\nBody containing {title}", result)
    }
}
