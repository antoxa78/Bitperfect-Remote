package com.antoxa78.bitperfectremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antoxa78.bitperfectremote.data.DiscoveredMpdServer
import com.antoxa78.bitperfectremote.data.MpdDiscovery
import kotlinx.coroutines.flow.catch

@Composable
fun MpdScanButton(
    onServerSelected: (DiscoveredMpdServer) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Scan network")
    }

    if (showDialog) {
        MpdDiscoveryDialog(
            onDismiss = { showDialog = false },
            onServerSelected = {
                showDialog = false
                onServerSelected(it)
            }
        )
    }
}

@Composable
private fun MpdDiscoveryDialog(
    onDismiss: () -> Unit,
    onServerSelected: (DiscoveredMpdServer) -> Unit
) {
    val context = LocalContext.current
    val discovery = remember(context) { MpdDiscovery(context) }
    var servers by remember { mutableStateOf<List<DiscoveredMpdServer>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scanAttempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(scanAttempt) {
        scanning = true
        errorMessage = null
        servers = emptyList()
        try {
            discovery.discover()
                .catch { errorMessage = it.message ?: "Network scan failed" }
                .collect { servers = it }
        } finally {
            scanning = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WifiFind, contentDescription = null) },
        title = { Text("Find MPD Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanning your network…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (servers.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(servers, key = { it.key }) { server ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = server.name.ifBlank { "MPD server" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${server.host}:${server.port}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Router, contentDescription = null)
                                },
                                modifier = Modifier.clickable { onServerSelected(server) }
                            )
                            HorizontalDivider()
                        }
                    }
                } else if (!scanning) {
                    Text(
                        text = errorMessage ?: "No MPD servers found. Make sure your phone " +
                            "is on the same Wi-Fi network as the player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { scanAttempt++ },
                enabled = !scanning
            ) {
                Text("Scan again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
