package me.ash.reader.ui.component.base

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import me.ash.reader.ui.ext.extractDomain

@Composable
fun RYAsyncImage(
    modifier: Modifier = Modifier,
    data: Any? = null,
    size: Size = Size.ORIGINAL,
    scale: Scale = Scale.FIT,
    precision: Precision = Precision.AUTOMATIC,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
    @DrawableRes placeholder: Int? = null,
    @DrawableRes error: Int? = null,
) {
    val context = LocalContext.current
    val request =
        remember(data, size, scale, precision, placeholder, error, context) {
            val domain = data?.toString()?.extractDomain()
            ImageRequest.Builder(context)
                .apply {
                    if (domain != null) addHeader("Referer", domain)
                    if (placeholder != null) placeholder(placeholder)
                    if (error != null) error(error)
                }
                .data(data)
                .crossfade(true)
                .scale(scale)
                .precision(precision)
                .size(size)
                .build()
        }
    val painter =
        rememberAsyncImagePainter(
            model = request,
        )
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    )
}
