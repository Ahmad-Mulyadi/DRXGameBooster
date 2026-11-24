package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val InterFamily =
  FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
  )

val ManropeFamily =
  FontFamily(
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
  )

object DrxTypographyTokens {
  // Caption (11-12sp Regular) for release dates, package/version info, footnotes
  val caption =
    TextStyle(
      fontFamily = InterFamily,
      fontWeight = FontWeight.Normal,
      fontSize = 11.5.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.2.sp,
      fontFeatureSettings = "tnum",
    )

  // Compact hero/card statistic number
  val statNumberCompact =
    TextStyle(
      fontFamily = ManropeFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      lineHeight = 26.sp,
      letterSpacing = (-0.3).sp,
      fontFeatureSettings = "tnum",
    )

  // Slider / telemetry inline numeric readout
  val numericReadout =
    TextStyle(
      fontFamily = InterFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      lineHeight = 18.sp,
      letterSpacing = (-0.1).sp,
      fontFeatureSettings = "tnum",
    )

  // Greeting / top context label
  val greeting =
    TextStyle(
      fontFamily = InterFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 13.sp,
      lineHeight = 18.sp,
      letterSpacing = 0.sp,
    )

  // Tooltip / chart hover label
  val tooltip =
    TextStyle(
      fontFamily = InterFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.sp,
      fontFeatureSettings = "tnum",
    )
}

val DrxTypography =
  Typography(
    // Hero Title (28-32sp Bold)
    displayLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.5).sp,
      ),
    // Statistics Number (26-34sp Bold with tabular figures)
    displaySmall =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum",
      ),
    // Page Title (24-28sp SemiBold)
    headlineLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.35).sp,
      ),
    // Prominent Sub-Page / Modal Title (22sp SemiBold)
    headlineMedium =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.25).sp,
      ),
    // Compact Stat Number (26sp Bold with tabular figures)
    headlineSmall =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum",
      ),
    // Section Title (18-20sp SemiBold)
    titleLarge =
      TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.5.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.15).sp,
      ),
    // Card Title (15-17sp Medium)
    titleMedium =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
      ),
    // Control / Setting / Item Title (14-15sp Medium)
    titleSmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    // Body Text (14-15sp Regular)
    bodyLarge =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
      ),
    // Body / Secondary Content (14sp Regular)
    bodyMedium =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    // Description (12-13sp Regular, ~1.44x line height)
    bodySmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
    // Button Label (14-15sp SemiBold)
    labelLarge =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
      ),
    // Navigation Label & Stat Label (10-12sp Medium)
    labelMedium =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum",
      ),
    // Small Badge (10-11sp SemiBold)
    labelSmall =
      TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
      ),
  )
