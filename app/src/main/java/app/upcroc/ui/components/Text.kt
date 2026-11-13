package app.upcroc.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.upcroc.ui.theme.LocalFontFamily
import app.upcroc.ui.theme.isMainText
import app.upcroc.ui.theme.LocalMainWeight
import app.upcroc.ui.theme.LocalSubWeight
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = UpType.body,
    color: Color = Upcroc.Text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
) {
    val family = LocalFontFamily.current
    val weight = style.fontWeight ?: FontWeight.W400
    val chosen = if (weight.isMainText()) LocalMainWeight.current else LocalSubWeight.current
    val resolved = style.copy(color = color, fontFamily = style.fontFamily ?: family, fontWeight = chosen)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) resolved else resolved.copy(textAlign = textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}
