package com.conice.morss.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.conice.morss.infrastructure.preference.LocalBasicFonts
import com.conice.morss.infrastructure.preference.LocalThemeIndex
import com.conice.morss.infrastructure.android.findActivity
import com.conice.morss.ui.theme.palette.LocalTonalPalettes
import com.conice.morss.ui.theme.palette.TonalPalettes
import com.conice.morss.ui.theme.palette.core.ProvideZcamViewingConditions
import com.conice.morss.ui.theme.palette.dynamic.extractTonalPalettesFromUserWallpaper
import com.conice.morss.ui.theme.palette.dynamicDarkColorScheme
import com.conice.morss.ui.theme.palette.dynamicLightColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    useDarkTheme: Boolean,
    wallpaperPalettes: List<TonalPalettes> = extractTonalPalettesFromUserWallpaper(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current

    LaunchedEffect(view, useDarkTheme) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDarkTheme
            isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    val themeIndex = LocalThemeIndex.current

    val tonalPalettes =
        wallpaperPalettes[
            if (themeIndex >= wallpaperPalettes.size) {
                when {
                    wallpaperPalettes.size == 5 -> 0
                    wallpaperPalettes.size > 5 -> 5
                    else -> 0
                }
            } else {
                themeIndex
            }]

    ProvideZcamViewingConditions {
        CompositionLocalProvider(
            LocalTonalPalettes provides tonalPalettes.apply { Preparing() },
            LocalTextStyle provides LocalTextStyle.current.applyTextDirection(),
        ) {
            val lightColors = dynamicLightColorScheme()
            val darkColors = dynamicDarkColorScheme()
            MaterialTheme(
                // Reading is a sustained-attention task. Use the predictable standard scheme as
                // the app-wide baseline and reserve expressive motion for explicit focal moments.
                motionScheme = MotionScheme.standard(),
                colorScheme = if (useDarkTheme) darkColors else lightColors,
                typography =
                    LocalBasicFonts.current
                        .asTypography(LocalContext.current)
                        .applyTextDirection(),
                shapes = Shapes,
                content = content,
            )
        }
    }
}
