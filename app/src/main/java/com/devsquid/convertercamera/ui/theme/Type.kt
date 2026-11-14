package com.devsquid.convertercamera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.devsquid.convertercamera.R

/** IBM Plex Sans, bundled in res/font (SIL Open Font License 1.1). */
val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private val DefaultTypography = Typography()

/** Material 3 defaults with every text style switched to [IbmPlexSans]. */
val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = IbmPlexSans),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = IbmPlexSans),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = IbmPlexSans),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = IbmPlexSans),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = IbmPlexSans),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = IbmPlexSans),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = IbmPlexSans),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = IbmPlexSans),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = IbmPlexSans),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = IbmPlexSans),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = IbmPlexSans),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = IbmPlexSans),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = IbmPlexSans),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = IbmPlexSans),
)
