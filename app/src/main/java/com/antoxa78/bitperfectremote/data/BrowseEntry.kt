package com.antoxa78.bitperfectremote.data

data class BrowseEntry(
    val file: String,
    val title: String,
    val artist: String,
    val album: String,
    val isDirectory: Boolean,
    // True when lsinfo reported this as a "playlist:" record, i.e. a stored playlist that
    // lives in the player's playlist directory and is addressed by bare name rather than
    // by a path into the music library.
    val isStoredPlaylist: Boolean = false
)
