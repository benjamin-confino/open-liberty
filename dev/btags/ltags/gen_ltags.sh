#!/usr/bin/env bash
# gen_ltags.sh — Split a component .fns file into per-source .range files.
#
# ⚠️  CAUTION: the .range file format and sort order (by start line, numeric)
#    are required by gen_xtags_comp.sh's binary-search range lookup.  The awk
#    relies on ctags output arriving grouped by file and sorted by start line
#    within each file — an invariant maintained by ctags_part.sh --sort=yes.
#    Changing the column layout or sort order will silently break xtags.
#
# Reads one ftags .fns file (covering all source files in one component) and
# writes one .range file per source file into <ltags_dir>/.
#
# One invocation per component (not per source file) — a single streaming awk
# pass splits all sources in the component in O(n).
#
# The .fns file is produced by ftags.sh via ctags, which emits methods in
# source-file order then line-number order within each file.  Entries for the
# same file are always contiguous and already sorted by start line, so no
# sort subprocess is needed — each output .range file is written in one
# sequential pass with no buffering.
#
# Usage:
#   gen_ltags.sh <fns_file> <ltags_dir>
#
# Input format (6 columns, from ftags.sh):
#   simple_name TAB qualified_name TAB rel_path TAB start TAB end TAB signature
#
# Output: for each unique rel_path seen in col3, writes
#   <ltags_dir>/<stem>.range  where stem = rel_path with / replaced by _
# each file contains 4-column TSV sorted by start line (numeric):
#   rel_path TAB start TAB end TAB qualified_name

set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Usage: gen_ltags.sh <fns_file> <ltags_dir>" >&2
    exit 1
fi

FNS_FILE="$1"
LTAGS_DIR="$2"

if [ ! -f "$FNS_FILE" ]; then
    echo "File not found: $FNS_FILE" >&2
    exit 1
fi

if [ ! -d "$LTAGS_DIR" ]; then
    echo "Directory not found: $LTAGS_DIR" >&2
    exit 1
fi

# Streaming awk splitter.
# Entries arrive grouped by rel_path (col3) and sorted by start line (col4)
# within each group — the order ftags.sh / ctags naturally produces.
# We open a new output file whenever the rel_path changes and close the
# previous one: no per-file buffering, no sort subprocess.
awk -v ltags_dir="$LTAGS_DIR" '
BEGIN { FS = "\t"; OFS = "\t"; cur_path = ""; cur_file = "" }

NF >= 5 {
    relpath = $3
    if (relpath != cur_path) {
        # Close previous file (if any).
        if (cur_file != "") close(cur_file)

        # Derive output filename: replace / with _ and append .range
        stem = relpath
        gsub(/\//, "_", stem)
        cur_file  = ltags_dir "/" stem ".range"
        cur_path  = relpath
    }
    # col2 = qualified_name, col4 = start, col5 = end
    print relpath, $4, $5, $2 > cur_file
}

END {
    if (cur_file != "") close(cur_file)
}
' "$FNS_FILE"
