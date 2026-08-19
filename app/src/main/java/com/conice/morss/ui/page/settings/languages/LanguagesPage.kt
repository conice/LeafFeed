package com.conice.morss.ui.page.settings.languages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.LanguagesPreference
import com.conice.morss.infrastructure.preference.LocalLanguages
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.page.settings.SettingItem
import com.conice.morss.ui.theme.palette.onLight
import java.util.Locale

@Composable
fun LanguagesPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = Locale.getDefault()

    val languages = LocalLanguages.current.run {
        if (toLocale() == currentLocale) this
        else LanguagesPreference.default
    }

    val scope = rememberCoroutineScope()

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
                item(key = languages.value) {
                    DisplayText(text = stringResource(R.string.languages), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    LanguagesPreference.values.map {
                        SettingItem(
                            title = it.toDesc(),
                            onClick = {
                                it.put(context, scope)
                            },
                        ) {
                            RadioButton(selected = it == languages, onClick = {
                                it.put(context, scope)
                            })
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )
}
