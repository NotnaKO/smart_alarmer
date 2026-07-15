#!/usr/bin/env bash
set -euo pipefail

VERSION_CODE="${1:-${SMART_ALARMER_VERSION_CODE:-1}}"
VERSION_NAME="${2:-${SMART_ALARMER_VERSION_NAME:-0.1.0-alpha.1}}"
KEYSTORE_FILE="${SMART_ALARMER_KEYSTORE_FILE:-$HOME/smart-alarmer-upload.jks}"
KEY_ALIAS="${SMART_ALARMER_KEY_ALIAS:-upload}"

if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  echo "Version code must be a positive integer." >&2
  exit 1
fi

if [[ ! -f "$KEYSTORE_FILE" ]]; then
  echo "Upload keystore not found: $KEYSTORE_FILE" >&2
  exit 1
fi

if [[ -z "${SMART_ALARMER_KEYSTORE_PASSWORD:-}" ]]; then
  read -r -s -p "Keystore password: " SMART_ALARMER_KEYSTORE_PASSWORD
  echo
fi

if [[ -z "${SMART_ALARMER_KEY_PASSWORD:-}" ]]; then
  read -r -s -p "Upload key password: " SMART_ALARMER_KEY_PASSWORD
  echo
fi

export SMART_ALARMER_VERSION_CODE="$VERSION_CODE"
export SMART_ALARMER_VERSION_NAME="$VERSION_NAME"
export SMART_ALARMER_KEYSTORE_FILE="$KEYSTORE_FILE"
export SMART_ALARMER_KEYSTORE_PASSWORD
export SMART_ALARMER_KEY_ALIAS="$KEY_ALIAS"
export SMART_ALARMER_KEY_PASSWORD

./gradlew test :app:lintDebug :app:bundleRelease :app:assembleRelease

AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"
VERIFY_OUTPUT="$(LC_ALL=C jarsigner -verify "$AAB" 2>&1)"
if [[ "$VERIFY_OUTPUT" != *"jar verified."* ]]; then
  echo "Release bundle was created but its signature could not be verified:" >&2
  echo "$VERIFY_OUTPUT" >&2
  exit 1
fi

echo "Signed release bundle ready: $AAB"
echo "Signed release APK ready: $APK"
