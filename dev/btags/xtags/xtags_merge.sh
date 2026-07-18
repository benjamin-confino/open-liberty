#!/usr/bin/env bash
# xtags_merge.sh — Merge per-component xtags .calls fragments into letter-split TSVs.
#
# ⚠️  CAUTION: this script has two carefully co-designed paths (full rebuild
#    and incremental splice).  The KNOWN_NAMES filter, the known_names.tsv
#    pre-build cache, the incremental sort -u on small .new files, and the
#    O(N+M) merge-splice awk all depend on specific sort-order invariants
#    maintained upstream by gen_xtags_comp.sh.  Altering sort steps, the
#    strip-pattern logic, or the awk splice algorithm can produce silently
#    incorrect output.
#
# Usage (cold / full rebuild):
#   xtags_merge.sh <calls_dir> <output_dir> <ftags_dir>
#
# Usage (incremental — splice only changed components):
#   xtags_merge.sh <calls_dir> <output_dir> <ftags_dir> <comp1> [comp2 ...]
#
#   <compN> are component path prefixes as they appear in col 3 (file:line)
#   of xtags rows, e.g. "sv", "src".  The flat <letter>.tsv files are updated
#   by a streaming O(N+M) splice — strip old lines for changed components and
#   merge-insert the new lines from the freshly-written .calls files.
#
# Only call sites where the callee name exists in the ftags index are kept.
#
# Output layout:
#   <output_dir>/<letter>.tsv   — callee first-letter split, cross-component
#
# File format (3 columns, TAB-separated):
#   callee_name TAB caller_qualified_name TAB file:line

set -euo pipefail

