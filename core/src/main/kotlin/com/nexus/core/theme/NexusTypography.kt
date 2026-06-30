package com.nexus.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nexus.core.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val InterFont = GoogleFont("Inter")

val NexusFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Light)
)

@Immutable
data class NexusTypography(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val buttonLabel: TextStyle
)

val LocalNexusTypography = staticCompositionLocalOf {
    NexusTypography(
        display = TextStyle.Default,
        h1 = TextStyle.Default,
        h2 = TextStyle.Default,
        title = TextStyle.Default,
        body = TextStyle.Default,
        label = TextStyle.Default,
        caption = TextStyle.Default,
        buttonLabel = TextStyle.Default
    )
}

val defaultNexusTypography = NexusTypography(
    display = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-1).sp,
        lineHeight = 44.sp
    ),
    h1 = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 38.sp
    ),
    h2 = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.sp,
        lineHeight = 28.sp
    ),
    title = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp
    ),
    body = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        lineHeight = 22.sp
    ),
    label = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp
    ),
    caption = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 18.sp
    ),
    buttonLabel = TextStyle(
        fontFamily = NexusFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 20.sp
    )
)
