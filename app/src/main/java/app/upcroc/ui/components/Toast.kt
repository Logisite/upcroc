package app.upcroc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import kotlinx.coroutines.delay

/**
 * One small message at a time, sitting just above the dock. This replaces the
 * Material snackbar; the mock's toast is a rounded #222 strip with 10.5 px text.
 */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var nonce by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        if (text.isBlank()) return
        message = text
        nonce++
    }

    fun clear() {
        message = null
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.message
    LaunchedEffect(state.nonce) {
        if (state.message == null) return@LaunchedEffect
        delay(2400)
        state.clear()
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Upcroc.SurfaceHighest)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = message.orEmpty(),
                style = UpType.caption.copy(fontWeight = FontWeight.W600),
                color = Color(0xFFF5F5F5),
                maxLines = 3,
            )
        }
    }
}
