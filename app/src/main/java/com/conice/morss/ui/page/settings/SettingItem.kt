/**
 * Copyright (C) 2021 Kyant0
 *
 * @link https://github.com/Kyant0/MusicYou
 * @author Kyant0
 * @modifier Ashinch
 */

package com.conice.morss.ui.page.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import com.conice.morss.ui.theme.palette.LocalTonalPalettes
import com.conice.morss.ui.theme.palette.onDark
import com.conice.morss.ui.interaction.expressivePressFeedback
import com.conice.morss.ui.theme.LayoutTokens

val LocalInteractionSources = compositionLocalOf<MutableInteractionSource?> { null }

enum class SettingItemType {
    Action,
    Navigation,
    Choice,
    External,
    Information,
    Destructive,
}

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    desc: String? = null,
    descMaxLines: Int = 3,
    disabledReason: String? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    type: SettingItemType = SettingItemType.Action,
    role: Role? = Role.Button,
    stateDescriptionText: String? = null,
    loading: Boolean = false,
    highlighted: Boolean = false,
    settingKey: String? = null,
    targetKey: String? = null,
    separatedActions: Boolean = false,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val tonalPalettes = LocalTonalPalettes.current
    val interactionSource = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember(settingKey) { BringIntoViewRequester() }
    val isInteractive = type != SettingItemType.Information
    val isEnabled = enabled && !loading
    val isTarget = settingKey != null && settingKey == targetKey

    LaunchedEffect(isTarget) {
        if (isTarget) bringIntoViewRequester.bringIntoView()
    }

    Surface(
        modifier = modifier
            .then(
                if (settingKey != null) {
                    Modifier
                        .testTag("setting:$settingKey")
                        .bringIntoViewRequester(bringIntoViewRequester)
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        enabled = isEnabled,
                        role = role,
                        interactionSource = interactionSource,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                if (!enabled) disabled()
                stateDescriptionText?.let { this.stateDescription = it }
            }
            .expressivePressFeedback(
                interactionSource = interactionSource,
                enabled = isInteractive && isEnabled,
                pressedAlpha = .72f,
                pressedScale = .99f,
            ),
        color = if (highlighted || isTarget) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            Color.Unspecified
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LayoutTokens.MinimumTouchTarget)
                .padding(
                    LayoutTokens.PageHorizontalPadding,
                    LayoutTokens.SettingVerticalPadding,
                    LayoutTokens.ActionGap,
                    LayoutTokens.SettingVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    modifier = Modifier.padding(end = 24.dp),
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                iconPainter?.let {
                    Icon(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(24.dp),
                        painter = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (type == SettingItemType.Destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                )
                desc?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = descMaxLines,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                disabledReason?.takeIf { !enabled }?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            val defaultAction: (@Composable () -> Unit)? = when {
                loading -> ({
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                })
                type == SettingItemType.Navigation -> ({
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                })
                type == SettingItemType.Choice -> ({
                    Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = null)
                })
                type == SettingItemType.External -> ({
                    Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null)
                })
                else -> null
            }
            (action ?: defaultAction)?.let {
                if (separatedActions) {
                    VerticalDivider(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(1.dp, 32.dp),
                        color = tonalPalettes neutralVariant 80 onDark (tonalPalettes neutralVariant 30)
                    )
                }
                CompositionLocalProvider(LocalInteractionSources provides if (separatedActions) null else interactionSource) {
                    Box(Modifier.padding(start = 16.dp)) {
                        it()
                    }
                }
            }
        }
    }
}
