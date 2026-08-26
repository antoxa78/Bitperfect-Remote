package com.antoxa78.bitperfectremote.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves album artwork for internet radio streams. MPD's `albumart`/`readpicture`
 * commands only work for local files, so for streams we match the currently playing
 * track ("Artist - Title" from the stream metadata) against online catalogs and return
 * the artwork URL. Coil then loads the image directly from that URL.
 */
object OnlineArtwork {
    private const val TAG = "OnlineArtwork"

    private const val TIMEOUT_MS = 5000

    suspend fun resolveTrackArtwork(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            deezerArtwork(artist, title) ?: itunesArtwork(artist, title)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve artwork", e)
            null
        }
    }

    private fun deezerArtwork(artist: String, title: String): String? {
        val rawQuery = buildString {
            append("track:\"")
            append(title)
            append("\"")
            if (artist.isNotBlank()) {
                append(" artist:\"")
                append(artist)
                append("\"")
            }
        }
        val url = "https://api.deezer.com/search?q=${URLEncoder.encode(rawQuery, "UTF-8")}&limit=1"
        val body = httpGet(url) ?: return null
        return jsonString(body, "cover_big") ?: jsonString(body, "cover_medium")
    }

    private fun itunesArtwork(artist: String, title: String): String? {
        val raw = if (artist.isBlank()) title else "$artist $title"
        val term = URLEncoder.encode(raw, "UTF-8").replace("+", "%20")
        val url = "https://itunes.apple.com/search?term=$term&entity=song&media=music&limit=1"
        val body = httpGet(url) ?: return null
        val art = jsonString(body, "artworkUrl100") ?: return null
        // iTunes serves artwork at the size encoded in the URL path; upscale to 600x600.
        return art.replace("100x100bb", "600x600bb")
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "BitperfectRemote/1.1")
            conn.instanceFollowRedirects = true
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // Minimal JSON string-value extractor: returns the first value of `"key": "value"`.
    private fun jsonString(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\":")
        if (keyIdx < 0) return null
        val start = json.indexOf('"', keyIdx + key.length + 2)
        if (start < 0) return null
        val sb = StringBuilder()
        var i = start + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (val n = json[i + 1]) {
                    'u' -> {
                        // substring(i+2, i+6) needs indices i+2..i+5, so i+5 must be valid,
                        // i.e. i+6 <= json.length (the original i+6 < json.length was off by one).
                        if (i + 6 <= json.length) {
                            val hex = json.substring(i + 2, i + 6).toIntOrNull(16)
                            sb.append(hex?.toChar() ?: '?')
                        }
                        i += 6
                    }
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(n); i += 2 }
                }
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
