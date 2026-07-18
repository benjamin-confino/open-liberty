#!/usr/bin/env bash
# ftags.sh — Generate Function Tags for a component directory or single file.
#
# ⚠️  CAUTION: the awk column extraction, ctags flag choices, and .fns output
#    format are tightly coupled to ftags_merge.sh and gen_ltags.sh consumers.
#    Changing column layout, ctags --fields or --extras flags, or the
#    de-duplication logic can break the entire downstream index chain.
#
# Outputs tab-separated records to stdout:
#   simple_name TAB qualified_name TAB rel_path TAB start TAB end TAB sig
#
# Usage (component directory — Java or C):
#   ftags.sh <comp_dir_or_c_subdir> <lodestone_root> <ctags_binary> [<output_fns>]
#
#   When <output_fns> is given, two build modes apply automatically:
#     Cold (output absent)   — full recursive ctags scan of the directory.
#     Warm (output present)  — find files newer than <output_fns>.
#                              If none: exit 0 silently (up to date).
#                              If some: full rescan of the component
#                                       (ctags output is whole-component, not delta).
#
# Usage (single file — legacy):
#   ftags.sh <source_file> <lodestone_root> <ctags_binary>
#
# Relies on Universal Ctags (>= 5.9) end: field for line ranges.

set -euo pipefail

if [ $# -lt 3 ]; then
    echo "Usage: ftags.sh <source_file_or_dir> <lodestone_root> <ctags_binary> [<output_fns>]" >&2
    exit 1
fi

SOURCE="$1"
LODESTONE_ROOT="${2%/}"
CTAGS_BIN="$3"
OUTPUT_FNS="${4:-}"    # optional — enables cold/warm mode when given

ABS_ROOT="$(cd "$LODESTONE_ROOT" && pwd)"

# ---------------------------------------------------------------------------
# Directory mode — recurse over a whole component subtree
# ---------------------------------------------------------------------------

if [ -d "$SOURCE" ]; then
    case "$SOURCE" in
        */src/user/*) DIR_LANG="C" ;;
        *)            DIR_LANG="Java" ;;
    esac

    # ------------------------------------------------------------------
    # Incremental check: if output exists and nothing is newer, skip.
    # ------------------------------------------------------------------
    if [ -n "$OUTPUT_FNS" ] && [ -f "$OUTPUT_FNS" ]; then
        case "$DIR_LANG" in
            Java)
                newer=$({ find "$SOURCE" -name "*.java" -newer "$OUTPUT_FNS" 2>/dev/null; } \
                    | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
                    | head -1 || true)
                ;;
            C)
                newer=$(find "$SOURCE" \( -name "*.c" -o -name "*.h" \) \
                    -newer "$OUTPUT_FNS" 2>/dev/null | head -1 || true)
                ;;
        esac
        if [ -z "$newer" ]; then
            exit 0    # warm — nothing changed, caller keeps existing .fns
        fi
    fi

    # ------------------------------------------------------------------
    # Full component rescan (cold or warm-with-changes).
    # ------------------------------------------------------------------
    if [ "$DIR_LANG" = "Java" ]; then
        LANG_ARG="--language-force=Java"
        KINDS_ARG="--kinds-Java=cm"
    else
        LANG_ARG="--language-force=C"
        KINDS_ARG="--kinds-C=f"
    fi

    "$CTAGS_BIN" \
        $LANG_ARG \
        $KINDS_ARG \
        --fields=+neZsS \
        --output-format=e-ctags \
        --sort=no \
        -R \
        -f - \
        "$SOURCE" 2>/dev/null \
    | awk -v lang="$DIR_LANG" -v root="$ABS_ROOT/" '
    BEGIN { FS = "\t" }
    /^!_/ { next }
    NF < 4 { next }
    {
        name = $1; file = $2; kind = $4
        rel = file
        if (substr(rel, 1, length(root)) == root)
            rel = substr(rel, length(root) + 1)
        lineno = 0; endno = 0; scope = ""; sig = ""
        for (i = 5; i <= NF; i++) {
            col = $i; eq = index(col, ":")
            if (eq == 0) continue
            key = substr(col, 1, eq - 1); val = substr(col, eq + 1)
            if (key == "line")      { lineno = int(val) }
            if (key == "end")       { endno  = int(val) }
            if (key == "signature") { sig    = val }
            if (key == "scope") {
                colon = index(val, ":")
                scope = (colon > 0) ? substr(val, colon + 1) : val
            }
        }
        if (lineno == 0) next
        if (endno == 0)  endno = lineno
        if (sig   == "") sig   = "-"
        if (lang == "Java") {
            if (kind != "m") next
            qualified = (scope != "") ? scope "." name : name
        } else {
            if (kind != "f") next
            qualified = name
        }
        print name "\t" qualified "\t" rel "\t" lineno "\t" endno "\t" sig
    }'
    exit
fi

# ---------------------------------------------------------------------------
# Single-file mode (legacy — used for incremental per-file rebuilds)
# ---------------------------------------------------------------------------

if [ ! -f "$SOURCE" ]; then
    echo "File not found: $SOURCE" >&2
    exit 1
fi

case "${SOURCE##*.}" in
    java) LANGUAGE="Java" ;;
    c)    LANGUAGE="C"    ;;
    *)    exit 0          ;;
esac

ABS_SOURCE="$(cd "$(dirname "$SOURCE")" && pwd)/$(basename "$SOURCE")"
if [[ "$ABS_SOURCE" == "$ABS_ROOT/"* ]]; then
    REL_PATH="${ABS_SOURCE#"$ABS_ROOT/"}"
else
    REL_PATH="$SOURCE"
fi

KINDS=$([ "$LANGUAGE" = "Java" ] && echo "cm" || echo "f")

"$CTAGS_BIN" \
    "--language-force=${LANGUAGE}" \
    "--kinds-${LANGUAGE}=${KINDS}" \
    --fields=+neZsS \
    --output-format=e-ctags \
    --sort=no \
    -f - \
    "$SOURCE" 2>/dev/null \
| awk -v lang="$LANGUAGE" -v rel="$REL_PATH" '
BEGIN { FS = "\t" }
/^!_/ { next }
NF < 4 { next }
{
    name = $1; kind = $4
    lineno = 0; endno = 0; scope = ""; sig = ""
    for (i = 5; i <= NF; i++) {
        col = $i; eq = index(col, ":")
        if (eq == 0) continue
        key = substr(col, 1, eq - 1); val = substr(col, eq + 1)
        if (key == "line")      { lineno = int(val) }
        if (key == "end")       { endno  = int(val) }
        if (key == "signature") { sig    = val }
        if (key == "scope") {
            colon = index(val, ":")
            scope = (colon > 0) ? substr(val, colon + 1) : val
        }
    }
    if (lineno == 0) next
    if (endno == 0)  endno = lineno
    if (sig   == "") sig   = "-"
    if (lang == "Java") {
        if (kind != "m") next
        qualified = (scope != "") ? scope "." name : name
    } else {
        qualified = name
    }
    print name "\t" qualified "\t" rel "\t" lineno "\t" endno "\t" sig
}'
