package com.upcroc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * One schema field, rendered to Astryx's Field anatomy: label, description,
 * control, status. The control is chosen from the schema's own type, so a field
 * this app has never seen still gets a real widget.
 */
@Composable
fun SchemaField(
    spec: FieldSpec,
    value: String,
    onChange: (String) -> Unit,
) {
    when (spec.kind) {
        FieldSpec.Kind.BOOLEAN -> BooleanField(spec, value == "true") { onChange(it.toString()) }
        FieldSpec.Kind.ENUM -> EnumField(spec, value, onChange)
        FieldSpec.Kind.NUMBER -> NumberField(spec, value, onChange)
        FieldSpec.Kind.TEXT -> TextField_(spec, value, onChange)
        FieldSpec.Kind.RAW -> TextField_(spec, value, onChange)
    }
}

@Composable
private fun TextField_(spec: FieldSpec, value: String, onChange: (String) -> Unit) {
    AstryxInput(
        value = value,
        onValueChange = onChange,
        label = spec.label,
        description = spec.description,
        placeholder = if (spec.kind == FieldSpec.Kind.RAW) "json" else spec.name,
        required = spec.required,
    )
}

@Composable
private fun NumberField(spec: FieldSpec, value: String, onChange: (String) -> Unit) {
    val invalid = value.toDoubleOrNull()?.let { !spec.inRange(it) } == true
    AstryxInput(
        value = value,
        onValueChange = { raw ->
            // Reject out of range at the keystroke. The bounds are printed above
            // the control, so an impossible value is never discoverable only by
            // being rejected by the server.
            val cleaned = raw.filter { it.isDigit() || it == '.' }
            val n = cleaned.toDoubleOrNull()
            if (cleaned.isEmpty() || n == null || spec.inRange(n)) onChange(cleaned)
        },
        label = spec.label,
        description = spec.help,
        placeholder = spec.rangeHint.ifBlank { spec.name },
        required = spec.required,
        isError = invalid,
        keyboardType = KeyboardType.Number,
    )
}

@Composable
private fun EnumField(spec: FieldSpec, value: String, onChange: (String) -> Unit) {
    var open by remember(spec.name) { mutableStateOf(false) }
    val shape = RoundedCornerShape(Astryx.RadiusElement)
    Column(Modifier.fillMaxWidth().padding(bottom = Astryx.Spacing3)) {
        Text(
            if (spec.required) "${spec.label} · Required" else spec.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (spec.help.isNotBlank()) {
            Text(
                spec.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.padding(top = Astryx.Spacing1)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Astryx.TouchField)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(Astryx.BorderWidth, MaterialTheme.colorScheme.outline, shape)
                    .clickable { open = true }
                    .padding(horizontal = Astryx.Spacing3, vertical = Astryx.Spacing1),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (value.isBlank()) "Not set" else humanise(value),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("\u25be", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Not set") },
                    onClick = { onChange(""); open = false },
                )
                spec.options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(humanise(opt)) },
                        onClick = { onChange(opt); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun BooleanField(spec: FieldSpec, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Astryx.TouchField)
            .padding(bottom = Astryx.Spacing3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (spec.description.isNotBlank()) {
                Text(
                    spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}