if [ $# -lt 3 ]; then
    echo "Usage: xtags_merge.sh <calls_dir> <output_dir> <ftags_dir> [changed_comp...]" >&2
    exit 1
fi

CALLS_DIR="$1"
OUT_DIR="$2"
FTAGS_DIR="$3"
shift 3
# Remaining args are changed component names (may be empty).

mkdir -p "$OUT_DIR"

# ---------------------------------------------------------------------------
# Build the set of known callee names from ftags flat TSVs.
# Used in both full and incremental paths to filter out external calls.
# ---------------------------------------------------------------------------

# Use a pre-built known_names.tsv if available (written by make when ftags
# changes) — avoids re-reading all 437k ftags lines on every xtags run.
KNOWN_NAMES_PREBUILT="$FTAGS_DIR/known_names.tsv"
KNOWN_NAMES=$(mktemp)
trap 'rm -f "$KNOWN_NAMES"' EXIT

if [ -f "$KNOWN_NAMES_PREBUILT" ]; then
    cp "$KNOWN_NAMES_PREBUILT" "$KNOWN_NAMES"
else
    find "$FTAGS_DIR" -maxdepth 1 -type f -name '*.tsv' ! -name 'hints.tsv' \
        | xargs cat \
        | awk -F'\t' '{print $1}' \
        | sort -u > "$KNOWN_NAMES"
fi

if [ $# -eq 0 ]; then
    # ---------------------------------------------------------------------------
    # Full rebuild: process all .calls files.
    # ---------------------------------------------------------------------------
    ALL_CALLS=$(find "$CALLS_DIR" -type f -name '*.calls' -size +0c | sort)

    if [ -z "$ALL_CALLS" ]; then
        echo "[xtags] No non-empty .calls files found." >&2
        exit 0
    fi

    awk -F'\t' -v outdir="$OUT_DIR" '
    FNR == NR { known[$1] = 1; next }
    NF < 3          { next }
    !($1 in known)  { next }
    {
        c = substr($1, 1, 1)
        letter = (c ~ /[A-Za-z]/) ? tolower(c) : "_"
        print $0 >> (outdir "/" letter ".tsv")
    }
    ' "$KNOWN_NAMES" <(echo "$ALL_CALLS" | xargs cat)

    # Deduplicate and sort.  The fan-out awk interleaves rows from multiple
    # .calls files, so a full sort (not merge-sort) is required here.
    for f in "$OUT_DIR"/*.tsv; do
        [ -f "$f" ] && sort -u -o "$f" "$f"
    done
else
    # ---------------------------------------------------------------------------
    # Incremental splice.
    #
    # For each changed component, collect its fresh .calls files, filter to
    # known callees, and splice the results into each affected letter file.
    #
    # Component identification in existing flat rows: col 3 is "file:line";
    # the file path prefix (first path segment before "/") is the component.
    # ---------------------------------------------------------------------------

    # Build strip-prefix list.
    strip_pat=""
    for comp in "$@"; do
        strip_pat="${strip_pat}|${comp}/"
    done
    strip_pat="${strip_pat#|}"

    # Collect .calls files for changed components.
    # .calls filenames are <comp>_<path-encoded-filename>.calls
    # e.g. sv_com_ibm_svc_sv_CsmPartition.java.calls
    NEW_CALLS=""
    for comp in "$@"; do
        for f in "$CALLS_DIR"/${comp}_*.calls "$CALLS_DIR"/java_${comp}_*.calls \
                 "$CALLS_DIR"/c_src_${comp}_*.calls; do
            [ -f "$f" ] && [ -s "$f" ] && NEW_CALLS="${NEW_CALLS}${f}"$'\n'
        done
    done
    NEW_CALLS=$(printf '%s' "$NEW_CALLS" | grep -v '^$' | sort || true)

    if [ -z "$NEW_CALLS" ]; then
        # No new .calls produced (e.g. pure deletion) — just strip old lines.
        :
    fi

    # Filter new .calls to known callees and split by letter into temp files.
    # Use a temp dir for letter .new files; use a temp file as a set of letters
    # seen (bash 3 compatible — no associative arrays).
    TMPDIR_WORK=$(mktemp -d)
    LETTERS_FILE=$(mktemp)
    trap 'rm -rf "$TMPDIR_WORK"; rm -f "$KNOWN_NAMES" "$LETTERS_FILE"' EXIT

    if [ -n "$NEW_CALLS" ]; then
        awk -F'\t' -v tmpdir="$TMPDIR_WORK" '
        FNR == NR { known[$1] = 1; next }
        NF < 3         { next }
        !($1 in known) { next }
        {
            c = substr($1, 1, 1)
            letter = (c ~ /[A-Za-z]/) ? tolower(c) : "_"
            print $0 >> (tmpdir "/" letter ".new")
        }
        ' "$KNOWN_NAMES" <(echo "$NEW_CALLS" | xargs cat)

        # Sort each temp new-letter file; record letters seen.
        for f in "$TMPDIR_WORK"/*.new; do
            [ -f "$f" ] || continue
            sort -u -o "$f" "$f"
            l="${f##*/}"; echo "${l%.new}" >> "$LETTERS_FILE"
        done
    fi

    # Add existing flat letters to the set (deletion case).
    for f in "$OUT_DIR"/[a-z_].tsv; do
        [ -f "$f" ] && { l="${f##*/}"; echo "${l%.tsv}"; } >> "$LETTERS_FILE"
    done
    ALL_LETTERS=$(sort -u "$LETTERS_FILE")
    rm -f "$LETTERS_FILE"

    for letter in $ALL_LETTERS; do
        flat="$OUT_DIR/${letter}.tsv"
        new_file="$TMPDIR_WORK/${letter}.new"
        [ -f "$new_file" ] || new_file=""

        if [ ! -f "$flat" ]; then
            [ -n "$new_file" ] && cp "$new_file" "$flat"
            continue
        fi

        # O(N+M) awk merge-splice over the sorted flat file — run in background
        # so all letter splices proceed in parallel (xtags flats are large).
        {
        awk -v strip="$strip_pat" -v new_file="$new_file" '
        BEGIN {
            FS = "\t"
            new_count = 0
            if (new_file != "") {
                while ((getline line < new_file) > 0)
                    new_lines[++new_count] = line
                close(new_file)
            }
            new_i = 1
            n_strip = split(strip, sa, "|")
        }
        {
            # col 3 is "file:line" — extract component prefix.
            colon = index($3, ":")
            fpath = (colon > 0) ? substr($3, 1, colon-1) : $3
            slash = index(fpath, "/")
            comp_prefix = (slash > 0) ? substr(fpath, 1, slash) : fpath "/"
            skip = 0
            for (si = 1; si <= n_strip; si++) {
                if (comp_prefix == sa[si]) { skip = 1; break }
            }
            while (new_i <= new_count && new_lines[new_i] <= $0)
                print new_lines[new_i++]
            if (!skip) print $0
        }
        END { while (new_i <= new_count) print new_lines[new_i++] }
        ' "$flat" > "$flat.tmp" && mv "$flat.tmp" "$flat"
        } &
    done
    wait   # all letter splices finish before counting below
fi

total=$(cat "$OUT_DIR"/*.tsv 2>/dev/null | wc -l | tr -d ' ')
printf "[xtags] Mapped Bob-friendly call-site lookup index  (%'d call graph links)\n" "$total"
