package me.ash.reader.infrastructure.rss

import android.content.Context
import android.util.Log
import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.MediaModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndImageImpl
import org.jdom2.Element
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import java.io.ByteArrayInputStream
import java.net.URI
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.html.Readability
import me.ash.reader.ui.ext.decodeHTML
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.isFuture
import me.ash.reader.ui.ext.spacerDollar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import okhttp3.internal.commonIsSuccessful
import okio.IOException
import org.jsoup.Jsoup

val enclosureRegex = """<enclosure\s+url="([^"]+)"\s+type=".*"\s*/>""".toRegex()
val imgRegex = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)

internal fun parsePodcastDurationSeconds(value: String): Long? {
    val parts = value.trim().split(":").map { it.toLongOrNull() }
    if (parts.any { it == null }) return null
    val numbers = parts.filterNotNull()
    return when (numbers.size) {
        3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        2 -> numbers[0] * 60 + numbers[1]
        1 -> numbers[0]
        else -> null
    }
}

private val podcastAudioExtensions = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "flac")

internal fun isPodcastAudio(url: String?, mimeType: String?, medium: String? = null): Boolean {
    val mediaUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val path = runCatching { URI(mediaUrl).path }
        .getOrNull()
        ?: mediaUrl.substringBefore('?').substringBefore('#')
    val extension = path
        .substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    return medium.equals("audio", ignoreCase = true) ||
        normalizedMimeType?.startsWith("audio/") == true ||
        normalizedMimeType in setOf("application/ogg", "application/x-ogg") ||
        extension in podcastAudioExtensions
}

/** Removes common tracking parameters without changing the article identity. */
fun normalizeArticleUrl(value: String?): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return raw
    return runCatching {
        val uri = URI(raw)
        val query = uri.rawQuery
            ?.split('&')
            ?.filter { part ->
                val key = part.substringBefore('=').lowercase()
                key.isNotBlank() && !key.startsWith("utm_") &&
                    key !in setOf("fbclid", "gclid", "dclid", "mc_cid", "mc_eid")
            }
            ?.sorted()
            ?.joinToString("&")
        URI(uri.scheme, uri.rawUserInfo, uri.host, uri.port, uri.rawPath, query, null)
            .toASCIIString()
    }.getOrDefault(raw)
}

