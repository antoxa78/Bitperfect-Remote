# Bitperfect Remote

A modern, fast, and feature-rich Android remote control for BitPerfect Player and [Music Player Daemon (MPD)](https://www.musicpd.org/).

Built with **Kotlin** and **Jetpack Compose**.

## Features

- **Playback Control**: Play, pause, stop, next/previous track, seek bar with real-time progress.
- **Volume & Controls**: Volume slider, repeat, single, random/shuffle, and consume modes.
- **Audio Stream Info**: Displays real-time audio format (bitrate, sample rate, bit depth, channels).
- **Music Library Browser**: Browse your music collection by directories and files, add songs/folders to the queue or play immediately.
- **Queue Management**: View, reorder, clear, and manage current playback queue.
- **Album Art**: Built-in support for fetching and displaying album artwork.
- **Theming & Customization**:
  - Dark / Light / System theme support
  - Multiple accent colors (Blue, Green, Purple, Orange, Red, Teal, Pink, Indigo)
- **Auto-connect & Reconnect**: Automatically reconnects when opening the app or waking the screen.
- **Password Support**: Supports password-protected MPD servers.

## Screenshots

<!-- Add screenshots here -->

## Getting Started

### Prerequisites

- Android 8.0 (API level 26) or higher
- An active MPD server reachable over your local network or Wi-Fi

### Installation

Download the latest release APK from the [Releases](https://github.com/antoxa78/Bitperfect-Remote/releases) page and install it on your Android device.

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/antoxa78/Bitperfect-Remote.git
   ```
2. Open the project in **Android Studio** (Koala or newer recommended).
3. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
   or for release:
   ```bash
   ./gradlew assembleRelease
   ```

## License

This project is open source and available under the [MIT License](LICENSE).
