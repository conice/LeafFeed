package me.ash.reader.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.domain.model.article.RuleType

@Composable
fun RuleTypeSelectionDialog(
    visible: Boolean,
    title: String,
    selected: Set<RuleType>,
    onSelectedChange: (Set<RuleType>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                RuleType.entries.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = type in selected,
                            onCheckedChange = {
                                onSelectedChange(if (it) selected + type else selected - type)
                            },
                        )
                        Text(stringResource(if (type == RuleType.FILTER) R.string.filter_rules else R.string.highlight_rules))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
