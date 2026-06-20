#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

echo "=========================================="
echo "   Smart Alarmer Launcher & Installer"
echo "=========================================="

echo "1. Checking available emulators..."
EMULATOR_NAME=$(android emulator list | head -n 1)

if [ -z "$EMULATOR_NAME" ]; then
    echo "ERROR: No emulator found. Please create one using 'android emulator create'."
    exit 1
fi

echo "Starting emulator: $EMULATOR_NAME..."
android emulator start "$EMULATOR_NAME"

echo "2. Building the debug APK..."
./gradlew assembleDebug

echo "3. Deploying and launching the app..."
android run \
  --activity=com.example.smartalarmer.MainActivity \
  --apks=app/build/outputs/apk/debug/app-debug.apk

echo "=========================================="
echo "   SUCCESS: App is running on emulator!"
echo "=========================================="
