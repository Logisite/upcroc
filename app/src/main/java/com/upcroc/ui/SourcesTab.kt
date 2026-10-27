package com.upcroc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upcroc.data.QueryEntity
import com.upcroc.net.SchemaRepo
import java.util.UUID

/**
 * The plug-in surface. Sources live in a table, so they can be added, edited,
 * reordered, enabled, disabled or removed here and the watcher picks them up on
 * its next check, with no code change and no restart.
 *
 * Editing no longer exposes a params JSON blob. The form is generated from the
 * tool's own inputSchema, which Mcp publishes on every session.
 */
@Composable
fun SourcesTab(model: UiModel) {
    val queries by model.queries.collectAsStateWithLifecycle()
    val schema by SchemaRepo.fields.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<QueryEntity?>(null) }

    // The form renders from Upwork's published schema, so ask for it rather
    // than waiting for the watcher to happen to run a check.
    LaunchedEffect(Unit) {
        if (!SchemaRepo.isLoaded()) model.refreshSchema()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Sources", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    editing = QueryEntity(
                        id = "q" + UUID.randomUUID(),
                        label = "",
                        action = "search",
                        params = "",
                        sortOrder = queries.size,
                    )
                }) { Text("Add") }
                TextButton(onClick = { model.resetQueries() }) { Text("Reset") }
            }
        }

        if (schema.values.all { it.isEmpty() }) {
            item {
                Text(
                    "Could not load Upwork's filter schema. Check the connection, then reopen this tab.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(queries, key = { it.id }) { q ->
            SourceRow(
                query = q,
                fieldCount = schema[q.action]?.size ?: 0,
                onToggle = { model.saveQuery(q.copy(enabled = it)) },
                onEdit = { editing = q },
                onRemove = { model.deleteQuery(q.id) },
            )
        }

        editing?.let { q ->
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                ) {
                    SourceEditor(
                        original = q,
                        schemas = schema,
                        onSave = {
                            model.saveQuery(it)
                            editing = null
                        },
                        onCancel = { editing = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    query: QueryEntity,
    fieldCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    query.label.ifBlank { humanise(query.action) },
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append(humanise(query.action))
                        val n = parseParams(query.params).count { it.value.isNotBlank() }
                        append(" · ")
                        append(if (n == 1) "1 filter" else "$n filters")
                        if (fieldCount > 0) append(" of $fieldCount available")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = query.enabled, onCheckedChange = onToggle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}