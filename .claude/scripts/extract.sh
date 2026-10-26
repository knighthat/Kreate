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

# Prints one declaration of a Kotlin file (function, class, object, property) with its KDoc and
# annotations, numbered like the file, so it can be read without the rest of the file.
#
#   extract.sh <file.kt> <name>
#
# Every declaration of that name is printed (overloads, members of several classes). The end is
# found by indentation: the declaration ends at its closing `}` on its own indent, or before the
# next line indented no deeper than it. Relies on the project's formatting.

file="$1"
name="$2"
[ -f "$file" ] && [ -n "$name" ] || { echo "usage: extract.sh <file.kt> <name>" >&2; exit 1; }

awk -v name="$name" '
    function indent(s) { match(s, /^ */); return RLENGTH }
    { lines[NR] = $0 }
    END {
        pattern = "^ *(@[A-Za-z.]+(\\([^)]*\\))? +)*([a-z]+ +)*(fun|class|interface|object|val|var|typealias) +(<[^>]*> +)?([A-Za-z0-9_.]+(<[^>]*>)?[?]?\\.)?" name "([^A-Za-z0-9_]|$)"
        found = 0
        for (i = 1; i <= NR; i++) {
            if (lines[i] !~ pattern) continue
            found++
            level = indent(lines[i])

            # KDoc, comments and annotations right above, at the same indent
            start = i
            while (start > 1 && indent(lines[start - 1]) >= level && lines[start - 1] ~ /^ *(@|\/\*\*|\*|\/\/)/)
                start--

            # A one-line declaration ends on its own line
            end = i
            for (j = i + 1; j <= NR; j++) {
                line = lines[j]
                if (line ~ /^ *$/) continue
                d = indent(line)
                if (d > level) { end = j; continue }
                if (d == level && line ~ /^ *\)/) { end = j; continue }
                if (d == level && line ~ /^ *\}/) end = j
                break
            }

            if (found > 1) print "--"
            for (k = start; k <= end; k++) printf "%5d  %s\n", k, lines[k]
        }
        if (!found) { print "extract: no declaration named " name > "/dev/stderr"; exit 1 }
    }
' "$file"
