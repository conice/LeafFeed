package me.ash.reader.ui.component.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedYAxisTransitionSlow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockRadioButton(
    modifier: Modifier = Modifier,
    selected: Int = 0,
    onSelected: (Int) -> Unit,
    itemRadioGroups: List<BlockRadioGroupButtonItem> = listOf(),
) {
    val motionScheme = MaterialTheme.motionScheme

    Column {
        SingleChoiceSegmentedButtonRow(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            itemRadioGroups.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = {
                        onSelected(index)
                        item.onClick()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, itemRadioGroups.size),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(item.text)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                sharedYAxisTransitionSlow(
                    direction = if (targetState >= initialState) Direction.Forward else Direction.Backward,
                    motionScheme = motionScheme,
                )
            },
            label = "blockRadioContent",
        ) { targetIndex ->
            itemRadioGroups.getOrNull(targetIndex)?.content?.invoke()
        }
    }
}

data class BlockRadioGroupButtonItem(
    val text: String,
    val onClick: () -> Unit = {},
    val content: @Composable () -> Unit,
)
