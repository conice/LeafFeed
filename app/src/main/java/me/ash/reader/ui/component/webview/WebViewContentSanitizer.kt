package me.ash.reader.ui.component.webview

import org.jsoup.Jsoup

/** Removes active content while retaining the article markup used by the reader. */
internal fun sanitizeWebViewContent(content: String): String {
    val document = Jsoup.parseBodyFragment(content)
    document
        .select("script, object, embed, applet, iframe, frame, frameset, base, link, meta")
        .remove()
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
