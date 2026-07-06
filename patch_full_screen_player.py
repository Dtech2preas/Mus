import sys
import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 1. Add queue state collection
    search_str_state = """    val audioSessionId by viewModel.audioSessionId.collectAsState()"""
    replace_str_state = """    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()"""

    if search_str_state in content:
        content = content.replace(search_str_state, replace_str_state)
    else:
        print("Failed to patch state in", filepath)

    # 2. Add showQueueBottomSheet state
    search_str_dialogs = """    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }"""
    replace_str_dialogs = """    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showQueueBottomSheet by remember { mutableStateOf(false) }"""

    if search_str_dialogs in content:
        content = content.replace(search_str_dialogs, replace_str_dialogs)
    else:
        print("Failed to patch dialogs in", filepath)

    # 3. Add Queue button to controls row
    # The controls usually have shuffle, skip previous, play/pause, skip next, repeat.
    # We will add it next to repeat, or maybe in the more options menu.
    # Let's find the bottom controls row.
    search_str_controls = """            // --- Secondary Controls (Like, Playlist, Sleep, Equalizer) ---"""
    replace_str_controls = """            // --- Secondary Controls (Like, Playlist, Sleep, Equalizer, Queue) ---"""

    if search_str_controls in content:
        content = content.replace(search_str_controls, replace_str_controls)

    search_str_icons = """                IconButton(onClick = { viewModel.launchEqualizer(context) }) {
                    Icon(
                        imageVector = Icons.Outlined.Equalizer,
                        contentDescription = "Equalizer",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }"""
    replace_str_icons = """                IconButton(onClick = { viewModel.launchEqualizer(context) }) {
                    Icon(
                        imageVector = Icons.Outlined.Equalizer,
                        contentDescription = "Equalizer",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { showQueueBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }"""
    if search_str_icons in content:
        content = content.replace(search_str_icons, replace_str_icons)
    else:
        print("Failed to patch icons in", filepath)

    # 4. Add the Queue Bottom Sheet implementation
    search_str_sheet = """    if (showAddToPlaylistDialog) {"""
    replace_str_sheet = """    if (showQueueBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueBottomSheet = false },
            containerColor = DeepBlack,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Up Next",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currentQueue.size) { index ->
                        val item = currentQueue[index]
                        val isCurrent = index == currentQueueIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Could add play specific item logic here
                                    viewModel.playSong(item.mediaId, item.mediaMetadata.title.toString(), item.mediaMetadata.artist.toString(), item.mediaMetadata.artworkUri?.toString() ?: "")
                                    showQueueBottomSheet = false
                                }
                                .background(if (isCurrent) GlassWhite else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Number or Icon
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Playing",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.mediaMetadata.title?.toString() ?: "Unknown Title",
                                    color = if (isCurrent) AccentBlue else TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAddToPlaylistDialog) {"""

    if search_str_sheet in content:
        content = content.replace(search_str_sheet, replace_str_sheet)
    else:
        print("Failed to patch sheet in", filepath)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/ui/FullScreenPlayer.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/ui/FullScreenPlayer.kt")
