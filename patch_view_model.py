import sys

def patch_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Expose currentQueue and currentQueueIndex in MusicViewModel
    search_str = """    val shuffleModeEnabled = MusicControllerManager.shuffleModeEnabled
    val repeatMode = MusicControllerManager.repeatMode
    val audioSessionId = MusicControllerManager.audioSessionId
    val isSmartShuffleEnabled = MusicControllerManager.isSmartShuffleEnabled
    val showAdPopupEvent = MusicControllerManager.showAdPopupEvent"""

    replace_str = """    val shuffleModeEnabled = MusicControllerManager.shuffleModeEnabled
    val repeatMode = MusicControllerManager.repeatMode
    val audioSessionId = MusicControllerManager.audioSessionId
    val isSmartShuffleEnabled = MusicControllerManager.isSmartShuffleEnabled
    val showAdPopupEvent = MusicControllerManager.showAdPopupEvent
    val currentQueue = MusicControllerManager.currentQueue
    val currentQueueIndex = MusicControllerManager.currentQueueIndex"""

    if search_str in content:
        content = content.replace(search_str, replace_str)
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Patched {filepath}")
    else:
        print(f"Could not find target string in {filepath}")

patch_file("app/src/main/java/com/example/musicdownloader/MusicViewModel.kt")
patch_file("our-music/src/main/java/com/example/musicdownloader/MusicViewModel.kt")
