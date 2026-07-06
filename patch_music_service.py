import sys
import os

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    search_str = """            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}"""

    replace_str = """            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Handle dynamically added queue items ensuring they resolve properly
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            val updatedMediaItems = mediaItems.map { item ->
                // Ensure the URI is maintained across the IPC boundary for our dtech scheme
                // The ResolvingDataSource we set up in onCreate will handle the actual resolution
                item.buildUpon()
                    .setUri(item.localConfiguration?.uri ?: item.mediaId.let { android.net.Uri.parse("dtech://stream/$it") })
                    .build()
            }.toMutableList()

            return com.google.common.util.concurrent.Futures.immediateFuture(updatedMediaItems)
        }
    }
}"""

    if search_str in content:
        content = content.replace(search_str, replace_str)
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")
    else:
        print(f"Could not find target string in {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/MusicService.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/MusicService.kt")
