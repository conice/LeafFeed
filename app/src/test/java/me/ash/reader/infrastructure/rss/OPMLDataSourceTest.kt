package me.ash.reader.infrastructure.rss

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock

internal const val OPML_TEMPLATE: String = """
<opml version="1.0">
    <head>
        <title>Import OPML Unit Test👿</title>
    </head>
     <body>
        {{var}}
    </body>
</opml>
"""

@RunWith(MockitoJUnitRunner::class)
class OPMLDataSourceTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockIODispatcher: CoroutineDispatcher

    private lateinit var opmlDataSource: OPMLDataSource

    private val defaultGroup = Group(id = "1", name = "Default", accountId = 1)

    private lateinit var mockObject: OPMLDataSourceTest

    @Before
    fun setUp() {
        mockContext = mock<Context> { }
        mockIODispatcher = mock<CoroutineDispatcher> {}
        opmlDataSource = OPMLDataSource(mockContext, mockIODispatcher)
    }

    private fun fill(value: String): String = OPML_TEMPLATE.replace("{{var}}", value)

    private fun parse(opml: String): List<GroupWithFeed> = runBlocking {
        opmlDataSource.parseFileInputStream(
            inputStream = opml.byteInputStream(Charsets.UTF_8),
            defaultGroup = defaultGroup,
            targetAccountId = 1
        )
    }

    @Test
    fun testUtf8BomIsIgnored() {
        val opml = fill("""
            <outline type="rss" text="Feed" xmlUrl="https://example.com/feed.xml"/>
        """)
        val result = runBlocking {
            opmlDataSource.parseFileInputStream(
                inputStream = ("\uFEFF" + opml).byteInputStream(Charsets.UTF_8),
                defaultGroup = defaultGroup,
                targetAccountId = 1,
            )
        }

        Assert.assertEquals(1, result.size)
        Assert.assertEquals("https://example.com/feed.xml", result[0].feeds.single().url)
    }

    @Test
    fun testEmptyTitle() {
        val opml = fill("""
            <outline text="Blogs" title="Blogs">
                <outline type="rss" xmlUrl="https://ash7.io/index.xml" htmlUrl="https://ash7.io"/>
            </outline>
        """)
        val result = parse(opml)
        Assert.assertEquals(2, result.size)
        Assert.assertEquals("Default", result[0].group.name)
        Assert.assertEquals(0, result[0].feeds.size)
        Assert.assertEquals("Blogs", result[1].group.name)
        Assert.assertEquals(1, result[1].feeds.size)
        Assert.assertEquals("ash7.io", result[1].feeds[0].name)
        Assert.assertEquals("https://ash7.io/index.xml", result[1].feeds[0].url)
    }

    @Test
    fun testTopLevelFeedUsesDefaultGroup() {
        val opml = fill("""
            <outline type="rss" text="Loose Feed" xmlUrl="https://loose.example/feed.xml"/>
        """)
        val result = parse(opml)
        Assert.assertEquals(1, result.size)
        Assert.assertEquals(defaultGroup.id, result[0].feeds[0].groupId)
        Assert.assertEquals("Loose Feed", result[0].feeds[0].name)
    }

    @Test
    fun testExtendedAttributesAndFallbackName() {
        val opml = fill("""
            <outline text="Blogs" title="Blogs">
                <outline type="rss" xmlUrl="https://ash7.io/index.xml"
                    isNotification="true" isFullContent="true" isBrowser="true"/>
            </outline>
        """)
        val feed = parse(opml)[1].feeds[0]
        Assert.assertEquals("ash7.io", feed.name)
        Assert.assertTrue(feed.isNotification)
        Assert.assertTrue(feed.isFullContent)
        Assert.assertTrue(feed.isBrowser)
    }

    @Test
    fun testDuplicateXmlUrlInSameGroupKeepsFirstFeed() {
        val result = parse(fill("""
            <outline text="Blogs" title="Blogs">
                <outline type="rss" text="First" xmlUrl="https://example.com/feed.xml"/>
                <outline type="rss" text="Duplicate" xmlUrl="https://example.com/feed.xml"/>
            </outline>
        """))

        Assert.assertEquals(1, result[1].feeds.size)
        Assert.assertEquals("First", result[1].feeds[0].name)
    }

    @Test
    fun testDuplicateXmlUrlAcrossGroupsKeepsFirstFeed() {
        val result = parse(fill("""
            <outline text="First group" title="First group">
                <outline type="rss" text="First" xmlUrl="https://example.com/feed.xml"/>
            </outline>
            <outline text="Second group" title="Second group">
                <outline type="rss" text="Duplicate" xmlUrl="https://example.com/feed.xml"/>
            </outline>
        """))

        Assert.assertEquals(1, result[1].feeds.size)
        Assert.assertEquals(0, result[2].feeds.size)
        Assert.assertEquals("First", result[1].feeds[0].name)
    }

    @Test
    fun testNestedGroupsImportFeeds() {
        val result = parse(fill("""
            <outline text="Technology">
                <outline text="Android">
                    <outline type="rss" text="Android Developers"
                        xmlUrl="https://android-developers.googleblog.com/feeds/posts/default"/>
                </outline>
            </outline>
        """))

        Assert.assertEquals(3, result.size)
        Assert.assertEquals("Technology", result[1].group.name)
        Assert.assertEquals(0, result[1].feeds.size)
        Assert.assertEquals("Android", result[2].group.name)
        Assert.assertEquals(1, result[2].feeds.size)
        Assert.assertEquals(
            "https://android-developers.googleblog.com/feeds/posts/default",
            result[2].feeds[0].url,
        )
    }

    @Test
    fun testTopLevelFeedWithChildrenUsesDefaultGroup() {
        val result = parse(fill("""
            <outline type="rss" text="Feed" xmlUrl="https://example.com/feed.xml">
                <outline text="Metadata"/>
            </outline>
        """))

        Assert.assertEquals(1, result.size)
        Assert.assertEquals(1, result[0].feeds.size)
        Assert.assertEquals(defaultGroup.id, result[0].feeds[0].groupId)
    }
}
