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

# Runs Gradle tasks with --continue and prints only what failed: compiler errors, Gradle's
# "What went wrong" blocks and failed tests (message and the project's stack frames, read from the
# test results the run wrote). The full log is kept and its path printed.
#
#   build.sh [-w] <task>... [gradle options]
#
# -w also lists compiler warnings (only their count otherwise).

warnings=0
[ "$1" = "-w" ] && { warnings=1; shift; }
[ $# -eq 0 ] && { echo "usage: build.sh [-w] <task>... [gradle options]" >&2; exit 1; }

root="$(cd "$(dirname "$0")/../.." && pwd)"
log="$(mktemp "${TMPDIR:-/tmp}/kreate-build-XXXXXX.log")"
marker="$log.start"
touch "$marker"
started=$(date +%s)

(cd "$root" && ./gradlew "$@" --continue --console=plain) >"$log" 2>&1
status=$?

read -r -d '' py <<'PY'
import os, re, sys, xml.etree.ElementTree as ET

root, log, marker, warnings = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] == "1"
text = open(log, errors="replace").read()

def rel(path):
    path = re.sub(r"^file://", "", path)
    return os.path.relpath(path, root) if path.startswith(root) else path

errors, warns = [], []
for line in text.splitlines():
    # Kotlin: "e: file:///path/File.kt:12:5 message"
    m = re.match(r"^([ew]): (?:file://)?(/\S+?):(\d+):(\d+) (.*)", line)
    if m:
        (errors if m[1] == "e" else warns).append(f"{rel(m[2])}:{m[3]} {m[5]}")
        continue
    # javac and AAPT: "/path/File.java:12: error: message", "ERROR: /path:12: message"
    m = re.match(r"^(?:ERROR: )?(/\S+?):(\d+):(?:\d+:)? ?(?:error:)? ?(.*)", line)
    if m and ("error" in line.lower()):
        errors.append(f"{rel(m[1])}:{m[2]} {m[3]}")

for e in dict.fromkeys(errors):
    print("e:", e)
if warns:
    if warnings:
        for w in dict.fromkeys(warns):
            print("w:", w)
    else:
        print(f"({len(set(warns))} compiler warnings, -w lists them)")

# Failed tests, from the results this run wrote
start = os.path.getmtime(marker)
failed = 0
for dirpath, dirnames, files in os.walk(root):
    dirnames[:] = [d for d in dirnames if d not in (".git", ".gradle", ".claude", "intermediates", "tmp")]
    if "test-results" not in dirpath:
        continue
    for name in files:
        path = os.path.join(dirpath, name)
        if not name.endswith(".xml") or os.path.getmtime(path) < start:
            continue
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                failed += 1
                cls = case.get("classname", "").rsplit(".", 1)[-1]
                message = (problem.get("message") or "").strip().splitlines()
                print(f"FAILED {cls}.{case.get('name')}: {message[0] if message else problem.get('type', '')}")
                frames = [l.strip() for l in (problem.text or "").splitlines() if "me.knighthat.kreate3" in l and l.strip().startswith("at ")]
                for frame in frames[:5]:
                    print("    " + frame)

# Gradle's own failure blocks, without the "Try" advice; skip the test summary already shown
for block in re.findall(r"\* What went wrong:\n(.*?)(?=\n\* Try:|\n\* Exception is:|\n\n\* |\Z)", text, re.S):
    block = block.strip()
    if failed and "There were failing tests" in block:
        continue
    if errors and re.search(r"task '[^']*:compile\w*(Kotlin|Java)'", block):
        block = block.splitlines()[0]
    print("gradle:", "\n        ".join(l for l in block.splitlines() if l.strip())[:1500])
PY
python3 -I -c "$py" "$root" "$log" "$marker" "$warnings"

rm -f "$marker"
elapsed=$(( $(date +%s) - started ))
if [ $status -eq 0 ]; then
    echo "BUILD OK (${elapsed}s, log $log)"
else
    echo "BUILD FAILED (${elapsed}s, log $log)"
fi
exit $status
