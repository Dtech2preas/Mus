import re
with open("app/src/main/java/com/example/musicdownloader/MusicControllerManager.kt", "r") as f:
    content = f.read()

content = content.replace(
"""                        val currentMediaItem = controller.currentMediaItem
                        val currentTitle = currentMediaItem?.mediaMetadata?.title?.toString()
                        val currentArtist = currentMediaItem?.mediaMetadata?.artist?.toString()

                        val recommendation = SmartShuffleManager.getNextRecommendation(context, currentTitle, currentArtist)""",
"""                        val currentMediaItem = controller.currentMediaItem
                        val currentTitle = currentMediaItem?.mediaMetadata?.title?.toString()
                        val currentArtist = currentMediaItem?.mediaMetadata?.artist?.toString()
                        val currentVideoId = currentMediaItem?.mediaId

                        val recommendation = SmartShuffleManager.getNextRecommendation(context, currentTitle, currentArtist, currentVideoId)""")

with open("app/src/main/java/com/example/musicdownloader/MusicControllerManager.kt", "w") as f:
    f.write(content)
