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
                template = "{序号}. {标题}\n\n{正文}",
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
                template = "{标题}\n{unknown}",
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
                template = "{标题}\n{正文}",
                index = 1,
                title = "A title containing {正文}",
                body = "Body containing {标题}",
            )

        assertEquals("A title containing {正文}\nBody containing {标题}", result)
    }
}
