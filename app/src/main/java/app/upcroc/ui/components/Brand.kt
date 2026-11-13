package app.upcroc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.theme.UpType

/**
 * Draws the Upcroc mark in this [DrawScope]'s box. The 512-unit design is scaled to
 * the smaller dimension and normalised so its true visual bounds are centred in that
 * square, leaving equal clear space on all four sides. Callers can therefore centre
 * the square anywhere without offset maths.
 */
fun DrawScope.drawCrescentMark(
    tint: Color,
    ballColor: Color,
    ballOffset: Offset = Offset.Zero,
) {
    val s = size.minDimension / 512f
    // True bezier bounds are x 104..438, y 133.97..382.03, so the visual centre is
    // (271, 258). Move it onto the canvas centre (256, 256).
    val dx = (256f - 271f) * s
    val dy = (256f - 258f) * s
    val path = Path().apply {
        moveTo(438f * s, 146f * s)
        cubicTo(338f * s, 108f * s, 104f * s, 164f * s, 104f * s, 258f * s)
        cubicTo(104f * s, 352f * s, 300f * s, 408f * s, 378f * s, 370f * s)
        cubicTo(230f * s, 330f * s, 214f * s, 182f * s, 438f * s, 146f * s)
        close()
    }
    translate(left = dx, top = dy) {
        drawPath(path, tint)
        drawCircle(
            color = ballColor,
            radius = 21f * s,
            center = Offset(382f * s + ballOffset.x, 256f * s + ballOffset.y),
        )
    }
}

/**
 * The mark is one closed bezier: a deep crescent whose bite holds the blue
 * ball. The ball is the first "c" in upcroc (up ◗ roc), never a letterform.
 */
@Composable
fun CrescentMark(
    modifier: Modifier = Modifier,
    tint: Color = Upcroc.Text,
    ballColor: Color = Upcroc.Blue,
    showBall: Boolean = true,
    ballOffsetPx: Offset = Offset.Zero,
) {
    Canvas(modifier) {
        drawCrescentMark(
            tint = tint,
            ballColor = if (showBall) ballColor else Color.Transparent,
            ballOffset = ballOffsetPx,
        )
    }
}

/** Square app tile used in onboarding and the about block. */
@Composable
fun CrescentTile(
    size: Dp = 92.dp,
    modifier: Modifier = Modifier,
    background: Color = Upcroc.SurfaceHigh,
    corner: Dp = 26.dp,
    markScale: Float = 0.86f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        CrescentMark(
            modifier = Modifier
                .size(size * 0.74f)
                .graphicsLayer {
                    scaleX = markScale
                    scaleY = markScale
                },
        )
    }
}

/** Static final lockup. The splash animates into exactly this. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, markSize: Dp = 64.dp, wordSize: Float = 40f) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AppText(
            "up",
            style = UpType.heroBig.copy(
                fontSize = wordSize.sp,
                letterSpacing = (-2.6f).sp,
                lineHeight = wordSize.sp,
            ),
            color = Upcroc.Text,
        )
        Spacer(Modifier.width(27.dp))
        CrescentMark(Modifier.size(markSize))
        Spacer(Modifier.width(27.dp))
        AppText(
            "roc",
            style = UpType.heroBig.copy(
                fontSize = wordSize.sp,
                letterSpacing = (-2.6f).sp,
                lineHeight = wordSize.sp,
            ),
            color = Upcroc.Blue,
        )
    }
}
