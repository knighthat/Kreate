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

# Prints the app's log from an Android Studio emulator, collapsed: only the app's own processes
# (by its uid, so a restart is followed), from a level up, and lines that differ only in numbers or
# hashes shown once with their count, at the place of the last one.
#
#   logcat.sh [-l V|D|I|W|E] [-n lines] [-g regex] [-m minutes | -s 'MM-DD HH:MM:SS.mmm'] [-p package]
#
# -m keeps the last minutes only (the buffer holds days). A group of lines sits where its last line
# was, with that line's time. Defaults: level W, the last 80 lines, package me.knighthat.kreate3. It reads the buffer
# (`logcat -d`) and never clears it.
#
# Only emulators: ANDROID_SERIAL, when set, must be emulator-*; otherwise the first emulator-* of
# `adb devices` is used. Any other device (a real phone) is refused.

level=W
lines=80
pattern=""
since=""
minutes=""
package=me.knighthat.kreate3
while getopts "l:n:g:s:m:p:" opt; do
    case "$opt" in
        l) level="$OPTARG" ;;
        n) lines="$OPTARG" ;;
        g) pattern="$OPTARG" ;;
        s) since="$OPTARG" ;;
        m) minutes="$OPTARG" ;;
        p) package="$OPTARG" ;;
        *) exit 1 ;;
    esac
done

serial="${ANDROID_SERIAL:-$(adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')}"
case "$serial" in
  emulator-*) ;;
  "") echo "No emulator attached" >&2; exit 2 ;;
  *) echo "Refusing $serial: not an emulator" >&2; exit 2 ;;
esac

uid=$(adb -s "$serial" shell pm list packages -U "$package" | awk -v p="package:$package" '$1 == p { sub("uid:", "", $2); print $2 }')
[ -z "$uid" ] && { echo "$package isn't installed on $serial" >&2; exit 3; }

if [ -n "$minutes" ]; then
    now=$(adb -s "$serial" shell date +%s | tr -d '\r')
    since=$(adb -s "$serial" shell date -d "@$(( now - minutes * 60 ))" "'+%m-%d %H:%M:%S.000'" | tr -d '\r')
fi

args=(-d -v threadtime --uid "$uid" "*:$level")
[ -n "$since" ] && args+=(-T "$since")

read -r -d '' py <<'PY'
import re, sys
from collections import OrderedDict

lines, pattern = int(sys.argv[1]), sys.argv[2]
seen = OrderedDict()
# threadtime: "10-26 14:03:12.345  1234  1250 W Tag     : message"
entry = re.compile(r"^(\S+ \S+)\s+\d+\s+\d+\s+([VDIWEF])\s+(.*?)\s*: (.*)$")
similar = re.compile(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}|0x[0-9a-fA-F]+|@[0-9a-fA-F]+|\b(?=[a-f]*\d)[0-9a-f]{6,}\b|\d+")
for raw in sys.stdin:
    m = entry.match(raw.rstrip("\n"))
    if not m:
        continue
    time, level, tag, message = m.groups()
    text = f"{level} {tag}: {message}"
    if pattern and not re.search(pattern, text):
        continue
    # lines that differ only in numbers, hex values or object hashes are one line
    key = similar.sub("#", text)
    if key in seen:
        seen[key][2] += 1
        seen[key][3] = time
        seen.move_to_end(key)
    else:
        seen[key] = [text, time, 1, time]
items = list(seen.values())
if len(items) > lines:
    print(f"# {len(items) - lines} earlier lines left out (-n shows more)")
for text, first, count, last in items[-lines:]:
    print(f"{last[:14]} {text}" + (f"  ×{count} like it since {first[:14]}" if count > 1 else ""))
PY
adb -s "$serial" logcat "${args[@]}" | python3 -I -c "$py" "$lines" "$pattern"
