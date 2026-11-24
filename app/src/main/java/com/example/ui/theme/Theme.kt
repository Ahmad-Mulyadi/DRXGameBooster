package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.DrxThemePreset

data class DrxExtendedColors(
  val isDark: Boolean,
  val secondaryBg: Color,
  val neonCyan: Color,
  val electricBlue: Color,
  val electricBlueDeep: Color,
  val silver: Color,
  val mutedText: Color,
  val cardSurface: Color,
  val elevatedSurface: Color,
  val glassBorder: Color,
  val optimalGreen: Color,
  val warningAmber: Color,
  val alertCrimson: Color,
)

val LocalDrxColors = staticCompositionLocalOf {
  DrxExtendedColors(
    isDark = true,
    secondaryBg = GraphiteSecondary,
    neonCyan = AccentCyan,
    electricBlue = AccentBlue,
    electricBlueDeep = AccentBlueDeep,
    silver = MetallicSilver,
    mutedText = TextMuted,
    cardSurface = SurfacePrimary,
    elevatedSurface = SurfaceElevated,
    glassBorder = BorderHairline,
    optimalGreen = SuccessMint,
    warningAmber = WarningAmber,
    alertCrimson = ErrorCoral,
  )
}

@Composable
fun DrxGameTurboTheme(
  darkTheme: Boolean = true,
  themePreset: DrxThemePreset = DrxThemePreset.DRX_DEFAULT,
  customAccentArgb: Long = 0L,
  content: @Composable () -> Unit,
) {
  val customPrimary = if (customAccentArgb != 0L) Color(customAccentArgb.toInt()) else null
  val presetPrimary = customPrimary ?: Color(themePreset.primaryHex.toInt())
  val presetSecondary =
    customPrimary?.let {
      Color(
        red = (it.red * 0.75f + 0.25f).coerceIn(0f, 1f),
        green = (it.green * 0.85f + 0.15f).coerceIn(0f, 1f),
        blue = (it.blue * 0.90f + 0.10f).coerceIn(0f, 1f),
        alpha = 1f,
      )
    } ?: Color(themePreset.secondaryHex.toInt())

  val colorScheme =
    if (darkTheme) {
      val bg = Color(themePreset.bgHex.toInt())
      val card = Color(themePreset.cardHex.toInt())
      val elevated =
        when (themePreset) {
          DrxThemePreset.DRX_DEFAULT -> SurfaceElevated
          DrxThemePreset.AMOLED_BLACK -> Color(0xFF141923)
          DrxThemePreset.PURPLE_NEON -> Color(0xFF1F1938)
          DrxThemePreset.RED_INFERNO -> Color(0xFF28151B)
          DrxThemePreset.MINIMAL_DARK -> Color(0xFF1E2430)
          else -> SurfaceElevated
        }
      val secondaryBg =
        when (themePreset) {
          DrxThemePreset.DRX_DEFAULT -> GraphiteSecondary
          DrxThemePreset.AMOLED_BLACK -> Color(0xFF06080D)
          else -> card.copy(alpha = 0.88f)
        }
      darkColorScheme(
        primary = presetPrimary,
        onPrimary = Color.White,
        primaryContainer = elevated,
        onPrimaryContainer = TextMain,
        secondary = presetSecondary,
        onSecondary = bg,
        secondaryContainer = elevated,
        onSecondaryContainer = presetSecondary,
        tertiary = SuccessMint,
        background = bg,
        onBackground = TextMain,
        surface = secondaryBg,
        onSurface = TextMain,
        surfaceVariant = card,
        onSurfaceVariant = TextSecondary,
        outline = BorderHairline,
        error = ErrorCoral,
      )
    } else {
      lightColorScheme(
        primary = presetPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0ECFF),
        onPrimaryContainer = Color(0xFF072B6B),
        secondary = presetSecondary,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0F8FF),
        onSecondaryContainer = Color(0xFF0A445C),
        tertiary = Color(0xFF1F9D68),
        background = LightCyberBg,
        onBackground = TextPrimaryLight,
        surface = LightCyberSurface,
        onSurface = TextPrimaryLight,
        surfaceVariant = LightCyberCard,
        onSurfaceVariant = TextSecondaryLight,
        outline = LightCyberBorder,
        error = Color(0xFFD64545),
      )
    }

  val extended =
    if (darkTheme) {
      val card = Color(themePreset.cardHex.toInt())
      val secondaryBg =
        when (themePreset) {
          DrxThemePreset.DRX_DEFAULT -> GraphiteSecondary
          DrxThemePreset.AMOLED_BLACK -> Color(0xFF06080D)
          else -> GraphiteSecondary
        }
      val elevated =
        when (themePreset) {
          DrxThemePreset.DRX_DEFAULT -> SurfaceElevated
          DrxThemePreset.AMOLED_BLACK -> Color(0xFF141923)
          DrxThemePreset.PURPLE_NEON -> Color(0xFF1F1938)
          DrxThemePreset.RED_INFERNO -> Color(0xFF28151B)
          DrxThemePreset.MINIMAL_DARK -> Color(0xFF1E2430)
          else -> SurfaceElevated
        }
      val deepPrimary =
        Color(
          red = (presetPrimary.red * 0.70f).coerceIn(0f, 1f),
          green = (presetPrimary.green * 0.72f).coerceIn(0f, 1f),
          blue = (presetPrimary.blue * 0.85f).coerceIn(0f, 1f),
          alpha = 1f,
        )
      DrxExtendedColors(
        isDark = true,
        secondaryBg = secondaryBg,
        neonCyan = presetSecondary,
        electricBlue = presetPrimary,
        electricBlueDeep = deepPrimary,
        silver = MetallicSilver,
        mutedText = TextMuted,
        cardSurface = card,
        elevatedSurface = elevated,
        glassBorder = BorderHairline,
        optimalGreen = SuccessMint,
        warningAmber = WarningAmber,
        alertCrimson = ErrorCoral,
      )
    } else {
      DrxExtendedColors(
        isDark = false,
        secondaryBg = LightCyberCard,
        neonCyan = presetSecondary,
        electricBlue = presetPrimary,
        electricBlueDeep = Color(0xFF0E5AD6),
        silver = Color(0xFF2B3445),
        mutedText = Color(0xFF7B8596),
        cardSurface = LightCyberSurface,
        elevatedSurface = LightCyberCard,
        glassBorder = LightCyberBorder,
        optimalGreen = Color(0xFF1F9D68),
        warningAmber = Color(0xFFD18B28),
        alertCrimson = Color(0xFFD64545),
      )
    }

  CompositionLocalProvider(LocalDrxColors provides extended) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = DrxTypography,
      content = content,
    )
  }
}
