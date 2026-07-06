import sys
import re

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Apply tracking logic to onMediaItemTransition using regex to find it safely
    pattern = re.compile(r'(override fun onMediaItemTransition\(mediaItem: MediaItem\?, reason: Int\) \{.*?)(\n\s*// Trigger Smart Logic)', re.DOTALL)

    replacement = r"""\1

                mediaController?.let { controller ->
                    val currentIndex = controller.currentMediaItemIndex

                    // Cleanup previous user-queued item if we moved past it
                    if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                         if (userQueuedItemsCount > 0 && previousMediaItemIndex != -1 && currentIndex > previousMediaItemIndex) {
                             val removeIndex = previousMediaItemIndex
                             if (removeIndex >= 0 && removeIndex < controller.mediaItemCount) {
                                 userQueuedItemsCount--
                                 try {
                                     controller.removeMediaItem(removeIndex)
                                 } catch (e: Exception) {
                                     AppLogger.log("[Controller] Failed to remove played queue item: ${e.message}")
                                 }
                             }
                         }
                    }
                    previousMediaItemIndex = controller.currentMediaItemIndex
                }

                updateQueueState()\2"""

    content = pattern.sub(replacement, content)

    # Also add the method updateQueueState() since it might be missing
    if "private fun updateQueueState()" not in content:
        insert_idx = content.rfind("}")
        if insert_idx != -1:
            content = content[:insert_idx] + """
    private fun updateQueueState() {
        mediaController?.let { controller ->
            val queue = mutableListOf<MediaItem>()
            for (i in 0 until controller.mediaItemCount) {
                queue.add(controller.getMediaItemAt(i))
            }
            _currentQueue.value = queue
            _currentQueueIndex.value = controller.currentMediaItemIndex
        }
    }
}"""

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
