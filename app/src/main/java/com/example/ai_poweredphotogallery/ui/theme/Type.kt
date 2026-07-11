package com.example.ai_poweredphotogallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Use the platform font throughout the app. Generic Cursive selects a Latin handwriting
// face and falls back to a different font for Chinese, which creates inconsistent text.
private val BaseTypography = Typography()
private val AppFontFamily = FontFamily.Default

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = AppFontFamily),
)