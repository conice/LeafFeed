package com.conice.morss.ui.page.settings.accounts

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.conice.morss.R
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.component.base.Subtitle
import com.conice.morss.ui.page.settings.SettingItem
import com.conice.morss.ui.page.settings.accounts.addition.*
import com.conice.morss.ui.theme.palette.onLight

@Composable
fun AddAccountsPage(
    viewModel: AccountViewModel = hiltViewModel(),
    additionViewModel: AdditionViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToAccountDetails: (Int) -> Unit,
) {
    val context = LocalContext.current

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
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.add_accounts), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.local),
                    )
                    SettingItem(
                        title = stringResource(R.string.local),
                        desc = stringResource(R.string.local_desc),
                        icon = Icons.Rounded.RssFeed,
                        onClick = {
                            additionViewModel.showAddLocalAccountDialog()
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.self_hosted),
                    )
                    SettingItem(
                        title = stringResource(R.string.fresh_rss),
                        desc = stringResource(R.string.fresh_rss_desc),
                        iconPainter = painterResource(id = R.drawable.ic_freshrss),
                        onClick = {
                            additionViewModel.showAddFreshRSSAccountDialog()
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.google_reader),
                        desc = stringResource(R.string.google_reader_desc),
                        icon = Icons.Rounded.RssFeed,
                        onClick = {
                            additionViewModel.showAddGoogleReaderAccountDialog()
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.fever),
                        desc = stringResource(R.string.fever_desc),
                        iconPainter = painterResource(id = R.drawable.ic_fever),
                        onClick = {
                            additionViewModel.showAddFeverAccountDialog()
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    AddLocalAccountDialog(onBack, navigateToAccountDetails)
    AddFeverAccountDialog(onBack, navigateToAccountDetails)
    AddGoogleReaderAccountDialog(onBack, navigateToAccountDetails)
    AddFreshRSSAccountDialog(onBack, navigateToAccountDetails)
}

@Preview
@Composable
fun AddAccountsPreview() {
    AddAccountsPage(onBack = {}, navigateToAccountDetails = {})
}
