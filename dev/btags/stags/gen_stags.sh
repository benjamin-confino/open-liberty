#!/usr/bin/env bash
# gen_stags.sh — Build Structure Tags for ONE ctags partial file.
#
# ⚠️  CAUTION: the awk column parsing is tightly coupled to the ctags
#    --extras=+q and --fields flags used in ctags_part.sh.  The sort -o step
#    at the end is not just cosmetic — it produces sorted part files required
#    by gen_stags_all.sh's sort --merge assembly.  Removing or weakening it
#    silently corrupts the flat index.
#
# Called once per component by the parallel stags make target.
# Reads a single btags/ctags/parts/*.tags file and writes the structural
# kinds (class, enum, interface, struct, typedef, macro, union) into
# per-letter TSV files under <output_dir>/parts/stags_<comp>/.
#
# The component name is derived from the partial filename:
#   java_sv.tags   → comp = sv    (Java component)
#   java_se.tags   → comp = se
#   c_src.tags     → comp = src   (hand-written C)
#   c_src2.tags    → comp = src2  (JavaParser-generated C)
#
# Output layout (per invocation):
#   <output_dir>/parts/stags_<comp>/<letter>.tsv
#
# The cross-component aggregation is done separately by gen_stags_all.sh
# which merges parts/stags_*/ into <output_dir>/<letter>.tsv (flat, at root).
#
# File format (3 columns, TAB-separated):
#   qualified_name TAB rel_file:line TAB kind
#   e.g.
#     CsmPartition          sv/com/ibm/svc/sv/CsmPartition.java:72    class
#     CsmDomain.ACAFields   ca/com/ibm/svc/ca/csm/CsmDomain.java:3448 enum
#     Ic_failed_xxx         src/user/ic/ic_def.h:12                   macro
#
# Usage:
#   gen_stags.sh <lodestone_root> <part_file> <output_dir>
#
# Prerequisites: bash, awk, sort

set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "Usage: gen_stags.sh <lodestone_root> <part_file> <output_dir>" >&2
    exit 1
fi

ROOT=$(cd "$1" && pwd)
PART_FILE="$2"
OUT_DIR="$3"
[[ "$OUT_DIR" != /* ]] && OUT_DIR="$(pwd)/$OUT_DIR"

if [[ ! -f "$PART_FILE" ]]; then
    echo "[stags] ERROR: part file not found: $PART_FILE" >&2
    exit 1
fi

# Derive component name from the part filename:
#   java_sv.tags  → sv
#   c_src.tags    → src
#   c_src2.tags   → src2
BASENAME=$(basename "$PART_FILE" .tags)
case "$BASENAME" in
    java_*)  COMP="${BASENAME#java_}" ;;
    c_src2)  COMP="src2" ;;
    c_src)   COMP="src"  ;;
    *)       COMP="$BASENAME" ;;
esac

COMP_DIR="$OUT_DIR/parts/stags_$COMP"
mkdir -p "$COMP_DIR"

# ---------------------------------------------------------------------------
# Parse the partial ctags file.
#
# Because the component is already known from the filename we write every row
# directly to parts/stags_<comp>/; gen_stags_all.sh merges those into the
# flat per-letter TSVs at the stags root.
#
# Universal Ctags e-ctags format (tab-separated):
#   $1 = name
#   $2 = absolute file path
#   $3 = ex pattern (ignored here)
#   $4 = kind letter / word (c/g/i/d/s/t/u — we skip f/m/function/method)
#   $5+ = key:value extension fields  (line:N, kind:word, class:X, ...)
#
# Edge case: a literal tab inside a ctags ex-pattern shifts subsequent
# columns, putting the kind word in $5 as a bare word.  Both the $4
# early-exit filter and the post-loop kindword check handle this.
# ---------------------------------------------------------------------------

awk -v root="$ROOT/" -v comp_dir="$COMP_DIR" '
BEGIN {
    FS = "\t"
    _kwords["class"]=1; _kwords["enum"]=1; _kwords["interface"]=1
    _kwords["struct"]=1; _kwords["typedef"]=1; _kwords["macro"]=1
    _kwords["union"]=1; _kwords["function"]=1; _kwords["method"]=1
    _kwords["package"]=1; _kwords["field"]=1
}

# Skip pseudo-tag header lines
/^!_/ { next }

# Need at least 4 columns
NF < 4 { next }

{
    name    = $1
    apath   = $2
    kletter = $4

    # Early exit on methods — those are ftags territory.
    if (kletter == "m" || kletter == "method") next

    # Parse key:value fields from col 5 onward
    lineno   = 0
    kindword = ""
    scope    = ""

    for (i = 5; i <= NF; i++) {
        eq = index($i, ":")
        if (eq == 0) {
            # Bare word — shifted kind column due to tab-in-ex-pattern
            if ($i in _kwords && kindword == "") kindword = $i
            continue
        }
        k = substr($i, 1, eq-1)
        v = substr($i, eq+1)
        if (k == "line")  lineno   = v
        if (k == "kind")  kindword = v
        if (k == "class" || k == "namespace" || k == "struct" || k == "enum")
            scope = v
    }

    if (lineno == 0) next

    # Fall back to kind letter if kind word field absent
    if (kindword == "") kindword = kletter

    # Second-chance filter (covers tab-shifted columns).
    # "function"/"m"/"method" are ftags territory.
    # "f" as bare fallback means a C function with no kind: field — skip it.
    # "field" (Java class member) is explicitly kept for stags.
    if (kindword == "method"   || kindword == "function" ||
        kindword == "m"        || kindword == "package") next
    if (kindword == "f") next   # bare "f" = C function kind-letter fallback

    # Make path relative to root
    rel = apath
    if (index(apath, root) == 1) rel = substr(apath, length(root)+1)

    # Qualified name: dotted name from --extras=+q used directly;
    # otherwise prepend scope if present
    if (index(name, ".") > 0) {
        qual = name
    } else {
        qual = (scope != "") ? scope "." name : name
    }

    row = qual "\t" rel ":" lineno "\t" kindword

    # Row 1: keyed by qualified name
    c = substr(qual, 1, 1)
    letter = (c ~ /[A-Za-z]/) ? tolower(c) : "_"
    print row >> (comp_dir "/" letter ".tsv")

    # Row 2: keyed by simple name — last dot-segment — when it differs from qual.
    # Lets Bob look up a class/struct/enum by short name without knowing the scope.
    dot = index(qual, ".")
    if (dot > 0) {
        simple = qual
        while ((dot = index(simple, ".")) > 0)
            simple = substr(simple, dot + 1)
        if (simple != qual) {
            c2 = substr(simple, 1, 1)
            letter2 = (c2 ~ /[A-Za-z]/) ? tolower(c2) : "_"
            print simple "\t" rel ":" lineno "\t" kindword >> (comp_dir "/" letter2 ".tsv")
        }
    }
}
' "$PART_FILE"

# Sort each letter file for this component so gen_stags_all.sh can use
# sort --merge (O(N) merge-sort) for the flat cross-component assembly.
# No duplicates are possible: the awk guard (simple != qual) prevents them.
shopt -s nullglob 2>/dev/null || true
for f in "$COMP_DIR"/*.tsv; do
    sort -o "$f" "$f"
done
