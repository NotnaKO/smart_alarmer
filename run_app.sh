#!/usr/bin/env bash

set -euo pipefail

echo "=========================================="
echo "   Smart Alarmer Launcher & Installer"
echo "=========================================="

SDK_DIR="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties | head -n 1)}"
readonly ADB="$SDK_DIR/platform-tools/adb"
readonly APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo "1. Checking available emulators..."
DEFAULT_AVD="small_phone"
if ! "$SDK_DIR/emulator/emulator" -list-avds | grep -qx "$DEFAULT_AVD"; then
    DEFAULT_AVD="$($SDK_DIR/emulator/emulator -list-avds | head -n 1)"
fi
EMULATOR_NAME="${ANDROID_AVD:-$DEFAULT_AVD}"

if [ -z "$EMULATOR_NAME" ]; then
    echo "ERROR: No emulator found. Please create one using 'android emulator create'."
    exit 1
fi

echo "Starting safe emulator: $EMULATOR_NAME..."
bash scripts/start_safe_emulator.sh "$EMULATOR_NAME"
DEVICE_SERIAL="$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"

echo "2. Building the debug APK..."
./gradlew assembleDebug --max-workers=2

echo "3. Deploying and launching the app..."
"$ADB" -s "$DEVICE_SERIAL" install -r "$APK_PATH"
"$ADB" -s "$DEVICE_SERIAL" shell am force-stop com.notnako.smartalarmer
"$ADB" -s "$DEVICE_SERIAL" shell monkey -p com.notnako.smartalarmer -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

echo "=========================================="
echo "   SUCCESS: App is running on emulator!"
echo "=========================================="
