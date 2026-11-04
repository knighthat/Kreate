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

# Drafts changelogs.yml with a local Ollama model: each commit since the last stable tag is
# classified on its own (adds, removes, changes, fixes, or skip) following .claude/docs/versioning.md.
#
#   changelog.sh [<from-ref>] [--write] [--max N]
#
# - from-ref defaults to the last stable tag (v*, no "-"); without any tag, every commit.
# - Commits whose subject names tests, previews, emulators, sqlite, the file index, agent rules or
#   scripts are dropped without asking the model.
# - Each section lists the most important items first. --max N (default 20) keeps only the N most
#   important of adds, removes and changes together; fixes are never dropped.
# - The draft goes to stdout. --write puts it in app/src/main/res/raw/changelogs.yml instead
#   (replacing it), so run it before editing that file.
# - SKIM_MODEL (default qwen2.5-coder:7b) and OLLAMA_URL (default http://localhost:11434) override.
# - A small model misjudges: read the draft. It can't see that a later commit fixes or reworks an
#   earlier one of the same release, so merge those lines by hand.

set -euo pipefail
cd "$(dirname "$0")/../.."

model=${SKIM_MODEL:-qwen2.5-coder:7b}
url=${OLLAMA_URL:-http://localhost:11434}
yml=app/src/main/res/raw/changelogs.yml

from=""
write=false
max=20
expect_max=false
for a in "$@"; do
  if $expect_max; then max=$a; expect_max=false; continue; fi
  case "$a" in
    --write) write=true ;;
    --max) expect_max=true ;;
    -*) echo "unknown option $a" >&2; exit 1 ;;
    *) from="$a" ;;
  esac
done
if [ -z "$from" ]; then
  from=$(git tag --list 'v*' --sort=-v:refname | grep -v -- '-' | head -n1 || true)
fi

curl -sf "$url/api/tags" > /dev/null || { echo "Ollama is not answering at $url" >&2; exit 3; }

read -r -d '' system <<'PROMPT' || true
You sort git commits of an Android music player into changelog entries for its users.
Answer with JSON only: {"section": "adds|removes|changes|fixes|skip", "importance": 1-3, "text": "..."}.
Keep a commit only when it changes something the user directly sees, taps or hears in the app.
- adds: a feature or setting users did not have. removes: a feature or setting taken away.
- changes: something users already had now works or looks differently, or a default flipped.
- fixes: a bug users could hit (crash, wrong behavior, broken screen) is gone.
- skip everything else: refactors, tests, previews, build, CI, docs, agent rules, file index,
  scripts, emulator or developer tooling, code style, performance or state handling inside the
  code, string extraction, rewording or cleanup, translations, units or constants in code, and any
  commit that only touches those. When unsure whether users can see it, skip.
- importance: 3 a major feature, page or redesign; 2 a visible improvement; 1 a small tweak.
- text: one short phrase in plain English for a user. The section is already its heading, so never
  start with Added, Adds, Removed, Fixed, Fixes or Changed: adds and removes name the thing ("Song
  downloads with offline covers"), changes say what is different now ("Album pages have a tinted
  header"), fixes name the problem that is gone ("Crash when a paused song is disliked"). Use only facts stated in the commit, add
  nothing. No code names, library names or file names. Describe the feature, not the commit.
  Empty when skip.
PROMPT

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
total=$(git rev-list --count ${from:+"$from"..}HEAD)
n=0
while IFS= read -r -d '' msg; do
  n=$((n + 1))
  # Subjects about developer-only work never reach the model
  subject=$(sed -n '/./{p;q}' <<< "$msg")
  [[ "$subject" =~ (^|[^a-z])(tests?|previews?|emulators?|sqlite3?|file\ index|agent|rules?|scripts?)([^a-z]|$) ]] && continue
  printf '\r%d/%d' "$n" "$total" >&2
  body=$(jq -n --arg m "$model" --arg s "$system" --arg c "$msg" '{
    model: $m, stream: false, format: "json", options: {temperature: 0},
    messages: [{role: "system", content: $s}, {role: "user", content: $c}]}')
  ans=$(curl -sf "$url/api/chat" -d "$body" | jq -r '.message.content') || continue
  section=$(jq -r '.section // "skip"' <<< "$ans" 2>/dev/null || echo skip)
  text=$(jq -r '.text // "" | gsub("[\\t\\n]"; " ")' <<< "$ans" 2>/dev/null || echo "")
  imp=$(jq -r '.importance // 1 | tonumber | if . < 1 then 1 elif . > 3 then 3 else . end' <<< "$ans" 2>/dev/null || echo 1)
  # Safety net for the heading verbs the model still starts with
  text=$(sed -E 's/^(Added|Adds|Add|Removed|Removes|Remove|Fixed|Fixes|Fix|Changed|Changes|Change)[[:space:]]+//; s/^./\U&/; s/\.$//' <<< "$text")
  [ -n "$text" ] || continue
  case "$section" in
    adds|removes|changes|fixes) printf '%s\t%s\t%s\n' "$imp" "$section" "$text" >> "$tmp" ;;
  esac
done < <(git log --reverse --format='%B%x00' ${from:+"$from"..}HEAD)
echo >&2

# Most important first (ties keep commit order), duplicates dropped, adds/removes/changes capped
kept=$(sort -t$'\t' -k1,1nr -s "$tmp" | awk -F'\t' -v max="$max" '
  seen[$3]++ { next }
  $2 == "fixes" { print; next }
  ++n <= max { print }')

emit() {
  local list
  list=$(awk -F'\t' -v s="$1" '$2 == s { print $3 }' <<< "$kept")
  [ -n "$list" ] || return 0
  echo "$1:"
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/^/  - "/' -e 's/$/"/' <<< "$list"
}

draft=$(emit adds; emit removes; emit changes; emit fixes)

if $write; then
  printf '%s\n' "$draft" > "$yml"
  echo "wrote $yml" >&2
else
  printf '%s\n' "$draft"
fi
