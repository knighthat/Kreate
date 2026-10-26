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

# Finds where a name is used in the tracked Kotlin files and says which declaration each use sits
# in, so the uses can be judged without opening the files.
#
#   callers.sh <name> [path...]
#
# Prints `file:line  Class › function  │ code`. Imports are left out; the declarations of the
# name itself are marked "(declares)". Finds the enclosing declaration by indentation, like
# extract.sh, so it relies on the project's formatting.

root="$(cd "$(dirname "$0")/../.." && pwd)"
[ -z "$1" ] && { echo "usage: callers.sh <name> [path...]" >&2; exit 1; }
name="$1"; shift

read -r -d '' py <<'PY'
import os, re, subprocess, sys
from collections import defaultdict

root, name, paths = sys.argv[1], sys.argv[2], sys.argv[3:]
grep = subprocess.run(["git", "-C", root, "grep", "-n", "-w", "-F", name, "--", *(paths or ["*.kt", "*.kts"])],
                      capture_output=True, text=True).stdout
hits = defaultdict(list)
for line in grep.splitlines():
    file, number, _ = line.split(":", 2)
    if file.endswith((".kt", ".kts")):
        hits[file].append(int(number))

declaration = re.compile(
    r"^( *)(?:@[\w.]+(?:\([^)]*\))? +)*(?:[a-z]+ +)*(fun|class|interface|object|val|var) +"
    r"(?:<[^>]*> +)?(?:[\w.]+(?:<[^>]*>)?\??\.)?(\w+)")
declares = re.compile(
    r"^ *(?:@[\w.]+(?:\([^)]*\))? +)*(?:[a-z]+ +)*(?:fun|class|interface|object|val|var|typealias) +"
    r"(?:<[^>]*> +)?(?:[\w.]+(?:<[^>]*>)?\??\.)?" + re.escape(name) + r"\b")

count = 0
for file, numbers in sorted(hits.items()):
    lines = open(os.path.join(root, file)).read().split("\n")
    for n in numbers:
        code = lines[n - 1]
        if code.lstrip().startswith(("import ", "package ")):
            continue
        count += 1
        depth = len(code) - len(code.lstrip(" "))
        found = []  # innermost first
        for i in range(n - 2, -1, -1):
            m = declaration.match(lines[i])
            if m and len(m[1]) < depth:
                found.append((m[2], m[3]))
                depth = len(m[1])
                if depth == 0:
                    break
        # a value is named only when it's a property (of a class or the file), not a local one
        chain = [label + ("()" if kind == "fun" else "") for j, (kind, label) in enumerate(found)
                 if kind not in ("val", "var") or j + 1 == len(found) or found[j + 1][0] in ("class", "interface", "object")]
        where = " › ".join(reversed(chain)) or "(top level)"
        stripped = code.lstrip()
        mark = " (declares)" if declares.match(code) else " (comment)" if stripped.startswith(("*", "/*", "//")) else ""
        text = code.strip()
        print(f"{file}:{n}  {where}{mark}  │ {text[:100] + '…' if len(text) > 100 else text}")
print(f"# {count} uses of {name}")
PY
python3 -I -c "$py" "$root" "$name" "$@"
