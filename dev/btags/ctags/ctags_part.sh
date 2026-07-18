#!/usr/bin/env bash
# ctags_part.sh — Run ctags on one component directory and write a partial tags file.
#
# ⚠️  CAUTION: the incremental splice logic (sort-merge awk pass) and ctags
#    flag selection have been carefully tuned for performance and correctness.
#    Changing ctags flags, sort order assumptions, or the awk splice algorithm
#    can silently corrupt the tags file or lose entries.
#
# Usage:
#   ctags_part.sh <ctags_bin> <language> <kinds> <src_dir> <output_file>
#
# Three build modes, selected automatically:
#
#   Cold  (output absent)  — full recursive scan of <src_dir>.
#   Warm/clean (output present, no changes) — exit 0, keep existing output.
#   Warm/incremental (output present, changes detected):
#       1. Run ctags on the changed files only (not the whole component).
#       2. Splice the result into the existing sorted output in a single
#          streaming awk pass — no re-sort of unchanged lines.
#          The awk reads the old file once top-to-bottom:
#            • skips lines whose filename (field 2) is in the changed set
#            • at each surviving line, drains any new entries that sort
#              before it (merge-insert)
#          This is O(N + M) where N = old lines, M = new lines.
#
# <language>  : "Java" or "C"
# <kinds>     : ctags --kinds value, e.g. "cgimpf" or "dfgstu"
# <src_dir>   : root of the component (Java: contains com/; C: the subdir)
# <output_file>: path to write the .tags partial

set -euo pipefail

CTAGS_BIN="$1"
LANGUAGE="$2"
KINDS="$3"
SRC_DIR="$4"
OUTPUT="$5"

# ---------------------------------------------------------------------------
# Build the full source-file list for this component.
# ---------------------------------------------------------------------------
# Resolve SRC_DIR to an absolute path so all find results are absolute,
# matching the absolute paths ctags writes into the .tags file.
SRC_DIR="$(cd "$SRC_DIR" && pwd)"

case "$LANGUAGE" in
    Java)
        srcs=$({ find "$SRC_DIR/com" -name "*.java" 2>/dev/null; \
                 find "$SRC_DIR/src/com" -name "*.java" 2>/dev/null; \
                 find "$SRC_DIR/src/main/java/com" -name "*.java" 2>/dev/null; } \
            | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
            | sort || true)
        ;;
    C)
        srcs=$(find "$SRC_DIR" \( -name "*.c" -o -name "*.h" \) 2>/dev/null \
            | sort || true)
        ;;
    *)
        echo "ctags_part.sh: unknown language '$LANGUAGE'" >&2
        exit 1
        ;;
esac

if [ -z "$srcs" ]; then
    # No sources — write empty placeholder so downstream targets have a file.
    touch "$OUTPUT"
    exit 0
fi

# ---------------------------------------------------------------------------
# Cold build: output absent — full scan.
# ---------------------------------------------------------------------------
if [ ! -f "$OUTPUT" ]; then
    printf '%s\n' $srcs | "$CTAGS_BIN" \
        "--language-force=${LANGUAGE}" \
        "--kinds-${LANGUAGE}=${KINDS}" \
        --fields=+nKs \
        --sort=yes \
        --extras=+q \
        -f "$OUTPUT" -L -
    exit 0
fi

# ---------------------------------------------------------------------------
# Warm build: output present.
#
# Step 1: find files newer than the output (modified/added).
# ---------------------------------------------------------------------------
case "$LANGUAGE" in
    Java)
        newer=$({ find "$SRC_DIR/com" -name "*.java" -newer "$OUTPUT" 2>/dev/null; \
                  find "$SRC_DIR/src/com" -name "*.java" -newer "$OUTPUT" 2>/dev/null; \
                  find "$SRC_DIR/src/main/java/com" -name "*.java" -newer "$OUTPUT" 2>/dev/null; } \
            | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
            | sort || true)
        ;;
    C)
        newer=$(find "$SRC_DIR" \( -name "*.c" -o -name "*.h" \) -newer "$OUTPUT" 2>/dev/null \
            | sort || true)
        ;;
