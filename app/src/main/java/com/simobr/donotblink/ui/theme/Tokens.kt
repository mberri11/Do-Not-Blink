package com.simobr.donotblink.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.R

/**
 * FROZEN design tokens. Read them; do not add to them, change them, round them, or adjust them
 * for visual balance. A value that looks wrong is a conversation, not an edit.
 *
 * TokensResolutionTest asserts every literal in this file.
 */
object DnbColor {
    val Black = Color(0xFF000000)
    val Phosphor = Color(0xFFFFB000)
    val Hot = Color(0xFFFFD97A)
    val Dim = Color(0xFF3A2A00)
    val Glint = Color(0xFFFFFFFF)
    val Hairline = Color(0xFF2A1F00)
    val Ink = Color(0xFF241A00)
    val Rule = Color(0xFF1A1300)
    val LabelMid = Color(0xFF8A6A1A)
}

object DnbDim {
    val ringStartRadius = 168.dp
    val ringTargetRadius = 96.dp
    val ringCentreYFromTop = 430.dp
    val ctaWidth = 300.dp
    val ctaHeight = 58.dp
    val ctaInsetX = 45.dp
    val screenPadH = 26.dp
    val settingsRowHeight = 62.dp
    val titleRowHeight = 30.dp
    val homeIndicatorW = 108.dp
    val homeIndicatorH = 3.dp
}

/**
 * JetBrains Mono, bundled as two resources. Weights 400 and 500 only — never a synthetic weight,
 * never a downloadable font. The app must render identically with the radio off.
 */
object DnbFont {
    val JetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.W400),
        Font(R.font.jetbrains_mono_medium, FontWeight.W500),
    )
}

object DnbType {
    val wordmark = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 19.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.34.em,
        color = DnbColor.Phosphor,
    )

    val bestNumeral = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 132.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = (-0.01).em,
        color = DnbColor.Glint,
    )

    val failNumeral = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 92.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.em,
        color = DnbColor.Glint,
    )

    val continueNumeral = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 46.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.em,
        color = DnbColor.Glint,
    )

    val roundNumeral = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 96.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.em,
        color = DnbColor.Phosphor,
    )

    val streakLive = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 26.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.10.em,
        color = DnbColor.Phosphor,
    )

    val streakPerfect = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 32.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.10.em,
        color = DnbColor.Glint,
    )

    val ctaLarge = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 14.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.34.em,
        color = DnbColor.Hot,
    )

    val ctaSmall = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.32.em,
        color = DnbColor.Hot,
    )

    val sectionTitle = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.40.em,
        color = DnbColor.Phosphor,
    )

    val settingsRow = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
        letterSpacing = 0.24.em,
        color = DnbColor.Phosphor,
    )

    val titleRowName = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.22.em,
        color = DnbColor.Phosphor,
    )

    val microLabel = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.W400,
        letterSpacing = 0.45.em,
        color = DnbColor.Dim,
    )

    val failLine = TextStyle(
        fontFamily = DnbFont.JetBrainsMono,
        fontSize = 17.sp,
        fontWeight = FontWeight.W400,
        letterSpacing = 0.14.em,
        color = DnbColor.Phosphor,
    )
}
