package com.antoxa78.bitperfectremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.antoxa78.bitperfectremote.ui.MpdServer
import com.antoxa78.bitperfectremote.ui.PlayerViewModel
import com.antoxa78.bitperfectremote.ui.onSurfaceAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    var serverName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("6600") }
    var password by remember { mutableStateOf("") }
    // Key (ip:port) of the server currently being edited, if any.
    var editingKey by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    // Track whether the user has pressed Save so we don't react to the initial
    // auto-connect that happens before the screen was even opened.
    var connectRequested by remember { mutableStateOf(false) }

    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val activeKey by viewModel.activeServerKey.collectAsState()
    val servers = viewModel.servers

    // Show "saved" only once the connection actually succeeds, not immediately on click.
    LaunchedEffect(isConnected) {
        if (connectRequested && isConnected) {
            savedMessage = "Server saved and connected."
            connectRequested = false
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stored MPD Servers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (editingKey != null) "Edit MPD Server" else "Add MPD Server",
                style = MaterialTheme.typography.titleMedium,
                color = onSurfaceAccentColor()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it; savedMessage = null },
                label = { Text("Server Name (Optional)") },
                placeholder = { Text("e.g. Living Room") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it; savedMessage = null },
                label = { Text("Player IP Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it; savedMessage = null },
                label = { Text("MPD Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; savedMessage = null },
                label = { Text("MPD Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (savedMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = savedMessage ?: "",
                    color = onSurfaceAccentColor(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    savedMessage = null
                    if (ipAddress.trim().isBlank()) {
                        savedMessage = "Please enter a Player IP Address"
                        return@Button
                    }
                    val p = port.toIntOrNull() ?: 6600
                    if (editingKey != null) {
                        viewModel.updateServer(editingKey!!, serverName, ipAddress, port, password)
                        editingKey = null
                    } else {
                        viewModel.addServer(serverName, ipAddress, port, password)
                    }
                    connectRequested = true
                    viewModel.connect(ipAddress.trim(), p, password, true, serverName.trim())
                },
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingKey != null) "Save Changes & Connect" else "Save & Connect")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Saved Servers",
                style = MaterialTheme.typography.titleMedium,
                color = onSurfaceAccentColor()
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (servers.isEmpty()) {
                Text(
                    text = "No servers saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                servers.forEach { server ->
                    ServerListItem(
                        server = server,
                        isActive = server.key == activeKey,
                        onConnect = { viewModel.connectToServer(server) },
                        onEdit = {
                            editingKey = server.key
                            serverName = server.name
                            ipAddress = server.ip
                            port = server.port
                            password = server.password
                            savedMessage = null
                        },
                        onDelete = {
                            viewModel.removeServer(server.key)
                            if (editingKey == server.key) {
                                editingKey = null
                                serverName = ""
                                ipAddress = ""
                                port = "6600"
                                password = ""
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ServerListItem(
    server: MpdServer,
    isActive: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name.ifBlank { server.ip },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${server.ip}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Last used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onConnect) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Connect to ${server.ip}")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${server.ip}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${server.ip}")
            }
        }
    }
}
