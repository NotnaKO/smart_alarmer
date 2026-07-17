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
started_emulator=false

shutdown_managed_emulator() {
    if [[ "$started_emulator" == "true" ]]; then
        serial="$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
        if [[ -n "$serial" ]]; then
            "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
        fi
        pid_file="/tmp/smart-alarmer-${ANDROID_AVD:-small_phone}-headless.pid"
        if [[ -f "$pid_file" ]]; then
            emulator_pid="$(<"$pid_file")"
            for _ in $(seq 1 10); do
                kill -0 "$emulator_pid" 2>/dev/null || break
                sleep 1
            done
            kill "$emulator_pid" 2>/dev/null || true
            rm -f "$pid_file"
        fi
        safe_avd_name="$(tr '[:upper:]_' '[:lower:]-' <<<"${ANDROID_AVD:-small_phone}")"
        systemctl --user stop "smart-alarmer-emulator-${safe_avd_name}-headless.service" >/dev/null 2>&1 || true
    fi
}
trap shutdown_managed_emulator EXIT INT TERM

device_serial="$($ADB devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }')"
if [[ -z "$device_serial" ]]; then
    if "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { found=1 } END { exit !found }'; then
        echo "ERROR: Refusing to run against an emulator started without project safety limits." >&2
        echo "Stop it and rerun this script, or connect a physical device." >&2
        exit 1
    fi
    export SAFE_EMULATOR_MODE=headless
    bash scripts/start_safe_emulator.sh "${ANDROID_AVD:-small_phone}"
    started_emulator=true
    device_serial="$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
fi
export ANDROID_SERIAL="$device_serial"

echo "Preparing deterministic English test locale on $ANDROID_SERIAL"
./gradlew installDebug --no-daemon --max-workers=2
"$ADB" -s "$ANDROID_SERIAL" shell cmd locale set-app-locales com.notnako.smartalarmer --user 0 --locales en-US

echo "Running instrumented tests with a 15-minute hard timeout"
timeout --foreground --signal=TERM --kill-after=20s "${INSTRUMENTED_TEST_TIMEOUT:-15m}" \
    ./gradlew connectedDebugAndroidTest --no-daemon --max-workers=2 "$@"
