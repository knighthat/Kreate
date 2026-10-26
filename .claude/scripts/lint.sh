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

# Summarizes the lint reports the last `./gradlew lint` left (the newest XML report of each
# module), so the reports themselves never need reading.
#
#   lint.sh             one line per issue id: count, severity, id, summary, files
#   lint.sh <id>...     every issue of those ids: file:line, message, the flagged code
#
# It reads reports, it doesn't run lint: the report's age is printed, run lint again when stale.

root="$(cd "$(dirname "$0")/../.." && pwd)"

read -r -d '' py <<'PY'
import glob, os, sys, time, xml.etree.ElementTree as ET
from collections import defaultdict

root, ids = sys.argv[1], set(sys.argv[2:])

reports = {}
for path in glob.glob(os.path.join(root, "*/build/reports/lint-results-*.xml")):
    module = os.path.relpath(path, root).split(os.sep)[0]
    if module not in reports or os.path.getmtime(path) > os.path.getmtime(reports[module]):
        reports[module] = path
if not reports:
    sys.exit("lint: no reports, run ./gradlew lint (or .claude/scripts/build.sh lint)")

def rel(path):
    return os.path.relpath(path, root) if path.startswith(root) else path

for module, path in sorted(reports.items()):
    age = int((time.time() - os.path.getmtime(path)) / 60)
    print(f"# {module}: {os.path.basename(path)}, {age} min old")

groups = defaultdict(list)
for module, path in sorted(reports.items()):
    for issue in ET.parse(path).getroot().iter("issue"):
        groups[issue.get("id")].append(issue)

if ids:
    for id in sorted(ids):
        for issue in groups.get(id, []):
            location = issue.find("location")
            where = f"{rel(location.get('file'))}:{location.get('line', '?')}" if location is not None else "?"
            print(f"{where} [{id}] {issue.get('message')}")
            code = issue.get("errorLine1")
            if code:
                print("    " + code.strip())
        if id not in groups:
            print(f"{id}: no issues")
else:
    order = {"Fatal": 0, "Error": 1, "Warning": 2, "Information": 3}
    for id, issues in sorted(groups.items(), key=lambda g: (order.get(g[1][0].get("severity"), 9), -len(g[1]))):
        files = sorted({rel(l.get("file")) for i in issues for l in i.findall("location")})
        shown = ", ".join(os.path.basename(f) for f in files[:4]) + (f", +{len(files) - 4}" if len(files) > 4 else "")
        print(f"{len(issues):4d}  {issues[0].get('severity'):<8} {id}: {issues[0].get('summary')} ({shown})")
PY
python3 -I -c "$py" "$root" "$@"
