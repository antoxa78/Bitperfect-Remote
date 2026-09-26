package com.antoxa78.bitperfectremote.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shared metrics for the action bars at the bottom of the Music Browser and the
 * Network Shares screens. Both screens hold three actions in a row, so the
 * metrics live here to guarantee that every button renders at exactly the same
 * size in every menu and at every folder level.
 */

/** Fixed height of every action bar button; equal to the height used in the dialogs. */
val ActionBarButtonHeight = 48.dp

/**
 * Tighter than the Material default so longer labels such as "Add to Playlist"
 * still fit in a third of the bar on narrow screens.
 */
val ActionBarButtonPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)

/**
 * Label for an action bar button. Wrapping is disabled and the height is fixed,
 * so a long label is ellipsized instead of growing a single button taller than
 * its neighbours.
 */
@Composable
fun ActionBarButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}
