package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.ash.reader.ui.theme.LayoutTokens

@Composable
fun Subtitle(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LayoutTokens.SectionLabelVerticalPadding),
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
}
