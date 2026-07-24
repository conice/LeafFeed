package me.ash.reader.infrastructure.rss

import android.content.Context
import be.ceau.opml.OpmlParser
import be.ceau.opml.entity.Outline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.spacerDollar
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.*
import javax.inject.Inject

class OPMLDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Throws(Exception::class)
    suspend fun parseFileInputStream(
        inputStream: InputStream,
        defaultGroup: Group,
        targetAccountId: Int,
    ): List<GroupWithFeed> {
        // Some OPML exporters prepend a UTF-8 BOM. The parser's InputStream
        // overload decodes it as a regular character, which makes XmlPullParser
        // fail before it reaches the document element.
        val bufferedInputStream = inputStream as? BufferedInputStream
            ?: BufferedInputStream(inputStream)
        bufferedInputStream.mark(UTF8_BOM.size)
        val prefix = ByteArray(UTF8_BOM.size)
        val prefixLength = bufferedInputStream.read(prefix)
        if (prefixLength != UTF8_BOM.size || !prefix.contentEquals(UTF8_BOM)) {
            bufferedInputStream.reset()
        }
        val opml = OpmlParser().parse(bufferedInputStream)
        val groupWithFeedList = mutableListOf<GroupWithFeed>().also {
            it.addGroup(defaultGroup)
        }
        val seenFeedUrls = mutableSetOf<String>()

        fun visit(outline: Outline?, parentGroupId: String) {
            if (outline == null || outline.attributes == null) return
            val feedUrl = outline.extractUrl()
            if (feedUrl != null) {
                if (!seenFeedUrls.add(feedUrl)) return
                groupWithFeedList.addFeed(
                    Feed(
                        id = targetAccountId.spacerDollar(UUID.randomUUID().toString()),
                        name = outline.extractName(),
                        url = feedUrl,
                        groupId = parentGroupId,
                        accountId = targetAccountId,
                        isNotification = outline.extractPresetNotification(),
                        isFullContent = outline.extractPresetFullContent(),
                        isBrowser = outline.extractPresetBrowser(),
                    )
                )
                return
            }

            // A folder may contain more folders, so walk the complete outline tree.
            val groupId = if (outline.isDefaultGroup()) {
                defaultGroup.id
            } else {
                targetAccountId.spacerDollar(UUID.randomUUID().toString()).also {
                    groupWithFeedList.addGroup(
                        Group(id = it, name = outline.extractName(), accountId = targetAccountId)
                    )
                }
            }
            outline.subElements.forEach { visit(it, groupId) }
        }

        opml.body.outlines.forEach { visit(it, defaultGroup.id) }
        return groupWithFeedList
    }

    private fun MutableList<GroupWithFeed>.addGroup(group: Group) {
        add(GroupWithFeed(group = group, feeds = mutableListOf()))
    }

    private fun MutableList<GroupWithFeed>.addFeed(feed: Feed) {
        first { it.group.id == feed.groupId }.feeds.add(feed)
    }

    private fun Outline?.extractName(): String {
        if (this == null) return ""
        return attributes.getOrDefault("title", null)
            ?: text
            ?: attributes.getOrDefault("xmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("htmlUrl", null).extractDomain()
            ?: attributes.getOrDefault("url", null).extractDomain()
            ?: ""
    }

    private fun Outline?.extractUrl(): String? {
        if (this == null) return null
        val url = attributes.getOrDefault("xmlUrl", null)
            ?: attributes.getOrDefault("url", null)
        return url?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Outline?.extractPresetNotification(): Boolean =
        this?.attributes?.getOrDefault("isNotification", null).toBoolean()

    private fun Outline?.extractPresetFullContent(): Boolean =
        this?.attributes?.getOrDefault("isFullContent", null).toBoolean()

    private fun Outline?.extractPresetBrowser(): Boolean =
        this?.attributes?.getOrDefault("isBrowser", null).toBoolean()

    private fun Outline?.isDefaultGroup(): Boolean =
        this?.attributes?.getOrDefault("isDefault", null).toBoolean()

    private companion object {
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
