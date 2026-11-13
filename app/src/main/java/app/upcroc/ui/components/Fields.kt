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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.LocalFontFamily
import app.upcroc.ui.theme.LocalMainWeight
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import java.util.Locale

@Composable
fun UpField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Upcroc.Field)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        AppText(label, style = UpType.tiny.copy(fontWeight = FontWeight.W600), color = Upcroc.Muted)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = UpType.bodyStrong.copy(
                    color = Upcroc.Text,
                    fontFamily = LocalFontFamily.current,
                    fontWeight = LocalMainWeight.current,
                ),
                cursorBrush = SolidColor(Upcroc.Blue),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank() && placeholder.isNotBlank()) {
                            AppText(placeholder, style = UpType.body, color = Upcroc.Dim)
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Upcroc.Field)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        IconGlyph(UiIcon.Search, Modifier.size(17.dp), Upcroc.Dim)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = UpType.body.copy(
                color = Upcroc.Text,
                fontFamily = LocalFontFamily.current,
                fontWeight = LocalMainWeight.current,
            ),
            cursorBrush = SolidColor(Upcroc.Blue),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) AppText(placeholder, style = UpType.body, color = Upcroc.Dim)
                    inner()
                }
            },
        )
    }
}

@Composable
fun NumberField(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
) {
    var text by remember {
        mutableStateOf(value?.let { if (integer) it.toInt().toString() else it.toString() } ?: "")
    }
    LaunchedEffect(value) {
        val incoming = value?.let { if (integer) it.toInt().toString() else it.toString() } ?: ""
        if (incoming != text && text.toDoubleOrNull() != value) text = incoming
    }
    UpField(
        label = label,
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() || it == '.' }
            onValueChange(text.toDoubleOrNull())
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * Twenty-four hour stepper used by the quiet-hours editor. Hours move one at a
 * time, minutes move in five minute steps, both wrapping inside the day.
 */
@Composable
fun TimeStepper(
    label: String,
    minutesOfDay: Int,
    modifier: Modifier = Modifier,
    background: androidx.compose.ui.graphics.Color = Upcroc.Hero,
    onChange: (Int) -> Unit,
) {
    val safe = minutesOfDay.coerceIn(0, 1439)
    val hour = safe / 60
    val minute = safe % 60
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AppText(label, style = UpType.rowTitle, color = Upcroc.Text, modifier = Modifier.weight(1f))
        StepButton("−", "Earlier hour") { onChange(((hour + 23) % 24) * 60 + minute) }
        AppText(String.format(Locale.US, "%02d", hour), style = UpType.statBig, color = Upcroc.Text)
        StepButton("+", "Later hour") { onChange(((hour + 1) % 24) * 60 + minute) }
        AppText(":", style = UpType.statBig, color = Upcroc.Dim)
        StepButton("−", "Earlier minute") { onChange(hour * 60 + ((minute + 55) % 60)) }
        AppText(String.format(Locale.US, "%02d", minute), style = UpType.statBig, color = Upcroc.Text)
        StepButton("+", "Later minute") { onChange(hour * 60 + ((minute + 5) % 60)) }
    }
}

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Upcroc.Field)
            .semantics { contentDescription = description }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, style = UpType.bodyStrong, color = Upcroc.Muted, maxLines = 1)
    }
}
