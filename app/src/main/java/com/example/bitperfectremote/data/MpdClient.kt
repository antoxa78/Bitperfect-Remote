package com.example.bitperfectremote.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MpdClient {
    companion object {
        private const val TAG = "MpdClient"
    }

    private var socket: Socket? = null
    private var inputStream: BufferedInputStream? = null
    private var writer: PrintWriter? = null
    private var host: String = ""
    private var port: Int = 6600
    private val mutex = Mutex()

    private fun readLine(bis: BufferedInputStream): String {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = bis.read()
            if (b == -1) throw java.io.IOException("Connection closed")
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString("UTF-8")
    }

    suspend fun connect(host: String, port: Int = 6600, password: String = ""): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                disconnectInternal()
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 3000)
                sock.soTimeout = 10000 // Prevent socket hangs/freezes; generous for online art lookups
                socket = sock
                inputStream = BufferedInputStream(sock.getInputStream())
                writer = PrintWriter(sock.getOutputStream(), true)

                val bis = inputStream ?: return@withContext false
                val greeting = readLine(bis)
                if (greeting.startsWith("OK MPD")) {
                    this@MpdClient.host = host
                    this@MpdClient.port = port
                    
                    if (password.isNotEmpty()) {
                        val w = writer ?: return@withContext false
                        w.println("password $password")
                        val line = readLine(bis)
                        if (!line.startsWith("OK")) {
                            Log.e(TAG, "Password authentication failed: $line")
                            disconnectInternal()
                            return@withContext false
                        }
                    }

                    Log.d(TAG, "Connected to MPD server at $host:$port")
                    true
                } else {
                    disconnectInternal()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                disconnectInternal()
                false
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {}
    }

    private fun disconnectInternal() {
        try {
            writer?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            writer = null
            inputStream = null
            socket = null
        }
    }

    val isConnected: Boolean
        get() = socket != null && socket!!.isConnected && !socket!!.isClosed

    suspend fun sendCommand(command: String): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val w = writer ?: throw IllegalStateException("Not connected")
            val bis = inputStream ?: throw IllegalStateException("Not connected")

            w.println(command)
            val lines = mutableListOf<String>()
            while (true) {
                val line = readLine(bis)
                if (line == "OK") break
                if (line.startsWith("ACK")) {
                    throw Exception("MPD Error: $line")
                }
                lines.add(line)
            }
            lines
        }
    }

    suspend fun getAlbumArt(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        if (uri.isBlank()) return@withContext null
        mutex.withLock {
            val w = writer ?: return@withContext null
            val bis = inputStream ?: return@withContext null

            try {
                // MPD binary response format:
                //   size: <n>
                //   binary: <n>
                //   <n raw bytes>
                //   OK
                // Attempt albumart first, then fall back to readpicture.
                w.println("albumart \"$uri\" 0")
                var result = readBinaryResponse(bis)
                if (result == null) {
                    w.println("readpicture \"$uri\" 0")
                    result = readBinaryResponse(bis)
                }
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch album art", e)
            }
            null
        }
    }

    private fun readBinaryResponse(bis: BufferedInputStream): ByteArray? {
        var binarySize = -1
        while (true) {
            val line = readLine(bis)
            if (line == "OK") break
            if (line.startsWith("ACK")) return null
            if (line.startsWith("size: ")) continue
            if (line.startsWith("binary: ")) {
                binarySize = line.substringAfter("binary: ").trim().toIntOrNull() ?: -1
                break // raw binary data follows immediately after this line
            }
        }
        if (binarySize <= 0) return null

        val buffer = ByteArray(binarySize)
        var totalRead = 0
        while (totalRead < binarySize) {
            val read = bis.read(buffer, totalRead, binarySize - totalRead)
            if (read == -1) break
            totalRead += read
        }

        // Consume trailing newline + OK line
        while (true) {
            val line = readLine(bis)
            if (line == "OK" || line.startsWith("ACK")) break
        }
        return buffer
    }

    suspend fun getStatus(): PlayerStatus = withContext(Dispatchers.IO) {
        val lines = sendCommand("status")
        var state = "stop"
        var volume = 100
        var repeat = false
        var random = false
        var songPos = -1
        var songId = -1
        var elapsed = 0f
        var duration = 0f
        var bitrate = 0
        var sampleRate = 0
        var bitDepth = 0
        var channels = 0

        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0]) {
                    "state" -> state = parts[1]
                    "volume" -> volume = parts[1].toIntOrNull() ?: 100
                    "repeat" -> repeat = parts[1] == "1"
                    "random" -> random = parts[1] == "1"
                    "song" -> songPos = parts[1].toIntOrNull() ?: -1
                    "songid" -> songId = parts[1].toIntOrNull() ?: -1
                    "elapsed" -> elapsed = parts[1].toFloatOrNull() ?: 0f
                    "duration" -> duration = parts[1].toFloatOrNull() ?: 0f
                    "bitrate" -> bitrate = parts[1].toIntOrNull() ?: 0
                    "audio" -> {
                        // Format: sampleRate:bitDepth:channels e.g. "44100:24:2"
                        val a = parts[1].split(":")
                        if (a.size >= 1) sampleRate = a[0].toIntOrNull() ?: 0
                        if (a.size >= 2) bitDepth = a[1].toIntOrNull() ?: 0
                        if (a.size >= 3) channels = a[2].toIntOrNull() ?: 0
                    }
                }
            }
        }
        PlayerStatus(state, volume, repeat, random, songPos, songId, elapsed, duration, bitrate, sampleRate, bitDepth, channels)
    }

    suspend fun getCurrentSong(): TrackInfo = withContext(Dispatchers.IO) {
        val lines = sendCommand("currentsong")
        var id = ""
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0f
        var file = ""

        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "id" -> id = parts[1]
                    "title" -> title = parts[1]
                    "artist" -> artist = parts[1]
                    "album" -> album = parts[1]
                    "duration" -> duration = parts[1].toFloatOrNull() ?: 0f
                    "file" -> file = parts[1]
                }
            }
        }
        if (title.isEmpty() && file.isNotEmpty()) {
            title = file.substringAfterLast('/')
        }
        TrackInfo(id, title.ifEmpty { "Unknown Title" }, artist.ifEmpty { "Unknown Artist" }, album.ifEmpty { "Unknown Album" }, duration, file)
    }

    suspend fun getPlaylist(): List<QueueItem> = withContext(Dispatchers.IO) {
        val lines = sendCommand("playlistinfo")
        val items = mutableListOf<QueueItem>()
        var currId = ""
        var currPos = -1
        var currTitle = ""
        var currArtist = ""
        var currAlbum = ""
        var currFile = ""

        for (line in lines) {
            val parts = line.split(": ", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "file" -> {
                        if (currFile.isNotEmpty()) {
                            items.add(QueueItem(currId, currPos, currTitle.ifEmpty { currFile.substringAfterLast('/') }, currArtist.ifEmpty { "Unknown Artist" }, currAlbum))
                            currId = ""; currPos = -1; currTitle = ""; currArtist = ""; currAlbum = ""
                        }
                        currFile = parts[1]
                    }
                    "id" -> currId = parts[1]
                    "pos" -> currPos = parts[1].toIntOrNull() ?: -1
                    "title" -> currTitle = parts[1]
                    "artist" -> currArtist = parts[1]
                    "album" -> currAlbum = parts[1]
                }
            }
        }
        if (currFile.isNotEmpty()) {
            items.add(QueueItem(currId, currPos, currTitle.ifEmpty { currFile.substringAfterLast('/') }, currArtist.ifEmpty { "Unknown Artist" }, currAlbum))
        }
        items
    }

    suspend fun play() = sendCommand("play")
    suspend fun pause() = sendCommand("pause 1")
    suspend fun resume() = sendCommand("pause 0")
    suspend fun stop() = sendCommand("stop")
    suspend fun next() = sendCommand("next")
    suspend fun previous() = sendCommand("previous")
    suspend fun setVolume(vol: Int) = sendCommand("setvol $vol")
    suspend fun seek(pos: Float) = sendCommand("seekcur $pos")
    suspend fun playId(id: String) = sendCommand("playid $id")
    suspend fun clear() = sendCommand("clear")
    suspend fun setRepeat(enable: Boolean) = sendCommand(if (enable) "repeat 1" else "repeat 0")
    suspend fun setRandom(enable: Boolean) = sendCommand(if (enable) "random 1" else "random 0")
}
