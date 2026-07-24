package me.ash.reader.domain.service

import java.net.URI
import java.util.Locale

internal fun feedUrlsEquivalent(first: String, second: String): Boolean =
    canonicalFeedUrl(first) == canonicalFeedUrl(second)

internal fun canonicalFeedUrl(value: String): String {
    val input = value.trim()
    val uri = runCatching { URI(input) }.getOrNull()
        ?: return input.trimEnd('/')
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return input.trimEnd('/')
    val host = uri.host?.lowercase(Locale.ROOT) ?: return input.trimEnd('/')
    val port = uri.port.takeUnless {
        it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443)
    }
    val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()

    return buildString {
        append(scheme)
        append("://")
        append(userInfo)
        append(host)
        port?.let { append(":$it") }
        append(path)
        append(query)
    }
}
