package com.conice.morss.ui.page.home.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.conice.morss.R
import com.conice.morss.domain.model.general.MarkAsReadConditions

@Composable
fun MarkAsReadFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemClick: (MarkAsReadConditions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(),
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primaryFixed
            } else {
                MaterialTheme.colorScheme.primaryFixedDim
            },
            contentColor = MaterialTheme.colorScheme.onPrimaryFixedVariant,
        ) {
            Icon(
                imageVector = Icons.Rounded.DoneAll,
                contentDescription = stringResource(R.string.mark_as_read),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            MarkAsReadMenuItem(
                text = stringResource(R.string.mark_as_read_seven_days),
                conditions = MarkAsReadConditions.SevenDays,
                onExpandedChange = onExpandedChange,
                onItemClick = onItemClick,
            )
            MarkAsReadMenuItem(
                text = stringResource(R.string.mark_as_read_three_days),
                conditions = MarkAsReadConditions.ThreeDays,
                onExpandedChange = onExpandedChange,
                onItemClick = onItemClick,
            )
            MarkAsReadMenuItem(
                text = stringResource(R.string.mark_as_read_one_day),
                conditions = MarkAsReadConditions.OneDay,
                onExpandedChange = onExpandedChange,
                onItemClick = onItemClick,
            )
            MarkAsReadMenuItem(
                text = stringResource(R.string.mark_all_as_read),
                conditions = MarkAsReadConditions.All,
                onExpandedChange = onExpandedChange,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun MarkAsReadMenuItem(
    text: String,
    conditions: MarkAsReadConditions,
    onExpandedChange: (Boolean) -> Unit,
    onItemClick: (MarkAsReadConditions) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            onExpandedChange(false)
            onItemClick(conditions)
        },
    )
}
