package app.upcroc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

@Composable
fun UpCard(
    modifier: Modifier = Modifier,
    color: Color = Upcroc.Surface,
    radius: Dp = 18.dp,
    padding: PaddingValues = PaddingValues(15.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

enum class BannerTone { Warning, Danger, Ok }

@Composable
fun Banner(
    icon: UiIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    tone: BannerTone = BannerTone.Warning,
) {
    val bg = when (tone) {
        BannerTone.Warning -> Upcroc.BannerWarnBg
        BannerTone.Danger -> Upcroc.BannerDangerBg
        BannerTone.Ok -> Upcroc.BannerOkBg
    }
    val fg = when (tone) {
        BannerTone.Warning -> Upcroc.BannerWarn
        BannerTone.Danger -> Upcroc.BannerDanger
        BannerTone.Ok -> Upcroc.BannerOk
    }
    val sub = when (tone) {
        BannerTone.Warning -> Upcroc.BannerWarnSub
        BannerTone.Danger -> Upcroc.BannerDangerSub
        BannerTone.Ok -> Upcroc.BannerOkSub
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconGlyph(icon, Modifier.size(18.dp).padding(top = 1.dp), fg)
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.groupTitle, color = fg)
            Spacer(Modifier.height(3.dp))
            AppText(body, style = UpType.muted, color = sub)
        }
        if (action != null && onAction != null) {
            TextLink(action, onAction, color = fg, style = UpType.link)
        }
    }
}

@Composable
fun EmptyState(
    icon: UiIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Upcroc.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            IconGlyph(icon, Modifier.size(24.dp), Upcroc.Dim)
        }
        Spacer(Modifier.height(14.dp))
        AppText(title, style = UpType.groupTitle, color = Upcroc.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        AppText(
            body,
            style = UpType.muted,
            color = Upcroc.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.82f),
        )
    }
}

@Composable
fun PageHeader(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton40(UiIcon.Back, "Back", onBack, modifier = Modifier.padding(start = 0.dp))
        Column(Modifier.weight(1f)) {
            AppText(kicker.uppercase(), style = UpType.pageKicker, color = Upcroc.Dim)
            Spacer(Modifier.height(1.dp))
            AppText(title, style = UpType.screenTitle, color = Upcroc.Text)
        }
        if (actions != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { actions() }
        }
    }
}

@Composable
fun TopBar(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    avatar: String? = null,
    avatarColor: Color = Upcroc.Blue,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 2.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (avatar != null) {
            Avatar(avatar, size = 34.dp, color = avatarColor)
        }
        Column(Modifier.weight(1f)) {
            AppText(kicker.uppercase(), style = UpType.tiny.copy(letterSpacing = 0.8.sp), color = Upcroc.Dim)
            Spacer(Modifier.height(1.dp))
            AppText(title, style = UpType.screenTitle, color = Upcroc.Text)
        }
        if (actions != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { actions() }
        }
    }
}

@Composable
fun InlineSectionLabel(label: String, modifier: Modifier = Modifier) {
    AppText(label, style = UpType.section, color = Upcroc.Text, modifier = modifier)
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))
