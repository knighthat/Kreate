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

# Looks up string resources of every module's res/values (strings.xml, plurals.xml) without
# reading the files whole. Prints file:line, key and text; plurals as `one | other`.
#
#   strings.sh <key>...        keys starting with it (a key that exists exactly is marked "=")
#   strings.sh -t <text>       strings whose English contains the text (case ignored)
#   strings.sh -c              number of keys per prefix (the part before the first `_`)

root="$(cd "$(dirname "$0")/../.." && pwd)"

read -r -d '' py <<'PY'
import glob, html, os, re, sys
from collections import Counter

root, args = sys.argv[1], sys.argv[2:]
if not args:
    sys.exit("usage: strings.sh <key>... | -t <text> | -c")

entries = []  # (file, line, key, kind, text)
for path in sorted(glob.glob(os.path.join(root, "*/src/main/res/values/*.xml"))):
    content = open(path).read()
    rel = os.path.relpath(path, root)
    line = lambda m: content.count("\n", 0, m.start()) + 1
    for m in re.finditer(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', content, re.S):
        entries.append((rel, line(m), m[1], "", m[2]))
    for m in re.finditer(r'<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>', content, re.S):
        items = re.findall(r'<item\s+quantity="([^"]+)"\s*>(.*?)</item>', m[2], re.S)
        entries.append((rel, line(m), m[1], " [plural]", " | ".join(t for _, t in items)))
    for m in re.finditer(r'<string-array\s+name="([^"]+)"[^>]*>(.*?)</string-array>', content, re.S):
        items = re.findall(r'<item>(.*?)</item>', m[2], re.S)
        entries.append((rel, line(m), m[1], " [array]", " | ".join(items)))

def show(e, mark=" "):
    text = re.sub(r"\s+", " ", html.unescape(e[4])).strip()
    print(f"{mark} {e[0]}:{e[1]}  {e[2]}{e[3]} = {text}")

if args[0] == "-c":
    for prefix, n in Counter(e[2].split("_")[0] for e in entries).most_common():
        print(f"{n:4d}  {prefix}_")
elif args[0] == "-t":
    needle = " ".join(args[1:]).lower()
    hits = [e for e in entries if needle in html.unescape(e[4]).lower()]
    for e in hits:
        show(e)
    if not hits:
        print(f"no string contains \"{needle}\"")
else:
    for key in args:
        hits = [e for e in entries if e[2].startswith(key)]
        for e in hits:
            show(e, "=" if e[2] == key else " ")
        if not hits:
            print(f"  no key starts with {key}")
PY
python3 -I -c "$py" "$root" "$@"
