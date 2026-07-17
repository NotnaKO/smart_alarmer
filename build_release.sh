#!/usr/bin/env bash
set -euo pipefail

DEFAULT_VERSION_CODE="$(git rev-list --count HEAD 2>/dev/null || echo 1)"
DEFAULT_VERSION_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
DEFAULT_VERSION_NAME="${DEFAULT_VERSION_TAG#v}"
DEFAULT_VERSION_NAME="${DEFAULT_VERSION_NAME:-0.1.0-alpha.1}"
VERSION_CODE="${SMART_ALARMER_VERSION_CODE:-$DEFAULT_VERSION_CODE}"
VERSION_NAME="${SMART_ALARMER_VERSION_NAME:-$DEFAULT_VERSION_NAME}"
KEYSTORE_FILE="${SMART_ALARMER_KEYSTORE_FILE:-$HOME/smart-alarmer-upload.jks}"
KEY_ALIAS="${SMART_ALARMER_KEY_ALIAS:-upload}"

usage() {
  cat <<'EOF'
Usage:
  ./build_release.sh                         # derive code and name from Git
  ./build_release.sh <version-name>          # derive code from Git
  ./build_release.sh <version-code>          # legacy: derive name from latest tag
  ./build_release.sh <version-code> <version-name>
EOF
}

case "$#" in
  0) ;;
  1)
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
      usage
      exit 0
    elif [[ "$1" =~ ^[1-9][0-9]*$ ]]; then
      VERSION_CODE="$1"
    else
      VERSION_NAME="$1"
    fi
    ;;
  2)
    VERSION_CODE="$1"
    VERSION_NAME="$2"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac

if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  echo "Version code must be a positive integer." >&2
  exit 1
fi

if [[ ! "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$ ]]; then
  echo "Version name must be semantic, for example 0.1.0-alpha.3." >&2
  exit 1
fi

echo "Building Smart Alarmer $VERSION_NAME ($VERSION_CODE)"

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

./gradlew test ktlintCheck :app:lintDebug :app:bundleRelease :app:assembleRelease

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
