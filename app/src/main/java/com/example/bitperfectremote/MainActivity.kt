package com.example.bitperfectremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bitperfectremote.ui.PlayerViewModel
import com.example.bitperfectremote.ui.screens.ConnectionScreen
import com.example.bitperfectremote.ui.screens.MainRemoteScreen
import com.example.bitperfectremote.ui.screens.MusicBrowserScreen
import com.example.bitperfectremote.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: PlayerViewModel = viewModel()
            val isDarkTheme = when (viewModel.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            // Auto-connect on startup if saved address exists
            LaunchedEffect(Unit) {
                if (viewModel.savedIp.isNotBlank() && viewModel.savedIp != "192.168.1.") {
                    val p = viewModel.savedPort.toIntOrNull() ?: 6600
                    viewModel.connect(viewModel.savedIp, p, viewModel.savedPassword, true)
                }
            }

            // Auto-reconnect when screen turns on / app resumes
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (!viewModel.isConnected.value && !viewModel.isConnecting.value &&
                            viewModel.savedIp.isNotBlank() && viewModel.savedIp != "192.168.1.") {
                            val p = viewModel.savedPort.toIntOrNull() ?: 6600
                            viewModel.connect(viewModel.savedIp, p, viewModel.savedPassword, true)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MaterialTheme(
                colorScheme = if (isDarkTheme) {
                    val a = accentColor(viewModel.themeColor)
                    darkColorScheme(
                        primary = a,
                        primaryContainer = a.copy(alpha = 0.28f),
                        onPrimaryContainer = a.copy(alpha = 0.95f),
                        secondaryContainer = a.copy(alpha = 0.18f),
                        onSecondaryContainer = a.copy(alpha = 0.9f)
                    )
                } else {
                    val a = accentColor(viewModel.themeColor)
                    lightColorScheme(
                        primary = a,
                        primaryContainer = a.copy(alpha = 0.16f),
                        onPrimaryContainer = a.copy(alpha = 0.95f),
                        secondaryContainer = a.copy(alpha = 0.10f),
                        onSecondaryContainer = a.copy(alpha = 0.9f)
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isConnected by viewModel.isConnected.collectAsState()
                    var currentScreen by remember { mutableStateOf("main") } // "main", "settings", "browser"

                    // If we have a saved IP on first launch, start trying to connect or jump right to player screen
                    val hasSavedServer = viewModel.savedIp.isNotBlank() && viewModel.savedIp != "192.168.1."

                    when (currentScreen) {
                        "settings" -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "main" }
                            )
                        }
                        "browser" -> {
                            MusicBrowserScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = "main" }
                            )
                        }
                        "connection" -> {
                            ConnectionScreen(
                                viewModel = viewModel,
                                onConnected = { currentScreen = "main" }
                            )
                        }
                        else -> {
                            if (isConnected || hasSavedServer) {
                                MainRemoteScreen(
                                    viewModel = viewModel,
                                    onDisconnect = {
                                        currentScreen = "connection"
                                        viewModel.disconnect()
                                    },
                                    onOpenSettings = { currentScreen = "settings" },
                                    onOpenBrowser = { currentScreen = "browser" }
                                )
                            } else {
                                ConnectionScreen(
                                    viewModel = viewModel,
                                    onConnected = {
                                        currentScreen = "main"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Accent color palette selectable from the Application Themes menu.
fun accentColor(key: String): Color = when (key) {
    "green" -> Color(0xFF10B981)
    "purple" -> Color(0xFF8B5CF6)
    "orange" -> Color(0xFFF97316)
    "red" -> Color(0xFFEF4444)
    "teal" -> Color(0xFF14B8A6)
    "pink" -> Color(0xFFEC4899)
    "indigo" -> Color(0xFF6366F1)
    else -> Color(0xFF3B82F6) // blue (default)
}
