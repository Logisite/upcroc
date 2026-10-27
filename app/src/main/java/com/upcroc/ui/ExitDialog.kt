package com.upcroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What leaving the app means.
 *
 * Hide keeps the service and engine resident with everything already on disk,
 * which is the point of the app. Kill really ends the process. Making the user
 * choose means neither happens by surprise.
 */
@Composable
fun ExitDialog(
    watching: Boolean,
    connected: Boolean,
    jobsStored: Int,
    onHide: () -> Unit,
    onKill: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave upcroc") },
        text = {
            Text(
                text = (when {
                    watching -> "It is watching and will keep notifying you."
                    connected -> "It is connected and holding your session."
                    else -> "It is not connected to Upwork yet."
                }) + "\n\n$jobsStored jobs cached.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onHide) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                Text("  Hide")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                TextButton(onClick = onKill) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Text("  Kill")
                }
                TextButton(onClick = onDismiss) { Text("Stay") }
            }
        },
    )
}