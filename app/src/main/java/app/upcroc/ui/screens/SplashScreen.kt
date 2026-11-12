package app.upcroc.ui.screens

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.CrescentMark
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The approved splash, rebuilt from prototype/splash.js:
 * ball flies in 200–900 ms, spin starts at 900 ms for 1500 ms, the mark and
 * words settle at 2400–3020 ms, and the app moves on at 5700 ms.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val ballFly = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val lock = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        var fired = false
        fun finish() {
            if (!fired) {
                fired = true
                onFinished()
            }
        }
        if (reduceMotion) {
            ballFly.snapTo(1f)
            spin.snapTo(1f)
            lock.snapTo(1f)
            words.snapTo(1f)
            fade.snapTo(1f)
            bar.snapTo(1f)
            delay(2200)
            finish()
            return@LaunchedEffect
        }
        launch {
            delay(900)
            spin.animateTo(
                1f,
                tween(1500, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
            )
        }
        launch {
            delay(2400)
            launch {
                lock.animateTo(
                    1f,
                    tween(620, easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)),
                )
            }
            words.animateTo(
                1f,
                tween(620, easing = CubicBezierEasing(0.2f, 0.75f, 0.2f, 1f)),
            )
        }
        launch {
            delay(3060)
            fade.animateTo(1f, tween(500))
        }
        launch {
            delay(200)
            bar.animateTo(1f, tween(3100, easing = CubicBezierEasing(0.3f, 0.6f, 0.4f, 1f)))
        }
        launch {
            delay(5700)
            finish()
        }
        ballFly.animateTo(
            1f,
            tween(700, delayMillis = 200, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)),
        )
    }

    val interaction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Upcroc.Ink)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onFinished() },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerX = widthPx / 2f
        val centerY = heightPx * 0.46f
        val markSize = (200f + (64f - 200f) * lock.value).dp
        val markPx = with(density) { markSize.toPx() }
        val rotation = -45f - 315f * spin.value
        val wordSize = (56f + (40f - 56f) * words.value).sp
        val wordAlpha = if (reduceMotion) 1f else words.value
        val flyProgress = if (reduceMotion) 1f else words.value
        val fadeAlpha = if (reduceMotion) 1f else fade.value
        val ballOffset = (1f - ballFly.value) * 6.4f * markPx

        CrescentMark(
            modifier = Modifier
                .size(markSize)
                .offset(
                    x = with(density) { (centerX - markPx / 2f).toDp() },
                    y = with(density) { (centerY - markPx / 2f).toDp() },
                )
                .graphicsLayer { rotationZ = rotation },
            tint = Upcroc.Text,
            ballColor = Upcroc.Blue,
            ballOffsetPx = Offset(ballOffset, ballOffset),
        )

        val wordStyle = UpType.heroBig.copy(
            fontSize = wordSize,
            lineHeight = wordSize,
            letterSpacing = (-2.6f).sp,
        )

        // "up" ends 27 dp left of centre, then flies in from 150 dp further out.
        AppText(
            text = "up",
            style = wordStyle,
            color = Upcroc.Text,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationX = -with(density) { 27.dp.toPx() } - size.width / 2f -
                        (1f - flyProgress) * with(density) { 150.dp.toPx() }
                    translationY = centerY - size.height / 2f
                    alpha = wordAlpha
                },
        )
        AppText(
            text = "roc",
            style = wordStyle,
            color = Upcroc.Blue,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationX = with(density) { 27.dp.toPx() } + size.width / 2f +
                        (1f - flyProgress) * with(density) { 150.dp.toPx() }
                    translationY = centerY - size.height / 2f
                    alpha = wordAlpha
                },
        )

        AppText(
            text = "Upwork job alerts",
            style = UpType.caption.copy(fontSize = 11.5.sp, letterSpacing = 0.2.sp),
            color = Upcroc.Muted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (centerY + 54.dp.toPx()).toDp() })
                .alpha(fadeAlpha),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-58).dp)
                .width(108.dp),
        ) {
            Box(
                Modifier
                    .width(108.dp)
                    .height(2.dp)
                    .background(Upcroc.SurfaceHigh),
            ) {
                Box(
                    Modifier
                        .width((108f * bar.value).dp)
                        .height(2.dp)
                        .background(Upcroc.Blue)
                )
            }
        }

        AppText(
            text = "Tap to continue",
            style = UpType.tiny.copy(fontSize = 10.5.sp, letterSpacing = 0.2.sp),
            color = Upcroc.Faint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-26).dp)
                .alpha(fadeAlpha),
        )
        Spacer(Modifier.height(0.dp))
    }
}

/** Final lockup shown if the data layer is still loading after the splash. */
@Composable
fun SplashHold(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Upcroc.Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            app.upcroc.ui.components.BrandLockup()
            Spacer(Modifier.height(14.dp))
            AppText("Loading your searches…", style = UpType.caption, color = Upcroc.Muted)
        }
    }
}
