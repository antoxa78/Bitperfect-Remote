package com.antoxa78.bitperfectremote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MpdLibraryClient(private val client: MpdClient) {

    // The Bitperfect MPD server rejects relative paths ("relative paths not supported").
    // Always send absolute path arguments.
    private fun toAbsolutePath(path: String): String {
        val trimmed = path.trim()
        return if (trimmed.isEmpty() || trimmed.startsWith("/")) trimmed else "/$trimmed"
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
        client.sendCommand("add \"${toAbsolutePath(uri)}\"")
    }

    suspend fun loadFolderToQueue(uri: String, replace: Boolean) = withContext(Dispatchers.IO) {
        if (replace) {
            client.clear()
        }
        // MPD add command adds all files in directory recursively if uri is a directory
        client.sendCommand("add \"${toAbsolutePath(uri)}\"")
        client.play()
    }
}
