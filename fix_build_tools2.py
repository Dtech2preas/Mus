with open('.github/workflows/android_build.yml', 'r') as f:
    content = f.read()

# Fix the indentation of "- name: Setup Android Build Tools"
content = content.replace("- name: Setup Android Build Tools", "    - name: Setup Android Build Tools")

# Better fix for sign-android-release action issue: pass buildToolsVersion
content = content.replace(
"""        keyPassword: ${{ secrets.KEY_PASSWORD }}""",
"""        keyPassword: ${{ secrets.KEY_PASSWORD }}
        buildToolsVersion: 34.0.0""")

with open('.github/workflows/android_build.yml', 'w') as f:
    f.write(content)