esac

# Step 2: detect deleted files — present in the old .tags but absent from
# the current source list.  Extract the set of unique filenames from the
# existing output (field 2, tab-delimited, skipping pseudo-tag lines).
old_files=$(grep -v '^!_' "$OUTPUT" | cut -f2 | sort -u || true)
current_files=$(printf '%s\n' $srcs | sort)
deleted=$(comm -23 <(echo "$old_files") <(echo "$current_files") || true)

# Union of changed (newer + deleted) — entries for these files are removed.
changed=$(printf '%s\n' $newer $deleted | sort -u || true)

if [ -z "$changed" ]; then
    exit 0   # up to date — nothing to do
fi

# ---------------------------------------------------------------------------
# Incremental splice.
#
# Step 3: run ctags on the *newer* files only (deleted ones are gone), writing
#         new entries to a temp file.
# Step 4: awk merge-splice — streams the old file once, skipping lines for
#         changed files and inserting new entries at the correct sorted
#         position as it goes.  No re-sort; the file stays in order.
# ---------------------------------------------------------------------------
TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

NEW_ENTRIES="$TMPDIR_WORK/new.tags"
OUTPUT_TMP="$TMPDIR_WORK/output.tags"

# Run ctags on newer (non-deleted) files only.
# If there are no newer files (pure deletion), we still run the splice to
# remove the deleted entries.
if [ -n "$newer" ]; then
    printf '%s\n' $newer | "$CTAGS_BIN" \
        "--language-force=${LANGUAGE}" \
        "--kinds-${LANGUAGE}=${KINDS}" \
        --fields=+nKs \
        --sort=yes \
        --extras=+q \
        -f "$NEW_ENTRIES" -L -
else
    touch "$NEW_ENTRIES"
fi

# awk merge-splice:
#   - ARGV[1]  = new entries file (sorted, may have !_ pseudo-tag header)
#   - ARGV[2]  = old output file  (sorted, may have !_ pseudo-tag header)
#   - changed[] hash built from shell variable $changed
#
# Algorithm:
#   BEGIN : load all new (non-pseudo-tag) lines into new_lines[]; preload
#           new_lines[new_i] as the "pending" new entry.
#   For each old line:
#     - skip pseudo-tag lines (^!_)
#     - skip lines whose tab-field-2 is in the changed[] set
#     - before printing, drain any new entries that sort <= current old line
#   END   : drain any remaining new entries
#
# Result written to stdout → redirected to $OUTPUT_TMP.
awk -v changed_str="$changed" '
BEGIN {
    FS = "\t"
    # Build the set of filenames whose old entries must be removed.
    n = split(changed_str, arr, "\n")
    for (i = 1; i <= n; i++) {
        if (arr[i] != "") skip[arr[i]] = 1
    }

    # Load the new-entries file (ARGV[1]) into an array.
    new_count = 0
    newfile = ARGV[1]
    delete ARGV[1]          # prevent awk from processing it as a data file
    while ((getline line < newfile) > 0) {
        if (line ~ /^!_/) continue    # skip pseudo-tag header lines
        new_lines[++new_count] = line
    }
    close(newfile)
    new_i = 1               # index of next pending new entry
}

# Processing ARGV[2] = the old output file.
/^!_/ { next }             # skip pseudo-tag header lines from old file

{
    old_line = $0
    # Before emitting this old line, drain new entries that sort before it.
    while (new_i <= new_count && new_lines[new_i] <= old_line) {
        print new_lines[new_i++]
    }
    # Emit this old line only if its source file is not in the changed set.
    if (!($2 in skip)) {
        print old_line
    }
}

END {
    # Drain any new entries that come after all old lines.
    while (new_i <= new_count) {
        print new_lines[new_i++]
    }
}
' "$NEW_ENTRIES" "$OUTPUT" > "$OUTPUT_TMP"

mv "$OUTPUT_TMP" "$OUTPUT"
