package com.upcroc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Astryx as a theme, not as a wrapper around Material 3.
 *
 * M3's defaults are what make a Compose app look generic: 4dp input radii,
 * a 12/14/16/20/24/28 type ramp, and a dynamic colour scheme that hands you
 * whatever the wallpaper says. Astryx overrides all three, so the colour and
 * type schemes here are built from the tokens rather than inherited.
 *
 * Every colour below is a token value read from @astryxdesign/core. No token in
 * Astryx has a single colour — they are all light-dark() pairs — so there are
 * two schemes rather than one, switched by isSystemInDarkTheme().
 */

private val LightTextPrimary = Color(0xFF0A1317)
private val LightTextSecondary = Color(0xFF4E606F)
private val LightBody = Color(0xFFF1F4F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightAccent = Color(0xFF0064E0)
private val LightBorderEmph = Color(0xFFCCD3DB)

private val DarkTextPrimary = Color(0xFFDFE2E5)
private val DarkTextSecondary = Color(0xFFAAAFB5)
private val DarkBody = Color(0xFF111112)
private val DarkSurface = Color(0xFF1F1F22)
private val DarkAccent = Color(0xFF2694FE)
private val DarkBorderEmph = Color(0xFF494D53)

/** --color-border is a 10% tint and deliberately fails WCAG 1.4.11. */
val BorderLightToken = Color(0xFF053659).copy(alpha = 0.10f)
val BorderDarkToken = Color(0xFFF2F4F6).copy(alpha = 0.10f)

/**
 * Status colours have no Material 3 equivalent: M3 offers error but nothing for
 * warning or success, and borrowing tertiary/secondary for them is wrong
 * semantically. These are carried explicitly so the log viewer can use them.
 */
data class AstryxStatus(val warning: Color, val success: Color, val info: Color)

private val LightStatus = AstryxStatus(
    warning = Color(0xFFE9AF08),
    success = Color(0xFF0D8626),
    info = LightAccent,
)

private val DarkStatus = AstryxStatus(
    warning = Color(0xFFF2C00B),
    success = Color(0xFF0D8626),
    info = DarkAccent,
)

val LocalAstryxStatus = androidx.compose.runtime.staticCompositionLocalOf { LightStatus }

private val LightScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    secondary = LightTextSecondary,
    background = LightBody,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFEAEEF2),
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorderEmph,
    outlineVariant = BorderLightToken,
    error = Color(0xFFE3193B),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF00243F),
    secondary = DarkTextSecondary,
    background = DarkBody,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorderEmph,
    outlineVariant = BorderDarkToken,
    error = Color(0xFFF5394F),
    onError = Color(0xFF690005),
)

/**
 * Astryx's type scale is geometric from a 14px base — round(14 x 1.2^step) —
 * which is a different shape entirely from M3's 12/14/16/20/24/28 ramp. The M3
 * slots are filled with Astryx values rather than the other way round, so any
 * existing composable picks up the right size without being rewritten.
 *
 * Caveat: Roboto ships no SemiBold, so the w600 steps synthesise or fall back
 * to Medium. Headings will read lighter than the reference until a SemiBold
 * face is bundled.
 */
private fun sp(v: Int) = v.sp

private val AstryxType = Typography(
    headlineSmall = TextStyle(fontSize = sp(24), lineHeight = sp(32), fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = sp(20), lineHeight = sp(28), fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = sp(17), lineHeight = sp(24), fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = sp(14), lineHeight = sp(20), fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = sp(14), lineHeight = sp(20), fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = sp(14), lineHeight = sp(20), fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = sp(12), lineHeight = sp(20), fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = sp(14), lineHeight = sp(20), fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = sp(12), lineHeight = sp(20), fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = sp(10), lineHeight = sp(16), fontWeight = FontWeight.Medium),
)

val AstryxCode = TextStyle(
    fontSize = sp(14),
    lineHeight = sp(20),
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
)

/** --radius-element / --radius-container, applied through M3's shape slots. */
private val AstryxShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(Astryx.RadiusInner),
    small = RoundedCornerShape(Astryx.RadiusElement),
    medium = RoundedCornerShape(Astryx.RadiusElement),
    large = RoundedCornerShape(Astryx.RadiusContainer),
    extraLarge = RoundedCornerShape(Astryx.RadiusContainer),
)

@Composable
fun AstryxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAstryxStatus provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AstryxType,
            shapes = AstryxShapes,
            content = content,
        )
    }
}