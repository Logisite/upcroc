package app.upcroc.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}

@Composable
fun IconButton40(
    icon: UiIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Upcroc.Muted,
    size: Dp = 40.dp,
    badge: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(icon, Modifier.size(19.dp), tint)
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Upcroc.Blue)
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: UiIcon? = null,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Upcroc.Blue else Upcroc.SurfaceHigh)
            .pressable(onClick, enabled)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            IconGlyph(icon, Modifier.size(17.dp), if (enabled) Color.White else Upcroc.Dim)
            Spacer(Modifier.width(8.dp))
        }
        AppText(
            text,
            style = UpType.button,
            color = if (enabled) Color.White else Upcroc.Dim,
            maxLines = 1,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: UiIcon? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(Upcroc.SurfaceHigh)
            .border(1.dp, Upcroc.Line, shape)
            .pressable(onClick, enabled)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            IconGlyph(icon, Modifier.size(17.dp), if (enabled) Upcroc.Text else Upcroc.Dim)
            Spacer(Modifier.width(8.dp))
        }
        AppText(text, style = UpType.button, color = if (enabled) Upcroc.Text else Upcroc.Dim, maxLines = 1)
    }
}

@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Upcroc.Blue,
    style: androidx.compose.ui.text.TextStyle = UpType.link,
) {
    AppText(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .pressable(onClick)
            .padding(vertical = 2.dp),
        style = style,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun UpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset by animateDpAsState(if (checked) 16.dp else 0.dp, tween(180), label = "switch")
    val track by animateColorAsState(if (checked) Upcroc.Blue else Upcroc.SurfaceHighest, tween(180), label = "track")
    val thumb by animateColorAsState(if (checked) Color.White else Color(0xFF8A8A8A), tween(180), label = "thumb")
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = offset.toPx() }
                .size(16.dp)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1C))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            val bg by animateColorAsState(
                if (active) Upcroc.Text else Color.Transparent,
                tween(180),
                label = "seg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(bg)
                    .pressable(onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    label,
                    style = UpType.caption.copy(fontWeight = FontWeight.W700),
                    color = if (active) Upcroc.Surface else Upcroc.Muted,
                )
            }
        }
    }
}

@Composable
fun Chip(
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier
        .clip(RoundedCornerShape(9.dp))
        .background(if (active) Upcroc.BlueSoft else Upcroc.Field)
    val clickable = if (onClick != null) base.pressable(onClick) else base
    Box(
        modifier = clickable.padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            label,
            style = UpType.caption.copy(fontWeight = FontWeight.W600),
            color = if (active) Upcroc.Blue else Upcroc.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun QuickButton(
    icon: UiIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Upcroc.Blue,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Upcroc.Field)
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(icon, Modifier.size(18.dp), tint)
    }
}

@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    textSize: Float = 10f,
    color: Color = Upcroc.Blue,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            initials,
            style = UpType.caption.copy(fontWeight = FontWeight.W800, fontSize = textSize.sp),
            color = color,
            maxLines = 1,
        )
    }
}


@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    color: Color = Upcroc.Green,
    pulse: Boolean = false,
) {
    if (!pulse) {
        Box(modifier.size(6.dp).clip(CircleShape).background(color))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart),
        label = "pulseAlpha",
    )
    Box(modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((6 + 8 * p).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = (1f - p) * 0.35f))
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    background: Color = Upcroc.Hero,
    titleColor: Color = Upcroc.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(background)
    val clickable = if (onClick != null) base.pressable(onClick) else base
    Row(
        modifier = clickable.padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.rowTitle, color = titleColor, maxLines = 2)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                AppText(subtitle, style = UpType.small, color = Upcroc.Muted, maxLines = 2)
            }
        }
        if (value != null) {
            AppText(value, style = UpType.caption.copy(fontWeight = FontWeight.W600), color = Upcroc.Muted, maxLines = 1)
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            IconGlyph(UiIcon.Chevron, Modifier.size(15.dp), Upcroc.Dim)
        }
    }
}

@Composable
fun SectionHeader(
    label: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AppText(label, style = UpType.section, color = Upcroc.Text)
        if (action != null && onAction != null) TextLink(action, onAction)
    }
}

@Composable
fun DividerSoft(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Upcroc.LineSoft))
}

@Composable
fun LabelValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = UpType.small, color = Upcroc.Dim)
        AppText(value, style = UpType.small, color = Upcroc.Muted, maxLines = 1)
    }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier, bordered: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (bordered) Color.Transparent else Upcroc.SurfaceHigh)
            .then(if (bordered) Modifier.border(1.dp, Upcroc.Line, shape) else Modifier)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        AppText(value, style = UpType.statBig, color = Upcroc.Text)
        Spacer(Modifier.height(1.dp))
        AppText(label.uppercase(), style = UpType.tiny.copy(letterSpacing = 0.6.sp), color = Upcroc.Dim, maxLines = 1)
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Upcroc.Hero,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(background)
    val clickable = if (onClick != null) base.pressable(onClick) else base
    Row(
        modifier = clickable.padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.rowTitle, color = Upcroc.Text, maxLines = 2)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                AppText(subtitle, style = UpType.small, color = Upcroc.Muted, maxLines = 2)
            }
        }
        UpSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone = StatusTone.Live,
    modifier: Modifier = Modifier,
) {
    val bg = when (tone) {
        StatusTone.Live -> Upcroc.StatusBg
        StatusTone.Paused -> Color(0xFF1E1E1E)
        StatusTone.Warn -> Upcroc.BannerWarnBg
        StatusTone.Error -> Upcroc.BannerDangerBg
    }
    val fg = when (tone) {
        StatusTone.Live -> Upcroc.StatusText
        StatusTone.Paused -> Upcroc.Muted
        StatusTone.Warn -> Upcroc.BannerWarn
        StatusTone.Error -> Upcroc.BannerDanger
    }
    val dot = when (tone) {
        StatusTone.Live -> Upcroc.Green
        StatusTone.Paused -> Upcroc.Dim
        StatusTone.Warn -> Upcroc.Warn
        StatusTone.Error -> Upcroc.Error
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        AppText(label, style = UpType.statusChip, color = fg, maxLines = 1)
    }
}

enum class StatusTone { Live, Paused, Warn, Error }

@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    color: Color = Upcroc.Blue,
    size: Dp = 18.dp,
    stroke: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "spinnerAngle",
    )
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val pad = stroke.toPx() / 2f
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(this.size.width - pad * 2, this.size.height - pad * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.toPx()),
        )
        drawArc(
            color = color,
            startAngle = angle - 90f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(this.size.width - pad * 2, this.size.height - pad * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    background: Color = Upcroc.Card,
    bordered: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, Upcroc.Line, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        AppText(value, style = UpType.statBig, color = Upcroc.Text, maxLines = 1)
        Spacer(Modifier.height(1.dp))
        AppText(
            label.uppercase(),
            style = UpType.tiny.copy(letterSpacing = 0.5.sp),
            color = Upcroc.Dim,
            maxLines = 2,
        )
    }
}

@Composable
fun ContentPadding(): PaddingValues = PaddingValues(horizontal = 16.dp)
