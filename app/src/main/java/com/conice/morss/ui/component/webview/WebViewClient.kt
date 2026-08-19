package com.conice.morss.ui.component.webview

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import timber.log.Timber

class WebViewClient(
    private val refererUrl: String?,
    private val imageClicksEnabled: Boolean,
    private val onOpenLink: (url: String) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        val referrer = refererUrl?.takeIf { it.isNotBlank() }
        if (
            referrer != null &&
                request.method.equals("GET", ignoreCase = true) &&
                url.isHttpUrl() &&
                request.isLikelyImageRequest()
        ) {
            return loadWithReferer(url, request, referrer)
                ?: super.shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (imageClicksEnabled) view?.evaluateJavascript(OnImgClickScript, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        if (uri.scheme?.lowercase() in EXTERNAL_LINK_SCHEMES) {
            onOpenLink(uri.toString())
        }
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            Timber.w("Reader WebView failed with error code %s", error?.errorCode)
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    private fun loadWithReferer(
        url: String,
        request: WebResourceRequest,
        referrer: String,
    ): WebResourceResponse? {
        val connection =
            try {
                (URI.create(url).toURL().openConnection() as? HttpURLConnection) ?: return null
            } catch (error: Exception) {
                Timber.w(
                    "Unable to open a reader resource connection (%s)",
                    error.javaClass.simpleName,
                )
                return null
            }
        return try {
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            request.requestHeaders.forEach { (name, value) ->
                if (
                    REQUEST_HEADERS_HANDLED_BY_CONNECTION.none {
                        it.equals(name, ignoreCase = true)
                    }
                ) {
                    connection.setRequestProperty(name, value)
                }
            }
            connection.setRequestProperty("Referer", referrer)

            val statusCode = connection.responseCode
            val stream =
                (if (statusCode >= 400) connection.errorStream else connection.inputStream)
                    ?: ByteArrayInputStream(ByteArray(0))
            val contentType = connection.contentType.orEmpty()
            val mimeType = contentType.substringBefore(';').ifBlank { "application/octet-stream" }
            val encoding =
                contentType.substringAfter("charset=", missingDelimiterValue = "")
                    .substringBefore(';')
                    .trim()
                    .ifBlank { null }
            val headers =
                connection.headerFields.entries
                    .mapNotNull { (name, values) ->
                        name?.let { it to values.filterNotNull().joinToString(",") }
                    }
                    .toMap()
            WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                connection.responseMessage
                    ?.filter { it.code in 0x20..0x7E }
                    ?.takeIf { it.isNotBlank() }
                    ?: "Response",
                headers,
                DisconnectingInputStream(stream, connection),
            )
        } catch (error: Exception) {
            connection.disconnect()
            Timber.w(
                "Unable to load a reader resource with its referrer (%s)",
                error.javaClass.simpleName,
            )
            null
        }
    }

    private fun String.isHttpUrl(): Boolean =
        runCatching {
            URI(this).let { uri ->
                uri.scheme?.lowercase() in HTTP_SCHEMES &&
                    !uri.isOpaque &&
                    !uri.rawAuthority.isNullOrBlank() &&
                    uri.rawUserInfo == null
            }
        }.getOrDefault(false)

    private fun WebResourceRequest.isLikelyImageRequest(): Boolean {
        val acceptsImages =
            requestHeaders.entries.any { (name, value) ->
                name.equals("Accept", ignoreCase = true) && value.contains("image/", true)
            }
        if (acceptsImages) return true
        val extension = url.path?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    companion object {
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
        private val HTTP_SCHEMES = setOf("http", "https")
        private val EXTERNAL_LINK_SCHEMES = setOf("http", "https", "mailto", "tel")
        private val REQUEST_HEADERS_HANDLED_BY_CONNECTION =
            setOf(
                "Accept-Encoding",
                "Connection",
                "Host",
                "If-Modified-Since",
                "If-None-Match",
                "Proxy-Connection",
                "Referer",
                "TE",
                "Trailer",
                "Transfer-Encoding",
                "Upgrade",
            )
        private val IMAGE_EXTENSIONS =
            setOf("avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "svg", "webp")
        private const val OnImgClickScript = """
            javascript:(function() {
                var imgs = document.getElementsByTagName("img");
                for(var i = 0; i < imgs.length; i++){
                    imgs[i].pos = i;
                    imgs[i].onclick = function(event) {
                        event.preventDefault();
                        window.${JavaScriptInterface.NAME}.onImgTagClick(this.src, this.alt);
                    }
                }
            })()
            """
    }
}

private class DisconnectingInputStream(
    delegate: InputStream,
    private val connection: HttpURLConnection,
) : FilterInputStream(delegate) {
    override fun close() {
        try {
            super.close()
        } finally {
            connection.disconnect()
        }
    }
}
