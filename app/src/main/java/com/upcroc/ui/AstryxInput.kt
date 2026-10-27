package com.upcroc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Astryx TextInput, built on BasicTextField rather than Material 3's TextField.
 *
 * The reason is not stylistic preference. M3's TextField is a different control:
 * the label *floats* into the box on focus, and focus is marked by a 2dp
 * indicator line along the bottom edge only. Astryx keeps the label statically
 * above the control and draws the boundary on all four sides, which is what
 * --color-border-emphasized is for. Reaching for M3's component and restyling it
 * would mean fighting its internal layout rather than using it.
 *
 * Box spec, from Astryx source:
 *   radius --radius-element (8dp) · border --border-width (1px)
 *   border --color-border-emphasized, switching to --color-accent on focus
 *   background --color-background-surface
 *   padding --spacing-1 block / --spacing-2 inline (4 / 8)
 *   text --text-body (14sp / 20sp)
 *
 * Height is the one deviation: --size-element-md is 32px, which is a desktop-web
 * control height and an accessibility failure on Android. 52dp keeps the touch
 * target comfortable while leaving every other measurement intact.
 */
@Composable
fun AstryxInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    description: String = "",
    placeholder: String = "",
    required: Boolean = false,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = androidx.compose.animation.core.tween(Astryx.DurationFast, easing = Astryx.Standard),
        label = "border",
    )

    Column(
        modifier.fillMaxWidth().padding(bottom = Astryx.Spacing3),
        verticalArrangement = Arrangement.spacedBy(Astryx.Spacing1),
    ) {
        if (label.isNotBlank()) {
            Text(
                if (required) "$label · Required" else label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Astryx.TouchField)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Astryx.RadiusElement))
                .border(Astryx.BorderWidth, borderColor, RoundedCornerShape(Astryx.RadiusElement))
                .padding(horizontal = Astryx.Spacing2, vertical = Astryx.Spacing1),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        ),
                    ),
                    singleLine = singleLine,
                    interactionSource = interaction,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = VisualTransformation.None,
                )
            }
            if (trailing != null) {
                Box(Modifier.padding(start = Astryx.Spacing2)) { trailing() }
            }
        }
    }
}