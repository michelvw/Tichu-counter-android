package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.example.domain.ThemeMode

private val LightColorScheme = lightColorScheme(
  primary = LightBrand,
  onPrimary = LightOnBrand,
  primaryContainer = LightSurface2,
  onPrimaryContainer = LightBrandStrong,
  secondary = LightTeamB,
  onSecondary = LightOnBrand,
  secondaryContainer = LightSurface2,
  onSecondaryContainer = LightText,
  tertiary = LightTeamA,
  onTertiary = LightOnBrand,
  background = LightBg,
  onBackground = LightText,
  surface = LightSurface,
  onSurface = LightText,
  surfaceVariant = LightSurface2,
  onSurfaceVariant = LightTextMuted,
  outline = LightBorder,
  outlineVariant = LightBorderStrong,
  error = LightDanger,
  onError = LightOnBrand,
)

private val DarkColorScheme = darkColorScheme(
  primary = DarkBrand,
  onPrimary = DarkOnBrand,
  primaryContainer = DarkSurface2,
  onPrimaryContainer = DarkBrandStrong,
  secondary = DarkTeamB,
  onSecondary = DarkOnBrand,
  secondaryContainer = DarkSurface2,
  onSecondaryContainer = DarkText,
  tertiary = DarkTeamA,
  onTertiary = DarkOnBrand,
  background = DarkBg,
  onBackground = DarkText,
  surface = DarkSurface,
  onSurface = DarkText,
  surfaceVariant = DarkSurface2,
  onSurfaceVariant = DarkTextMuted,
  outline = DarkBorder,
  outlineVariant = DarkBorderStrong,
  error = DarkDanger,
  onError = DarkOnBrand,
)

private val TichuShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
)

val LocalTichuColors = staticCompositionLocalOf { LightTichuColors }

object TichuTheme {
  val colors: TichuSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalTichuColors.current
}

@Composable
fun TichuCounterTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) DarkTichuColors else LightTichuColors

  CompositionLocalProvider(LocalTichuColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = TichuShapes,
      content = content,
    )
  }
}
