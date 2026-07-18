#!/usr/bin/env bash
# gen_xtags_comp.sh — Build xtags .calls files for an entire component.
#
# ⚠️  CAUTION: this script contains several load-bearing performance and
#    correctness optimisations.  The index() guards on comment/string
#    stripping, the early continue when no '(' is on a line, and the
#    [[:space:]]* in the call-site scanner regex are all required — removing
#    or simplifying them reintroduces O(N²) behaviour or silently drops real
#    call sites (e.g. `identifier (` with a space before the paren).
#    The post-awk sort step keeps .calls files sorted so xtags_merge.sh can
#    use sort --merge; removing it forces a full O(N log N) re-sort there.
#
# Usage:
#   gen_xtags_comp.sh <fns_file> <src_root> <lodestone_root> <calls_dir>
#
# Processes all source files belonging to one component in a single awk pass,
# writing one <calls_dir>/<stem>.calls file per source file.  This replaces
# the old per-file gen_xtags.sh invocation model (one process per source file)
# with one process per component (~82 total vs ~12 000).
#
# Arguments:
#   <fns_file>       — component .fns file from ftags (6-column TSV)
#   <src_root>       — root directory that contains the source files
#                      (used to resolve relative paths from .fns col3)
#   <lodestone_root> — absolute path to the Lodestone workspace root
#   <calls_dir>      — directory to write <stem>.calls output files
#
# .fns input format (6 columns):
#   simple_name TAB qualified_name TAB rel_path TAB start TAB end TAB signature
#
# .calls output format per file (3 columns):
#   callee_name TAB caller_qualified_name TAB rel_path:line
#
# Algorithm:
#   Phase 1 — load the .fns file: build a range table per rel_path.
#             For each rel_path, store arrays of (start, end, qualified_name)
#             sorted by start line (guaranteed by ftags.sh output order).
#   Phase 2 — for each unique rel_path (only .java and .c files):
#             open the source file; stream it line by line; strip comments and
#             string literals with regex; for each line inside a method range,
#             scan for identifier( call patterns; emit to the .calls file.
#
# Comment/string stripping uses regex rather than a character-by-character
# loop, which is ~4x faster in awk.  String literal stripping handles the
# common case ("..." content on a single line) and is correct for code that
# does not contain strings with embedded escaped quotes mid-token.

set -euo pipefail

