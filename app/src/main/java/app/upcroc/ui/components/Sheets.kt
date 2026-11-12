package app.upcroc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

/**
 * Bottom sheet drawn from scratch. The backdrop and panel live in their own
 * Dialog window so they sit above the dock and navigation bars.
 */
@Composable
fun ModalSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = true,
        ),
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        Box(Modifier.fillMaxSize()) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(interactionSource = interaction, indication = null, onClick = onDismiss)
            )
            AnimatedVisibility(
                visible = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(240),
                ) + fadeIn(tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(180),
                ) + fadeOut(tween(160)),
            ) {
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .imePadding()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(Upcroc.Surface)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(32.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Upcroc.SurfaceHighest)
                    )
                    Spacer(Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    dismissible: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Upcroc.SurfaceHigh)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                AppText(title, style = UpType.section, color = Upcroc.Text)
                Spacer(Modifier.height(8.dp))
                body()
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = buttons,
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = "Cancel",
    danger: Boolean = false,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        body = {
            AppText(body, style = UpType.muted, color = Upcroc.Muted)
        },
        buttons = {
            if (dismissLabel != null) {
                DialogAction(dismissLabel, onDismiss, Modifier.weight(1f))
            }
            DialogAction(
                confirmLabel,
                onConfirm,
                Modifier.weight(1f),
                filled = true,
                danger = danger,
            )
        },
    )
}

@Composable
fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Close",
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        body = {
            AppText(body, style = UpType.muted, color = Upcroc.Muted)
        },
        buttons = {
            DialogAction(confirmLabel, onDismiss, Modifier.fillMaxWidth(), filled = true)
        },
    )
}

@Composable
fun DialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
) {
    val background = when {
        danger -> Color(0xFF3A1719)
        filled -> Upcroc.Blue
        else -> Upcroc.SurfaceHighest
    }
    val foreground = when {
        danger -> Upcroc.BannerDanger
        filled -> Color.White
        else -> Upcroc.Text
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pressable(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = UpType.button,
            color = foreground,
            maxLines = 1,
        )
    }
}

@Composable
fun SheetSectionTitle(text: String) {
    AppText(
        text,
        style = UpType.label.copy(fontWeight = FontWeight.W700),
        color = Upcroc.Dim,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SheetSpacer(height: androidx.compose.ui.unit.Dp = 10.dp) {
    Spacer(Modifier.size(0.dp, height))
}
