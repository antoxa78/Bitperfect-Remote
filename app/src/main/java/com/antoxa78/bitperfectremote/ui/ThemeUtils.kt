package com.antoxa78.bitperfectremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

// The accent color, but if it's too dark to read against the current surface
// (e.g. a black accent in dark mode), fall back to the theme's on-surface color
// so text/icons/tinted buttons remain visible. Bright accents keep their tint.
@Composable
fun onSurfaceAccentColor(): Color =
    if (MaterialTheme.colorScheme.primary.luminance() < 0.3f)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.primary

// Single source of truth for the accent color palette selectable from the
// Application Themes menu. MainActivity derives the app's MaterialTheme from
// accentColor(key), and the theme picker swatches in MainRemoteScreen are
// built from accentColorPalette, so the two can never drift out of sync.
val accentColorPalette: List<Pair<String, Color>> = listOf(
    "blue" to Color(0xFF3B82F6),
    "neon" to Color(0xFF00F0FF),
    "black" to Color(0xFF000000),
    "purple" to Color(0xFF8B5CF6),
    "teal" to Color(0xFF14B8A6),
    "pink" to Color(0xFFEC4899),
    "indigo" to Color(0xFF6366F1)
)

fun accentColor(key: String): Color =
    accentColorPalette.firstOrNull { it.first == key }?.second ?: accentColorPalette.first().second
