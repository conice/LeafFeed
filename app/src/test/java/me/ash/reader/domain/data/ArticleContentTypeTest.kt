package me.ash.reader.domain.data

import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentTypeTest {
    @Test
    fun `article mode excludes audio enclosures`() {
        assertTrue(ArticleContentType.ARTICLE.includes(null))
        assertFalse(ArticleContentType.ARTICLE.includes("https://example.com/episode.mp3"))
    }

    @Test
    fun `audio mode only includes audio enclosures`() {
        assertFalse(ArticleContentType.AUDIO.includes(null))
        assertTrue(ArticleContentType.AUDIO.includes("https://example.com/episode.mp3"))
    }

    @Test
    fun `empty selected type switches to the type that has content`() {
        assertEquals(
            ArticleContentType.AUDIO,
            resolveContentType(ArticleContentType.ARTICLE, false, true),
        )
        assertEquals(
            ArticleContentType.ARTICLE,
            resolveContentType(ArticleContentType.AUDIO, false, true),
        )
    }

    @Test
    fun `mixed and empty feeds preserve the selected type`() {
        assertEquals(
            ArticleContentType.ARTICLE,
            resolveContentType(ArticleContentType.ARTICLE, true, true),
        )
        assertEquals(
            ArticleContentType.AUDIO,
            resolveContentType(ArticleContentType.AUDIO, false, false),
        )
    }

    @Test
    fun `content type preference keys isolate accounts and scope types`() {
        val group = Group(id = "shared-id", name = "Group", accountId = 7)
        val feed = Feed(
            id = "shared-id",
            name = "Feed",
            url = "https://example.com/feed.xml",
            groupId = group.id,
            accountId = 7,
        )

        assertEquals(
            "account:7:group:shared-id",
            contentTypeScopeKey(FilterState(group = group)),
        )
        assertEquals(
            "account:7:feed:shared-id",
            contentTypeScopeKey(FilterState(feed = feed)),
        )
        assertEquals(
            "account:8:group:shared-id",
            contentTypeScopeKey(FilterState(group = group.copy(accountId = 8))),
        )
        assertEquals(null, contentTypeScopeKey(FilterState()))
    }
}
