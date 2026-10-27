package com.upcroc.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Destination(val label: String, val icon: ImageVector)

private val DESTINATIONS = listOf(
    Destination("Now", Icons.Filled.Notifications),
    Destination("History", Icons.Filled.List),
    Destination("Sources", Icons.Filled.Search),
    Destination("Setup", Icons.Filled.Settings),
)

@Composable
fun UpcrocScreen(model: UiModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var exiting by remember { mutableStateOf(false) }
    val settings by model.settings.collectAsStateWithLifecycle()

    // Leaving is a decision, never an accident: hiding keeps everything
    // resident, killing actually ends it.
    BackHandler { exiting = true }

    Scaffold(
        bottomBar = {
            NavigationBar {
                DESTINATIONS.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionCourse(model)
            when (tab) {
                0 -> TickerTab(model)
                1 -> HistoryTab(model)
                2 -> SourcesTab(model)
                else -> SetupTab(model)
            }
        }
    }

    if (exiting) {
        val act = findActivity()
        ExitDialog(
            watching = settings.watching,
            connected = model.connected(),
            jobsStored = model.jobCount,
            onHide = {
                exiting = false
                act?.moveTaskToBack(true)
            },
            onKill = {
                exiting = false
                model.kill()
            },
            onDismiss = { exiting = false },
        )
    }
}

@Composable
private fun findActivity(): Activity? {
    var ctx: Any? = LocalContext.current
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}