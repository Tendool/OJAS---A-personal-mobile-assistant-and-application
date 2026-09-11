package com.ojas.assistant.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Ojas is a night-sky app, so there is exactly one scheme. Light mode would fight the
 * galaxy behind every screen, and dynamic colour would pull it away from the reference.
 */
private val OjasColors = darkColorScheme(
    primary = NebulaBlue,
    onPrimary = SpaceVoid,
    primaryContainer = NebulaBlueDeep,
    onPrimaryContainer = Starlight,

    secondary = EmberOrange,
    onSecondary = SpaceVoid,
    secondaryContainer = Color(0xFF3A2A1C),
    onSecondaryContainer = SolarAmber,

    tertiary = VioletDrift,
    onTertiary = SpaceVoid,
    tertiaryContainer = Color(0xFF2A2444),
    onTertiaryContainer = VioletDrift,

    background = SpaceVoid,
    onBackground = Starlight,
    surface = SpaceSurface,
    onSurface = Starlight,
    surfaceVariant = SpaceSurfaceHigh,
    onSurfaceVariant = StarlightDim,
    surfaceContainer = SpaceSurface,
    surfaceContainerHigh = SpaceSurfaceHigh,
    surfaceContainerHighest = Color(0xFF172034),

    outline = SpaceOutline,
    outlineVariant = Color(0xFF18213A),

    error = PulsarRose,
    onError = SpaceVoid,

    scrim = Color(0xCC02040A)
)

private val Display = FontFamily.SansSerif

/**
 * Wide tracking on headings and small caps for labels reads as instrument panel rather
 * than document, which is the register the rest of the interface is written in.
 */
private val OjasTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Light,
        fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 19.sp, letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, letterSpacing = 0.6.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 1.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 1.6.sp, textAlign = TextAlign.Start
    )
)

@Composable
fun OjasTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    SideEffect {
        // The galaxy runs edge to edge behind the system bars, so their icons are always
        // drawn light regardless of what the host activity was themed with.
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = OjasColors,
        typography = OjasTypography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
