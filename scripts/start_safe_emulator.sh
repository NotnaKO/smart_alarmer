#!/usr/bin/env bash

set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$PROJECT_ROOT/local.properties" | head -n 1)}"

if [[ -z "$SDK_DIR" || ! -x "$SDK_DIR/platform-tools/adb" || ! -x "$SDK_DIR/emulator/emulator" ]]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
fi

readonly ADB="$SDK_DIR/platform-tools/adb"
readonly EMULATOR="$SDK_DIR/emulator/emulator"
emulator_version="$($EMULATOR -version 2>&1 | sed -n 's/^Android emulator version \([^ ]*\).*/\1/p' | head -n 1)"
os_id=""
os_version=""
if [[ -r /etc/os-release ]]; then
    os_id="$(sed -n 's/^ID=//p' /etc/os-release | tr -d '"' | head -n 1)"
    os_version="$(sed -n 's/^VERSION_ID=//p' /etc/os-release | tr -d '"' | head -n 1)"
fi
if [[ "$emulator_version" == "36.6.11.0" && "$os_id" == "fedora" && "$os_version" == "44" ]]; then
    echo "ERROR: Android Emulator $emulator_version is blocked on Fedora $os_version." >&2
    echo "It repeatedly crashed QEMU with SIGSEGV on both host and software renderers." >&2
    echo "Use a physical device or install a different emulator package version." >&2
    exit 1
fi

preferred_avd="small_phone"
if ! "$EMULATOR" -list-avds | grep -qx "$preferred_avd"; then
    preferred_avd="$($EMULATOR -list-avds | head -n 1)"
fi
AVD_NAME="${1:-${ANDROID_AVD:-$preferred_avd}}"
mode="${SAFE_EMULATOR_MODE:-window}"
readonly LOG_FILE="${SAFE_EMULATOR_LOG:-/tmp/smart-alarmer-${AVD_NAME}-${mode}.log}"
readonly PID_FILE="${SAFE_EMULATOR_PID_FILE:-/tmp/smart-alarmer-${AVD_NAME}-${mode}.pid}"
safe_avd_name="$(tr '[:upper:]_' '[:lower:]-' <<<"$AVD_NAME")"
readonly UNIT_NAME="smart-alarmer-emulator-${safe_avd_name}-${mode}.service"

if [[ -z "$AVD_NAME" ]]; then
    echo "ERROR: No Android AVD found." >&2
    exit 1
fi

existing_serial="$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
if [[ -n "$existing_serial" ]]; then
    if [[ -f "$PID_FILE" ]] && kill -0 "$(<"$PID_FILE")" 2>/dev/null; then
        echo "Reusing project-managed emulator: $existing_serial"
        exit 0
    else
        echo "ERROR: Emulator $existing_serial is already running with unknown resource limits." >&2
        echo "Stop it first or use a physical device; unsafe emulator reuse is intentionally blocked." >&2
        exit 1
    fi
fi

cpu_count="$(getconf _NPROCESSORS_ONLN)"
if ((cpu_count > 1)); then
    cpu_set="0,1"
else
    cpu_set="0"
fi

emulator_args=(
    "@$AVD_NAME"
    -gpu swiftshader
    -feature -Vulkan
    -no-snapshot
    -no-boot-anim
    -cores 2
    -memory 2048
    -camera-back none
    -camera-front none
)
if [[ "$mode" == "headless" ]]; then
    emulator_args+=(-no-window -no-audio)
elif [[ "$mode" != "window" ]]; then
    echo "ERROR: SAFE_EMULATOR_MODE must be 'window' or 'headless'." >&2
    exit 1
fi

echo "Starting resource-limited emulator $AVD_NAME ($mode, SwiftShader, Vulkan disabled, CPUs $cpu_set)"
if ! command -v systemd-run >/dev/null || ! systemctl --user show-environment >/dev/null 2>&1; then
    echo "ERROR: A user systemd manager is required for emulator resource isolation." >&2
    exit 1
fi

systemctl --user stop "$UNIT_NAME" >/dev/null 2>&1 || true
: >"$LOG_FILE"
systemd_options=(
    --user
    --unit="${UNIT_NAME%.service}"
    --collect
    --quiet
    --property=Type=simple
    --property=MemoryHigh=3G
    --property=MemoryMax=4G
    --property=MemorySwapMax=1G
    --property=CPUQuota=200%
    --property="AllowedCPUs=$cpu_set"
    --property=Nice=10
    --property=IOSchedulingClass=idle
    --property=OOMPolicy=kill
    --property=KillMode=control-group
    --property=TimeoutStopSec=10
    --property="StandardOutput=append:$LOG_FILE"
    --property="StandardError=append:$LOG_FILE"
)
[[ -n "${DISPLAY:-}" ]] && systemd_options+=(--setenv="DISPLAY=$DISPLAY")
[[ -n "${WAYLAND_DISPLAY:-}" ]] && systemd_options+=(--setenv="WAYLAND_DISPLAY=$WAYLAND_DISPLAY")
[[ -n "${XDG_RUNTIME_DIR:-}" ]] && systemd_options+=(--setenv="XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR")

systemd-run "${systemd_options[@]}" -- "$EMULATOR" "${emulator_args[@]}"
emulator_pid="$(systemctl --user show "$UNIT_NAME" --property=MainPID --value)"
echo "$emulator_pid" >"$PID_FILE"

cleanup_failed_start() {
    systemctl --user stop "$UNIT_NAME" >/dev/null 2>&1 || true
    rm -f "$PID_FILE"
}

for _ in $(seq 1 180); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
        echo "ERROR: Emulator exited during startup. See $LOG_FILE" >&2
        cleanup_failed_start
        exit 1
    fi
    serial="$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
    if [[ -n "$serial" && "$($ADB -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        echo "Emulator ready: $serial"
        exit 0
    fi
    sleep 1
done

echo "ERROR: Emulator did not boot within 180 seconds. See $LOG_FILE" >&2
cleanup_failed_start
exit 1
