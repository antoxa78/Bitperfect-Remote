package com.antoxa78.bitperfectremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.antoxa78.bitperfectremote.ui.PlayerViewModel
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    var ipAddress by remember { mutableStateOf(viewModel.savedIp) }
    var port by remember { mutableStateOf(viewModel.savedPort) }
    var password by remember { mutableStateOf(viewModel.savedPassword) }
    var serverName by remember { mutableStateOf(viewModel.savedName) }
    var savedMessage by remember { mutableStateOf(false) }
    // Track whether the user has pressed Save so we don't react to the initial
    // auto-connect that happens before the screen was even opened.
    var connectRequested by remember { mutableStateOf(false) }

    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()

    // Show "saved" only once the connection actually succeeds, not immediately on click.
    LaunchedEffect(isConnected) {
        if (connectRequested && isConnected) {
            savedMessage = true
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit Active MPD Server Connection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it; savedMessage = false },
                label = { Text("Server Name (Optional)") },
                placeholder = { Text("e.g. Living Room") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it; savedMessage = false },
                label = { Text("Player IP Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it; savedMessage = false },
                label = { Text("MPD Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; savedMessage = false },
                label = { Text("MPD Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (savedMessage) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Settings saved successfully!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    savedMessage = false
                    connectRequested = true
                    val p = port.toIntOrNull() ?: 6600
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
                    Text("Save and Connect")
                }
            }
        }
    }
}
