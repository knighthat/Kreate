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

# Bumps the app's version: one run is one release. Rules: .claude/docs/versioning.md
#
#   bump.sh <major|minor|patch|beta|X.Y.Z|X.Y.Z-beta.N> [--keep-code] [--dry-run]
#
# - Writes versionName (and versionCode + 1, unless --keep-code) in gradle/libs.versions.toml.
# - Stable release: checks the bump against app/changelogs.yml (any adds, removes or changes need
#   at least a minor bump), then writes each platform flavor's notes for the app (About > Version)
#   to app/src/<flavor>/res/raw/changelog.json and foss's shortened notes to
#   fastlane/metadata/android/en-US/changelogs/<versionCode * 10>.txt (500 bytes at most).
#   An item starting with "[foss]" or "[github, patreon]" goes only to the flavors it names.
#   app/changelogs.yml is deleted once they're written: it's never committed.
# - Beta release (X.Y.Z-beta.N): no changelog, prints the commit subjects since the last stable tag.
# - "beta" raises N of the current X.Y.Z-beta.N by one.
# - Prints the commit message and tag command; committing and tagging stay with the maintainer.
# - --dry-run prints what would happen and writes nothing.

set -euo pipefail
cd "$(dirname "$0")/../.."

toml=gradle/libs.versions.toml
yml=app/changelogs.yml
fastlane_dir=fastlane/metadata/android/en-US/changelogs
limit=500
# The platform flavors of app/build.gradle.kts; foss is the one F-Droid (fastlane) builds
platforms=(github foss patreon)

arg=""
keep_code=false
dry=false
for a in "$@"; do
  case "$a" in
    --keep-code) keep_code=true ;;
    --dry-run) dry=true ;;
    -*) echo "unknown option $a" >&2; exit 1 ;;
    *) arg="$a" ;;
  esac
done
[ -n "$arg" ] || { sed -n '20,34p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 1; }

current=$(sed -n 's/^versionName = "\(.*\)"/\1/p' "$toml")
code=$(sed -n 's/^versionCode = "\(.*\)"/\1/p' "$toml")
[ -n "$current" ] && [ -n "$code" ] || { echo "cannot read version from $toml" >&2; exit 1; }

base=${current%%-*}
IFS=. read -r major minor patch <<< "$base"

case "$arg" in
  major) new="$((major + 1)).0.0" ;;
  minor) new="$major.$((minor + 1)).0" ;;
  patch) new="$major.$minor.$((patch + 1))" ;;
  beta)
    [[ "$current" =~ ^(.*)-beta\.([0-9]+)$ ]] || { echo "$current is not a beta" >&2; exit 1; }
    new="${BASH_REMATCH[1]}-beta.$((BASH_REMATCH[2] + 1))" ;;
  *) new="$arg" ;;
esac
[[ "$new" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+)?$ ]] || { echo "bad version $new" >&2; exit 1; }
[ "$new" != "$current" ] || { echo "already at $current" >&2; exit 1; }

new_code=$code
$keep_code || new_code=$((code + 1))
beta=false
[[ "$new" == *-* ]] && beta=true

# Items of one section of changelogs.yml, one per line, quotes stripped
items() {
  awk -v key="$1:" '
    /^[A-Za-z]+:/ { sec = $1; next }
    sec == key && /^[[:space:]]*-[[:space:]]/ { sub(/^[[:space:]]*-[[:space:]]+/, ""); print }
  ' "$yml" | sed -e 's/[[:space:]]*$//' -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/"
}

# Items of one section for one platform flavor: untagged ones, and tagged ones naming it (tag cut)
platform_items() {
  local line
  while IFS= read -r line; do
    if [[ "$line" =~ ^\[([^]]*)\][[:space:]]*(.*)$ ]]; then
      [[ ",${BASH_REMATCH[1]// /}," == *",$2,"* ]] && printf '%s\n' "${BASH_REMATCH[2]}"
    else
      printf '%s\n' "$line"
    fi
  done < <(items "$1")
  return 0
}

# The app's notes of one platform flavor, as ChangelogSheet.kt reads them
changelog_json() {
  local -a args=(--arg version "$new")
  local key
  for key in adds removes changes fixes; do
    args+=(--argjson "$key" "$(platform_items "$key" "$1" | jq -R . | jq -sc .)")
  done
  jq -n "${args[@]}" '{version: $version, adds: $adds, removes: $removes, changes: $changes, fixes: $fixes}'
}

