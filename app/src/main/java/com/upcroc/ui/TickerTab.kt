package com.upcroc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Live bucket values. These are the numbers the engine is actually working
 * from, read straight from TickerState rather than recomputed in the UI.
 * Numbers only: no icons and no illustrative graphics here.
 */
@Composable
fun TickerTab(model: UiModel) {
    val ticker by model.ticker.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val nowSeconds = System.currentTimeMillis() / 1000
    val sessionHours =
        if (ticker.sessionStarted > 0) (nowSeconds - ticker.sessionStarted) / 3600.0 else 0.0
    val backoffLeft = (ticker.backoffUntil - nowSeconds).coerceAtLeast(0)

    // One unambiguous number, and one sentence saying what it counts down to.
    val (headline, caption) = when {
        ticker.inCheck -> "…" to "checking now"
        backoffLeft > 0 -> "$backoffLeft" to "seconds until retry after failure"
        settings.watching -> "${ticker.bucketLeft}" to "seconds until next check"
        ticker.bucketTotal > 0 -> "${ticker.bucketLeft}" to "paused — not checking"
        else -> "—" to "not watching"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The one number that matters, given the prominence it deserves.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                if (ticker.bucketTotal > 0) {
                    Spacer(Modifier.height(10.dp))
                    // A bar beats a number for "how far through am I". The
                    // numbers below stay authoritative.
                    LinearProgressIndicator(
                        progress = { ticker.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "drawn ${ticker.bucketTotal}s · " +
                            "${ticker.elapsedInBucket}s elapsed · " +
                            "${(ticker.progress * 100).toInt()}% drained",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Section("Right now")
        Num("state", stateLabel(ticker, settings.watching, backoffLeft))
        Num("bucket drawn", "${ticker.bucketTotal}")
        Num("bucket remaining", "${ticker.bucketLeft}")
        Num("elapsed in bucket", "${ticker.elapsedInBucket}")
        Num("drained", "${(ticker.progress * 100).toInt()}%")
        Num("session hours", String.format("%.2f", sessionHours))
        Num("retry in", "${backoffLeft}s")

        Section("Last check")
        Num("when", if (ticker.lastCheck > 0) ago(nowSeconds - ticker.lastCheck) else "never")
        Num("new", "${ticker.lastNew}")
        Num("notified", "${ticker.lastNotified}")
        Num("pages", "${ticker.lastPages}")
        Num("requests", "${ticker.lastRequests}")
        Num("seconds", "${ticker.lastSeconds}")

        Section("In effect")
        Num("base seconds", "${settings.bucketBaseSec}")
        Num("rotation seconds", "${settings.bucketJitterSec}")
        Num("page cap", "${settings.pageCap}")
        Num("pace ms", "${settings.paceMs}")
        Num("fresh window s", "${settings.freshWindowSec}")
        Num("late notify s", "${settings.lateNotifySec}")
        Num("retention days", "${settings.retentionDays}")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.watching, onCheckedChange = { model.setWatching(it) })
            Spacer(Modifier.width(12.dp))
            Text(if (settings.watching) "watching" else "not watching")
        }
    }
}

private fun ago(seconds: Long): String =
    if (seconds < 60) "${seconds}s ago"
    else if (seconds < 3600) "${seconds / 60}m ago"
    else "${seconds / 3600}h ago"

/** One word, so "idle" never has to cover three different situations. */
private fun stateLabel(t: com.upcroc.core.Ticker, watching: Boolean, backoffLeft: Long) =
    when {
        t.inCheck -> "checking"
        backoffLeft > 0 -> "retrying after failure"
        watching -> "waiting"
        else -> "paused"
    }

@Composable
internal fun Num(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(190.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
}