if [ $# -ne 4 ]; then
    echo "Usage: gen_xtags_comp.sh <fns_file> <src_root> <lodestone_root> <calls_dir>" >&2
    exit 1
fi

FNS_FILE="$1"
SRC_ROOT="${2%/}"
LODESTONE_ROOT="${3%/}"
CALLS_DIR="$4"

if [ ! -f "$FNS_FILE" ]; then
    echo "File not found: $FNS_FILE" >&2
    exit 1
fi

if [ ! -d "$CALLS_DIR" ]; then
    echo "Directory not found: $CALLS_DIR" >&2
    exit 1
fi

# Derive the component stem from the .fns filename so we can sort only the
# .calls files we write (avoid re-sorting files from other components).
# e.g. java_sv.fns → stem "sv"; c_src_hl.fns → stem "c_src_hl"
_fns_stem="${FNS_FILE##*/}"; _fns_stem="${_fns_stem%.fns}"

awk -v src_root="$SRC_ROOT" \
    -v lodestone_root="$LODESTONE_ROOT" \
    -v calls_dir="$CALLS_DIR" \
    -v fns_file="$FNS_FILE" '

# ---------------------------------------------------------------------------
# Phase 1: load the .fns file into per-file range tables.
# ---------------------------------------------------------------------------
BEGIN {
    FS = "\t"

    # Read the fns file
    while ((getline line < fns_file) > 0) {
        n = split(line, f, "\t")
        if (n < 5) continue
        # f[2]=qualified_name  f[3]=rel_path  f[4]=start  f[5]=end
        rp = f[3]
        # only index .java and .c files
        if (rp !~ /\.(java|c)$/) continue
        i = ++file_nranges[rp]
        range_start[rp, i] = int(f[4])
        range_end[rp, i]   = int(f[5])
        range_name[rp, i]  = f[2]
        # track insertion order of rel_paths for Phase 2
        if (!(rp in seen)) {
            seen[rp] = 1
            file_order[++nfiles] = rp
        }
    }
    close(fns_file)
}

# ---------------------------------------------------------------------------
# Phase 2: no stdin input — all work done in END.
# ---------------------------------------------------------------------------
END {
    for (fi = 1; fi <= nfiles; fi++) {
        rp = file_order[fi]
        process_file(rp)
    }
}

# ---------------------------------------------------------------------------
# process_file: stream one source file, detect call sites, write .calls.
# ---------------------------------------------------------------------------
function process_file(rel_path,    src_file, calls_file, stem,
                                   nr, in_block, line, method, rest,
                                   t, lo, hi, mid) {

    src_file = lodestone_root "/" rel_path
    stem = rel_path
    gsub(/\//, "_", stem)
    calls_file = calls_dir "/" stem ".calls"

    nr       = file_nranges[rel_path]
    in_block = 0
    lineno   = 0

    while ((getline raw_line < src_file) > 0) {
        lineno++
        line = raw_line

        # --- strip comments and string literals ---

        # Block comment continuation from previous line
        if (in_block) {
            if (index(line, "*/") > 0 && match(line, /\*\//)) {
                line     = substr(line, RSTART + RLENGTH)
                in_block = 0
            } else {
                continue
            }
        }

        # Remove block comments that open and close on this line
        if (index(line, "/*") > 0) {
            while (match(line, /\/\*[^*]*(\*[^\/][^*]*)*\*\//))
                line = substr(line, 1, RSTART - 1) substr(line, RSTART + RLENGTH)

            # Detect a block comment that opens but does not close on this line
            if (index(line, "/*") > 0 && match(line, /\/\*/)) {
                line     = substr(line, 1, RSTART - 1)
                in_block = 1
            }
        }

        # Strip line comments
        if (index(line, "//") > 0)
            line = substr(line, 1, index(line, "//") - 1)

        # Strip string literals
        if (index(line, "\"") > 0) gsub(/"[^"]*"/, "\"\"", line)
        if (index(line, "\x27") > 0) gsub(/'\''{1}[^'\'']*'\''{1}/, "''", line)

        # Skip lines with no open-paren — nothing to scan for call sites,
        # and no need to run the binary search either.
        if (index(line, "(") == 0) continue

        # --- find enclosing method (binary search on sorted range table) ---
        method = ""
        lo = 1; hi = nr
        while (lo <= hi) {
            mid = int((lo + hi) / 2)
            if      (lineno < range_start[rel_path, mid]) hi = mid - 1
            else if (lineno > range_end[rel_path, mid])   lo = mid + 1
            else { method = range_name[rel_path, mid]; break }
        }
        if (method == "") continue

        # --- scan for call sites: identifier( or identifier ( ---
        rest = line
        while (match(rest, /[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/)) {
            t = substr(rest, RSTART, RLENGTH)
            sub(/[[:space:]]*\($/, "", t)
            if (t != "if"    && t != "for"    && t != "while"  &&
                t != "switch" && t != "catch"  && t != "return" &&
                t != "new"    && t != "class"  && t != "interface" &&
                t != "enum"   && t != "synchronized" && t != method) {
                print t "\t" method "\t" rel_path ":" lineno > calls_file
            }
            rest = substr(rest, RSTART + RLENGTH)
        }
    }
    close(src_file)
    if (calls_file != "") close(calls_file)
}
' /dev/null

# Sort each .calls file written by this component so xtags_merge.sh can use
# sort --merge (O(N)) instead of sort (O(N log N)) per letter file.
# Match by component stem: java_sv.fns writes sv_*.calls; c_src_hl.fns writes
# c_src_hl_*.calls (the awk uses rel_path which starts with the component dir).
case "$_fns_stem" in
    java_*) _calls_pat="${_fns_stem#java_}_*.calls" ;;
    *)      _calls_pat="${_fns_stem}_*.calls" ;;
esac
for f in "$CALLS_DIR"/$_calls_pat; do
    [ -f "$f" ] && sort -o "$f" "$f" || true
done
