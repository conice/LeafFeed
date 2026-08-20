package com.conice.morss.ui.component.webview

import org.jsoup.Jsoup
import java.net.URI

/** Removes active content while retaining the article markup used by the reader. */
internal fun sanitizeWebViewContent(content: String, baseUrl: String? = null): String {
    val document = Jsoup.parseBodyFragment(content, baseUrl.orEmpty())
    document
        .select("script, object, embed, applet, frame, frameset, base, link, meta")
        .remove()
    document.select("iframe").forEach { iframe ->
        val source = iframe.absUrl("src").ifBlank { iframe.attr("src") }
        if (!source.isAllowedVideoFrameUrl()) {
            iframe.remove()
        }
    }
    document.select("form").forEach { it.unwrap() }
    document.select("input, button, select, textarea").remove()
    document.allElements.forEach { element ->
        element.attributes()
            .map { it.key }
            .filter { it.startsWith("on", ignoreCase = true) || it.equals("srcdoc", true) }
            .forEach { element.removeAttr(it) }

        listOf("href", "src", "action", "formaction", "xlink:href").forEach { attribute ->
            val value = element.attr(attribute)
            if (value.isUnsafeActiveUrl(attribute)) {
                element.removeAttr(attribute)
            }
        }
    }
    return document.body().html()
}

/**
 * Keep only video embeds from providers that are expected to render inside the reader. Other
 * frames are active documents and must not be allowed into the article WebView.
 */
private fun String.isAllowedVideoFrameUrl(): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme !in VIDEO_FRAME_SCHEMES || uri.rawUserInfo != null || uri.host.isNullOrBlank()) {
        return false
    }

    val host = uri.host.lowercase().removeSuffix(".")
    val path = uri.path.orEmpty()
    return when (host) {
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com" ->
            path.startsWith("/embed/")

        "player.vimeo.com" -> path.startsWith("/video/")
        "player.bilibili.com" -> path == "/player.html"
        else -> false
    }
}

private fun String.isUnsafeActiveUrl(attribute: String): Boolean {
    val compact = filterNot { it.code <= ASCII_SPACE }
    val scheme = compact.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (scheme == "javascript" || scheme == "vbscript") return true
    if (scheme != "data") return false

    // Inline raster images remain useful in feeds. Navigation and active HTML data URLs do not.
    return attribute != "src" ||
        compact.startsWith("data:text/html", ignoreCase = true) ||
        compact.startsWith("data:application/xhtml+xml", ignoreCase = true)
}

private const val ASCII_SPACE = 0x20
private val VIDEO_FRAME_SCHEMES = setOf("http", "https")
