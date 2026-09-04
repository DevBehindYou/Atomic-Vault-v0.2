package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AtomicFontSize {
    val title = 24.sp
    val heading = 17.sp
    val body = 16.sp
    val label = 14.sp
    val caption = 13.sp
    val micro = 12.sp
}

object AtomicFontWeight {
    val regular = FontWeight.W400
    val medium = FontWeight.W600
    val bold = FontWeight.W700
}

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.bold,
        fontSize = AtomicFontSize.title,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.bold,
        fontSize = AtomicFontSize.title,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.medium,
        fontSize = AtomicFontSize.heading,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.regular,
        fontSize = AtomicFontSize.body,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.regular,
        fontSize = AtomicFontSize.label,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.regular,
        fontSize = AtomicFontSize.caption,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.medium,
        fontSize = AtomicFontSize.label,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.medium,
        fontSize = AtomicFontSize.micro,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = AtomicFontWeight.regular,
        fontSize = AtomicFontSize.micro,
        lineHeight = 14.sp
    )
)
