package com.example.bitperfectremote.data

data class PlayerStatus(
    val state: String = "stop", // play, pause, stop
    val volume: Int = 100,
    val repeat: Boolean = false,
    val random: Boolean = false,
    val songPos: Int = -1,
    val songId: Int = -1,
    val elapsed: Float = 0f,
    val duration: Float = 0f,
    val bitrate: Int = 0,       // kbps
    val sampleRate: Int = 0,    // Hz
    val bitDepth: Int = 0,      // bits
    val channels: Int = 0
)

data class TrackInfo(
    val id: String = "",
    val title: String = "Unknown Title",
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val duration: Float = 0f,
    val file: String = ""
)

data class QueueItem(
    val id: String,
    val pos: Int,
    val title: String,
    val artist: String,
    val album: String
)
