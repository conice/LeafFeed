package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.theme.LayoutTokens

@Composable
fun DisplayText(
    modifier: Modifier = Modifier,
    text: String,
    desc: String,
    onTextClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .alphaIndicationClickable(enabled = onTextClick != null) { onTextClick?.invoke() }
                .padding(
                    start = LayoutTokens.PageHorizontalPadding,
                    top = LayoutTokens.PageTopPadding,
                    end = LayoutTokens.PageHorizontalPadding,
                    bottom = LayoutTokens.PageBottomPadding,
                )
    ) {
        Text(
            modifier = Modifier,
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RYExtensibleVisibility(visible = desc.isNotEmpty()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
