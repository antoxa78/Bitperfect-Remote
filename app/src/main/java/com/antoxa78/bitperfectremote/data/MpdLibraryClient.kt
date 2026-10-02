package com.antoxa78.bitperfectremote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Raised when the player cannot expand a playlist file that the browser listed. */
class PlaylistNotReadableException(message: String) : Exception(message)

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

    // Playlist/cue documents (*.m3u, *.m3u8, *.pls, *.cue) are not directly playable
    // audio: their contents have to be expanded into the queue with the "load" command.
    private fun isPlaylistFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".pls") || lower.endsWith(".cue")
    }

    private fun isCueSheet(path: String): Boolean = path.lowercase().endsWith(".cue")

    // "load" is the only command that expands a playlist into queue entries, but MPD can
    // only do so for playlists it is able to open. Names in the playlist directory always
    // work, and since the playlist plugins of MPD 0.24 so do paths in the music directory.
    // A remote URI additionally needs an input plugin for that scheme, which players with
    // built-in share support (Bitperfect) do not provide: they answer "No such playlist"
    // for a share they otherwise browse and play from without trouble. Report that as the
    // limitation it is instead of letting the raw protocol line reach the user.
    private suspend fun loadPlaylist(name: String) {
        try {
            client.sendCommand("load \"$name\"")
        } catch (e: MpdCommandException) {
            if (e.isNoSuchFile && name.contains("://")) {
                throw PlaylistNotReadableException(
                    "Player can't read playlists from network shares"
                )
            }
            throw e
        }
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
        var isStoredPlaylist = false

        fun flush() {
            if (currFile.isNotEmpty()) {
                entries.add(
                    BrowseEntry(
                        currFile,
                        currTitle.ifEmpty { currFile.substringAfterLast('/') },
                        currArtist,
                        currAlbum,
                        isDir,
                        isStoredPlaylist
                    )
                )
            }
            currFile = ""; currTitle = ""; currArtist = ""; currAlbum = ""
            isDir = false; isStoredPlaylist = false
        }

        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "file", "directory", "playlist" -> {
                        flush()
                        currFile = parts[1]
                        isDir = parts[0].lowercase() == "directory"
                        isStoredPlaylist = parts[0].lowercase() == "playlist"
                    }
                    "title" -> currTitle = parts[1]
                    "artist" -> currArtist = parts[1]
                    "album" -> currAlbum = parts[1]
                }
            }
        }
        flush()

        // MPD's database, which lsinfo reads, hides cue sheets: a .cue file is turned
        // into virtual tracks of the audio file it references, and the .cue document
        // itself never appears in the listing. listfiles reports every file on disk,
        // so merge the cue sheets it finds back in to make them browsable.
        val listedFiles = entries.mapTo(HashSet()) { it.file }
        for (cueSheet in listCueSheets(absPath)) {
            if (cueSheet.file !in listedFiles) {
                entries.add(cueSheet)
            }
        }
        entries
    }

    // Lists the directory with the "listfiles" command -- which, unlike the
    // database-backed lsinfo, returns every file on disk -- and keeps only the cue
    // sheets, the one kind of music document MPD deliberately omits from the database.
    private suspend fun listCueSheets(absPath: String): List<BrowseEntry> {
        val cmd = if (absPath.isEmpty()) "listfiles" else "listfiles \"$absPath\""
        val lines = try {
            client.sendCommand(cmd)
        } catch (e: Exception) {
            // The server may not implement listfiles. The lsinfo listing above already
            // succeeded, so browsing must continue without the extra cue sheets.
            emptyList()
        }

        val cueSheets = mutableListOf<BrowseEntry>()
        var currFile = ""
        var isDirectory = false
        fun flush() {
            if (currFile.isNotEmpty() && !isDirectory && isCueSheet(currFile)) {
                cueSheets.add(
                    BrowseEntry(
                        currFile,
                        currFile.substringAfterLast('/'),
                        "",
                        "",
                        false,
                        false
                    )
                )
            }
            currFile = ""
            isDirectory = false
        }
        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "file" -> {
                        flush()
                        currFile = parts[1]
                    }
                    "directory" -> {
                        flush()
                        currFile = parts[1]
                        isDirectory = true
                    }
                    // Ignore trailing attributes such as "size" and "Last-Modified",
                    // which belong to the entry above them.
                }
            }
        }
        flush()
        return cueSheets
    }

    /**
     * Queues one browsed entry. [storedPlaylist] marks entries the server reported as
     * "playlist:" in lsinfo: those live in the playlist directory and are addressed by
     * bare name, so they must not be turned into a path the way song URIs are.
     */
    suspend fun addUri(uri: String, storedPlaylist: Boolean = false) = withContext(Dispatchers.IO) {
        when {
            storedPlaylist -> loadPlaylist(uri)
            isPlaylistFile(uri) -> loadPlaylist(toAbsolutePath(uri))
            else -> client.sendCommand("add \"${toAbsolutePath(uri)}\"")
        }
    }

    /**
     * Queues [uri], replacing the current queue when [replace] is set. The new entries are
     * queued first and the previous ones dropped afterwards, so a rejected URI (an
     * unreadable playlist, an unreachable share) leaves the existing queue intact instead
     * of clearing it and only then failing.
     *
     * The old entries are removed only if they are verifiably still at the front of the
     * queue. Standard MPD appends on "load"/"add", but a server that replaces the queue on
     * "load" would otherwise have "delete 0:N" cut the first N songs of the freshly
     * loaded playlist (all of them when the old queue was at least as long).
     */
    suspend fun loadFolderToQueue(
        uri: String,
        replace: Boolean,
        storedPlaylist: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val previous = if (replace) client.getQueueSnapshot() else emptyList()
        addUri(uri, storedPlaylist)
        if (replace) {
            if (previous.isNotEmpty()) {
                val current = client.getQueueSnapshot()
                val appended = current.size > previous.size &&
                    current.subList(0, previous.size) == previous
                if (appended) {
                    client.deleteRange(0, previous.size)
                }
            }
            // Only start playback when replacing the queue; "Add to Playlist" (replace=false)
            // should queue silently without interrupting or starting playback.
            client.play()
        }
    }
}
