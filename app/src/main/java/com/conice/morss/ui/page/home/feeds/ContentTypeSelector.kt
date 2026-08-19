package com.conice.morss.ui.page.home.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conice.morss.R
import com.conice.morss.application.data.ArticleContentType
import com.conice.morss.ui.component.base.RYSelectionChip
import com.conice.morss.ui.component.base.Subtitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentTypeSelector(
    selectedContentType: ArticleContentType,
    onSelect: (ArticleContentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Subtitle(text = stringResource(R.string.content_type))
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        RYSelectionChip(
            content = stringResource(R.string.view_articles),
            selected = selectedContentType == ArticleContentType.ARTICLE,
            selectedIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                )
            },
            onClick = { onSelect(ArticleContentType.ARTICLE) },
        )
        RYSelectionChip(
            content = stringResource(R.string.view_audio),
            selected = selectedContentType == ArticleContentType.AUDIO,
            selectedIcon = {
                Icon(imageVector = Icons.Outlined.Headphones, contentDescription = null)
            },
            onClick = { onSelect(ArticleContentType.AUDIO) },
        )
    }
}
