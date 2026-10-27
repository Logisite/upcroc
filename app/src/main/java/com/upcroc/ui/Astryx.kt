package com.upcroc.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Astryx token values, read from @astryxdesign/core source rather than from
 * screenshots. Every number here is traceable to a token name.
 *
 * Note on scope: Astryx is a web system, so these are a *translation* into
 * Compose Material 3, not a port. Where M3 disagrees, the token wins — an
 * input is 8dp because --radius-element is 8, not because M3's
 * OutlinedTextField happens to be 4dp.
 */
object Astryx {

    /** --radius-element: inputs, buttons, items, code blocks. */
    val RadiusElement = 8.dp

    /** --radius-container: cards, dialogs. */
    val RadiusContainer = 12.dp

    /** --radius-inner: checkbox press overlay only. */
    val RadiusInner = 4.dp

    /** --border-width. */
    val BorderWidth = 1.dp

    /** --spacing-*: a 4px grid. */
    val Spacing1 = 4.dp
    val Spacing2 = 8.dp
    val Spacing3 = 12.dp
    val Spacing4 = 16.dp

    /** --color-border: decorative hairline, deliberately below WCAG 1.4.11. */
    val BorderLight = Color(0xFF053659).copy(alpha = 0.10f)
    val BorderDark = Color(0xFFF2F4F6).copy(alpha = 0.10f)

    /** --color-border-emphasized: the form-control boundary, held to 3:1. */
    val BorderEmphasizedLight = Color(0xFFCCD3DB)
    val BorderEmphasizedDark = Color(0xFF494D53)

    /**
     * Control heights: --size-element-sm/md/lg.
     *
     * The 52dp used for fields is a deliberate deviation. 32px is a desktop-web
     * control height; Android needs a 48dp touch target. Radius, border and
     * type are unchanged so the proportion survives the platform change.
     */
    val SizeSm = 28.dp
    val SizeMd = 32.dp
    val SizeLg = 36.dp
    val TouchField = 52.dp

    /** --ease-standard: cubic-bezier(0.24, 1, 0.4, 1). */
    val Standard = CubicBezierEasing(0.24f, 1f, 0.4f, 1f)

    /** --duration-fast / medium / slow. */
    const val DurationFast = 175
    const val DurationMedium = 410
}

/**
 * Log level colours, taken from the status + palette tokens.
 *
 * Status *message* colours in Astryx are a tinted background plus a palette
 * text colour, not the status colour itself — so these are the palette text
 * steps: red, orange, blue, then secondary for debug.
 *
 * Colour is never the only signal. Astryx requires a visible label beside a
 * StatusDot for WCAG 1.4.1, so every use of these must still print the level
 * word.
 */
object LogPalette {
    val ErrorLight = Color(0xFFE3193B)
    val ErrorDark = Color(0xFFF5394F)
    val WarningLight = Color(0xFFE9AF08)
    val WarningDark = Color(0xFFF2C00B)
    val InfoLight = Color(0xFF0064E0)
    val InfoDark = Color(0xFF2694FE)
    val MutedLight = Color(0xFF4E606F)
    val MutedDark = Color(0xFFAAAFB5)
}