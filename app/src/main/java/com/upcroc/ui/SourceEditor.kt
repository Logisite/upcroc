package com.upcroc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.upcroc.data.QueryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val ACTIONS = listOf("search", "smart_search", "get")

/**
 * The source editor. There is no raw params field anywhere: the form is
 * generated from the tool's own inputSchema, and what it saves is a params
 * object assembled from the controls the user actually touched.
 *
 * `schemas` is the whole per-action map, not one action's fields, because the
 * type picker is what chooses the action — resolving it here is what makes
 * changing type actually swap the fields on screen.
 */
@Composable
fun SourceEditor(
    original: QueryEntity,
    schemas: Map<String, List<FieldSpec>>,
    onSave: (QueryEntity) -> Unit,
    onCancel: () -> Unit,
) {
    var action by remember { mutableStateOf(original.action) }
    var label by remember { mutableStateOf(original.label) }
    var values by remember { mutableStateOf(parseParams(original.params)) }
    var openSection by remember { mutableStateOf<String?>(null) }

    val fields = remember(action) { schemas[action].orEmpty() }
    val sections = remember(fields) {
        fields.groupBy { it.group }.toList().sortedBy { (g, _) -> groupRank(g) }
    }
    val modelled = fields.count { it.kind != FieldSpec.Kind.RAW }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Edit source", style = MaterialTheme.typography.titleMedium)

        TypePicker(action) { picked ->
            action = picked
            // Values for fields the new action does not declare are dropped, so
            // a switch from search to get cannot smuggle a stale filter along.
            val keep = fieldsFor(schemas, picked).map { it.name }.toSet()
            values = values.filterKeys { it in keep }
            openSection = null
        }

        Text("Label", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(humanise(action)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
        )

        if (fields.isEmpty()) {
            Text(
                "No filters published for ${humanise(action)} yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        sections.forEach { (group, specs) ->
            Section(
                title = group,
                count = specs.size,
                expanded = openSection == group,
                onToggle = { openSection = if (openSection == group) null else group },
            ) {
                specs.forEach { spec ->
                    val blocked = suppression(spec, values)
                    if (blocked != null) {
                        Suppressed(spec, blocked)
                    } else {
                        SchemaField(spec, values[spec.name].orEmpty()) { v ->
                            values = values.toMutableMap().apply { put(spec.name, v) }
                        }
                    }
                }
            }
        }

        Text(
            "$modelled of ${fields.size} filters modelled from Upwork's schema",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = {
                onSave(
                    original.copy(
                        label = label.ifBlank { humanise(action) },
                        action = action,
                        params = buildParams(effectiveValues(values)),
                    ),
                )
            }) { Text("Save") }
        }
    }
}

private fun fieldsFor(schemas: Map<String, List<FieldSpec>>, action: String) =
    schemas[action].orEmpty()

@Composable
private fun Suppressed(spec: FieldSpec, reason: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            spec.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "  ·  $reason",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypePicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Astryx.Spacing1)) {
        Text(
            "Type",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
        // Astryx calls this SegmentedControl and explicitly says not to use a
        // Selector here: two-to-five mutually exclusive options that must all
        // stay visible. M3's segmented row is the same control. Item shape is
        // overridden to the concentric 6dp so the inner corners match.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ACTIONS.forEachIndexed { i, a ->
                SegmentedButton(
                    selected = a == selected,
                    onClick = { onSelect(a) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = i,
                        count = ACTIONS.size,
                        baseShape = RoundedCornerShape(Astryx.RadiusElement),
                    ),
                ) {
                    Text(humanise(a), maxLines = 1)
                }
            }
        }
    }
}

/**
 * CollapsibleGroup with type="single": one section open at a time, which is
 * what keeps a twenty-field form scannable instead of exhausting.
 */
@Composable
private fun Section(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (expanded) " ▾" else " ▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseParams(raw: String): Map<String, String> =
    runCatching {
        (json.parseToJsonElement(raw) as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
            ?: emptyMap()
    }.getOrDefault(emptyMap())

/**
 * Emit only touched fields. A source that was never opened gains no empty keys,
 * and an absent key means "no filter" to the server.
 */
internal fun buildParams(values: Map<String, String>): String {
    val kept = values.entries
        .filter { it.value.isNotBlank() }
        .associate { (k, v) -> k to JsonPrimitive(v) }
    return JsonObject(kept).toString()
}