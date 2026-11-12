package app.upcroc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.upcroc.auth.AuthState
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

/**
 * Upwork sign-in without any Material fields or dialogs. The browser opens on
 * Upwork's own page; this only starts the flow and shows its live state.
 */
@Composable
fun AuthForm(
    state: AuthState,
    onSignIn: () -> Unit,
    onSubmitManual: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var manual by remember { mutableStateOf("") }

    if (state.signedIn) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Upcroc.BannerOkBg)
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Upcroc.Green.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                IconGlyph(UiIcon.Check, Modifier.size(15.dp), Upcroc.Green)
            }
            Column(Modifier.weight(1f)) {
                AppText(
                    "Connected to Upwork",
                    style = UpType.caption.copy(fontWeight = FontWeight.W800),
                    color = Upcroc.BannerOk,
                )
                AppText(
                    "Read-only access. Upcroc cannot apply or message.",
                    style = UpType.small,
                    color = Upcroc.BannerOkSub,
                )
            }
            TextLink("Reconnect", onSignIn, color = Upcroc.BannerOk, style = UpType.small)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PrimaryButton(
            text = if (state.busy) "Waiting for Upwork…" else "Sign in with Upwork",
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            icon = UiIcon.Key,
        )
        if (state.busy || !state.status.isNullOrBlank() || !state.error.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.busy) Spinner(size = 14.dp, stroke = 1.6.dp)
                AppText(
                    text = state.error ?: state.status.orEmpty(),
                    style = UpType.small,
                    color = if (state.error != null) Upcroc.BannerDanger else Upcroc.Muted,
                )
            }
        }
        if (state.manualPrompt) {
            Spacer(Modifier.height(10.dp))
            UpField(
                label = "Callback link or code",
                value = manual,
                onValueChange = { manual = it },
                placeholder = "http://127.0.0.1:… or the code",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = "Finish sign-in",
                onClick = { onSubmitManual(manual) },
                modifier = Modifier.fillMaxWidth(),
                enabled = manual.isNotBlank(),
                icon = UiIcon.Check,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            IconGlyph(UiIcon.Lock, Modifier.size(13.dp).padding(top = 1.dp), Upcroc.Dim)
            AppText(
                "You sign in on Upwork's page. Your password never touches Upcroc.",
                style = UpType.small,
                color = Upcroc.Dim,
            )
        }
    }
}
