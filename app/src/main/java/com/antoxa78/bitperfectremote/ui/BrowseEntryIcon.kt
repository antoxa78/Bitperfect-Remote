package com.antoxa78.bitperfectremote.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.antoxa78.bitperfectremote.data.BrowseEntry
import com.antoxa78.bitperfectremote.data.isCueSheetPath
import com.antoxa78.bitperfectremote.data.isPlaylistFilePath

// Long file names are ellipsized before their extension becomes visible, so the
// icon has to tell cue sheets and playlists apart from ordinary audio tracks.
internal fun browseEntryIcon(entry: BrowseEntry): ImageVector = when {
    entry.isDirectory && entry.file.contains("://") -> Icons.Filled.Storage
    entry.isDirectory -> Icons.Default.Folder
    isCueSheetPath(entry.file) -> Icons.Default.Description
    entry.isStoredPlaylist || isPlaylistFilePath(entry.file) -> Icons.AutoMirrored.Filled.QueueMusic
    else -> Icons.Default.Audiotrack
}
