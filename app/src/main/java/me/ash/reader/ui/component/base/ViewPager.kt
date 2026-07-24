package me.ash.reader.ui.component.base

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewPager(
    modifier: Modifier = Modifier,
    composableList: List<@Composable () -> Unit>,
    userScrollEnabled: Boolean = true,
) {
    HorizontalPager(
        state = rememberPagerState { composableList.size },
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
            .wrapContentHeight()
            .fillMaxWidth(),
        userScrollEnabled = userScrollEnabled,
    ) { page ->
        composableList[page]()
    }
}
