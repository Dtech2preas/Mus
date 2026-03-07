import re
with open("app/src/main/java/com/example/musicdownloader/ui/HomeScreen.kt", "r") as f:
    content = f.read()

# Increase take size to 100 for more items
content = content.replace("val madeForYou = randomizedAllGenreSongs.filter { seenIds.add(it.id) }.distinctBy { it.id }.take(20)", "val madeForYou = randomizedAllGenreSongs.filter { seenIds.add(it.id) }.distinctBy { it.id }.take(100)")

# Increase chunk size to 10 for more rows
content = content.replace("val chunks = displayMadeForYou.chunked(2)", "val chunks = displayMadeForYou.chunked(10)")

with open("app/src/main/java/com/example/musicdownloader/ui/HomeScreen.kt", "w") as f:
    f.write(content)
