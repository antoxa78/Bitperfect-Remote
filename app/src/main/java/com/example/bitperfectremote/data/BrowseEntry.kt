package com.example.bitperfectremote.data

data class BrowseEntry(
    val file: String,
    val title: String,
    val artist: String,
    val album: String,
    val isDirectory: Boolean
)
