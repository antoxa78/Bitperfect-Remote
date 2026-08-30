package com.antoxa78.bitperfectremote

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
import com.antoxa78.bitperfectremote.ui.PlayerViewModel
import com.antoxa78.bitperfectremote.ui.screens.ConnectionScreen
import com.antoxa78.bitperfectremote.ui.screens.MainRemoteScreen
import com.antoxa78.bitperfectremote.ui.screens.MusicBrowserScreen
import com.antoxa78.bitperfectremote.ui.screens.NetworkSharesScreen
import com.antoxa78.bitperfectremote.ui.screens.SettingsScreen

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

            val accent = accentColor(viewModel.themeColor)
            val baseScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            val surfaceTint = 0.06f
            val surfaceVariantTint = 0.14f
            val secondaryContainerTint = 0.18f
            val primaryContainerTint = if (isDarkTheme) 0.28f else 0.18f

            val accentLuminance = accent.luminance()
            // A very dark accent (e.g. black) makes accent-derived "on" colors
            // unreadable against the darkened containers, so fall back to the
            // base scheme's theme-aware on-colors in that case. We use the
            // neutral on-surface colors because the default Material 3 container
            // on-colors carry a purple tint that becomes visible with a black
            // accent.
            val isDarkAccent = accentLuminance <= 0.3f
            // Pick the higher-contrast text/icon color on solid accent surfaces.
            // A dark accent (lum <= 0.3, e.g. black) always needs white on top in
            // both light and dark themes. Bright/medium accents get black. The
            // blind crossover is ~0.18 lum, but 0.3 keeps darker blue/indigo/purple
            // on white while flipping light/medium tones like neon and teal to
            // black (white on the base scheme's onPrimary is a dark purple in dark
            // mode, which would be invisible on a solid black accent).
            val onAccent = if (isDarkAccent) Color.White else Color.Black
            val onContainerFallback = baseScheme.onSurface

            MaterialTheme(
                colorScheme = baseScheme.copy(
                    primary = accent,
                    onPrimary = onAccent,
                    primaryContainer = accent.copy(alpha = primaryContainerTint),
                    onPrimaryContainer = if (isDarkAccent) onContainerFallback else accent.copy(alpha = 0.95f),
                    secondary = accent,
                    onSecondary = onAccent,
                    secondaryContainer = accent.copy(alpha = secondaryContainerTint),
                    onSecondaryContainer = if (isDarkAccent) onContainerFallback else accent.copy(alpha = 0.9f),
                    surface = baseScheme.surface.blendWith(accent, surfaceTint),
                    onSurface = baseScheme.onSurface,
                    surfaceVariant = baseScheme.surfaceVariant.blendWith(accent, surfaceVariantTint),
                    onSurfaceVariant = if (isDarkAccent) onContainerFallback.copy(alpha = 0.74f) else baseScheme.onSurfaceVariant.blendWith(accent, 0.35f),
                    background = baseScheme.background.blendWith(accent, surfaceTint),
                    onBackground = baseScheme.onBackground,
                    outline = if (isDarkAccent) onContainerFallback.copy(alpha = 0.38f) else accent.copy(alpha = 0.4f),
                    outlineVariant = if (isDarkAccent) onContainerFallback.copy(alpha = 0.16f) else accent.copy(alpha = 0.2f)
                )
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
                        "shares" -> {
                            NetworkSharesScreen(
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
                                    onOpenBrowser = { currentScreen = "browser" },
                                    onOpenShares = { currentScreen = "shares" }
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

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

private fun Color.blendWith(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red * (1 - r) + other.red * r,
        green = green * (1 - r) + other.green * r,
        blue = blue * (1 - r) + other.blue * r,
        alpha = alpha * (1 - r) + other.alpha * r
    )
}

// Accent color palette selectable from the Application Themes menu.
fun accentColor(key: String): Color = when (key) {
    "neon" -> Color(0xFF00F0FF)
    "black" -> Color(0xFF000000)
    "purple" -> Color(0xFF8B5CF6)
    "teal" -> Color(0xFF14B8A6)
    "pink" -> Color(0xFFEC4899)
    "indigo" -> Color(0xFF6366F1)
    else -> Color(0xFF3B82F6) // blue (default)
}