/** Some operations on RSS. */
class RssHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
) {

    data class SearchFeedResult(
        val feed: SyndFeed,
        val feedLink: String,
    )

    @Throws(Exception::class)
    suspend fun searchFeed(feedLink: String): SearchFeedResult {
        return withContext(ioDispatcher) {
            val directResponse = response(okHttpClient, feedLink)
            if (!directResponse.commonIsSuccessful) throw IOException(directResponse.message)
            val directBody = directResponse.body.bytes()
            val directHttpContentType = toHttpContentType(directResponse.header("Content-Type"))

            val parsedDirectFeed = runCatching { parseFeed(directBody, directHttpContentType) }.getOrNull()

            val resolvedFeedLink =
                if (parsedDirectFeed != null) feedLink
                else discoverFeedLink(feedLink, directBody)
                    ?: throw IOException("Unable to detect RSS feed URL")


            val feed = parsedDirectFeed ?: run {
                val discoveredResponse = response(okHttpClient, resolvedFeedLink)
                if (!discoveredResponse.commonIsSuccessful) {
                    throw IOException(discoveredResponse.message)
                }
                parseFeed(
                    discoveredResponse.body.bytes(),
                    toHttpContentType(discoveredResponse.header("Content-Type")),
                )
            }

            feed.also {
                it.icon = SyndImageImpl()
                it.icon.link = queryRssIconLink(resolvedFeedLink)
                it.icon.url = it.icon.link
            }

            SearchFeedResult(feed = feed, feedLink = resolvedFeedLink)
        }
    }

    private fun toHttpContentType(contentType: String?): String =
        contentType?.let {
            if (it.contains("charset=", ignoreCase = true)) it else "$it; charset=UTF-8"
        } ?: "text/xml; charset=UTF-8"

    private fun parseFeed(body: ByteArray, httpContentType: String): SyndFeed =
        ByteArrayInputStream(body).use { inputStream ->
            SyndFeedInput().build(XmlReader(inputStream, httpContentType))
        }

    private fun discoverFeedLink(pageUrl: String, body: ByteArray): String? {
        val document = Jsoup.parse(String(body, Charsets.UTF_8), pageUrl)
        val links = document.select("head link[rel~=(?i)alternate][href]")
        val preferred =
            links.firstOrNull {
                val type = it.attr("type").lowercase(Locale.ROOT)
                type == "application/rss+xml" ||
                    type == "application/atom+xml" ||
                    type == "application/rdf+xml"
            }
        val fallback = links.firstOrNull()
        return (preferred ?: fallback)?.absUrl("href")?.takeIf { it.isNotBlank() }
    }

    @Throws(Exception::class)
    suspend fun parseFullContent(link: String, title: String): String {
        return withContext(ioDispatcher) {
            val response = response(okHttpClient, link)
            if (response.commonIsSuccessful) {
                val responseBody = response.body
                val charset = responseBody.contentType()?.charset()
                val content =
                    responseBody.source().use {
                        if (charset != null) {
                            return@use it.readString(charset)
                        }

                        val peekContent = it.peek().readString(Charsets.UTF_8)

                        val charsetFromMeta =
                            runCatching {
                                    val element =
                                        Jsoup.parse(peekContent, link)
                                            .selectFirst("meta[http-equiv=content-type]")
                                    return@runCatching if (element == null) Charsets.UTF_8
                                    else {
                                        element
                                            .attr("content")
                                            .substringAfter("charset=")
                                            .removeSurrounding("\"")
                                            .lowercase()
                                            .let { Charset.forName(it) }
                                    }
                                }
                                .getOrDefault(Charsets.UTF_8)

                        if (charsetFromMeta == Charsets.UTF_8) {
                            peekContent
                        } else {
                            it.readString(charsetFromMeta)
                        }
                    }

                val articleContent = Readability.parseToElement(content, link)
                articleContent?.let {
                    val h1Element = articleContent.selectFirst("h1")
                    if (h1Element != null && h1Element.hasText() && h1Element.text() == title) {
                        h1Element.remove()
                    }
                    articleContent.toString()
                } ?: throw IOException("articleContent is null")
            } else throw IOException(response.message)
        }
    }

    suspend fun queryRssXml(
        feed: Feed,
        latestLink: String?,
        preDate: Date = Date(),
    ): List<Article> =
        try {
            val accountId = feed.accountId
            val response = response(okHttpClient, feed.url)
            val contentType = response.header("Content-Type")

            val httpContentType =
                contentType?.let {
                    if (it.contains("charset=", ignoreCase = true)) it
                    else "$it; charset=UTF-8"
                } ?: "text/xml; charset=UTF-8"

            response.body.byteStream().use { inputStream ->
                SyndFeedInput()
                    .apply { isPreserveWireFeed = true }
                    .build(XmlReader(inputStream, httpContentType))
                    .entries
                    .asSequence()
                    .takeWhile { latestLink == null || latestLink != it.link }
                    .map { buildArticleFromSyndEntry(feed, accountId, it, preDate) }
                    .toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RLog", "queryRssXml[${feed.name}]: ${e.message}")
            listOf()
        }

    fun buildArticleFromSyndEntry(
        feed: Feed,
        accountId: Int,
        syndEntry: SyndEntry,
        preDate: Date = Date(),
    ): Article {
        val desc = syndEntry.description?.value
        val content =
            syndEntry.contents
                .takeIf { it.isNotEmpty() }
                ?.let { it.joinToString("\n") { it.value } }
        //        Log.i(
        //            "RLog",
        //            "request rss:\n" +
        //                    "name: ${feed.name}\n" +
        //                    "feedUrl: ${feed.url}\n" +
        //                    "url: ${syndEntry.link}\n" +
        //                    "title: ${syndEntry.title}\n" +
        //                    "desc: ${desc}\n" +
        //                    "content: ${content}\n"
        //        )
        val enclosure = syndEntry.enclosures?.firstOrNull {
            isPodcastAudio(it.url, it.type)
        }
        val mediaModule = syndEntry.getModule(MediaModule.URI) as? MediaEntryModule
        val mediaContents = mediaModule?.mediaContents.orEmpty().toList() +
            mediaModule?.mediaGroups.orEmpty().flatMap { it.contents.orEmpty().toList() }
        val mediaAudio = mediaContents.firstOrNull { content ->
            val url = (content.reference as? UrlReference)?.url?.toString()
            isPodcastAudio(url, content.type, content.medium)
        }
        val foreignElements: List<Element> = buildList {
            syndEntry.foreignMarkup.orEmpty().forEach { root: Element ->
                add(root)
                val descendants = root.getDescendants()
                while (descendants.hasNext()) {
                    (descendants.next() as? Element)?.let(::add)
                }
            }
        }
        val foreignAudio = foreignElements.firstOrNull { element ->
            val url = element.getAttributeValue("url")
            val type = element.getAttributeValue("type")
            val medium = element.getAttributeValue("medium")
            element.name.equals("content", ignoreCase = true) &&
                isPodcastAudio(url, type, medium)
        }
        fun foreignText(name: String): String? = foreignElements.asSequence()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.textTrim
            ?.takeIf { it.isNotBlank() }
        val episodeGuid = foreignText("guid") ?: syndEntry.uri
        val mediaAudioUrl = (mediaAudio?.reference as? UrlReference)?.url?.toString()
        val audioUrl = enclosure?.url ?: mediaAudioUrl ?: foreignAudio?.getAttributeValue("url")
        val stableKey = audioUrl?.let { "audio:$it" }
        val stableId = stableKey?.let {
            UUID.nameUUIDFromBytes("${feed.id}|$it".toByteArray()).toString()
        }
        val duration = foreignText("duration")?.let(::parsePodcastDurationSeconds)
            ?: mediaAudio?.duration
            ?: foreignAudio?.getAttributeValue("duration")?.let(::parsePodcastDurationSeconds)
        val transcript = foreignElements.firstOrNull { it.name.equals("transcript", true) }
            ?.getAttributeValue("url")
        return Article(
            id = accountId.spacerDollar(stableId ?: UUID.randomUUID().toString()),
            accountId = accountId,
            feedId = feed.id,
            date =
                (syndEntry.publishedDate ?: syndEntry.updatedDate)?.takeIf { !it.isFuture(preDate) }
                    ?: preDate,
            title = syndEntry.title.decodeHTML() ?: feed.name,
            author = syndEntry.author,
            rawDescription = content ?: desc ?: "",
            shortDescription = Readability.parseToText(desc ?: content, syndEntry.link).take(280),
            //            fullContent = content,
            img = findThumbnail(syndEntry)?.takeUnless { it == audioUrl } ?: findThumbnail(content ?: desc),
            link = normalizeArticleUrl(syndEntry.link).ifBlank { audioUrl.orEmpty() },
            updateAt = preDate,
            audioUrl = audioUrl,
            audioMimeType = enclosure?.type ?: mediaAudio?.type ?: foreignAudio?.getAttributeValue("type"),
            audioLength = enclosure?.length ?: mediaAudio?.fileSize
                ?: foreignAudio?.getAttributeValue("fileSize")?.toLongOrNull(),
            durationSeconds = duration,
            episodeGuid = episodeGuid,
            seasonNumber = foreignText("season")?.toIntOrNull(),
            episodeNumber = foreignText("episode")?.toIntOrNull(),
            transcriptUrl = transcript,
            isExplicit = foreignText("explicit")?.lowercase() in setOf("yes", "true", "explicit"),
        )
    }

    fun findThumbnail(syndEntry: SyndEntry): String? {
        val imageEnclosure = syndEntry.enclosures?.firstOrNull {
            it.type?.startsWith("image/") == true ||
                it.url?.substringAfterLast('.', "")?.substringBefore('?')?.lowercase() in
                    setOf("jpg", "jpeg", "png", "webp", "gif")
        }
        if (imageEnclosure?.url != null) {
            return imageEnclosure.url
        }

        val mediaModule = syndEntry.getModule(MediaModule.URI) as? MediaEntryModule
        if (mediaModule != null) {
            return findThumbnail(mediaModule)
        }

        return null
    }

    private fun findThumbnail(mediaModule: MediaEntryModule): String? {
        val candidates =
            buildList {
                    add(mediaModule.metadata)
                    addAll(mediaModule.mediaGroups.map { mediaGroup -> mediaGroup.metadata })
                    addAll(mediaModule.mediaContents.map { content -> content.metadata })
                }
                .flatMap { it.thumbnail.toList() }

        val thumbnail = candidates.firstOrNull()

        if (thumbnail != null) {
            return thumbnail.url.toString()
        } else {
            val imageMedia = mediaModule.mediaContents.firstOrNull { it.medium == "image" }
            if (imageMedia != null) {
                return (imageMedia.reference as? UrlReference)?.url.toString()
            }
        }
        return null
    }

    fun findThumbnail(text: String?): String? {
        text ?: return null
        val enclosure = enclosureRegex.find(text)?.groupValues?.get(1)
        if (enclosure?.isNotBlank() == true) {
            return enclosure
        }
        // From https://gitlab.com/spacecowboy/Feeder
        // Using negative lookahead to skip data: urls, being inline base64
        // And capturing original quote to use as ending quote
        // Base64 encoded images can be quite large - and crash database cursors
        return imgRegex.find(text)?.groupValues?.get(2)?.takeIf { !it.startsWith("data:") }
    }

    suspend fun queryRssIconLink(feedLink: String?): String? {
        if (feedLink.isNullOrEmpty()) return null
        val iconFinder = BestIconFinder(okHttpClient)
        val domain = feedLink.extractDomain()
        return iconFinder.findBestIcon(domain ?: feedLink).also {
            Log.i("RLog", "queryRssIconByLink: get $it from $domain")
        }
    }

    suspend fun saveRssIcon(feedDao: FeedDao, feed: Feed, iconLink: String) {
        feedDao.update(feed.copy(icon = iconLink))
    }

    private suspend fun response(client: OkHttpClient, url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).build()).executeAsync()
}
