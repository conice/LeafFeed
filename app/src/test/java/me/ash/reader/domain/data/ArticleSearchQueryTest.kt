package me.ash.reader.domain.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleSearchQueryTest {
    @Test
    fun `builds a safe prefix query for every search term`() {
        assertEquals(
            "\"android\"* AND \"reader\"*",
            " android   reader ".toArticleFtsQuery(),
        )
    }

    @Test
    fun `escapes quotes in search terms`() {
        assertEquals("\"say\"\"hello\"*", "say\"hello".toArticleFtsQuery())
    }
}
