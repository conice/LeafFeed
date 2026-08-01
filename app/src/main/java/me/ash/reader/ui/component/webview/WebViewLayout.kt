package me.ash.reader.ui.component.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import me.ash.reader.infrastructure.preference.ReadingFontsPreference

object WebViewLayout {

    @SuppressLint("SetJavaScriptEnabled")
    fun get(
        context: Context,
        readingFontsPreference: ReadingFontsPreference,
        webViewClient: WebViewClient,
        onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    ) =
        WebView(context).apply {
            val readerWebView = this
            this.webViewClient = webViewClient
            scrollBarSize = 0
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            with(this.settings) {
                allowContentAccess = false
                allowFileAccess = readingFontsPreference is ReadingFontsPreference.External
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                standardFontFamily =
                    when (readingFontsPreference) {
                        ReadingFontsPreference.Cursive -> "cursive"
                        ReadingFontsPreference.Monospace -> "monospace"
                        ReadingFontsPreference.SansSerif -> "sans-serif"
                        ReadingFontsPreference.Serif -> "serif"
                        ReadingFontsPreference.GoogleSans -> {
                            "sans-serif"
                        }
                        ReadingFontsPreference.External -> {
                            "sans-serif"
                        }

                        else -> "sans-serif"
                    }
                domStorageEnabled = false
                databaseEnabled = false
                setGeolocationEnabled(false)
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                safeBrowsingEnabled = true
                setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = true
                }
            }
            onImageClick?.let { imageClick ->
                addJavascriptInterface(
                    object : JavaScriptInterface {
                        @JavascriptInterface
                        override fun onImgTagClick(imgUrl: String?, alt: String?) {
                            if (imgUrl != null) {
                                readerWebView.post { imageClick.invoke(imgUrl, alt.orEmpty()) }
                            }
                        }
                    },
                    JavaScriptInterface.NAME,
                )
            }
        }
}
