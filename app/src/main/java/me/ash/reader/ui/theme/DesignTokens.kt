package me.ash.reader.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The shared design vocabulary for the app. Keep page-specific values out of this file;
 * values here are deliberately few so new surfaces inherit the same visual rhythm.
 */
object LayoutTokens {
    val PageHorizontalPadding = 24.dp
    val CompactHorizontalPadding = 16.dp
    val PageTopPadding = 24.dp
    val PageBottomPadding = 16.dp
    val SectionSpacing = 24.dp
    val SectionLabelVerticalPadding = 8.dp
    val SettingVerticalPadding = 14.dp
    val ActionGap = 16.dp
    val ListItemGap = 4.dp
    val ContentGap = 12.dp
    val MinimumTouchTarget = 48.dp
    val DividerThickness = 1.dp
}

/** Motion roles shared by navigation, feedback and content changes. */
object MotionTokens {
    const val FastEffectsMillis = 150
    const val FastSpatialMillis = 200
    const val DefaultSpatialMillis = 300
    const val SlowSpatialMillis = 450
    const val DefaultFadeOutMillis = 75
    const val IndeterminateCycleMillis = 800
    const val PressedScale = 0.98f
    const val ContentOffsetFactor = 0.10f
}

/** Shapes are intentionally restrained for a reading-first product. */
object ShapeTokens {
    val Control = Shapes.small
    val Surface = Shapes.medium
    val Sheet = Shapes.large
    val Pill = Shapes.extraLarge
}
