package me.ash.reader.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class RefreshIndicatorResult { Success, Error }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactRefreshIndicatorContent(
    message: String?,
    progress: Float? = null,
    result: RefreshIndicatorResult? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(48.dp)
                .widthIn(min = 48.dp, max = 280.dp)
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(horizontal = if (message == null) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (result) {
            RefreshIndicatorResult.Success ->
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

            RefreshIndicatorResult.Error ->
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )

            null -> {
                if (progress == null) {
                    LoadingIndicator(
                        color = LocalContentColor.current,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    LoadingIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = LocalContentColor.current,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        message?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
