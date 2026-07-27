package me.ash.reader.ui.page.settings.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.infrastructure.preference.NavigationCustomization

@Composable
internal fun NavigationNumericSlider(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "dp",
    onChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("$title: ${sliderValue.toInt()}$suffix", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
internal fun AutomaticNavigationNumericSlider(
    title: String,
    value: Int,
    automaticValue: Int,
    range: IntRange,
    suffix: String = "dp",
    onChange: (Int) -> Unit,
) {
    val effectiveValue = value.takeIf { it > 0 } ?: automaticValue
    var sliderValue by remember(effectiveValue) { mutableFloatStateOf(effectiveValue.toFloat()) }
    var isAutomatic by remember(value) {
        mutableStateOf(value == NavigationCustomization.AUTOMATIC_BOTTOM_HEIGHT)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            if (isAutomatic) "$title: Auto (${effectiveValue}$suffix)"
            else "$title: ${sliderValue.toInt()}$suffix",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                isAutomatic = false
            },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}
