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

# Reads the exported Room schema (database/schemas/<database>/<version>.json), by default the
# highest version present, so the schema file never needs reading whole.
#
#   schema.sh                       tables and views, with their column counts
#   schema.sh <table|view>...       columns, primary key, indices and foreign keys, or a view's SQL
#   schema.sh -diff [from] [to]     what changed between two versions (default: the last two)
#   schema.sh -v <version> ...      read that version instead of the highest

root="$(cd "$(dirname "$0")/../.." && pwd)"

read -r -d '' py <<'PY'
import glob, json, os, re, sys

root, args = sys.argv[1], sys.argv[2:]

files = {}
for path in glob.glob(os.path.join(root, "database/schemas/*/*.json")):
    name = os.path.splitext(os.path.basename(path))[0]
    if name.isdigit():
        files[int(name)] = path
if not files:
    sys.exit("schema: no exported schema under database/schemas")
versions = sorted(files)

def load(version):
    if version not in files:
        sys.exit(f"schema: no version {version} (have {', '.join(map(str, versions))})")
    db = json.load(open(files[version]))["database"]
    tables = {e["tableName"]: e for e in db.get("entities", [])}
    views = {v["viewName"]: v for v in db.get("views", [])}
    return tables, views

def columns(entity):
    pk = entity.get("primaryKey", {}).get("columnNames", [])
    out = {}
    for f in entity["fields"]:
        text = f["affinity"] + (" NOT NULL" if f.get("notNull") else "")
        if "defaultValue" in f:
            text += f" DEFAULT {f['defaultValue']}"
        if f["columnName"] in pk:
            text += " PK"
        out[f["columnName"]] = text
    return out

def indices(entity):
    return {i["name"]: ("UNIQUE " if i.get("unique") else "") + "(" + ", ".join(i["columnNames"]) + ")"
            for i in entity.get("indices", [])}

def foreign(entity):
    return {"(" + ", ".join(k["columns"]) + ")": f"→ {k['table']}(" + ", ".join(k["referencedColumns"]) + f") ON DELETE {k.get('onDelete', 'NO ACTION')}"
            for k in entity.get("foreignKeys", [])}

def sql(view):
    return re.sub(r"`\$\{VIEW_NAME\}`", view["viewName"], view["createSql"])

version = versions[-1]
if args[:1] == ["-v"]:
    if len(args) < 2 or not args[1].isdigit():
        sys.exit("schema: -v needs a version number")
    version, args = int(args[1]), args[2:]

if args[:1] == ["-diff"]:
    picked = [int(a) for a in args[1:3]]
    if not picked and len(versions) < 2:
        sys.exit(f"schema: only version {versions[0]} is exported, nothing to compare")
    if len(picked) == 2:
        old, new = picked
    elif len(picked) == 1:
        old, new = picked[0], versions[-1]
    else:
        old, new = versions[-2:]
    (ot, ov), (nt, nv) = load(old), load(new)
    print(f"# version {old} → {new}")
    for name in sorted(set(ot) | set(nt)):
        if name not in nt:
            print(f"- table {name}"); continue
        if name not in ot:
            print(f"+ table {name}")
            for c, t in columns(nt[name]).items():
                print(f"    + {c} {t}")
            continue
        changes = []
        for label, get in (("", columns), ("index ", indices), ("foreign key ", foreign)):
            a, b = get(ot[name]), get(nt[name])
            for k in sorted(set(a) | set(b)):
                if k not in b: changes.append(f"    - {label}{k} {a[k]}")
                elif k not in a: changes.append(f"    + {label}{k} {b[k]}")
                elif a[k] != b[k]: changes.append(f"    ~ {label}{k} {a[k]} → {b[k]}")
        if changes:
            print(f"~ table {name}"); print("\n".join(changes))
    for name in sorted(set(ov) | set(nv)):
        if name not in nv: print(f"- view {name}")
        elif name not in ov: print(f"+ view {name}")
        elif sql(ov[name]) != sql(nv[name]): print(f"~ view {name} (SQL changed)")
    sys.exit()

tables, views = load(version)
print(f"# version {version} ({os.path.relpath(files[version], root)})")
if not args:
    for name, e in sorted(tables.items()):
        print(f"table {name} ({len(e['fields'])} columns)")
    for name in sorted(views):
        print(f"view  {name}")
    sys.exit()

for name in args:
    if name in tables:
        e = tables[name]
        print(f"table {name}")
        for c, t in columns(e).items():
            print(f"  {c} {t}")
        for k, v in indices(e).items():
            print(f"  index {k} {v}")
        for k, v in foreign(e).items():
            print(f"  foreign key {k} {v}")
    elif name in views:
        print(f"view {name}\n{sql(views[name])}")
    else:
        print(f"{name}: no such table or view in version {version}")
PY
python3 -I -c "$py" "$root" "$@"
