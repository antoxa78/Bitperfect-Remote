package com.example.bitperfectremote.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bitperfectremote.data.MpdClient
import com.example.bitperfectremote.data.OnlineArtwork
import com.example.bitperfectremote.data.PlayerStatus
import com.example.bitperfectremote.data.QueueItem
import com.example.bitperfectremote.data.TrackInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val client = MpdClient()
    // Separate connection used only for album art, so a slow/hanging artwork
    // request (e.g. online lookup on the server) never blocks status polling
    // or triggers a reconnection.
    private val artClient = MpdClient()
    private val prefs = application.getSharedPreferences("BitperfectRemotePrefs", Context.MODE_PRIVATE)

    val savedIp: String get() = prefs.getString("saved_ip", "192.168.1.") ?: "192.168.1."
    val savedPort: String get() = prefs.getString("saved_port", "6600") ?: "6600"
    val savedPassword: String get() = prefs.getString("saved_password", "") ?: ""

    private var connHost = ""
    private var connPort = 6600
    private var connPassword = ""

    // Theme mode: "system", "dark", "light"
    var themeMode by mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
        private set

    // Accent color key: "blue", "green", "purple", "orange", "red", "teal", "pink", "indigo"
    var themeColor by mutableStateOf(prefs.getString("theme_color", "blue") ?: "blue")
        private set

    fun updateThemeMode(mode: String) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun updateThemeColor(color: String) {
        themeColor = color
        prefs.edit().putString("theme_color", color).apply()
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _status = MutableStateFlow(PlayerStatus())
    val status: StateFlow<PlayerStatus> = _status.asStateFlow()

    private val _currentSong = MutableStateFlow(TrackInfo())
    val currentSong: StateFlow<TrackInfo> = _currentSong.asStateFlow()

    private val _coverArt = MutableStateFlow<ByteArray?>(null)
    val coverArt: StateFlow<ByteArray?> = _coverArt.asStateFlow()

    // For radio streams MPD can't provide artwork, so we resolve an image URL
    // online from the current track's Artist/Title (see OnlineArtwork).
    private val _coverArtUrl = MutableStateFlow<String?>(null)
    val coverArtUrl: StateFlow<String?> = _coverArtUrl.asStateFlow()

    private var lastFetchedArtFile = ""
    private var artJob: Job? = null

    private val _playlist = MutableStateFlow<List<QueueItem>>(emptyList())
    val playlist: StateFlow<List<QueueItem>> = _playlist.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollJob: Job? = null
    private var explicitDisconnect = false

    fun connect(host: String, port: Int = 6600, password: String = "", remember: Boolean = true) {
        explicitDisconnect = false
        connHost = host
        connPort = port
        connPassword = password
        if (remember) {
            prefs.edit()
                .putString("saved_ip", host)
                .putString("saved_port", port.toString())
                .putString("saved_password", password)
                .apply()
        }

        viewModelScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null
            val success = client.connect(host, port, password)
            _isConnected.value = success
            _isConnecting.value = false
            if (success) {
                _errorMessage.value = null
                startPolling(host, port, password)
            } else {
                _errorMessage.value = "Failed to connect to $host:$port"
            }
        }
    }

    fun disconnect() {
        explicitDisconnect = true
        pollJob?.cancel()
        artJob?.cancel()
        client.disconnect()
        artClient.disconnect()
        _isConnected.value = false
        _errorMessage.value = null
    }

    private fun startPolling(host: String, port: Int, password: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Clear error immediately upon starting polling on a fresh connection
            _errorMessage.value = null
            while (!explicitDisconnect) {
                if (client.isConnected) {
                    try {
                        val s = client.getStatus()
                        _status.value = s
                        val song = client.getCurrentSong()
                        _currentSong.value = song
                        val pl = client.getPlaylist()
                        _playlist.value = pl
                        if (_errorMessage.value != null) {
                            _errorMessage.value = null
                        }
                        // Trigger artwork fetch asynchronously on a separate connection.
                        if (song.file.isNotBlank()) {
                            requestCoverArt(song.file, song.artist, song.title)
                        }
                    } catch (e: Exception) {
                        // Immediately drop connection and start reconnecting on first poll failure after sleep/wake
                        _isConnected.value = false
                        _errorMessage.value = "Connection lost. Reconnecting..."
                        client.disconnect()
                        attemptReconnection(host, port, password)
                        break
                    }
                } else {
                    _isConnected.value = false
                    _errorMessage.value = "Connection lost. Reconnecting..."
                    client.disconnect()
                    attemptReconnection(host, port, password)
                    break
                }
                delay(2000)
            }
        }
    }

    // Fetches cover art on a dedicated connection + coroutine. Failures here
    // must NEVER affect the main connection state or trigger reconnection.
    private fun requestCoverArt(file: String, artist: String, title: String) {
        val key = "$file\u0001$artist\u0001$title"
        if (file.isBlank() || key == lastFetchedArtFile) return
        lastFetchedArtFile = key
        _coverArt.value = null
        _coverArtUrl.value = null
        artJob?.cancel()
        artJob = viewModelScope.launch {
            try {
                if (file.startsWith("http://") || file.startsWith("https://")) {
                    // Radio stream: resolve the playing track's album art online.
                    var lookupArtist = artist
                    var lookupTitle = title
                    if (lookupArtist.isBlank() || lookupArtist == "Unknown Artist") {
                        val dash = title.indexOf(" - ")
                        if (dash > 0) {
                            lookupArtist = title.substring(0, dash).trim()
                            lookupTitle = title.substring(dash + 3).trim()
                        }
                    }
                    val url = OnlineArtwork.resolveTrackArtwork(lookupArtist, lookupTitle)
                    if (key == lastFetchedArtFile) {
                        _coverArtUrl.value = url
                    }
                } else {
                    if (!artClient.isConnected) {
                        artClient.connect(connHost, connPort, connPassword)
                    }
                    val art = artClient.getAlbumArt(file)
                    if (key == lastFetchedArtFile) {
                        _coverArt.value = art
                    }
                }
            } catch (e: Exception) {
                // Ignore artwork failures entirely - they must not trigger reconnects.
            }
        }
    }

    private suspend fun attemptReconnection(host: String, port: Int, password: String) {
        var attempts = 0
        while (!explicitDisconnect && !client.isConnected && attempts < 10) {
            attempts++
            delay(3000)
            try {
                // Ensure any previous broken socket is cleaned up before reconnecting
                client.disconnect()
                val success = client.connect(host, port, password)
                if (success) {
                    _isConnected.value = true
                    _errorMessage.value = null
                    startPolling(host, port, password)
                    return
                }
            } catch (e: Exception) {
                // retry
            }
        }
        if (!explicitDisconnect && !client.isConnected) {
            _errorMessage.value = "Reconnection failed. Please check connection."
        }
    }

    private inline fun safeAction(crossinline action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                refresh()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("closed", true) || 
                    msg.contains("Not connected", true) ||
                    msg.contains("Broken pipe", true) ||
                    msg.contains("Connection", true)) {
                    _isConnected.value = false
                    _errorMessage.value = "Connection lost. Reconnecting..."
                    client.disconnect()
                    // Use the actually-connected endpoint (not prefs, which may be
                    // stale when "remember" was unchecked).
                    val h = connHost.ifBlank { savedIp }
                    val p = if (connPort > 0) connPort else savedPort.toIntOrNull() ?: 6600
                    attemptReconnection(h, p, connPassword)
                } else {
                    _errorMessage.value = msg
                }
            }
        }
    }

    fun togglePlayPause() {
        safeAction {
            if (_status.value.state == "play") {
                client.pause()
            } else if (_status.value.state == "pause") {
                client.resume()
            } else {
                client.play()
            }
        }
    }

    fun next() {
        safeAction {
            client.next()
        }
    }

    fun previous() {
        safeAction {
            client.previous()
        }
    }

    fun setVolume(vol: Int) {
        safeAction {
            client.setVolume(vol)
            _status.value = _status.value.copy(volume = vol)
        }
    }

    fun seek(pos: Float) {
        safeAction {
            client.seek(pos)
        }
    }

    fun playId(id: String) {
        safeAction {
            client.playId(id)
        }
    }

    fun toggleRepeat() {
        safeAction {
            client.setRepeat(!_status.value.repeat)
            _status.value = _status.value.copy(repeat = !_status.value.repeat)
        }
    }

    fun toggleShuffle() {
        safeAction {
            client.setRandom(!_status.value.random)
            _status.value = _status.value.copy(random = !_status.value.random)
        }
    }

    private suspend fun refresh() {
        try {
            _status.value = client.getStatus()
            val song = client.getCurrentSong()
            _currentSong.value = song
            _playlist.value = client.getPlaylist()
            if (_errorMessage.value != null) {
                _errorMessage.value = null
            }
            if (song.file.isNotBlank()) {
                requestCoverArt(song.file, song.artist, song.title)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
