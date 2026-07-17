#!/usr/bin/env bash

set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

SDK_DIR="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties | head -n 1)}"
if [[ -z "$SDK_DIR" || ! -x "$SDK_DIR/platform-tools/adb" ]]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
fi

readonly ADB="$SDK_DIR/platform-tools/adb"
device_serial="${ANDROID_SERIAL:-$($ADB devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [[ -z "$device_serial" ]]; then
    echo "ERROR: No running emulator or physical device." >&2
    echo "Start the emulator normally with run_app.sh, then rerun this script." >&2
    exit 1
fi
export ANDROID_SERIAL="$device_serial"

readonly LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/smart-alarmer-instrumented-tests.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "ERROR: Another instrumented test run is already active." >&2
    exit 1
fi

is_full_run=true
for argument in "$@"; do
    if [[ "$argument" == *"testInstrumentationRunnerArguments.class"* ]]; then
        is_full_run=false
        break
    fi
done

readonly FULL_RUN_STAMP="${XDG_RUNTIME_DIR:-/tmp}/smart-alarmer-last-full-instrumented-run"
cooldown_seconds="${INSTRUMENTED_FULL_RUN_COOLDOWN_SECONDS:-600}"
if [[ "$is_full_run" == "true" && -f "$FULL_RUN_STAMP" && "${ALLOW_REPEATED_FULL_INSTRUMENTED_RUN:-0}" != "1" ]]; then
    last_run="$(<"$FULL_RUN_STAMP")"
    [[ "$last_run" =~ ^[0-9]+$ ]] || last_run=0
    elapsed="$(( $(date +%s) - last_run ))"
    if ((elapsed < cooldown_seconds)); then
        echo "ERROR: A full instrumented suite started ${elapsed}s ago." >&2
        echo "Wait $((cooldown_seconds - elapsed))s, run a targeted class, or explicitly set ALLOW_REPEATED_FULL_INSTRUMENTED_RUN=1." >&2
        exit 1
    fi
fi
if [[ "$is_full_run" == "true" ]]; then
    date +%s >"$FULL_RUN_STAMP"
fi

echo "Preparing deterministic English test locale on $ANDROID_SERIAL"
./gradlew installDebug --no-daemon --max-workers=1
"$ADB" -s "$ANDROID_SERIAL" shell cmd locale set-app-locales com.notnako.smartalarmer --user 0 --locales en-US

echo "Running instrumented tests once, with one worker and a 10-minute hard timeout"
nice -n 10 timeout --foreground --signal=TERM --kill-after=20s "${INSTRUMENTED_TEST_TIMEOUT:-10m}" \
    ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=1 "$@"
