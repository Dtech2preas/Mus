import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    search_str = """                // Playlist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {"""

    replace_str = """                // Queue Display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { showQueueBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = TextSecondary
                        )
                    }
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }

                // Playlist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {"""

    if search_str in content:
        content = content.replace(search_str, replace_str)
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")
    else:
        print(f"Could not find target string in {filepath}")

patch_file("our-music/src/main/java/com/example/musicdownloader/ui/FullScreenPlayer.kt")
