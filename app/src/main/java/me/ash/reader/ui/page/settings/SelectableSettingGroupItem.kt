/**
 * Copyright (C) 2021 Kyant0
 *
 * @link https://github.com/Kyant0/MusicYou
 * @author Kyant0
 * @modifier Ashinch
 */

package me.ash.reader.ui.page.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.interaction.expressivePressFeedback

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectableSettingGroupItem(
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    selected: Boolean = false,
    title: String,
    desc: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "settingGroupContainer",
    )
    val titleColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "settingGroupTitle",
    )
    val secondaryColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "settingGroupSecondary",
    )

    Surface(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enable,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .expressivePressFeedback(
                interactionSource = interactionSource,
                enabled = enable,
                pressedAlpha = .72f,
                pressedScale = .99f,
            ),
        color = Color.Unspecified,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    color = containerColor,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                .padding(8.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    tint = secondaryColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = if (desc == null) 2 else 1,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = titleColor,
                )
                desc?.let {
                    Text(
                        text = it,
                        color = secondaryColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
