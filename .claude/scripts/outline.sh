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

# Prints the declarations of Kotlin files with their line numbers, so a long file can be read by
# line range instead of whole.
#
#   outline.sh <file.kt>... [-d depth]
#
# depth is how many levels of nesting to show (default 2: top level and members); local values
# inside functions are left out by it.

depth=2
files=()
while [ $# -gt 0 ]; do
    case "$1" in
        -d) depth="$2"; shift 2 ;;
        *) files+=("$1"); shift ;;
    esac
done
[ ${#files[@]} -eq 0 ] && { echo "usage: outline.sh <file.kt>... [-d depth]" >&2; exit 1; }

for file in "${files[@]}"; do
    [ ${#files[@]} -gt 1 ] && echo "== $file ($(wc -l < "$file") lines)"
    awk -v depth="$depth" '
        {
            match($0, /^ */)
            level = int(RLENGTH / 4)
        }
        level >= depth { next }
        /^ *(@[A-Za-z.]+(\([^)]*\))? +)*((public|private|internal|protected|override|open|abstract|sealed|data|enum|inline|value|annotation|inner|companion|const|lateinit|suspend|operator|infix|tailrec|external|expect|actual|fun interface) +)*(class|interface|object|fun|val|var|typealias|constructor|init) / ||
        /^ *companion object/ || /^ *init *\{/ {
            line = $0
            sub(/^ +/, "", line)
            sub(/ *[={] *$/, "", line)
            if (length(line) > 110) line = substr(line, 1, 107) "..."
            printf "%5d  %s%s\n", NR, sprintf("%*s", level * 2, ""), line
        }
    ' "$file"
done
