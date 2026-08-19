package com.conice.morss.ui.page.settings.tips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.OpenLinkPreference
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.infrastructure.android.openURL
import com.conice.morss.ui.theme.palette.onLight

@Composable
fun LicenseListPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        actions = {
            FeedbackIconButton(
                modifier = Modifier.size(22.dp),
                imageVector = Icons.Rounded.Star,
                contentDescription = stringResource(R.string.open_source_licenses),
                tint = MaterialTheme.colorScheme.onSurface
            ) {
                context.openURL(
                    url = resources.getString(R.string.github_link) + "/blob/main/LICENSE",
                    openLink = OpenLinkPreference.AutoPreferCustomTabs,
                )
            }
        },
        content = {
            Column {
                LibrariesContainer(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = WindowInsets(0, 0, 0, 24)
                        .add(WindowInsets.navigationBars)
                        .asPaddingValues(),
                )
            }
        },
    )
}
