package app.upcroc.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.upcroc.ui.theme.Upcroc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The icon set is redrawn from prototype/icons.js. Every glyph lives on the
 * same 24-unit grid and uses the same 1.6 stroke, so they feel like one family
 * instead of a stock icon pack.
 */
enum class UiIcon {
    Radar, Search, Settings, Bell, Signal, Plus, Pause, Play, Check, Checkmark,
    Health, Bookmark, BookmarkFilled, Chevron, Back, Close, Edit, Refresh, Warn, Error, Info,
    Key, Shield, Lock, Battery, Moon, Sound, Logs, Download, External, Trash,
    Copy, Share, Filter, Offline, Clock, Layers, Logout, User, Bolt, Star,
    Calendar, Globe, Cursor,
}

@Composable
fun IconGlyph(
    icon: UiIcon,
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Upcroc.Muted,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier) {
        drawToolIcon(icon, tint, strokeWidth.toPx())
    }
}

private fun DrawScope.line(tint: Color, w: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    val k = size.minDimension / 24f
    drawLine(tint, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), w, StrokeCap.Round)
}

private fun DrawScope.circle(tint: Color, w: Float, cx: Float, cy: Float, r: Float) {
    val k = size.minDimension / 24f
    drawCircle(tint, r * k, Offset(cx * k, cy * k), style = Stroke(w, cap = StrokeCap.Round))
}

private fun DrawScope.dot(tint: Color, cx: Float, cy: Float, r: Float) {
    val k = size.minDimension / 24f
    drawCircle(tint, r * k, Offset(cx * k, cy * k))
}

private fun DrawScope.arc(
    tint: Color,
    w: Float,
    cx: Float,
    cy: Float,
    r: Float,
    start: Float,
    sweep: Float,
) {
    val k = size.minDimension / 24f
    drawArc(
        color = tint,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset((cx - r) * k, (cy - r) * k),
        size = Size(r * 2f * k, r * 2f * k),
        style = Stroke(w, cap = StrokeCap.Round),
    )
}

