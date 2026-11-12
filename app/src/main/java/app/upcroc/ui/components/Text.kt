package app.upcroc.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val resolved = style.copy(color = color)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) resolved else resolved.copy(textAlign = textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}