fastlane=""
if ! $beta; then
  [ -f "$yml" ] || { echo "missing $yml" >&2; exit 1; }
  features=$(( $(items adds | wc -l) + $(items removes | wc -l) + $(items changes | wc -l) ))
  fixes=$(items fixes | wc -l)
  [ $((features + fixes)) -gt 0 ] || { echo "$yml has no items" >&2; exit 1; }
  for key in adds removes changes fixes; do
    while IFS= read -r tag; do
      for p in ${tag//,/ }; do
        [[ " ${platforms[*]} " == *" $p "* ]] || { echo "unknown flavor \"$p\" in $yml ($key)" >&2; exit 1; }
      done
    done < <(items "$key" | sed -n 's/^\[\([^]]*\)\].*/\1/p')
  done

  # The notes cover the changes since the last stable tag, so the bump is measured from it (a
  # beta's X.Y.Z isn't released yet); without one this is the first stable release, not checked
  stable=$(git tag --list 'v*' --sort=-v:refname | grep -v -- '-' | head -n1 || true)
  if [ -n "$stable" ]; then
    IFS=. read -r smajor sminor _ <<< "${stable#v}"
    IFS=. read -r nmajor nminor _ <<< "$new"
    if [ "$nmajor" = "$smajor" ] && [ "$nminor" = "$sminor" ] && [ "$features" -gt 0 ]; then
      echo "warning: $features adds/removes/changes but $new is only a patch bump from $stable" >&2
    elif { [ "$nmajor" != "$smajor" ] || [ "$nminor" != "$sminor" ]; } && [ "$features" -eq 0 ]; then
      echo "warning: only fixes listed but $new is more than a patch bump from $stable" >&2
    fi
  fi

  # Items go in rounds (every section's first item, then every second one, ...) while the text
  # fits, so the notes stay balanced and the most important items, listed first, survive
  declare -A sec
  for pair in Added:adds Removed:removes Changed:changes Fixed:fixes; do
    mapfile -t "list_${pair#*:}" < <(platform_items "${pair#*:}" foss)
  done
  render() {
    local out="" label key n i
    for pair in Added:adds Removed:removes Changed:changes Fixed:fixes; do
      label=${pair%%:*}; key=${pair#*:}
      n=${take[$key]:-0}
      [ "$n" -gt 0 ] || continue
      local -n arr="list_$key"
      out+="$label:"$'\n'
      for ((i = 0; i < n; i++)); do out+="- ${arr[i]}"$'\n'; done
      out+=$'\n'
    done
    printf '%s' "$out" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}'
  }
  declare -A take
  total_items=$(( ${#list_adds[@]} + ${#list_removes[@]} + ${#list_changes[@]} + ${#list_fixes[@]} ))
  taken=0
  for ((round = 1; round <= total_items; round++)); do
    progressed=false
    for key in adds removes changes fixes; do
      local -n arr="list_$key" 2>/dev/null || declare -n arr="list_$key"
      [ "${take[$key]:-0}" -lt "${#arr[@]}" ] || continue
      take[$key]=$(( ${take[$key]:-0} + 1 ))
      if [ "$(render | wc -c)" -gt "$limit" ]; then
        take[$key]=$(( ${take[$key]} - 1 ))
        break 2
      fi
      taken=$((taken + 1)); progressed=true
    done
    $progressed || break
  done
  [ "$total_items" -gt 0 ] || { echo "no item is for foss, so the fastlane notes would be empty" >&2; exit 1; }
  [ "$taken" -gt 0 ] || { echo "no item fits in $limit bytes" >&2; exit 1; }
  fastlane=$(render)
  size=$(printf '%s\n' "$fastlane" | wc -c)
  [ "$taken" -eq "$total_items" ] || echo "warning: fastlane notes hold $taken of $total_items items ($limit bytes)" >&2
fi

echo "versionName $current -> $new"
echo "versionCode $code -> $new_code"

if $beta; then
  last=$(git tag --list 'v*' --sort=-v:refname | grep -v -- '-' | head -n1 || true)
  echo
  echo "Changes since ${last:-the start}:"
  git log --format='- %s' ${last:+"$last"..HEAD}
else
  echo "fastlane: $fastlane_dir/$((new_code * 10)).txt ($size bytes, foss items)"
  for p in "${platforms[@]}"; do
    echo "app notes: app/src/$p/res/raw/changelog.json ($(changelog_json "$p" | jq '[.adds, .removes, .changes, .fixes] | add | length') items)"
  done
fi

if $dry; then
  echo "(dry run, nothing written)"
  exit 0
fi

sed -i -e "s/^versionName = .*/versionName = \"$new\"/" -e "s/^versionCode = .*/versionCode = \"$new_code\"/" "$toml"
if ! $beta; then
  mkdir -p "$fastlane_dir"
  printf '%s\n' "$fastlane" > "$fastlane_dir/$((new_code * 10)).txt"
  for p in "${platforms[@]}"; do
    mkdir -p "app/src/$p/res/raw"
    changelog_json "$p" > "app/src/$p/res/raw/changelog.json"
  done
  # Only an input: the notes it held now live in the files above
  rm -f "$yml"
  echo "deleted $yml"
fi

echo
echo "Next:"
echo "  commit message: bump version to $new"
echo "  git tag v$new"
$beta || echo "  new files to add: $fastlane_dir/$((new_code * 10)).txt (and any new app/src/*/res/raw/changelog.json)"
