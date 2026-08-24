package com.example.bitperfectremote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bitperfectremote.BuildConfig
import com.example.bitperfectremote.data.PlayerStatus
import com.example.bitperfectremote.data.QueueItem
import com.example.bitperfectremote.data.TrackInfo
import com.example.bitperfectremote.ui.PlayerViewModel
import kotlinx.coroutines.launch

private val themeColors = listOf(
    "blue" to Color(0xFF3B82F6),
    "green" to Color(0xFF10B981),
    "purple" to Color(0xFF8B5CF6),
    "orange" to Color(0xFFF97316),
    "red" to Color(0xFFEF4444),
    "teal" to Color(0xFF14B8A6),
    "pink" to Color(0xFFEC4899),
    "indigo" to Color(0xFF6366F1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRemoteScreen(
    viewModel: PlayerViewModel,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val status by viewModel.status.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playlist by viewModel.playlist.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var themeMode by remember { mutableStateOf(viewModel.themeMode) }
    var themeExpanded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 2.dp
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Bitperfect Remote",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connected to ${viewModel.savedIp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    label = { Text("Music Browser") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenBrowser()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Stored MPD Servers") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Application Themes Submenu
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Application Themes")
                            Icon(
                                imageVector = if (themeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    selected = false,
                    onClick = { themeExpanded = !themeExpanded },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                if (themeExpanded) {
                    val themes = listOf("system" to "System Default", "light" to "Light Mode", "dark" to "Dark Mode")
                    Column(modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 8.dp)) {
                        themes.forEach { (key, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (themeMode == key),
                                        onClick = {
                                            themeMode = key
                                            viewModel.updateThemeMode(key)
                                        },
                                        role = Role.RadioButton
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (themeMode == key),
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Accent Color",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            themeColors.forEach { (key, color) ->
                                val selected = viewModel.themeColor == key
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .selectable(
                                            selected = selected,
                                            onClick = {
                                                viewModel.updateThemeColor(key)
                                            },
                                            role = Role.RadioButton
                                        )
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("Disconnect") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onDisconnect()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(bottom = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Bitperfect Remote", 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (viewModel.savedIp.isNotBlank() && viewModel.savedIp != "192.168.1.") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${viewModel.savedIp}:${viewModel.savedPort}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val selIcon = MaterialTheme.colorScheme.onPrimary
                    val selText = MaterialTheme.colorScheme.onSurface
                    val indicator = MaterialTheme.colorScheme.primary
                    val unsel = MaterialTheme.colorScheme.onSurfaceVariant
                    NavigationBarItem(
                        icon = { Icon(if (selectedTab == 0) Icons.Default.PlayCircleFilled else Icons.Default.PlayCircleOutline, contentDescription = "Player") },
                        label = { Text("Player", color = if (selectedTab == 0) selText else unsel) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selIcon,
                            selectedTextColor = selText,
                            indicatorColor = indicator,
                            unselectedIconColor = unsel,
                            unselectedTextColor = unsel
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(if (selectedTab == 1) Icons.Default.QueueMusic else Icons.Default.List, contentDescription = "Queue") },
                        label = { Text("Queue", color = if (selectedTab == 1) selText else unsel) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selIcon,
                            selectedTextColor = selText,
                            indicatorColor = indicator,
                            unselectedIconColor = unsel,
                            unselectedTextColor = unsel
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                // Content fills the whole area; the error banner is overlaid on top
                // so it never resizes the Now Playing layout (which would clip the
                // title under the album art).
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> NowPlayingTab(viewModel, status, currentSong)
                        1 -> QueueTab(viewModel, playlist, status.songId)
                    }
                }

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingTab(viewModel: PlayerViewModel, status: PlayerStatus, song: TrackInfo) {
    val coverArt by viewModel.coverArt.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flexible top area: absorbs song-info height changes between tracks so
        // the bottom controls stay fixed and never shift/resize on track switch.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Artwork
        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.62f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverArt != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverArt)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Song Info
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (song.title.isBlank()) "No Media Playing" else song.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (song.artist.isBlank()) "Bitperfect MPD" else song.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee()
            )
            if (song.album.isNotBlank()) {
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            }
        } // end flexible top area

        // File info: bit depth / sample rate / channels / bitrate (pinned, always visible)
        val fileInfo = buildAudioInfo(status)
        if (fileInfo.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = fileInfo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Progress
        Column(modifier = Modifier.fillMaxWidth()) {
            val progress = if (song.duration > 0f) (status.elapsed / song.duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = progress,
                onValueChange = { newVal ->
                    viewModel.seek(newVal * song.duration)
                },
                enabled = song.duration > 0f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(status.elapsed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(song.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Repeat / Shuffle
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (status.repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (status.random) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { viewModel.previous() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
            }

            LargeFloatingActionButton(
                onClick = { viewModel.togglePlayPause() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (status.state == "play") Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(42.dp)
                )
            }

            FilledTonalIconButton(
                onClick = { viewModel.next() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueTab(viewModel: PlayerViewModel, playlist: List<QueueItem>, currentSongId: Int) {
    if (playlist.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your queue is empty", color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }

            items(playlist) { item ->
                val isCurrent = item.id.toIntOrNull() == currentSongId
                Card(
                    onClick = { viewModel.playId(item.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isCurrent) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = (playlist.indexOf(item) + 1).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalSecs = seconds.toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%d:%02d", mins, secs)
}

private fun buildAudioInfo(status: PlayerStatus): String {
    val parts = mutableListOf<String>()
    if (status.bitDepth > 0) {
        parts.add(if (status.bitDepth == 32) "32-bit float" else "${status.bitDepth}-bit")
    }
    if (status.sampleRate > 0) {
        val khz = status.sampleRate / 1000.0
        parts.add("%.1f kHz".format(khz))
    }
    if (status.channels > 0) {
        parts.add(
            when (status.channels) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "${status.channels}ch"
            }
        )
    }
    if (status.bitrate > 0) {
        parts.add("${status.bitrate} kbps")
    }
    return parts.joinToString("  ")
}
