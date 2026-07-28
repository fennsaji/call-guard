#!/bin/bash
# Re-exec with bash if invoked via `sh run_android.sh`
[ -z "$BASH_VERSION" ] && exec bash "$0" "$@"

# CallShield Android Local Development Script
# Builds the debug APK with env-injected BuildConfig fields, installs it on a
# connected device/emulator, and launches the app.
#
# Usage: ./run_android.sh [env-file] [device-id]
# Default env file: android/local.properties
#
# Examples:
#   ./run_android.sh                              # Uses android/local.properties, auto-selects device
#   ./run_android.sh android/local.properties.dev # Uses alternate env file
#   ./run_android.sh android/local.properties emulator-5554  # Target specific device

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/android"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ── Arguments ──────────────────────────────────────────────────────────────────
ENV_FILE_ARG="${1:-}"
DEVICE_ID_ARG="${2:-}"

if [[ -z "$ENV_FILE_ARG" ]]; then
    ENV_FILE="$ANDROID_DIR/local.properties"
elif [[ "$ENV_FILE_ARG" = /* ]]; then
    ENV_FILE="$ENV_FILE_ARG"
else
    ENV_FILE="$SCRIPT_DIR/$ENV_FILE_ARG"
fi

printf "${BLUE}🛡️  CallShield Android — Local Development Build${NC}\n"
printf "${BLUE}📄 Env file: ${ENV_FILE}${NC}\n"

# ── Validate env file ──────────────────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
    printf "${RED}❌ Environment file not found: $ENV_FILE${NC}\n"
    echo ""
    echo "Copy the example and fill in your values:"
    echo "  cp android/local.properties.example android/local.properties"
    echo "  # then edit android/local.properties"
    exit 1
fi

# ── Load env vars (key=value format, skip comments and sdk.dir) ────────────────
load_prop() {
    local key="$1"
    grep "^${key}=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d'=' -f2- | tr -d '\r'
}

HMAC_SALT=$(load_prop "HMAC_SALT")

printf "${GREEN}✅ Properties loaded${NC}\n"
if [ -n "$HMAC_SALT" ]; then
    printf "  HMAC_SALT:         ${HMAC_SALT}\n"
else
    printf "  HMAC_SALT:         ${YELLOW}(using default: callshield-v1-salt-2024)${NC}\n"
fi

# ── Detect Android SDK ─────────────────────────────────────────────────────────
SDK_DIR=$(load_prop "sdk.dir")

if [ -z "$ANDROID_HOME" ]; then
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR" ]; then
        export ANDROID_HOME="$SDK_DIR"
        printf "\n${GREEN}✅ Android SDK from local.properties: ${ANDROID_HOME}${NC}\n"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        printf "\n${YELLOW}⚙️  Auto-detected Android SDK: ${ANDROID_HOME}${NC}\n"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        printf "\n${YELLOW}⚙️  Auto-detected Android SDK: ${ANDROID_HOME}${NC}\n"
    fi
fi

if [ -n "$ANDROID_HOME" ]; then
    export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$PATH"
fi

# Ensure JAVA_HOME is set — required by gradlew
# Always prefer Android Studio's bundled JDK to avoid version mismatches
AS_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
HOMEBREW_JDK17="/opt/homebrew/opt/openjdk@17"
SYSTEM_JDK="/Library/Java/JavaVirtualMachines"
if [ -d "$AS_JDK" ]; then
    export JAVA_HOME="$AS_JDK"
elif [ -d "$HOMEBREW_JDK17" ]; then
    export JAVA_HOME="$HOMEBREW_JDK17"
elif ls "$SYSTEM_JDK" 2>/dev/null | grep -q "17\|21"; then
    export JAVA_HOME="$SYSTEM_JDK/$(ls "$SYSTEM_JDK" | grep "17\|21" | head -1)/Contents/Home"
elif [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}❌ Java not found. Install via: brew install openjdk${NC}"
    exit 1
fi
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    printf "${GREEN}✅ JAVA_HOME: ${JAVA_HOME}${NC}\n"
fi

if ! command -v adb &>/dev/null; then
    printf "${RED}❌ adb not found. Ensure Android SDK platform-tools are installed.${NC}\n"
    echo "  Set ANDROID_HOME or add sdk.dir to ${ENV_FILE}"
    exit 1
fi

printf "${GREEN}✅ Android SDK: ${ANDROID_HOME}${NC}\n"

# ── Helpers ────────────────────────────────────────────────────────────────────
is_emulator() { [[ "$1" == emulator-* ]] && return 0 || return 1; }

device_name() {
    local id="$1"
    if is_emulator "$id"; then
        adb -s "$id" emu avd name 2>/dev/null | head -1 | tr -d '\r' || echo "Android Emulator"
    else
        adb -s "$id" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Physical Device"
    fi
}

# ── Device detection ───────────────────────────────────────────────────────────
printf "\n${BLUE}🔍 Detecting Android devices...${NC}\n"

ADB_DEVICES=$(adb devices 2>/dev/null | grep -v "List of devices" | grep "device$" | awk '{print $1}')

# Warn about unauthorized devices (USB debugging prompt not yet accepted on phone)
UNAUTH=$(adb devices 2>/dev/null | grep -v "List of devices" | grep "unauthorized" | awk '{print $1}')
if [ -n "$UNAUTH" ]; then
    printf "${YELLOW}⚠️  Unauthorized device(s) — accept the 'Allow USB debugging' prompt on your phone:${NC}\n"
    while IFS= read -r dev; do
        printf "   ${YELLOW}• ${dev}${NC}\n"
    done <<< "$UNAUTH"
    echo ""
fi

if [ -z "$ADB_DEVICES" ]; then
    printf "${YELLOW}📱 No connected devices found.${NC}\n"
    printf "${CYAN}   To use a physical device: enable USB debugging (Settings → Developer options) and connect via USB.${NC}\n"
    printf "${CYAN}   Checking available emulators...${NC}\n"
    echo ""

    if ! command -v emulator &>/dev/null; then
        printf "${RED}❌ No devices connected and emulator command not found.${NC}\n"
        echo "Connect a device or install Android SDK emulator support."
        exit 1
    fi

    AVAILABLE_EMULATORS=$(emulator -list-avds 2>/dev/null)
    if [ -z "$AVAILABLE_EMULATORS" ]; then
        printf "${RED}❌ No connected devices and no AVDs found.${NC}\n"
        echo "Connect a physical device via USB or create an emulator in Android Studio."
        exit 1
    fi

    printf "${CYAN}Available emulators:${NC}\n"
    idx=1
    declare -a EMULATOR_ARRAY
    while IFS= read -r avd; do
        printf "  ${GREEN}${idx})${NC} $avd\n"
        EMULATOR_ARRAY[$idx]="$avd"
        ((idx++))
    done <<< "$AVAILABLE_EMULATORS"

    echo ""
    read -p "Select emulator to start (1-$((idx-1))): " selection
    if [ -z "$selection" ] || [ "$selection" -lt 1 ] || [ "$selection" -ge "$idx" ]; then
        printf "${RED}❌ Invalid selection${NC}\n"; exit 1
    fi

    printf "${YELLOW}📱 Starting emulator: ${EMULATOR_ARRAY[$selection]}${NC}\n"
    # Triple detach: all three stdio fds redirected away from terminal, nohup ignores SIGHUP,
    # subshell re-parents emulator to launchd (PID 1). Survives Ctrl+C, window close, and
    # EOF-on-stdin (the most common cause of emulator exit after script stops).
    (nohup emulator -avd "${EMULATOR_ARRAY[$selection]}" -no-snapshot-load \
        </dev/null >/dev/null 2>&1 &)

    printf "${BLUE}⏳ Waiting for emulator to boot...${NC}\n"
    adb wait-for-device
    while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    printf "${GREEN}✅ Emulator booted${NC}\n"

    DEVICE_ID=$(adb devices | grep -v "List of devices" | grep "device$" | awk '{print $1}' | head -1)
else
    DEVICE_COUNT=$(echo "$ADB_DEVICES" | wc -l | tr -d ' ')

    if [ -n "$DEVICE_ID_ARG" ]; then
        if echo "$ADB_DEVICES" | grep -q "^${DEVICE_ID_ARG}$"; then
            DEVICE_ID="$DEVICE_ID_ARG"
            printf "${GREEN}✅ Using specified device: ${DEVICE_ID}${NC}\n"
        else
            printf "${RED}❌ Device '$DEVICE_ID_ARG' not found.${NC}\n"
            echo "Connected devices:"; echo "$ADB_DEVICES"; exit 1
        fi
    elif [ "$DEVICE_COUNT" -eq 1 ]; then
        DEVICE_ID=$(echo "$ADB_DEVICES" | head -1)
        NAME=$(device_name "$DEVICE_ID")
        TYPE=$(is_emulator "$DEVICE_ID" && echo "Emulator" || echo "Physical Device")
        printf "${GREEN}✅ Auto-selected: ${NAME} (${DEVICE_ID}) — ${TYPE}${NC}\n"
    else
        printf "${CYAN}📱 Multiple devices found. Select one:${NC}\n"
        echo ""
        idx=1
        declare -a DEVICE_ID_ARRAY
        while IFS= read -r dev; do
            NAME=$(device_name "$dev")
            TYPE=$(is_emulator "$dev" && echo "Emulator" || echo "Physical Device")
            printf "  ${GREEN}${idx})${NC} ${NAME}\n"
            printf "     ${BLUE}ID:${NC}   ${dev}\n"
            printf "     ${BLUE}Type:${NC} ${TYPE}\n"
            echo ""
            DEVICE_ID_ARRAY[$idx]="$dev"
            ((idx++))
        done <<< "$ADB_DEVICES"

        read -p "Select device (1-$((idx-1))): " selection
        if [ -z "$selection" ] || [ "$selection" -lt 1 ] || [ "$selection" -ge "$idx" ]; then
            printf "${RED}❌ Invalid selection${NC}\n"; exit 1
        fi

        DEVICE_ID="${DEVICE_ID_ARRAY[$selection]}"
        NAME=$(device_name "$DEVICE_ID")
        TYPE=$(is_emulator "$DEVICE_ID" && echo "Emulator" || echo "Physical Device")
        printf "${GREEN}✅ Selected: ${NAME} (${DEVICE_ID}) — ${TYPE}${NC}\n"
    fi
fi

# Ensure emulator is fully booted before building
if is_emulator "$DEVICE_ID"; then
    printf "${BLUE}⏳ Ensuring emulator is fully booted...${NC}\n"
    while [ "$(adb -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
fi

printf "${GREEN}✅ Device ready: ${DEVICE_ID}${NC}\n"

# ── Build & install ────────────────────────────────────────────────────────────
printf "\n${BLUE}🔨 Building debug APK...${NC}\n"

# Compose Gradle -P flags
GRADLE_PROPS=""
if [ -n "$HMAC_SALT" ]; then
    GRADLE_PROPS="-PHMAC_SALT=${HMAC_SALT}"
fi

cd "$ANDROID_DIR"
./gradlew installDebug $GRADLE_PROPS

printf "${GREEN}✅ APK built and installed${NC}\n"

# ── Launch app ─────────────────────────────────────────────────────────────────
APP_ID="com.fenn.callshield.debug"
MAIN_ACTIVITY="com.fenn.callshield.MainActivity"

printf "\n${BLUE}🚀 Launching ${APP_ID}...${NC}\n"
adb -s "$DEVICE_ID" shell am start -n "${APP_ID}/${MAIN_ACTIVITY}"

printf "\n${GREEN}✅ CallShield launched on device${NC}\n"
printf "\n${YELLOW}📱 Device:${NC} $(device_name "$DEVICE_ID") (${DEVICE_ID})\n"
printf "${YELLOW}📦 App ID:${NC} ${APP_ID}\n"

# ── Logcat ─────────────────────────────────────────────────────────────────────
printf "\n${CYAN}📋 Streaming logcat (Ctrl+C to stop — app keeps running)...${NC}\n"
printf "${BLUE}──────────────────────────────────────────────${NC}\n"

# Wait for app PID (up to 5s), then attach logcat to it
APP_PID=""
for i in $(seq 1 10); do
    APP_PID=$(adb -s "$DEVICE_ID" shell pidof "$APP_ID" 2>/dev/null | tr -d '\r ')
    [ -n "$APP_PID" ] && break
    sleep 0.5
done

adb -s "$DEVICE_ID" logcat -c  # clear stale logs

# Ctrl+C stops logcat only — trap ensures app is NOT killed
trap "printf '\n${YELLOW}👋 Logcat stopped. App is still running on device.${NC}\n'; exit 0" INT TERM

if [ -n "$APP_PID" ]; then
    printf "${GREEN}🔎 Attached to PID ${APP_PID}${NC}\n"
    adb -s "$DEVICE_ID" logcat --pid="$APP_PID"
else
    printf "${YELLOW}⚠️  Could not detect app PID — streaming all logs filtered to package${NC}\n"
    adb -s "$DEVICE_ID" logcat | grep --line-buffered -E "callshield|AndroidRuntime|FATAL"
fi
