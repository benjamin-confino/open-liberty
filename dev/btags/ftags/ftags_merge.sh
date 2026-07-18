#!/usr/bin/env bash
# ftags_merge.sh - Merge per-component ftags .fns fragments into a two-level
#                  directory structure: one folder per component, one TSV per
#                  first letter of the key name.
#
# ⚠️  CAUTION: this script contains three carefully co-designed paths (per-
#    component parts, full-rebuild flat merge, incremental splice) that all
#    depend on the same sort-order invariants.  Component part files are kept
#    sorted so the flat assembly can use sort --merge (O(N)).  The incremental
#    awk splice assumes the flat files are sorted.  Breaking any sort invariant
#    — e.g. removing a sort -o, changing a sort key, or reordering steps —
#    silently corrupts the index without triggering an error.
#
# Usage (cold / full rebuild — all components):
#   ftags_merge.sh <fns_dir> <output_dir>
#
# Usage (incremental — splice only changed components into existing flat files):
#   ftags_merge.sh <fns_dir> <output_dir> <comp1> [comp2 ...]
#
#   <compN> are component path prefixes as they appear in the file column
#   of the .fns data, e.g. "sv", "src/user/hl".
#   The per-component parts/ftags_<comp>/ directory is rebuilt from scratch;
#   the flat <letter>.tsv files are updated by a streaming splice that strips
#   old lines for the changed components and merge-inserts the new lines.
#
# Output layout:
#   <output_dir>/parts/ftags_<comp>/<letter>.tsv  — per-component
#   <output_dir>/<letter>.tsv                     — flat cross-component fallback
#   <output_dir>/hints.tsv                        — simple name → component list
#
# File format (3 columns, TAB-separated):
#   key_name TAB file:start-end TAB signature

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: ftags_merge.sh <fns_dir> <output_dir> [changed_comp...]" >&2
    exit 1
fi

FNS_DIR="$1"
OUT_DIR="$2"
shift 2
# Remaining args are changed component names (may be empty).

