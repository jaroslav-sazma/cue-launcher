#!/usr/bin/env bash
# Checks on a connected emulator that Cue: does no needless work while it is not on screen: the
# launcher is sent behind Settings, a notification is reposted every two seconds the way a progress
# notification is, and afterwards the process must hold no observer, receiver or service binding
# beyond the notification listener, and must have spent next to no CPU time. The listener stays
# bound in the background on purpose while a scenario or the badges need the posts — the need the
# launcher stored says so — and the process is then in the bound-service state, otherwise in the
# home state. Reading the stored need takes a debug build.
#
#   scripts/background-audit.sh [seconds] [--with-listener]
#
# --with-listener grants notification access for the run and takes it away again afterwards. The
# listener binds only while badges are on or a scenario watches notifications, so that is what makes
# the run exercise it. The reposted notification stays in the shade under the tag "cue-audit"; the
# shell has no way to cancel it.
set -euo pipefail

# The debug build, which run-as needs, carries the .debug suffix; its class names do not.
PACKAGE=app.cue.launcher.debug
LISTENER=$PACKAGE/app.cue.launcher.system.notifications.CueNotificationListener
SECONDS_AWAY=60
WITH_LISTENER=false
# 100 Hz clock ticks; a launcher at rest costs well under a tick per second.
TICKS_PER_MINUTE=30

for arg in "$@"; do
    case $arg in
        --with-listener) WITH_LISTENER=true ;;
        *) SECONDS_AWAY=$arg ;;
    esac
done

fail=0
report() { printf '%-28s %s\n' "$1" "$2"; }
problem() { report "$1" "FAIL  $2"; fail=1; }

adb get-state >/dev/null
adb shell pm path "$PACKAGE" >/dev/null || { echo "$PACKAGE is not installed"; exit 2; }

granted_before=$(adb shell settings get secure enabled_notification_listeners | grep -c "$LISTENER" || true)
if $WITH_LISTENER && [ "$granted_before" = 0 ]; then
    adb shell cmd notification allow_listener "$LISTENER"
fi
restore() {
    if $WITH_LISTENER && [ "$granted_before" = 0 ]; then
        adb shell cmd notification disallow_listener "$LISTENER"
    fi
    adb shell input keyevent KEYCODE_HOME
}
trap restore EXIT

adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell am start -a android.settings.SETTINGS >/dev/null
sleep 6

pid=$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)
[ -n "$pid" ] || { echo "$PACKAGE is not running"; exit 2; }
ticks() { adb shell cat "/proc/$1/stat" | awk '{ print $14 + $15 }'; }
start=$(ticks "$pid")

for ((i = 0; i < SECONDS_AWAY; i += 2)); do
    adb shell cmd notification post -t "Cue audit" cue-audit "Progress $i" >/dev/null
    sleep 2
done

# The state an event's gate reads stays subscribed for five seconds after the last event.
sleep 6

now=$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)
[ "$now" = "$pid" ] || problem "process" "restarted ($pid -> ${now:-dead})"
max_ticks=$(( TICKS_PER_MINUTE * SECONDS_AWAY / 60 ))
if [ "$now" != "$pid" ]; then
    exit 1
fi
spent=$(( $(ticks "$pid") - start ))

services=$(adb shell dumpsys activity services "$PACKAGE")
process=$(adb shell dumpsys activity p "$PACKAGE")
listener_bound=$(echo "$services" | grep 'ConnectionRecord{' | grep -c 'CueNotificationListener' || true)
needed=$(adb shell run-as "$PACKAGE" cat shared_prefs/notification_listener.xml 2>/dev/null \
    | grep -c 'name="needed" value="true"' || true)
state=$(echo "$process" | awk '/state: cur=/ { sub("cur=", "", $2); print $2; exit }')
if [ "$listener_bound" -gt 0 ] && [ "$needed" = 0 ]; then
    problem "notification listener" "bound although nothing needs it"
    expected="BFGS"
elif [ "$listener_bound" -gt 0 ]; then
    report "notification listener" "bound (a scenario or the badges need it)"
    expected="BFGS"
else
    report "notification listener" "not bound"
    expected="HOME|CEM|CACT|CACR|CRE|LAST"
fi
if echo "$state" | grep -qxE "$expected"; then
    report "process state" "ok    $state"
else
    problem "process state" "$state (expected $expected)"
fi

if [ "$spent" -le "$max_ticks" ]; then
    report "cpu over ${SECONDS_AWAY}s" "ok    $spent ticks"
else
    problem "cpu over ${SECONDS_AWAY}s" "$spent ticks (limit $max_ticks)"
fi

# Reduced-motion follows animator_duration_scale through a settings observer Compose keeps for the
# whole window; it costs nothing until the setting changes.
observers=$(adb shell dumpsys content | grep "pid=$pid " | grep -v 'settings/global/animator_duration_scale' || true)
if [ -z "$observers" ]; then report "content observers" "ok    none"; else problem "content observers" "$(echo "$observers" | awk '{ print $1 }' | tr '\n' ' ')"; fi

receivers=$(adb shell dumpsys activity broadcasts \
    | awk '/Registered Receivers:/,/Receiver Resolver Table:/' \
    | awk -v pid=" $pid " 'index($0, "ReceiverList{") && index($0, pid) { on = 1; next } /ReceiverList\{/ { on = 0 } on && /Action:/ { print $2 }' \
    | sort -u)
if [ -z "$receivers" ]; then report "registered receivers" "ok    none"; else problem "registered receivers" "$(echo "$receivers" | tr '\n' ' ')"; fi

bindings=$(echo "$services" | grep 'ConnectionRecord{' | grep -vc 'CueNotificationListener' || true)
if [ "$bindings" = 0 ]; then report "other service bindings" "ok    none"; else problem "other service bindings" "$bindings"; fi

# Bindings the launcher holds to other apps' services are listed with its process, not its services.
outgoing=$(echo "$process" | awk '/^    Connections:/ { on = 1; next } on && /^    [A-Za-z]/ { on = 0 } on && /ConnectionRecord/' | wc -l | tr -d ' ')
if [ "$outgoing" = 0 ]; then report "bindings to other apps" "ok    none"; else problem "bindings to other apps" "$outgoing"; fi

exit $fail
