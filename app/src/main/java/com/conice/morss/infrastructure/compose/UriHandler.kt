package com.conice.morss.infrastructure.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.conice.morss.infrastructure.android.openURL
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.OpenLinkPreference
import com.conice.morss.infrastructure.preference.OpenLinkSpecificBrowserPreference

@Composable
internal fun ProvideUriHandler(content: @Composable () -> Unit) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    CompositionLocalProvider(
        LocalUriHandler provides
            AppUriHandler(context, settings.openLink, settings.openLinkSpecificBrowser),
        content = content,
    )
}

private class AppUriHandler(
    private val context: Context,
    private val openLink: OpenLinkPreference,
    private val specificBrowser: OpenLinkSpecificBrowserPreference,
) : UriHandler {
    override fun openUri(uri: String) = context.openURL(uri, openLink, specificBrowser)
}
