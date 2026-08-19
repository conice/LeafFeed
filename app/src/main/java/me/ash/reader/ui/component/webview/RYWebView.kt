package me.ash.reader.ui.component.webview

import android.text.TextUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingImageHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalReadingSubheadBold
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.LocalReadingTextAlign
import me.ash.reader.infrastructure.preference.LocalReadingTextBold
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.alwaysLight
import java.net.URI

@Composable
fun RYWebView(
    modifier: Modifier = Modifier,
    content: String,
    baseUrl: String? = null,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    onSelectionActiveChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val tonalElevation = LocalReadingPageTonalElevation.current
    val selectionTextColor =
        (MaterialTheme.colorScheme.onTertiaryContainer alwaysLight true).toArgb()
    val selectionBgColor = (MaterialTheme.colorScheme.tertiaryContainer alwaysLight true).toArgb()
    val textColor: Int = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textBold: Boolean = LocalReadingTextBold.current.value
    val textAlign: String = LocalReadingTextAlign.current.toTextAlignCSS()
    val textMargin: Int = LocalReadingTextHorizontalPadding.current
    val boldTextColor: Int = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkTextColor: Int = MaterialTheme.colorScheme.primary.toArgb()
    val subheadBold: Boolean = LocalReadingSubheadBold.current.value
    val subheadUpperCase: Boolean = LocalReadingSubheadUpperCase.current.value
    val readingFonts = LocalReadingFonts.current
    val fontSize: Int = LocalReadingTextFontSize.current
    val letterSpacing: Float = LocalReadingTextLetterSpacing.current
    val lineHeight: Float = LocalReadingTextLineHeight.current
    val imgMargin: Int = LocalReadingImageHorizontalPadding.current
    val imgBorderRadius: Int = LocalReadingImageRoundedCorners.current
    val codeTextColor: Int = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBgColor: Int =
        MaterialTheme.colorScheme.surfaceColorAtElevation((tonalElevation.value + 6).dp).toArgb()
    val boldCharacters = LocalReadingBoldCharacters.current
    val safeBaseUri =
        remember(baseUrl) {
            baseUrl?.trim()?.let { candidate ->
                runCatching { URI(candidate) }.getOrNull()?.takeIf { uri ->
                    uri.scheme?.lowercase() in HTTP_SCHEMES &&
                        !uri.isOpaque &&
                        !uri.rawAuthority.isNullOrBlank() &&
                        uri.rawUserInfo == null
                }
            }
        }
    val safeBaseUrl = safeBaseUri?.toString()
    val refererUrl =
        remember(safeBaseUri) {
            safeBaseUri?.let { uri ->
                "${uri.scheme.lowercase()}://${uri.rawAuthority}/"
            }
        }
    val escapedBaseUrl = remember(safeBaseUrl) { TextUtils.htmlEncode(safeBaseUrl.orEmpty()) }
    val currentUriHandler by rememberUpdatedState(uriHandler)
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentOnSelectionActiveChange by rememberUpdatedState(onSelectionActiveChange)

    val webView =
        remember(
            context,
            readingFonts,
            refererUrl,
            onImageClick != null,
            onSelectionActiveChange != null,
        ) {
            WebViewLayout.get(
                context = context,
                readingFontsPreference = readingFonts,
                webViewClient =
                    WebViewClient(
                        refererUrl = refererUrl,
                        imageClicksEnabled = onImageClick != null,
                        onOpenLink = { url -> currentUriHandler.openUri(url) },
                    ),
                onImageClick =
                    if (onImageClick != null) {
                        { imageUrl, altText ->
                            currentOnImageClick?.invoke(imageUrl, altText)
                        }
                    } else {
                        null
                    },
                onSelectionActiveChange =
                    if (onSelectionActiveChange != null) {
                        { active -> currentOnSelectionActiveChange?.invoke(active) }
                    } else {
                        null
                    },
            )
        }

    DisposableEffect(webView) {
        onDispose {
            currentOnSelectionActiveChange?.invoke(false)
            webView.stopLoading()
            webView.removeJavascriptInterface(JavaScriptInterface.NAME)
            webView.destroy()
        }
    }

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External)
            ExternalFonts.FontType.ReadingFont.toPath(context)
        else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else null
    val sanitizedContent = remember(content) { sanitizeWebViewContent(content) }

    key(webView) {
        AndroidView(
            modifier = modifier,
            factory = { webView },
            update = {
                it.apply {
                    settings.defaultFontSize = fontSize
                    loadDataWithBaseURL(
                        null,
                        WebViewHtml.HTML.format(
                            WebViewStyle.get(
                                fontSize = fontSize,
                                fontPath = fontPath,
                                lineHeight = lineHeight,
                                letterSpacing = letterSpacing,
                                textMargin = textMargin,
                                textColor = textColor,
                                textBold = textBold,
                                textAlign = textAlign,
                                boldTextColor = boldTextColor,
                                subheadBold = subheadBold,
                                subheadUpperCase = subheadUpperCase,
                                imgMargin = imgMargin,
                                imgBorderRadius = imgBorderRadius,
                                linkTextColor = linkTextColor,
                                codeTextColor = codeTextColor,
                                codeBgColor = codeBgColor,
                                tableMargin = textMargin,
                                selectionTextColor = selectionTextColor,
                                selectionBgColor = selectionBgColor,
                            ),
                            escapedBaseUrl,
                            sanitizedContent,
                            WebViewScript.get(boldCharacters.value),
                        ),
                        "text/HTML",
                        "UTF-8",
                        null,
                    )
                }
            },
        )
    }
}

private val HTTP_SCHEMES = setOf("http", "https")
