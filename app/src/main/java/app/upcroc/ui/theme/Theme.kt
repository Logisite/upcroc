package app.upcroc.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.upcroc.R

/**
 * The whole palette is taken from prototype/shared.css. True black, solid
 * elevated surfaces, one blue. No gradients are part of the visual language.
 */
val LocalAppFontScale = staticCompositionLocalOf { 1f }
val LocalBaseFontScale = staticCompositionLocalOf { 1f }

object Upcroc {
    val Blue = Color(0xFF4F8DFD)
    val BlueSoft = Color(0xFF10203D)
    val BlueDeep = Color(0xFF1D4ED8)
    val Purple = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Orange = Color(0xFFF59E0B)
    val Green = Color(0xFF22C55E)

    val Ink = Color(0xFF000000)
    val Surface = Color(0xFF111111)
    val SurfaceHigh = Color(0xFF181818)
    val SurfaceHighest = Color(0xFF222222)
    val Card = Color(0xFF141414)
    val CardSoft = Color(0xFF151515)
    val Hero = Color(0xFF1A1A1A)
    val Account = Color(0xFF161616)
    val Field = Color(0xFF1F1F1F)
    val Notify = Color(0xFF1E1E1E)

    val Line = Color(0xFF2C2C2C)
    val LineSoft = Color(0xFF202020)

    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFA3A3A3)
    val Dim = Color(0xFF7F7F7F)
    val Faint = Color(0xFF4A4A4A)

    val Warn = Color(0xFFD9A441)
    val Error = Color(0xFFE06C6C)

    val RailBlue = Color(0xFF2F6BD9)
    val RailPurple = Color(0xFF6D4BC7)
    val RailOrange = Color(0xFFB8741A)

    val NavGlass = Color(0xA8484848)
    val NavIdle = Color(0xFF9A9A9A)

    val BannerWarnBg = Color(0xFF221A0C)
    val BannerWarn = Color(0xFFE8B65A)
    val BannerWarnSub = Color(0xFFB99860)
    val BannerDangerBg = Color(0xFF241012)
    val BannerDanger = Color(0xFFE58A8A)
    val BannerDangerSub = Color(0xFFB87C7C)
    val BannerOkBg = Color(0xFF0F2014)
    val BannerOk = Color(0xFF63C888)
    val BannerOkSub = Color(0xFF6E9A7C)

    val StatusBg = Color(0xFF10241A)
    val StatusText = Color(0xFF6CCF8E)

    val LogCheck = Blue
    val LogAlert = Color(0xFF5BC982)
    val LogWarn = Color(0xFFE8B65A)
    val LogError = Color(0xFFE58A8A)
    val LogInfo = Color(0xFF9BB8E8)
}

val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.W200),
    Font(R.font.manrope, FontWeight.W300),
    Font(R.font.manrope, FontWeight.W400),
    Font(R.font.manrope, FontWeight.W500),
    Font(R.font.manrope, FontWeight.W600),
    Font(R.font.manrope, FontWeight.W700),
    Font(R.font.manrope, FontWeight.W800),
)

private fun style(
    size: Float,
    weight: FontWeight = FontWeight.W500,
    tracking: Float = 0f,
    lineHeight: Float? = null,
): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = (size + 3f).sp,
    letterSpacing = tracking.sp,
    lineHeight = ((lineHeight ?: (size * 1.35f)) + 3f).sp,
)

/**
 * Type ramp. The browser mock was drawn at 372 px wide; every size here is
 * nudged 1–2 sp so it keeps the same optical weight on a real 400+ dp phone.
 */
object UpType {
    val screenTitle = style(19f, FontWeight.W800, -0.4f, 23f)
    val pageKicker = style(10f, FontWeight.W700, 0.7f, 13f)
    val section = style(16f, FontWeight.W800, -0.25f, 20f)
    val cardTitle = style(16f, FontWeight.W800, -0.3f, 20f)
    val groupTitle = style(13.5f, FontWeight.W800, -0.15f, 17f)
    val rowTitle = style(12.5f, FontWeight.W700, -0.1f, 16f)
    val body = style(11.5f, FontWeight.W500, 0f, 18f)
    val bodyStrong = style(11.5f, FontWeight.W700, 0f, 17f)
    val muted = style(11f, FontWeight.W500, 0f, 16f)
    val caption = style(10.5f, FontWeight.W600, 0f, 14f)
    val small = style(9.8f, FontWeight.W600, 0f, 13f)
    val tiny = style(9.2f, FontWeight.W600, 0f, 12f)
    val label = style(10f, FontWeight.W700, 0.8f, 13f)
    val button = style(12f, FontWeight.W800, 0f, 16f)
    val link = style(11.5f, FontWeight.W700, 0f, 15f)
    val heroBig = style(38f, FontWeight.W800, -1.4f, 39f)
    val donutBig = style(27f, FontWeight.W800, -1f, 30f)
    val statBig = style(18f, FontWeight.W800, -0.5f, 21f)
    val statusChip = style(10f, FontWeight.W700, 0.2f, 13f)
    val mono = style(10.5f, FontWeight.W500, 0f, 16f).copy(fontFamily = FontFamily.Monospace)
}

@Composable
fun UpcrocTheme(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Upcroc.Ink)) {
        content()
    }
}
