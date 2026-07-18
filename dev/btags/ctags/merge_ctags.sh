#!/usr/bin/env bash
# merge_ctags.sh — Merge per-component partial ctags files into a single
#                  sorted tags file.
#
# ⚠️  CAUTION: uses sort --merge (O(N) merge-sort) which requires each input
#    to be individually sorted.  This invariant is maintained by ctags_part.sh
#    using --sort=yes.  Breaking that invariant produces a silently wrong
#    merged output.
#
# Usage:
#   merge_ctags.sh <parts_dir> <output_file>
#
# Each *.tags file in <parts_dir> is a valid ctags file produced with
# --sort=yes.  This script:
#   1. Emits a single canonical pseudo-tag header.
#   2. Strips the per-file pseudo-tag headers from every partial file.
#   3. Merges all real tag lines with a single sort --merge (O(N) because
#      each input is already sorted).
#   4. Writes the result to <output_file>.
#
# Prerequisites: bash, sort (GNU or BSD — both support --merge / -m).

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: merge_ctags.sh <parts_dir> <output_file>" >&2
    exit 1
fi

PARTS_DIR="$1"
OUTPUT="$2"
[[ "$OUTPUT" != /* ]] && OUTPUT="$(pwd)/$OUTPUT"

# Collect partial files; if none are present, write a header-only tags file.
# Use a glob rather than mapfile so this works on bash 3 (macOS system bash).
PARTS=()
for f in "$PARTS_DIR"/*.tags; do
    [ -f "$f" ] && PARTS+=("$f")
done

TMPDIR_LOCAL=$(mktemp -d)
trap 'rm -rf "$TMPDIR_LOCAL"' EXIT

# Strip pseudo-tag lines (^!_) from each partial into a stripped temp file,
# keeping the content sorted (ctags already emits sorted output per-file).
STRIPPED=()
if [[ ${#PARTS[@]} -gt 0 ]]; then
    for part in "${PARTS[@]}"; do
        base=$(basename "$part")
        stripped="$TMPDIR_LOCAL/${base%.tags}.stripped"
        grep -v '^!_' "$part" > "$stripped" || true   # empty component is fine
        STRIPPED+=("$stripped")
    done
fi

# Canonical pseudo-tag header — matches what ctags would write for the merged file.
HEADER="$TMPDIR_LOCAL/header"
cat > "$HEADER" <<'EOF'
!_TAG_FILE_FORMAT	2	/extended format; --format=1 will not append ;" to lines/
!_TAG_FILE_SORTED	1	/0=unsorted, 1=sorted, 2=foldcase/
!_TAG_OUTPUT_EXCMD	mixed	/number, pattern, mixed, or combineV2/
!_TAG_OUTPUT_FILESEP	slash	/uses slash as filename separator/
!_TAG_OUTPUT_MODE	u-ctags	/u-ctags or e-ctags/
!_TAG_PATTERN_LENGTH_LIMIT	96	/0 for no limit/
!_TAG_PROC_CWD	./	//
!_TAG_PROGRAM_AUTHOR	Universal Ctags Team	//
!_TAG_PROGRAM_NAME	Universal Ctags	/Derived from Exuberant Ctags/
!_TAG_PROGRAM_URL	https://ctags.io/	/official site/
!_TAG_PROGRAM_VERSION	0.0.0	/merged/
EOF

if [[ ${#STRIPPED[@]} -eq 0 ]]; then
    cat "$HEADER" > "$OUTPUT"
else
    # sort --merge (-m) requires all inputs to be individually sorted,
    # which they are (ctags --sort=yes per partition).
    # The header file starts with '!' which sorts before all printable ASCII,
    # so it lands at the top naturally.
    sort --merge "$HEADER" "${STRIPPED[@]}" > "$OUTPUT"
fi

LINES=$(wc -l < "$OUTPUT" | tr -d ' ')
printf "[ctags] Merged %d parallel tag files → %s  (index count: %'d)\n" "${#PARTS[@]}" "$OUTPUT" "$LINES"
