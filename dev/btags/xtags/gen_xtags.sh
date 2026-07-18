#!/usr/bin/env bash
# gen_xtags.sh — Build a callers index (.calls) for a single Java or C source file.
#
# ⚠️  CAUTION: the comment/string stripping regex and binary-search range lookup
#    in this script have been carefully tuned.  The index() guards avoiding
#    unnecessary regex calls, the early-exit on lines without '(', and the
#    [[:space:]]* handling in the call-site scanner are all load-bearing.
#    Simplifying any of these can reintroduce O(N²) behaviour or miss real
#    call sites.
#
# Outputs tab-separated records to stdout:
#   callee_name TAB caller_qualified_name TAB rel_path:line
#
# Usage:
#   gen_xtags.sh <source_file> <lodestone_root> <range_file>
#
# Approach:
#   1. Load the ltags .range file for this source file — a small TSV of
#      (file, start, end, qualified_name) rows sorted by start line,
#      produced by gen_ltags.sh from ctags-derived data.
#   2. Scan the source file line by line for bare identifier( call patterns,
#      skipping content inside comments and string literals.
#   3. For each call site at line N, binary-search the range table to find
#      the enclosing method and emit the xtags record.
#
# This replaces the previous brace-counting state machine with an exact
# range lookup based on ctags data, eliminating the block-comment and
# one-liner method edge cases that affected the old approach.
#
# Limitations (by design — this is a name-based heuristic):
#   - Cannot resolve overloads or dynamic dispatch.
#   - Very common names (get, set, toString) will have many entries — use in
#     conjunction with ftags to filter by type.

set -euo pipefail

if [ $# -ne 3 ]; then
    echo "Usage: gen_xtags.sh <source_file> <lodestone_root> <range_file>" >&2
    exit 1
fi

SOURCE_FILE="$1"
LODESTONE_ROOT="${2%/}"
RANGE_FILE="$3"

if [ ! -f "$SOURCE_FILE" ]; then
    echo "File not found: $SOURCE_FILE" >&2
    exit 1
fi

# Silently skip file types we don't index (.h files etc.)
case "${SOURCE_FILE##*.}" in
    java|c) ;;
    *) exit 0 ;;
esac

# Compute path relative to lodestone root
ABS_SOURCE="$(cd "$(dirname "$SOURCE_FILE")" && pwd)/$(basename "$SOURCE_FILE")"
ABS_ROOT="$(cd "$LODESTONE_ROOT" && pwd)"

if [[ "$ABS_SOURCE" == "$ABS_ROOT/"* ]]; then
    REL_PATH="${ABS_SOURCE#"$ABS_ROOT/"}"
else
    REL_PATH="$SOURCE_FILE"
fi

# If the .range file is missing or empty there are no known methods —
# nothing to emit.
if [ ! -s "$RANGE_FILE" ]; then
    exit 0
fi

# ---------------------------------------------------------------------------
# AWK pass:
#   Phase 1 — load the .range file into a sorted array of method ranges.
#   Phase 2 — scan source lines for call sites; binary-search for enclosing
#              method; emit xtags records.
#
# Range file format (4 columns, sorted by start line):
#   rel_path TAB start TAB end TAB qualified_name
#
# Source scanning:
#   - Block comments (/* */) tracked across lines via in_block_comment flag.
#   - Line comments (//) detected in the character loop to avoid // inside
#     block comments incorrectly truncating lines.
#   - String/char literals skipped so identifier( inside strings is ignored.
#   - Call site pattern: [A-Za-z_][A-Za-z0-9_]*( — bare identifier followed
#     by open paren, excluding language keywords.
# ---------------------------------------------------------------------------

awk -v rel="$REL_PATH" -v rangefile="$RANGE_FILE" '

# ---- helper: binary search ranges[] for line N ----
# Returns the qualified method name if N falls within a range, else "".
function find_method(n,    lo, hi, mid) {
    lo = 1; hi = nranges
    while (lo <= hi) {
        mid = int((lo + hi) / 2)
        if (n < range_start[mid])
            hi = mid - 1
        else if (n > range_end[mid])
            lo = mid + 1
        else
            return range_name[mid]
    }
    return ""
}

BEGIN {
    in_block_comment = 0

    # Load range file: file TAB start TAB end TAB qualified_name
    nranges = 0
    while ((getline line < rangefile) > 0) {
        n = split(line, f, "\t")
        if (n < 4) continue
        nranges++
        range_start[nranges] = int(f[2])
        range_end[nranges]   = int(f[3])
        range_name[nranges]  = f[4]
    }
    close(rangefile)
}

{
    # Single pass: strip comments, build clean line, scan for call sites.
    n = split($0, chars, "")
    in_str = 0; in_char = 0; clean = ""

    for (i = 1; i <= n; i++) {
        c = chars[i]

        # Inside block comment — look for */
        if (in_block_comment) {
            if (c == "*" && i < n && chars[i+1] == "/") {
                in_block_comment = 0; i++
            }
            continue
        }

        # Detect comment openings outside string/char literals
        if (!in_str && !in_char) {
            if (c == "/" && i < n && chars[i+1] == "/") break          # // rest is comment
            if (c == "/" && i < n && chars[i+1] == "*") {               # /* open
                in_block_comment = 1; i++; continue
            }
        }

        # String/char literal tracking
        if (c == "\"" && !in_char) { in_str = !in_str; clean = clean c; continue }
        if (c == "'"'"'" && !in_str) { in_char = !in_char; clean = clean c; continue }

        clean = clean c
        if (in_str || in_char) continue
    }

    # Find the enclosing method for this line via range lookup
    method = find_method(NR)
    if (method == "") next

    # Scan clean line for call sites: identifier(
    rest = clean
    while (match(rest, /[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/)) {
        token = substr(rest, RSTART, RLENGTH)
        sub(/[[:space:]]*\($/, "", token)
        callee = token

        # Skip keywords
        if (callee != "if" && callee != "for" && callee != "while" &&
            callee != "switch" && callee != "catch" && callee != "return" &&
            callee != "new" && callee != "class" && callee != "interface" &&
            callee != "enum" && callee != "synchronized" &&
            callee != method) {
            print callee "\t" method "\t" rel ":" NR
        }
        rest = substr(rest, RSTART + RLENGTH)
    }
}
' "$SOURCE_FILE"
