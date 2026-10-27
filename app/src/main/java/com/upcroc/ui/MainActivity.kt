package com.upcroc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.upcroc.UpcrocApp

class MainActivity : ComponentActivity() {

    private lateinit var model: UiModel

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as UpcrocApp).container
        model = container.ui
        // Resident from launch, not only once watching is switched on.
        model.ensureResident()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // No splash screen and no recreate on browser return: the Activity is
        // singleTask, so returning from the auth tab resumes this composition.
        setContent {
            val held = remember { model }
            AstryxTheme {
                UpcrocScreen(held)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}