private fun DrawScope.roundRect(
    tint: Color,
    w: Float,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    r: Float,
    fill: Boolean = false,
) {
    val k = size.minDimension / 24f
    drawRoundRect(
        color = tint,
        topLeft = Offset(x * k, y * k),
        size = Size(width * k, height * k),
        cornerRadius = CornerRadius(r * k),
        style = if (fill) Fill else Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private inline fun DrawScope.strokePath(tint: Color, w: Float, block: Path.(Float) -> Unit) {
    val k = size.minDimension / 24f
    val path = Path().apply { block(k) }
    drawPath(path, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private inline fun DrawScope.fillPath(tint: Color, evenOdd: Boolean = false, block: Path.(Float) -> Unit) {
    val k = size.minDimension / 24f
    val path = Path().apply {
        fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        block(k)
    }
    drawPath(path, tint, style = Fill)
}

private fun DrawScope.drawToolIcon(icon: UiIcon, tint: Color, w: Float) {
    when (icon) {
        UiIcon.Radar -> {
            circle(tint, w, 12f, 12f, 8f)
            circle(tint, w, 12f, 12f, 4.2f)
            dot(tint, 12f, 12f, 1.3f)
            line(tint, w, 12f, 4f, 12f, 2f)
            line(tint, w, 12f, 22f, 12f, 20f)
            line(tint, w, 4f, 12f, 2f, 12f)
            line(tint, w, 22f, 12f, 20f, 12f)
        }
        UiIcon.Search -> {
            circle(tint, w, 10.8f, 10.8f, 6.3f)
            line(tint, w, 15.5f, 15.5f, 21f, 21f)
        }
        UiIcon.Settings -> {
            line(tint, w, 4f, 7f, 11f, 7f)
            line(tint, w, 15f, 7f, 20f, 7f)
            circle(tint, w, 13f, 7f, 2f)
            line(tint, w, 4f, 12f, 7f, 12f)
            line(tint, w, 11f, 12f, 20f, 12f)
            circle(tint, w, 9f, 12f, 2f)
            line(tint, w, 4f, 17f, 12f, 17f)
            line(tint, w, 16f, 17f, 20f, 17f)
            circle(tint, w, 14f, 17f, 2f)
        }
        UiIcon.Bell -> {
            strokePath(tint, w) { k ->
                moveTo(6f * k, 9f * k)
                cubicTo(6f * k, 5.7f * k, 8.7f * k, 3f * k, 12f * k, 3f * k)
                cubicTo(15.3f * k, 3f * k, 18f * k, 5.7f * k, 18f * k, 9f * k)
                cubicTo(18f * k, 14f * k, 20f * k, 15f * k, 20f * k, 16f * k)
                lineTo(4f * k, 16f * k)
                cubicTo(4f * k, 15f * k, 6f * k, 14f * k, 6f * k, 9f * k)
                close()
            }
            line(tint, w, 10f, 20f, 14f, 20f)
        }
        UiIcon.Signal -> {
            strokePath(tint, w) { k ->
                moveTo(4f * k, 12f * k)
                lineTo(7f * k, 12f * k)
                lineTo(9f * k, 7f * k)
                lineTo(12f * k, 17f * k)
                lineTo(14f * k, 12f * k)
                lineTo(20f * k, 12f * k)
            }
        }
        UiIcon.Plus -> {
            line(tint, w, 12f, 5f, 12f, 19f)
            line(tint, w, 5f, 12f, 19f, 12f)
        }
        UiIcon.Pause -> {
            roundRect(tint, w, 7f, 5f, 3.4f, 14f, 1.2f, fill = true)
            roundRect(tint, w, 13.6f, 5f, 3.4f, 14f, 1.2f, fill = true)
        }
        UiIcon.Play -> {
            fillPath(tint) { k ->
                moveTo(8f * k, 5f * k)
                lineTo(19f * k, 12f * k)
                lineTo(8f * k, 19f * k)
                close()
            }
        }
        UiIcon.Check, UiIcon.Refresh -> {
            arc(tint, w, 12f, 12f, 8f, 25f, 295f)
            strokePath(tint, w) { k ->
                moveTo(20f * k, 4f * k)
                lineTo(20f * k, 11f * k)
                lineTo(13f * k, 11f * k)
            }
        }
        UiIcon.Checkmark -> {
            strokePath(tint, w) { k ->
                moveTo(5f * k, 12.5f * k)
                lineTo(9.5f * k, 17f * k)
                lineTo(19f * k, 7f * k)
            }
        }
        UiIcon.Health -> {
            strokePath(tint, w) { k ->
                moveTo(3f * k, 12f * k)
                lineTo(7f * k, 12f * k)
                lineTo(9.5f * k, 6f * k)
                lineTo(13.5f * k, 18f * k)
                lineTo(16f * k, 12f * k)
                lineTo(21f * k, 12f * k)
            }
        }
        UiIcon.Bookmark -> {
            strokePath(tint, w) { k ->
                moveTo(7f * k, 4f * k)
                lineTo(17f * k, 4f * k)
                lineTo(17f * k, 20f * k)
                lineTo(12f * k, 16.8f * k)
                lineTo(7f * k, 20f * k)
                close()
            }
        }
        UiIcon.BookmarkFilled -> {
            fillPath(tint) { k ->
                moveTo(7f * k, 4f * k)
                lineTo(17f * k, 4f * k)
                lineTo(17f * k, 20f * k)
                lineTo(12f * k, 16.8f * k)
                lineTo(7f * k, 20f * k)
                close()
            }
        }
        UiIcon.Chevron -> {
            strokePath(tint, w) { k ->
                moveTo(9f * k, 6f * k)
                lineTo(15f * k, 12f * k)
                lineTo(9f * k, 18f * k)
            }
        }
        UiIcon.Back -> {
            strokePath(tint, w) { k ->
                moveTo(15f * k, 5f * k)
                lineTo(8f * k, 12f * k)
                lineTo(15f * k, 19f * k)
            }
        }
        UiIcon.Close -> {
            line(tint, w, 6f, 6f, 18f, 18f)
            line(tint, w, 18f, 6f, 6f, 18f)
        }
        UiIcon.Edit -> {
            strokePath(tint, w) { k ->
                moveTo(4f * k, 20f * k)
                lineTo(8f * k, 20f * k)
                lineTo(18f * k, 10f * k)
                lineTo(14f * k, 6f * k)
                lineTo(4f * k, 16f * k)
                close()
                moveTo(13f * k, 7f * k)
                lineTo(17f * k, 11f * k)
            }
        }
        UiIcon.Warn -> {
            strokePath(tint, w) { k ->
                moveTo(12f * k, 4f * k)
                lineTo(20.5f * k, 19f * k)
                lineTo(3.5f * k, 19f * k)
                close()
            }
            line(tint, w, 12f, 10f, 12f, 14.5f)
            dot(tint, 12f, 17f, 0.9f)
        }
        UiIcon.Error -> {
            circle(tint, w, 12f, 12f, 8.2f)
            line(tint, w, 9f, 9f, 15f, 15f)
            line(tint, w, 15f, 9f, 9f, 15f)
        }
        UiIcon.Info -> {
            circle(tint, w, 12f, 12f, 8.2f)
            line(tint, w, 12f, 11f, 12f, 16f)
            dot(tint, 12f, 8f, 0.9f)
        }
        UiIcon.Key -> {
            circle(tint, w, 8f, 12f, 4f)
            line(tint, w, 12f, 12f, 21f, 12f)
            line(tint, w, 18f, 12f, 18f, 15f)
            line(tint, w, 15f, 12f, 15f, 14f)
        }
        UiIcon.Shield -> {
            strokePath(tint, w) { k ->
                moveTo(12f * k, 3f * k)
                lineTo(19f * k, 5.5f * k)
                lineTo(19f * k, 11.5f * k)
                cubicTo(19f * k, 15.9f * k, 16f * k, 19.1f * k, 12f * k, 21f * k)
                cubicTo(8f * k, 19.1f * k, 5f * k, 15.9f * k, 5f * k, 11.5f * k)
                lineTo(5f * k, 5.5f * k)
                close()
                moveTo(8.8f * k, 12f * k)
                lineTo(11f * k, 14.2f * k)
                lineTo(15.2f * k, 9.8f * k)
            }
        }
        UiIcon.Lock -> {
            roundRect(tint, w, 5f, 10.5f, 14f, 10f, 2.5f)
            strokePath(tint, w) { k ->
                moveTo(8.5f * k, 10.5f * k)
                lineTo(8.5f * k, 8f * k)
                cubicTo(8.5f * k, 6.1f * k, 10.1f * k, 4.5f * k, 12f * k, 4.5f * k)
                cubicTo(13.9f * k, 4.5f * k, 15.5f * k, 6.1f * k, 15.5f * k, 8f * k)
                lineTo(15.5f * k, 10.5f * k)
            }
            dot(tint, 12f, 15.5f, 1.1f)
        }
        UiIcon.Battery -> {
            roundRect(tint, w, 3f, 8f, 16f, 8f, 2.5f)
            line(tint, w, 21f, 11f, 21f, 13f)
            line(tint, w, 6.5f, 11f, 6.5f, 13f)
            line(tint, w, 9.5f, 11f, 9.5f, 13f)
            line(tint, w, 12.5f, 11f, 12.5f, 13f)
        }
        UiIcon.Moon -> {
            fillPath(tint, evenOdd = true) { k ->
                addOval(Rect(Offset(3.6f * k, 3.6f * k), Size(16.8f * k, 16.8f * k)))
                addOval(Rect(Offset(10.5f * k, 1.5f * k), Size(15f * k, 15f * k)))
            }
        }
        UiIcon.Sound -> {
            strokePath(tint, w) { k ->
                moveTo(5f * k, 10f * k)
                lineTo(8f * k, 10f * k)
                lineTo(12f * k, 6.5f * k)
                lineTo(12f * k, 17.5f * k)
                lineTo(8f * k, 14f * k)
                lineTo(5f * k, 14f * k)
                close()
            }
            arc(tint, w, 15.5f, 12f, 4f, -55f, 110f)
            arc(tint, w, 16f, 12f, 7.5f, -55f, 110f)
        }
        UiIcon.Logs -> {
            line(tint, w, 5f, 6f, 19f, 6f)
            line(tint, w, 5f, 11f, 14f, 11f)
            line(tint, w, 5f, 16f, 17f, 16f)
            line(tint, w, 5f, 21f, 11f, 21f)
        }
        UiIcon.Download -> {
            line(tint, w, 12f, 4f, 12f, 14f)
            strokePath(tint, w) { k ->
                moveTo(8f * k, 10.5f * k)
                lineTo(12f * k, 14.5f * k)
                lineTo(16f * k, 10.5f * k)
                moveTo(5f * k, 19f * k)
                lineTo(19f * k, 19f * k)
            }
        }
        UiIcon.External -> {
            strokePath(tint, w) { k ->
                moveTo(14f * k, 4f * k)
                lineTo(20f * k, 4f * k)
                lineTo(20f * k, 10f * k)
                moveTo(20f * k, 4f * k)
                lineTo(12f * k, 12f * k)
                moveTo(19f * k, 14f * k)
                lineTo(19f * k, 19f * k)
                lineTo(5f * k, 19f * k)
                lineTo(5f * k, 5f * k)
                lineTo(10f * k, 5f * k)
            }
        }
        UiIcon.Trash -> {
            line(tint, w, 5f, 7f, 19f, 7f)
            strokePath(tint, w) { k ->
                moveTo(10f * k, 7f * k)
                lineTo(10f * k, 5f * k)
                lineTo(14f * k, 5f * k)
                lineTo(14f * k, 7f * k)
                moveTo(7f * k, 7f * k)
                lineTo(8f * k, 19f * k)
                lineTo(16f * k, 19f * k)
                lineTo(17f * k, 7f * k)
                moveTo(10.5f * k, 11f * k)
                lineTo(10.5f * k, 16f * k)
                moveTo(13.5f * k, 11f * k)
                lineTo(13.5f * k, 16f * k)
            }
        }
        UiIcon.Copy -> {
            strokePath(tint, w) { k ->
                moveTo(15f * k, 9f * k)
                lineTo(15f * k, 6f * k)
                cubicTo(15f * k, 5f * k, 14f * k, 4f * k, 13f * k, 4f * k)
                lineTo(6f * k, 4f * k)
                cubicTo(5f * k, 4f * k, 4f * k, 5f * k, 4f * k, 6f * k)
                lineTo(4f * k, 13f * k)
                cubicTo(4f * k, 14f * k, 5f * k, 15f * k, 6f * k, 15f * k)
                lineTo(9f * k, 15f * k)
            }
            roundRect(tint, w, 9f, 9f, 11f, 11f, 2f)
        }
        UiIcon.Share -> {
            circle(tint, w, 17f, 6f, 2.5f)
            circle(tint, w, 7f, 12f, 2.5f)
            circle(tint, w, 17f, 18f, 2.5f)
            line(tint, w, 9.2f, 10.8f, 14.6f, 7.4f)
            line(tint, w, 9.2f, 13.2f, 14.6f, 16.6f)
        }
        UiIcon.Filter -> {
            fillPath(tint) { k ->
                moveTo(4f * k, 5f * k)
                lineTo(20f * k, 5f * k)
                lineTo(14f * k, 12.5f * k)
                lineTo(14f * k, 19f * k)
                lineTo(10f * k, 21f * k)
                lineTo(10f * k, 12.5f * k)
                close()
            }
        }
        UiIcon.Offline -> {
            arc(tint, w, 12f, 13f, 10.5f, 200f, 140f)
            arc(tint, w, 12f, 13f, 6.5f, 200f, 140f)
            arc(tint, w, 12f, 13f, 3f, 200f, 140f)
            dot(tint, 12f, 18f, 1.2f)
            line(tint, w, 3f, 4f, 21f, 20f)
        }
        UiIcon.Clock -> {
            circle(tint, w, 12f, 12f, 8.4f)
            line(tint, w, 12f, 7.5f, 12f, 12f)
            line(tint, w, 12f, 12f, 15f, 14f)
        }
        UiIcon.Layers -> {
            strokePath(tint, w) { k ->
                moveTo(12f * k, 4f * k)
                lineTo(20f * k, 8.2f * k)
                lineTo(12f * k, 12.4f * k)
                lineTo(4f * k, 8.2f * k)
                close()
                moveTo(4f * k, 12.6f * k)
                lineTo(12f * k, 16.8f * k)
                lineTo(20f * k, 12.6f * k)
                moveTo(4f * k, 16.6f * k)
                lineTo(12f * k, 20.8f * k)
                lineTo(20f * k, 16.6f * k)
            }
        }
        UiIcon.Logout -> {
            strokePath(tint, w) { k ->
                moveTo(10f * k, 5f * k)
                lineTo(6f * k, 5f * k)
                cubicTo(5f * k, 5f * k, 5f * k, 6f * k, 5f * k, 6f * k)
                lineTo(5f * k, 18f * k)
                cubicTo(5f * k, 19f * k, 5f * k, 19f * k, 6f * k, 19f * k)
                lineTo(10f * k, 19f * k)
                moveTo(14f * k, 8f * k)
                lineTo(18f * k, 12f * k)
                lineTo(14f * k, 16f * k)
                moveTo(18f * k, 12f * k)
                lineTo(9f * k, 12f * k)
            }
        }
        UiIcon.User -> {
            circle(tint, w, 12f, 8f, 3.6f)
            strokePath(tint, w) { k ->
                moveTo(5f * k, 20f * k)
                cubicTo(5.8f * k, 16.4f * k, 8.5f * k, 14.6f * k, 12f * k, 14.6f * k)
                cubicTo(15.5f * k, 14.6f * k, 18.2f * k, 16.4f * k, 19f * k, 20f * k)
            }
        }
        UiIcon.Bolt -> {
            fillPath(tint) { k ->
                moveTo(13f * k, 3f * k)
                lineTo(5f * k, 14f * k)
                lineTo(11f * k, 14f * k)
                lineTo(10f * k, 21f * k)
                lineTo(18f * k, 10f * k)
                lineTo(12f * k, 10f * k)
                close()
            }
        }
        UiIcon.Star -> {
            fillPath(tint) { k ->
                val cx = 12f
                val cy = 12.2f
                val outer = 8.2f
                val inner = 3.7f
                repeat(10) { index ->
                    val radius = if (index % 2 == 0) outer else inner
                    val angle = (-90.0 + index * 36.0) * PI / 180.0
                    val x = (cx + radius * cos(angle)).toFloat() * k
                    val y = (cy + radius * sin(angle)).toFloat() * k
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        UiIcon.Calendar -> {
            roundRect(tint, w, 4f, 6f, 16f, 14f, 2.5f)
            line(tint, w, 8f, 3.5f, 8f, 7.5f)
            line(tint, w, 16f, 3.5f, 16f, 7.5f)
            line(tint, w, 4f, 11f, 20f, 11f)
        }
        UiIcon.Globe -> {
            circle(tint, w, 12f, 12f, 8.4f)
            line(tint, w, 3.6f, 12f, 20.4f, 12f)
            val k = size.minDimension / 24f
            drawOval(
                color = tint,
                topLeft = Offset(7.9f * k, 3.6f * k),
                size = Size(8.2f * k, 16.8f * k),
                style = Stroke(w),
            )
        }
        UiIcon.Cursor -> {
            fillPath(tint) { k ->
                moveTo(6f * k, 3f * k)
                lineTo(18f * k, 11f * k)
                lineTo(12.6f * k, 12.4f * k)
                lineTo(10f * k, 18f * k)
                close()
            }
        }
    }
}
