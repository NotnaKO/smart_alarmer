#!/bin/bash

set -euo pipefail

readonly PACKAGE_NAME="com.notnako.smartalarmer"
readonly APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
SDK_DIR="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties | head -n 1)}"

if [ -z "$SDK_DIR" ] || [ ! -x "$SDK_DIR/platform-tools/adb" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties."
    exit 1
fi

readonly ADB="$SDK_DIR/platform-tools/adb"
DEFAULT_AVD="small_phone"
if ! "$SDK_DIR/emulator/emulator" -list-avds | grep -qx "$DEFAULT_AVD"; then
    DEFAULT_AVD="$("$SDK_DIR/emulator/emulator" -list-avds | head -n 1)"
fi
EMULATOR_NAME="${ANDROID_AVD:-$DEFAULT_AVD}"

if [ -z "$EMULATOR_NAME" ]; then
    echo "ERROR: No Android emulator found. Create an AVD or set ANDROID_AVD."
    exit 1
fi

echo "Starting safe emulator: $EMULATOR_NAME"
bash scripts/start_safe_emulator.sh "$EMULATOR_NAME"

DEVICE_SERIAL="${ANDROID_SERIAL:-$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}"
if [ -z "$DEVICE_SERIAL" ]; then
    echo "ERROR: No booted Android device found."
    exit 1
fi

ADB_DEVICE=("$ADB" -s "$DEVICE_SERIAL")

echo "Building and installing the debug app"
./gradlew assembleDebug --max-workers=2
"${ADB_DEVICE[@]}" install -r "$APK_PATH"

echo "Setting Smart Alarmer locale to Russian"
"${ADB_DEVICE[@]}" shell cmd locale set-app-locales "$PACKAGE_NAME" --user 0 --locales ru-RU
"${ADB_DEVICE[@]}" shell am force-stop "$PACKAGE_NAME"
"${ADB_DEVICE[@]}" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

echo "Smart Alarmer is running in Russian on $DEVICE_SERIAL"