# ---------------------------------------------------------------------------
# Collect .fns files to process.
# Full rebuild: all non-empty .fns files.
# Incremental: only .fns files for changed components.
# ---------------------------------------------------------------------------
if [ $# -eq 0 ]; then
    FNS_FILES=$(find "$FNS_DIR" -type f -name '*.fns' -size +0c | sort)
    INCREMENTAL=0
else
    FNS_FILES=""
    for comp in "$@"; do
        # comp is like "sv" or "hl" (Java basename) or "hl" (C subdir basename).
        for pattern in "$FNS_DIR/java_${comp}.fns" "$FNS_DIR/c_src_${comp}.fns" "$FNS_DIR/${comp}.fns"; do
            [ -f "$pattern" ] && [ -s "$pattern" ] && FNS_FILES="${FNS_FILES}${pattern}"$'\n'
        done
    done
    FNS_FILES=$(printf '%s' "$FNS_FILES" | grep -v '^$' | sort || true)
    INCREMENTAL=1
fi

if [ -z "$FNS_FILES" ]; then
    echo "[ftags] No non-empty .fns files found." >&2
    exit 0
fi

echo "[ftags] Building Bob-tuned function/method lookup index (dual-keyed, letter-bucketed, hints) ..."

# ---------------------------------------------------------------------------
# Step 1: rebuild per-component part directories for changed components.
#
# For each changed component, wipe its parts/ftags_<comp>/ directory and
# rewrite it from scratch from its .fns file.  This is fast (one component).
# ---------------------------------------------------------------------------
# Build/rebuild per-component parts for the given .fns files.
#
# Multiple C fns files (c_src_hl.fns, c_src_sv.fns, …) all map to the same
# parts dir (ftags_src/).  We must NOT wipe that dir for each file — collect
# all fns files that share a comp_dir_name and process them together.
#
# Strategy: first pass collects fns files by comp_dir_name into temp lists;
# second pass wipes each unique parts dir once, then appends all fns data.

_stem_to_comp_dir() {
    local stem="$1"
    if [[ "$stem" == java_* ]]; then
        echo "${stem#java_}"
    elif [[ "$stem" == c_src_* ]]; then
        echo "src"
    else
        echo "$stem"
    fi
}

_append_fns_to_parts() {
    local fns_file="$1"
    local parts_dir="$2"
    awk -v outdir="$parts_dir" '
    BEGIN { FS = "\t" }
    function write_row(key, loc, sig,    c, letter, f) {
        c = substr(key, 1, 1)
        letter = (c ~ /[A-Za-z]/) ? tolower(c) : "_"
        f = outdir "/" letter ".tsv"
        print key "\t" loc "\t" sig >> f
    }
    {
        qual = $2; loc = $3 ":" $4 "-" $5; sig = (NF >= 6) ? $6 : "-"
        write_row(qual, loc, sig)
        dot = index(qual, ".")
        if (dot > 0) {
            simple = qual
            while ((dot = index(simple, ".")) > 0) simple = substr(simple, dot+1)
            if (simple != qual) write_row(simple, loc, sig)
        }
    }
    ' "$fns_file"
}

# Group fns files by comp_dir_name using a temp dir of per-comp lists.
COMP_LIST_DIR=$(mktemp -d)
trap 'rm -rf "$COMP_LIST_DIR"' EXIT
while IFS= read -r fns; do
    [ -z "$fns" ] && continue
    stem="${fns##*/}"; stem="${stem%.fns}"
    comp_dir_name=$(_stem_to_comp_dir "$stem")
    echo "$fns" >> "$COMP_LIST_DIR/$comp_dir_name"
done <<< "$FNS_FILES"

# For each unique comp_dir, wipe its parts dir once then append all fns data.
for comp_list in "$COMP_LIST_DIR"/*; do
    [ -f "$comp_list" ] || continue
    comp_dir_name=$(basename "$comp_list")
    parts_dir="$OUT_DIR/parts/ftags_${comp_dir_name}"
    rm -rf "$parts_dir"
    mkdir -p "$parts_dir"
    while IFS= read -r fns; do
        [ -n "$fns" ] && _append_fns_to_parts "$fns" "$parts_dir"
    done < "$comp_list"
    # Sort each letter file so the flat assembly can use sort --merge (O(N)).
    # No duplicates: the awk guard (simple != qual) prevents them.
    for f in "$parts_dir"/*.tsv; do
        [ -f "$f" ] && sort -o "$f" "$f"
    done
done
rm -rf "$COMP_LIST_DIR"
trap - EXIT

# ---------------------------------------------------------------------------
# Step 2: update flat cross-component letter files.
#
# Full rebuild path: cat all component parts into each letter's flat file.
# Incremental path: for each flat letter file, do a streaming O(N+M) splice —
#   strip old lines whose file-path column (col 2) starts with a changed
#   component prefix, and merge-insert the new lines from the rebuilt parts.
# ---------------------------------------------------------------------------

if [ "$INCREMENTAL" -eq 0 ]; then
    # Full rebuild: assemble flat files from all parts directories.
    # Parts are individually sorted; use sort --merge (O(N)) per letter.
    # No cross-component duplicate rows exist (file paths are unique per component).
    mkdir -p "$OUT_DIR"
    # Collect the set of letters that exist across all parts.
    LETTERS_SET=""
    for tsv in "$OUT_DIR"/parts/ftags_*/[a-z_].tsv; do
        [ -f "$tsv" ] || continue
        letter="${tsv##*/}"; letter="${letter%.tsv}"
        LETTERS_SET="$LETTERS_SET $letter"
    done
    # Deduplicate letter list (tiny, just the alphabet).
    LETTERS_SET=$(printf '%s\n' $LETTERS_SET | sort -u)
    for letter in $LETTERS_SET; do
        # Collect all sorted part files for this letter across all components.
        parts=""
        for tsv in "$OUT_DIR"/parts/ftags_*/"${letter}.tsv"; do
            [ -f "$tsv" ] && parts="$parts $tsv"
        done
        # shellcheck disable=SC2086
        [ -n "$parts" ] && sort -m $parts > "$OUT_DIR/${letter}.tsv"
    done
else
    # Incremental splice for each letter file that the changed components touch.
    #
    # Build the pipe-separated strip pattern: "sv/|hl/|src/user/hl/" etc.
    strip_pat=""
    while IFS= read -r fns; do
        [ -z "$fns" ] && continue
        stem="${fns##*/}"; stem="${stem%.fns}"
        if [[ "$stem" == java_* ]]; then
            strip_pat="${strip_pat}|${stem#java_}/"
        elif [[ "$stem" == c_src_* ]]; then
            strip_pat="${strip_pat}|src/user/${stem#c_src_}/"
        fi
    done <<< "$FNS_FILES"
    strip_pat="${strip_pat#|}"

    # Collect the set of letters to splice using a temp file (bash 3 compatible).
    LETTERS_FILE=$(mktemp)
    trap 'rm -f "$LETTERS_FILE"' EXIT
    while IFS= read -r fns; do
        [ -z "$fns" ] && continue
        stem="${fns##*/}"; stem="${stem%.fns}"
        comp_dir_name=$( [[ "$stem" == java_* ]] && echo "${stem#java_}" || echo "src" )
        parts_dir="$OUT_DIR/parts/ftags_${comp_dir_name}"
        for f in "$parts_dir"/[a-z_].tsv; do
            [ -f "$f" ] && { l="${f##*/}"; echo "${l%.tsv}"; } >> "$LETTERS_FILE"
        done
    done <<< "$FNS_FILES"
    # Also include all existing flat letters (deletion case).
    for f in "$OUT_DIR"/[a-z_].tsv; do
        [ -f "$f" ] && { l="${f##*/}"; echo "${l%.tsv}"; } >> "$LETTERS_FILE"
    done
    LETTERS_TO_SPLICE=$(sort -u "$LETTERS_FILE")
    rm -f "$LETTERS_FILE"
    trap - EXIT

    for letter in $LETTERS_TO_SPLICE; do
        flat="$OUT_DIR/${letter}.tsv"
        new_parts=""
        while IFS= read -r fns; do
            [ -z "$fns" ] && continue
            stem="${fns##*/}"; stem="${stem%.fns}"
            comp_dir_name=$( [[ "$stem" == java_* ]] && echo "${stem#java_}" || echo "src" )
            p="$OUT_DIR/parts/ftags_${comp_dir_name}/${letter}.tsv"
            [ -f "$p" ] && new_parts="$new_parts $p"
        done <<< "$FNS_FILES"
        if [ ! -f "$flat" ]; then
            # No existing flat file — parts are sorted, just merge them in.
            # shellcheck disable=SC2086
            [ -n "$new_parts" ] && sort -m $new_parts > "$flat"
            continue
        fi

        # O(N+M) awk merge-splice — run in background for parallel letter splices.
        # shellcheck disable=SC2086
        {
        awk -v strip="$strip_pat" '
        BEGIN {
            FS = "\t"
            # Load all new lines from new-part files (all but last ARGV).
            new_count = 0
            for (i = 1; i < ARGC-1; i++) {
                while ((getline line < ARGV[i]) > 0)
                    new_lines[++new_count] = line
                close(ARGV[i])
                delete ARGV[i]
            }
            # Sort new_lines (insertion sort — M is tiny, hundreds of lines).
            for (i = 2; i <= new_count; i++) {
                key = new_lines[i]; j = i-1
                while (j >= 1 && new_lines[j] > key) {
                    new_lines[j+1] = new_lines[j]; j--
                }
                new_lines[j+1] = key
            }
            new_i = 1
            n_strip = split(strip, sa, "|")
        }
        {
            # Skip lines whose file path (col 2) starts with a stripped prefix.
            colon = index($2, ":")
            fpath = (colon > 0) ? substr($2, 1, colon-1) : $2
            skip = 0
            for (si = 1; si <= n_strip; si++) {
                if (index(fpath, sa[si]) == 1) { skip = 1; break }
            }
            while (new_i <= new_count && new_lines[new_i] <= $0)
                print new_lines[new_i++]
            if (!skip) print $0
        }
        END { while (new_i <= new_count) print new_lines[new_i++] }
        ' $new_parts "$flat" > "$flat.tmp" && mv "$flat.tmp" "$flat"
        } &
    done
    wait   # all letter splices finish before hints rebuild
fi

# ---------------------------------------------------------------------------
# Step 3: rebuild hints.tsv.
#
# Rebuild from scratch — it is small and fast (one pass over all flat TSVs).
# Only qualified-name rows (containing ".") contribute to hints.
# ---------------------------------------------------------------------------
rm -f "$OUT_DIR/hints.tsv"
awk -F'\t' '
{
    key = $1; loc = $2
    dot = index(key, ".")
    if (dot == 0) next   # not a qualified name
    # simple name = last dot-segment
    simple = key
    while ((d = index(simple, ".")) > 0) simple = substr(simple, d+1)
    if (simple == key) next
    # component = first path segment of loc (before first "/")
    slash = index(loc, "/")
    comp = (slash > 0) ? substr(loc, 1, slash-1) : loc
    hint_key = simple SUBSEP comp
    if (!(hint_key in seen)) { seen[hint_key] = 1; hints[simple] = (simple in hints) ? hints[simple] "," comp : comp }
}
END {
    for (s in hints) print s "\t" hints[s]
}
' "$OUT_DIR"/[a-z_].tsv 2>/dev/null \
  | sort > "$OUT_DIR/hints.tsv"

# ---------------------------------------------------------------------------
# Summary — count from flat TSVs (single pass, much faster than per-part loop)
# ---------------------------------------------------------------------------
total=$(cat "$OUT_DIR"/[a-z_].tsv 2>/dev/null | wc -l | tr -d ' ')
printf "[ftags] Wrote Bob-tuned function/method lookup index from %d components  (%'d file:n..m ranges mapped)\n" \
    "$(ls -d "$OUT_DIR"/parts/ftags_*/ 2>/dev/null | wc -l | tr -d ' ')" "$total"
