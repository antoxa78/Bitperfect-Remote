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
