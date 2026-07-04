import sys

with open('.github/workflows/android_build.yml', 'r') as f:
    content = f.read()

# Add build-tools env step
env_step = """
    - name: Setup Android Build Tools
      run: |
        export BUILD_TOOLS_VERSION="34.0.0"
        echo "BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION}" >> $GITHUB_ENV
        # We also create a symlink from default location that sign-android-release searches for to the one we actually have
        sudo mkdir -p /usr/local/lib/android/sdk/build-tools/29.0.3/
        sudo ln -sf /usr/local/lib/android/sdk/build-tools/34.0.0/zipalign /usr/local/lib/android/sdk/build-tools/29.0.3/zipalign
        sudo ln -sf /usr/local/lib/android/sdk/build-tools/34.0.0/apksigner /usr/local/lib/android/sdk/build-tools/29.0.3/apksigner
"""

if "Setup Android Build Tools" not in content:
    content = content.replace(
        "    - name: Sign App APK",
        env_step.lstrip() + "    - name: Sign App APK"
    )

with open('.github/workflows/android_build.yml', 'w') as f:
    f.write(content)
