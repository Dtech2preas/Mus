import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Add timeline changed listener to keep queue UI in sync when items are removed
    search_str = """            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {"""

    replace_str = """            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                updateQueueState()
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {"""

    if search_str in content:
        content = content.replace(search_str, replace_str)
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
