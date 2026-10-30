package com.antoxa78.bitperfectremote.data

// Playlist/cue documents (*.m3u, *.m3u8, *.pls, *.cue) are not directly playable
// audio: their contents have to be expanded into the queue with the "load" command.
// Kept in one place so the client and the browser agree on what counts as one.
internal fun isPlaylistFilePath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".pls") || lower.endsWith(".cue")
}

internal fun isCueSheetPath(path: String): Boolean = path.lowercase().endsWith(".cue")
