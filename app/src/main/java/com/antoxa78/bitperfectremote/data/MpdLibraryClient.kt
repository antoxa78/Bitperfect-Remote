package com.antoxa78.bitperfectremote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MpdLibraryClient(private val client: MpdClient) {

    // The Bitperfect MPD server rejects relative paths ("relative paths not supported").
    // Always send absolute path arguments. Scheme URIs (smb://, http(s)://, content://)
    // are already absolute and must not gain a leading slash.
    private fun toAbsolutePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.contains("://")) return trimmed
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    // Playlist files (*.m3u, *.m3u8, *.pls) are not directly playable audio: MPD
    // must load their contents into the queue via the "load" command. Sending them
    // through "add" yields an "not playable file" server error.
    private fun isPlaylistFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".pls")
    }

    suspend fun lsinfo(path: String = ""): List<BrowseEntry> = withContext(Dispatchers.IO) {
        val absPath = toAbsolutePath(path)
        val cmd = if (absPath.isEmpty()) "lsinfo" else "lsinfo \"$absPath\""
        val lines = client.sendCommand(cmd)

        val entries = mutableListOf<BrowseEntry>()
        var currFile = ""
        var currTitle = ""
        var currArtist = ""
        var currAlbum = ""
        var isDir = false

        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "file", "directory", "playlist" -> {
                        if (currFile.isNotEmpty()) {
                            entries.add(BrowseEntry(currFile, currTitle.ifEmpty { currFile.substringAfterLast('/') }, currArtist, currAlbum, isDir))
                            currFile = ""; currTitle = ""; currArtist = ""; currAlbum = ""; isDir = false
                        }
                        currFile = parts[1]
                        isDir = parts[0].lowercase() == "directory"
                    }
                    "title" -> currTitle = parts[1]
                    "artist" -> currArtist = parts[1]
                    "album" -> currAlbum = parts[1]
                }
            }
        }
        if (currFile.isNotEmpty()) {
            entries.add(BrowseEntry(currFile, currTitle.ifEmpty { currFile.substringAfterLast('/') }, currArtist, currAlbum, isDir))
        }
        entries
    }

    suspend fun addUri(uri: String) = withContext(Dispatchers.IO) {
        val abs = toAbsolutePath(uri)
        if (isPlaylistFile(uri)) {
            client.sendCommand("load \"$abs\"")
        } else {
            client.sendCommand("add \"$abs\"")
        }
    }

    suspend fun loadFolderToQueue(uri: String, replace: Boolean) = withContext(Dispatchers.IO) {
        if (replace) {
            client.clear()
        }
        val abs = toAbsolutePath(uri)
        if (isPlaylistFile(uri)) {
            // Load a playlist file's contents rather than trying to add it as audio.
            client.sendCommand("load \"$abs\"")
        } else {
            // MPD add command adds all files in directory recursively if uri is a directory
            client.sendCommand("add \"$abs\"")
        }
        // Only start playback when replacing the queue; "Add to Playlist" (replace=false)
        // should queue silently without interrupting or starting playback.
        if (replace) {
            client.play()
        }
    }
}
