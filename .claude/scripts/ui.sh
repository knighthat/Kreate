#!/bin/bash
#
# Kreate
# Copyright (C) 2026 KnightHat
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

# Drives the app on an Android Studio emulator by what the screen shows.
#
#   ui.sh dump [pattern]       lists "text | content-desc | bounds" of the screen, optionally grep -i'd
#   ui.sh tap <label> [long]   taps (or long-presses) the first node whose text or
#                              content-desc is exactly <label>
#
# Only emulators: ANDROID_SERIAL, when set, must be emulator-*; otherwise the first emulator-* of
# `adb devices` is used. Any other device (a real phone) is refused.

serial="${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}"
case "$serial" in
  emulator-*) ;;
  "") echo "No emulator attached" >&2; exit 2 ;;
  *) echo "Refusing $serial: not an emulator" >&2; exit 2 ;;
esac

dump() {
  adb -s "$serial" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb -s "$serial" exec-out cat /sdcard/ui.xml | python3 -I -c '
import re, sys
for match in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    node = match.group(0)
    def attr(name):
        found = re.search(" " + name + "=\"([^\"]*)\"", node)
        return found.group(1) if found else ""
    text, desc = attr("text"), attr("content-desc")
    if text or desc:
        print((text or "-") + " | " + (desc or "-") + " | " + attr("bounds"))
'
}

case "$1" in
  dump)
    if [ -n "$2" ]; then dump | grep -i -- "$2"; else dump; fi
    ;;
  tap)
    line=$(dump | awk -F' \\| ' -v label="$2" '$1 == label || $2 == label { print; exit }')
    [ -z "$line" ] && { echo "Not on screen: $2" >&2; exit 1; }
    read -r x1 y1 x2 y2 < <(echo "$line" | awk -F' \\| ' '{ print $3 }' | sed 's/\]\[/ /; s/[][]//g' | tr ',' ' ')
    x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
    if [ "$3" = long ]; then
      adb -s "$serial" shell input swipe "$x" "$y" "$x" "$y" 800
    else
      adb -s "$serial" shell input tap "$x" "$y"
    fi
    echo "Tapped $2 at $x,$y"
    ;;
  *)
    echo "Usage: ui.sh dump [pattern] | ui.sh tap <label> [long]" >&2
    exit 64
    ;;
esac
