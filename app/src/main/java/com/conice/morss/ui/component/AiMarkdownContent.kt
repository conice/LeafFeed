package com.conice.morss.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.net.URI
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val AiMarkdownParser = Parser.builder().build()

@Composable
internal fun AiMarkdownContent(
    markdown: String,
    loading: Boolean,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onSuggestedTagsClick: ((List<String>) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val document = remember(markdown, loading) {
        AiMarkdownParser.parse(markdown).also { document ->
            if (loading) appendAiMarkdownCursor(document)
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiMarkdownChildren(
            parent = document,
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        if (loading && !document.hasInlineAiCursor()) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiMarkdownChildren(
    parent: Node,
    loading: Boolean,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onSuggestedTagsClick: ((List<String>) -> Unit)?,
) {
    var child = parent.firstChild
    while (child != null) {
        AiMarkdownBlock(
            node = child,
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        child = child.next
    }
}

@Composable
private fun AiMarkdownBlock(
    node: Node,
    loading: Boolean,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onSuggestedTagsClick: ((List<String>) -> Unit)?,
) {
    when (node) {
        is Heading -> AiMarkdownRichText(
            node = node,
            style = when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        is Paragraph -> AiMarkdownRichText(
            node = node,
            style =
                if (node.firstChild is StrongEmphasis && node.firstChild.next == null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        is BulletList -> AiMarkdownList(
            list = node,
            startNumber = null,
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        is OrderedList -> AiMarkdownList(
            list = node,
            startNumber = node.markerStartNumber ?: 1,
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
        is BlockQuote -> Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiMarkdownChildren(
                    parent = node,
                    loading = loading,
                    articleIds = articleIds,
                    articleTitles = articleTitles,
                    onArticleClick = onArticleClick,
                    onSuggestedTagsClick = onSuggestedTagsClick,
                )
            }
        }
        is FencedCodeBlock -> AiMarkdownCodeBlock(node.literal.orEmpty())
        is IndentedCodeBlock -> AiMarkdownCodeBlock(node.literal.orEmpty())
        is ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is HtmlBlock -> Text(
            text = node.literal.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> AiMarkdownChildren(
            parent = node,
            loading = loading,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onSuggestedTagsClick = onSuggestedTagsClick,
        )
    }
}

@Composable
private fun AiMarkdownList(
    list: Node,
    startNumber: Int?,
    loading: Boolean,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onSuggestedTagsClick: ((List<String>) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var item = list.firstChild
        var index = 0
        while (item != null) {
            if (item is ListItem) {
                val itemNumber = startNumber?.plus(index)
                val itemText = aiMarkdownMatchText(item, itemNumber)
                val itemSuggestedTags = parseAiSummaryTags(aiMarkdownPlainText(item).trim())
                val itemArticleNumber = findAiSummaryArticleNumber(
                    line = itemText,
                    articleTitles = articleTitles,
                    articleCount = articleIds.size,
                )
                val itemArticleId = itemArticleNumber?.let { articleIds.getOrNull(it - 1) }
                val rowHandlesArticleClick =
                    !loading &&
                        itemArticleId != null &&
                        !aiMarkdownContainsSafeLink(item) &&
                        !(itemSuggestedTags != null && onSuggestedTagsClick != null)
                Row(
                    modifier =
                        Modifier.fillMaxWidth().then(
                            if (rowHandlesArticleClick) {
                                Modifier.clickable { itemArticleId?.let(onArticleClick) }
                            } else {
                                Modifier
                            },
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = itemNumber?.let { "$it." } ?: "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 24.dp).padding(end = 8.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AiMarkdownChildren(
                            parent = item,
                            loading = loading,
                            articleIds = if (rowHandlesArticleClick) emptyList() else articleIds,
                            articleTitles = articleTitles,
                            onArticleClick = onArticleClick,
                            onSuggestedTagsClick = onSuggestedTagsClick,
                        )
                    }
                }
                index += 1
            }
            item = item.next
        }
    }
}

@Composable
private fun AiMarkdownRichText(
    node: Node,
    style: TextStyle,
    loading: Boolean,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onSuggestedTagsClick: ((List<String>) -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    val plainText = aiMarkdownPlainText(node).trim()
    val suggestedTags = parseAiSummaryTags(plainText)
    val articleNumber = findAiSummaryArticleNumber(
        line = plainText,
        articleTitles = articleTitles,
        articleCount = articleIds.size,
    )
    val articleId = articleNumber?.let { articleIds.getOrNull(it - 1) }
    val containsLink = aiMarkdownContainsSafeLink(node)
    val actionModifier = when {
        loading || containsLink -> Modifier
        suggestedTags != null && onSuggestedTagsClick != null ->
            Modifier.clickable { onSuggestedTagsClick(suggestedTags) }
        articleId != null -> Modifier.clickable { onArticleClick(articleId) }
        else -> Modifier
    }
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceVariant,
    )
    val text = remember(
        node,
        linkStyle,
        codeStyle,
        uriHandler,
        articleIds,
        articleTitles,
        onArticleClick,
    ) {
        buildAiMarkdownText(
            parent = node,
            linkStyle = linkStyle,
            codeStyle = codeStyle,
            articleIds = articleIds,
            articleTitles = articleTitles,
            onArticleClick = onArticleClick,
            onExternalLinkClick = uriHandler::openUri,
        )
    }
    Text(
        text = text,
        style = if (articleNumber != null) MaterialTheme.typography.titleSmall else style,
        modifier = Modifier.fillMaxWidth().then(actionModifier),
    )
}

@Composable
private fun AiMarkdownCodeBlock(code: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text = code.trimEnd(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun buildAiMarkdownText(
    parent: Node,
    linkStyle: SpanStyle,
    codeStyle: SpanStyle,
    articleIds: List<String>,
    articleTitles: List<String>,
    onArticleClick: (String) -> Unit,
    onExternalLinkClick: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is MarkdownText -> append(node.literal)
            is SoftLineBreak -> append(' ')
            is HardLineBreak -> append('\n')
            is Code -> withStyle(codeStyle) { append(node.literal) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendChildren(node, ::appendNode)
            }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendChildren(node, ::appendNode)
            }
            is Link -> {
                val url = node.destination.takeIf(::isSafeAiMarkdownUrl)
                if (url == null) {
                    appendChildren(node, ::appendNode)
                } else {
                    val articleId = findAiMarkdownArticleId(
                        link = node,
                        articleIds = articleIds,
                        articleTitles = articleTitles,
                    )
                    pushLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(style = linkStyle),
                            linkInteractionListener = {
                                if (articleId != null) {
                                    onArticleClick(articleId)
                                } else {
                                    onExternalLinkClick(url)
                                }
                            },
                        ),
                    )
                    appendChildren(node, ::appendNode)
                    pop()
                }
            }
            is Image -> appendChildren(node, ::appendNode)
            is HtmlInline -> append(node.literal)
            else -> appendChildren(node, ::appendNode)
        }
    }
    appendChildren(parent, ::appendNode)
}

private fun appendChildren(parent: Node, appendNode: (Node) -> Unit) {
    var child = parent.firstChild
    while (child != null) {
        appendNode(child)
        child = child.next
    }
}

internal fun aiMarkdownPlainText(parent: Node): String = buildString {
    fun appendNode(node: Node) {
        when (node) {
            is MarkdownText -> append(node.literal)
            is Code -> append(node.literal)
            is SoftLineBreak -> append(' ')
            is HardLineBreak -> append('\n')
            is HtmlInline -> append(node.literal)
            else -> appendChildren(node, ::appendNode)
        }
    }
    appendChildren(parent, ::appendNode)
}

internal fun aiMarkdownMatchText(parent: Node, listNumber: Int?): String {
    val plainText = aiMarkdownPlainText(parent).trim()
    return listNumber?.let { "$it. $plainText" } ?: plainText
}

internal fun findAiMarkdownArticleId(
    link: Link,
    articleIds: List<String>,
    articleTitles: List<String>,
): String? =
    findAiSummaryArticleNumber(
        line = aiMarkdownPlainText(link).trim(),
        articleTitles = articleTitles,
        articleCount = articleIds.size,
    )?.let { articleIds.getOrNull(it - 1) }

private fun aiMarkdownContainsSafeLink(parent: Node): Boolean {
    var child = parent.firstChild
    while (child != null) {
        if (child is Link && isSafeAiMarkdownUrl(child.destination)) return true
        if (aiMarkdownContainsSafeLink(child)) return true
        child = child.next
    }
    return false
}

internal fun parseAiMarkdown(markdown: String): Node = AiMarkdownParser.parse(markdown)

internal fun appendAiMarkdownCursor(document: Node): Boolean {
    var node = document.lastChild
    var inlineContainer: Node? = null
    while (node != null) {
        if (node is Paragraph || node is Heading) inlineContainer = node
        node = node.lastChild
    }
    inlineContainer?.appendChild(MarkdownText(" ▍"))
    return inlineContainer != null
}

private fun Node.hasInlineAiCursor(): Boolean {
    var node = lastChild
    while (node != null) {
        if (
            node is MarkdownText &&
                node.literal.endsWith("▍") &&
                (node.parent is Paragraph || node.parent is Heading)
        ) return true
        node = node.lastChild
    }
    return false
}

internal fun isSafeAiMarkdownUrl(url: String): Boolean = runCatching {
    val uri = URI(url.trim())
    uri.isAbsolute && (uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
}.getOrDefault(false)
