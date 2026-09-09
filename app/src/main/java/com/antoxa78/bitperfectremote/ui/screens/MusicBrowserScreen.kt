package com.antoxa78.bitperfectremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antoxa78.bitperfectremote.data.BrowseEntry
import com.antoxa78.bitperfectremote.ui.PlayerViewModel
import com.antoxa78.bitperfectremote.ui.onSurfaceAccentColor
import com.antoxa78.bitperfectremote.ui.smbLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Keep absolute paths intact; only trim stray whitespace and trailing slashes.
private fun normalizePath(path: String): String =
    path.trim().trimEnd('/')

// Friendly name for well-known Android storage roots, or null if not one.
private fun storageRootName(path: String): String? = when {
    path.contains("/storage/emulated", ignoreCase = true) -> "Internal Storage"
    path.startsWith("/storage/", ignoreCase = true) -> "External Drive"
    else -> null
}

// At the library root, replace raw storage paths with friendly names.
private fun mapRootStorageNames(entries: List<BrowseEntry>): List<BrowseEntry> {
    var externalCount = 0
    return entries.map { e ->
        val friendly = if (e.isDirectory) storageRootName(e.file) else null
        if (friendly != null && friendly == "Internal Storage") {
            e.copy(title = friendly)
        } else if (friendly != null) {
            externalCount++
            e.copy(title = if (externalCount == 1) "External Drive" else "External Drive $externalCount")
        } else {
            e
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicBrowserScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    // Use the ViewModel's dedicated browser client so directory listings don't
    // compete with the polling mutex on the main client connection.
    val libraryClient = viewModel.libraryClient

    // Navigation history stack. First element is always the library root ("").
    val pathHistory = remember { mutableStateListOf("") }
    val currentPath = pathHistory.lastOrNull() ?: ""

    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var selectedFolderForDialog by remember { mutableStateOf<BrowseEntry?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isConnected by viewModel.isConnected.collectAsState()

    fun navigateUp() {
        if (pathHistory.size > 1) {
            pathHistory.removeAt(pathHistory.size - 1)
        } else {
            // Already at the root of the browser: leave the browser entirely.
            onBack()
        }
    }

    fun openFolder(path: String) {
        val normalized = normalizePath(path)
        if (normalized != currentPath) {
            pathHistory.add(normalized)
        }
    }

    // Applies the [Replace All] / [Add To Playlist] bottom-bar actions to the
    // currently viewed folder. The library root has no folder to queue, so we
    // guide the user to open one first.
    fun queueCurrentFolder(replace: Boolean) {
        if (currentPath.isEmpty()) {
            snackbarMessage = "Open a folder first"
            return
        }
        scope.launch {
            isBusy = true
            try {
                libraryClient.loadFolderToQueue(currentPath, replace)
                snackbarMessage = if (replace) "Replaced playlist" else "Added to playlist"
            } catch (e: Exception) {
                snackbarMessage = "Failed: ${e.message}"
            } finally {
                isBusy = false
            }
        }
    }

    // System back button / gesture must behave exactly like the top-bar back arrow:
    // go one folder up, and exit the browser only when already at the root.
    BackHandler {
        navigateUp()
    }

    // Reload whenever the current path changes, a retry is requested,
    // or the connection comes back after being lost.
    LaunchedEffect(currentPath, reloadTick, isConnected) {
        if (!isConnected) return@LaunchedEffect
        isLoading = true
        loadError = null
        try {
            viewModel.ensureBrowserConnected()
            val loaded = libraryClient.lsinfo(currentPath)
            entries = if (currentPath.isEmpty()) mapRootStorageNames(loaded) else loaded
            loadError = null
        } catch (e: Exception) {
            // If the browser connection dropped, reconnect in the background
            // and retry automatically instead of showing a stale error.
            if (e.message?.contains("closed", true) == true || e.message?.contains("Connection", true) == true) {
                scope.launch {
                    if (viewModel.reconnectBrowser()) {
                        reloadTick++
                    }
                }
            }
            entries = emptyList()
            loadError = e.message ?: "Failed to list directory"
        }
        isLoading = false
    }

    // Auto-hide snackbar
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            delay(2500)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when {
                                currentPath.isEmpty() -> "Music Browser"
                                else -> storageRootName(currentPath) ?: currentPath.substringAfterLast('/')
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentPath.isNotEmpty()) {
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { navigateUp() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = onSurfaceAccentColor()
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                        FilledTonalButton(
                            onClick = { queueCurrentFolder(replace = true) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlaylistRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replace All")
                        }
                        Button(
                            onClick = { queueCurrentFolder(replace = false) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add to Playlist")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when {
                !isConnected -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Not connected to MPD server.\nWaiting for reconnection...",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Back to Player")
                        }
                    }
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = onSurfaceAccentColor()
                    )
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Failed to browse directory:\n${loadError}",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            OutlinedButton(
                                onClick = { navigateUp() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = onSurfaceAccentColor()
                                )
                            ) {
                                Text("Go Back")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { reloadTick++ }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                entries.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Directory is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navigateUp() }) {
                            Text("Back")
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            ListItem(
                                headlineContent = { Text(if (entry.isDirectory && entry.title.isBlank()) smbLabel(entry.file) else entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    if (!entry.isDirectory && entry.artist.isNotEmpty()) {
                                        Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = when {
                                            !entry.isDirectory -> Icons.Default.Audiotrack
                                            entry.file.contains("://") -> Icons.Filled.Storage
                                            else -> Icons.Default.Folder
                                        },
                                        contentDescription = null,
                                        tint = if (entry.isDirectory) onSurfaceAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    if (!entry.isDirectory) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                try {
                                                    libraryClient.addUri(entry.file)
                                                    snackbarMessage = "Added to queue"
                                                } catch (e: Exception) {
                                                    snackbarMessage = "Add failed: ${e.message}"
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                                        }
                                    } else {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (entry.isDirectory) {
                                            openFolder(entry.file)
                                        } else {
                                            // Default: replace the playlist and play the selected file.
                                            scope.launch {
                                                try {
                                                    libraryClient.loadFolderToQueue(entry.file, replace = true)
                                                    snackbarMessage = "Playing ${entry.title}"
                                                } catch (e: Exception) {
                                                    snackbarMessage = "Failed: ${e.message}"
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectedFolderForDialog = entry
                                    }
                                )
                            )
                            Divider()
                        }
                    }
                }
            }

            // Folder Action Dialog
            selectedFolderForDialog?.let { folder ->
                AlertDialog(
                    onDismissRequest = { selectedFolderForDialog = null },
                    shape = RoundedCornerShape(28.dp),
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            text = folder.title,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    text = {
                        Text(
                            text = "Choose how to queue this item:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            libraryClient.loadFolderToQueue(folder.file, replace = false)
                                            snackbarMessage = "Added folder to playlist"
                                        } catch (e: Exception) {
                                            snackbarMessage = "Failed: ${e.message}"
                                        }
                                    }
                                    selectedFolderForDialog = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add to Playlist", style = MaterialTheme.typography.labelLarge)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            libraryClient.loadFolderToQueue(folder.file, replace = true)
                                            snackbarMessage = "Replaced playlist with folder"
                                        } catch (e: Exception) {
                                            snackbarMessage = "Failed: ${e.message}"
                                        }
                                    }
                                    selectedFolderForDialog = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QueuePlayNext, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Replace Playlist", style = MaterialTheme.typography.labelLarge)
                            }

                            TextButton(
                                onClick = { selectedFolderForDialog = null },
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                )
            }

            snackbarMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) {
                    Text(message)
                }
            }
        }
    }
}
