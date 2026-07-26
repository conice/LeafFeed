package me.ash.reader.ui.page.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalNewVersionNumber
import me.ash.reader.infrastructure.preference.LocalSkipVersionNumber
import me.ash.reader.ui.component.base.Banner
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.getCurrentVersion
import me.ash.reader.ui.page.settings.tips.UpdateDialog
import me.ash.reader.ui.page.settings.tips.UpdateViewModel
import me.ash.reader.ui.theme.palette.onLight
import me.ash.reader.ui.theme.LayoutTokens

@Composable
fun SettingsPage(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToAccounts: () -> Unit,
    navigateToColorAndStyle: () -> Unit,
    navigateToInteraction: () -> Unit,
    navigateToTipsAndSupport: () -> Unit,
    navigateToReadingOptions: () -> Unit,
    navigateToPodcastSettings: () -> Unit,
    navigateToDataPrivacySettings: () -> Unit,
) {
    val context = LocalContext.current
    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion by remember { mutableStateOf(context.getCurrentVersion()) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val accountTitle = stringResource(R.string.settings_accounts_title)
    val accountDesc = stringResource(R.string.settings_accounts_sync_desc)
    val readingTitle = stringResource(R.string.settings_reading_title)
    val readingDesc = stringResource(R.string.settings_reading_desc)
    val aiSearchAlias = stringResource(R.string.ai_settings)
    val podcastTitle = stringResource(R.string.settings_media_notifications_title)
    val podcastDesc = stringResource(R.string.settings_media_notifications_desc)
    val notificationsSearchAlias = stringResource(R.string.settings_notifications_title)
    val privacyTitle = stringResource(R.string.settings_privacy_title)
    val privacyDesc = stringResource(R.string.settings_privacy_desc)
    val appearanceTitle = stringResource(R.string.appearance)
    val appearanceDesc = stringResource(R.string.color_and_style_desc)
    val interactionTitle = stringResource(R.string.settings_general_title)
    val interactionDesc = stringResource(R.string.interaction_desc)
    val languageSearchAlias = stringResource(R.string.languages)
    val supportTitle = stringResource(R.string.settings_help_about_title)
    val supportDesc = stringResource(R.string.tips_and_support_desc)
    val query = searchQuery.trim()

    fun matches(vararg values: String): Boolean =
        query.isBlank() || values.any { it.contains(query, ignoreCase = true) }

    val showAccount = matches(accountTitle, accountDesc)
    val showReading = matches(readingTitle, readingDesc, aiSearchAlias)
    val showPodcast = matches(podcastTitle, podcastDesc, notificationsSearchAlias)
    val showPrivacy = matches(privacyTitle, privacyDesc)
    val showAppearance = matches(appearanceTitle, appearanceDesc)
    val showInteraction = matches(interactionTitle, interactionDesc, languageSearchAlias)
    val showSupport = matches(supportTitle, supportDesc)
    val hasResults =
        showAccount || showReading || showPodcast || showPrivacy || showAppearance ||
            showInteraction || showSupport

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        actions = {
            FeedbackIconButton(
                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription =
                    stringResource(if (searchVisible) R.string.close else R.string.search),
                tint = MaterialTheme.colorScheme.onSurface,
            ) {
                searchVisible = !searchVisible
                if (!searchVisible) searchQuery = ""
            }
        },
        content = {
            LazyColumn {
                item { DisplayText(text = stringResource(R.string.settings), desc = "") }
                if (searchVisible) {
                    item {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.settings_search)) },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Box {
                        if (newVersion.whetherNeedUpdate(currentVersion, skipVersion)) {
                            Banner(
                                modifier = Modifier.zIndex(1f),
                                title = stringResource(R.string.get_new_updates),
                                desc =
                                    stringResource(
                                        R.string.get_new_updates_desc,
                                        newVersion.toString(),
                                    ),
                                icon = Icons.Outlined.Lightbulb,
                                action = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.close),
                                    )
                                },
                            ) {
                                updateViewModel.showDialog()
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                if (showAccount) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_account_section))
                        SelectableSettingGroupItem(
                            title = accountTitle,
                            desc = accountDesc,
                            icon = Icons.Outlined.AccountCircle,
                            onClick = navigateToAccounts,
                        )
                    }
                }
                if (showInteraction) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_interaction_section))
                        SelectableSettingGroupItem(
                            title = interactionTitle,
                            desc = interactionDesc,
                            icon = Icons.Outlined.TouchApp,
                            onClick = navigateToInteraction,
                        )
                    }
                }
                if (showAppearance || showReading) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_reading_section))
                        if (showReading) {
                            SelectableSettingGroupItem(
                                title = readingTitle,
                                desc = readingDesc,
                                icon = Icons.Outlined.MenuBook,
                                onClick = navigateToReadingOptions,
                            )
                        }
                        if (showAppearance) {
                            SelectableSettingGroupItem(
                                title = appearanceTitle,
                                desc = appearanceDesc,
                                icon = Icons.Outlined.Palette,
                                onClick = navigateToColorAndStyle,
                            )
                        }
                    }
                }
                if (showPodcast) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_media_section))
                        SelectableSettingGroupItem(
                            title = podcastTitle,
                            desc = podcastDesc,
                            icon = Icons.Outlined.Podcasts,
                            onClick = navigateToPodcastSettings,
                        )
                    }
                }
                if (showPrivacy) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_data_section))
                        SelectableSettingGroupItem(
                            title = privacyTitle,
                            desc = privacyDesc,
                            icon = Icons.Outlined.PrivacyTip,
                            onClick = navigateToDataPrivacySettings,
                        )
                    }
                }
                if (showSupport) {
                    item {
                        SettingsSectionTitle(stringResource(R.string.settings_system_section))
                        SelectableSettingGroupItem(
                            title = supportTitle,
                            desc = supportDesc,
                            icon = Icons.Outlined.TipsAndUpdates,
                            onClick = navigateToTipsAndSupport,
                        )
                    }
                }
                if (!hasResults) {
                    item {
                        Text(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                            text = stringResource(R.string.empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    UpdateDialog()
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(
            start = LayoutTokens.PageHorizontalPadding,
            top = LayoutTokens.SectionSpacing,
            end = LayoutTokens.PageHorizontalPadding,
            bottom = LayoutTokens.SectionLabelVerticalPadding,
        ),
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}
