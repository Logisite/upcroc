package com.upcroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upcroc.core.AuthStep

/**
 * The sign-in flow as ordered steps, so there is always one obvious next action.
 *
 * The paste fallback is not a nicety. The callback comes back to a loopback
 * port on this device, and Android may kill the app while the user is in the
 * browser, which strands the attempt. Being able to paste the code makes that
 * recoverable instead of fatal.
 */
@Composable
fun ConnectionCourse(model: UiModel) {
    val auth by model.authState.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val connected = model.connected()
    val waiting = auth.step == AuthStep.WAITING || auth.step == AuthStep.EXCHANGING
    var manual by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    connected -> "Connected as upcroc ai"
                    waiting -> "Waiting for Upwork"
                    else -> "Set up upcroc"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            Step(
                done = connected,
                active = !connected && !waiting,
                label = "Sign in to Upwork",
                detail = if (connected) "Token stored on this device"
                else "Opens the Upwork authorization page in your browser",
            ) {
                if (!connected && !waiting) {
                    Button(onClick = { model.beginAuth() }) {
                        Icon(Icons.Filled.AccountCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(IconSpacing))
                        Text("Connect")
                    }
                }
            }

            if (waiting) {
                Step(
                    done = false,
                    active = true,
                    label = "Approve in browser",
                    detail = "Then come back here — do not close the app",
                ) {
                    if (auth.url.isNotEmpty()) {
                        Button(onClick = { model.openAuthLink(auth.url) }) {
                            Icon(Icons.Filled.ArrowForward, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(IconSpacing))
                            Text("Authorize upcroc ai")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            auth.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Offered straight away rather than after the socket
                        // gives up: waiting five minutes to discover the paste
                        // path exists is not a recovery flow.
                        TextButton(onClick = { manual = !manual }) {
                            Text(if (manual) "Hide manual entry" else "Enter code manually")
                        }
                        if (manual) {
                            Recovery(model, "", auth.pasteHint)
                        }
                    }
                }
            }

            if (auth.step == AuthStep.NEEDS_PASTE || auth.step == AuthStep.FAILED) {
                Recovery(model, auth.error, auth.pasteHint)
            }

            Step(
                done = settings.watching,
                active = connected && !settings.watching,
                label = "Start watching",
                detail = "Runs one check per bucket cycle",
            ) {
                if (connected) {
                    if (settings.watching) {
                        TextButton(onClick = { model.setWatching(false) }) { Text("Pause") }
                    } else {
                        Button(onClick = { model.setWatching(true) }) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(IconSpacing))
                            Text("Start watching")
                        }
                    }
                }
            }

            if (!model.notificationsAllowed()) {
                Text(
                    text = "Notifications are blocked, so new jobs will be silent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Recovery(model: UiModel, error: String, hint: String) {
    var pasted by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = error.ifEmpty { "the browser could not reach upcroc" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Authorization code") },
            supportingText = {
                Text(
                    if (hint.isNotEmpty())
                        "Paste the code, or the whole address ending in /callback"
                    else "Paste the code from the browser address bar",
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = { model.submitCode(pasted) },
                enabled = pasted.isNotBlank(),
            ) {
                Icon(Icons.Filled.Done, null, Modifier.size(18.dp))
                Spacer(Modifier.width(IconSpacing))
                Text("Finish sign-in")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { model.beginAuth() }) { Text("Start over") }
        }
    }
}

@Composable
private fun Step(
    done: Boolean,
    active: Boolean,
    label: String,
    detail: String,
    action: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // RadioButton is the Material affordance for a selected step; it is
        // non-interactive here, so no handler.
        RadioButton(selected = done, onClick = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

private val IconSpacing = 8.dp