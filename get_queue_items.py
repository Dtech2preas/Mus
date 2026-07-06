import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Add StateFlow for media queue
    search_str = """    private val _repeatMode = MutableStateFlow(androidx.media3.common.Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()"""

    replace_str = """    private val _repeatMode = MutableStateFlow(androidx.media3.common.Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentQueue: StateFlow<List<MediaItem>> = _currentQueue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()"""

    if search_str in content:
        content = content.replace(search_str, replace_str)

    # Update queue when media items change
    search_str_2 = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                _currentMediaItem.value = mediaItem

                if (mediaItem != null) {"""

    replace_str_2 = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                _currentMediaItem.value = mediaItem
                updateQueueState()

                if (mediaItem != null) {"""

    if search_str_2 in content:
        content = content.replace(search_str_2, replace_str_2)

    search_str_3 = """            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                if (mediaController?.currentMediaItem != null) {
                    _currentMediaItem.value = mediaController?.currentMediaItem
                }
            }"""

    replace_str_3 = """            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                if (mediaController?.currentMediaItem != null) {
                    _currentMediaItem.value = mediaController?.currentMediaItem
                }
                updateQueueState()
            }

            private fun updateQueueState() {
                mediaController?.let { controller ->
                    val queue = mutableListOf<MediaItem>()
                    for (i in 0 until controller.mediaItemCount) {
                        queue.add(controller.getMediaItemAt(i))
                    }
                    _currentQueue.value = queue
                    _currentQueueIndex.value = controller.currentMediaItemIndex
                }
            }"""

    if search_str_3 in content:
        content = content.replace(search_str_3, replace_str_3)

    with open(filepath, 'w') as f:
        f.write(content)
    print(f"Patched {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/MusicControllerManager.kt")
