#!/usr/bin/env bash
# gen_stags_all.sh — Assemble the flat cross-component stags index.
#
# ⚠️  CAUTION: the full-rebuild path uses sort --merge (O(N)) which requires
#    all component part files to already be sorted (guaranteed by gen_stags.sh).
#    The incremental splice awk assumes the flat files are sorted.  Altering
#    sort steps, the strip-pattern logic, or the merge-splice algorithm can
#    produce silently incorrect output with no error signal.
#
# Usage (cold / full rebuild):
#   gen_stags_all.sh <stags_output_dir>
#
# Usage (incremental — splice only changed components):
#   gen_stags_all.sh <stags_output_dir> <comp1> [comp2 ...]
#
#   <compN> are component name suffixes as they appear in parts/stags_<comp>/
#   e.g. "sv", "src".  The flat <letter>.tsv files are updated by a streaming
#   O(N+M) splice — strip old lines for changed components and merge-insert
#   new lines from the freshly-rebuilt parts/stags_<comp>/ directory.
#
# Prerequisites: bash >= 3.2, sort, awk

set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: gen_stags_all.sh <stags_output_dir> [changed_comp...]" >&2
    exit 1
fi

OUT_DIR="$1"
[[ "$OUT_DIR" != /* ]] && OUT_DIR="$(pwd)/$OUT_DIR"
shift
# Remaining args are changed component names (may be empty).

PARTS_DIR="$OUT_DIR/parts"

if [ $# -eq 0 ]; then
    # ---------------------------------------------------------------------------
    # Full rebuild: merge all component parts into each letter's flat file.
    # Parts are individually sorted (by gen_stags.sh); use sort --merge (O(N)).
    # No cross-component duplicate rows exist (file paths are unique per component).
    # ---------------------------------------------------------------------------
    # Collect the set of letters that exist across all parts.
    LETTERS_SET=""
    for tsv in "$PARTS_DIR"/stags_*/[a-z_].tsv; do
        [ -f "$tsv" ] || continue
        letter="${tsv##*/}"; letter="${letter%.tsv}"
        LETTERS_SET="$LETTERS_SET $letter"
    done
    LETTERS_SET=$(printf '%s\n' $LETTERS_SET | sort -u)
    for letter in $LETTERS_SET; do
        parts=""
        for tsv in "$PARTS_DIR"/stags_*/"${letter}.tsv"; do
            [ -f "$tsv" ] && parts="$parts $tsv"
        done
        # shellcheck disable=SC2086
        [ -n "$parts" ] && sort -m $parts > "$OUT_DIR/${letter}.tsv"
    done
else
    # ---------------------------------------------------------------------------
    # Incremental splice: for each letter file, stream the existing flat file
    # once, stripping old lines for changed components and merge-inserting the
    # new lines from the freshly-rebuilt parts directories.
    #
    # Component identification: col 2 of stags rows is "file:line"; the file
    # path prefix (before the first "/") is the component name.
    # ---------------------------------------------------------------------------

    # Build pipe-separated strip pattern: "sv/" etc.
    strip_pat=""
    for comp in "$@"; do
        strip_pat="${strip_pat}|${comp}/"
    done
    strip_pat="${strip_pat#|}"

    # Determine which letters to splice (union of letters in new parts + existing flats).
    # Use a temp file as a set (bash 3 compatible — no associative arrays).
    LETTERS_FILE=$(mktemp)
    trap 'rm -f "$LETTERS_FILE"' EXIT
    for comp in "$@"; do
        parts_comp_dir="$PARTS_DIR/stags_${comp}"
        [ -d "$parts_comp_dir" ] || continue
        for f in "$parts_comp_dir"/[a-z_].tsv; do
            [ -f "$f" ] && { l="${f##*/}"; echo "${l%.tsv}"; } >> "$LETTERS_FILE"
        done
    done
    for f in "$OUT_DIR"/[a-z_].tsv; do
        [ -f "$f" ] && { l="${f##*/}"; echo "${l%.tsv}"; } >> "$LETTERS_FILE"
    done
    # Deduplicate letter list.
    LETTERS=$(sort -u "$LETTERS_FILE")
    rm -f "$LETTERS_FILE"
    trap - EXIT

    for letter in $LETTERS; do
        flat="$OUT_DIR/${letter}.tsv"

        # Collect new part files for this letter across all changed components.
        new_parts=""
        for comp in "$@"; do
            p="$PARTS_DIR/stags_${comp}/${letter}.tsv"
            [ -f "$p" ] && new_parts="$new_parts $p"
        done

        if [ ! -f "$flat" ]; then
            if [ -n "$new_parts" ]; then
                # Parts are sorted — just merge them directly.
                # shellcheck disable=SC2086
                sort -m $new_parts > "$flat"
            fi
            continue
        fi

        # O(N+M) awk merge-splice — run in background for parallel letter splices.
        # shellcheck disable=SC2086
        {
        awk -v strip="$strip_pat" '
        BEGIN {
            FS = "\t"
            # Load new lines from part files (all but the last ARGV = flat file).
            new_count = 0
            for (i = 1; i < ARGC-1; i++) {
                while ((getline line < ARGV[i]) > 0)
                    new_lines[++new_count] = line
                close(ARGV[i])
                delete ARGV[i]
            }
            # Sort (insertion sort — M is tiny, hundreds of lines).
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
            # Identify file-path prefix: col 2 is "path:line", extract "path".
            colon = index($2, ":")
            fpath = (colon > 0) ? substr($2, 1, colon-1) : $2
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
        ' $new_parts "$flat" > "$flat.tmp" && mv "$flat.tmp" "$flat"
        } &
    done
    wait   # all letter splices finish before hints rebuild
fi

# Count from flat TSVs (single wc -l pass — much faster than per-part-file loop).
total=$(cat "$OUT_DIR"/[a-z_].tsv 2>/dev/null | wc -l | tr -d ' ')
printf "[stags] Created Bob-specific type/struct/enum lookup index  (%'d entries)\n" "$total"
