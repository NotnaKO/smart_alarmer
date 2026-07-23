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
EMULATOR_NAME="${ANDROID_AVD:-$("$SDK_DIR/emulator/emulator" -list-avds | head -n 1)}"

if [ -z "$EMULATOR_NAME" ]; then
    echo "ERROR: No Android emulator found. Create an AVD or set ANDROID_AVD."
    exit 1
fi

echo "=========================================="
echo "   Smart Alarmer Launcher & Installer"
echo "=========================================="

echo "1. Starting emulator: $EMULATOR_NAME..."
android emulator start "$EMULATOR_NAME"

DEVICE_SERIAL="${ANDROID_SERIAL:-$("$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}"
if [ -z "$DEVICE_SERIAL" ]; then
    echo "ERROR: No booted Android device found."
    exit 1
fi

ADB_DEVICE=("$ADB" -s "$DEVICE_SERIAL")

echo "2. Building the debug APK..."
./gradlew assembleDebug

echo "3. Installing the debug app..."
"${ADB_DEVICE[@]}" install -r "$APK_PATH"

echo "4. Setting Smart Alarmer locale to English..."
"${ADB_DEVICE[@]}" shell cmd locale set-app-locales "$PACKAGE_NAME" --user 0 --locales en-US
"${ADB_DEVICE[@]}" shell am force-stop "$PACKAGE_NAME"
"${ADB_DEVICE[@]}" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

echo "=========================================="
echo "   SUCCESS: App is running in English!"
echo "=========================================="
