#!/usr/bin/env bash
# ctags/gen_srccache.sh — write .srccache.mk with JAVA_COMP_DIRS, JAVA_SOURCES, C_SOURCES.
# Standalone helper; not called by the makefiles directly.
#
# ⚠️  CAUTION: the find filters and source-tree traversal logic here have been
#    tuned to include exactly the right source files.  Altering the exclusion
#    patterns or depth limits may silently drop components or include generated
#    files that should be excluded.
#
# Usage: ctags/gen_srccache.sh <project_root> <output_mk>
#   Conventional output path: btags/ctags/.srccache.mk

set -euo pipefail

ROOT="${1%/}"
OUT="$2"
TMP="${OUT}.tmp"

JAVA_COMP_DIRS=$(
    find "$ROOT" -maxdepth 2 -name "com" -type d 2>/dev/null \
    | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/\|/btags/' \
    | sed 's|/com$||' | sort
)

JAVA_SOURCES=$(
    { find "$ROOT"/*/com -name "*.java" 2>/dev/null
      find "$ROOT" -maxdepth 6 -path "*/src/main/java/com/**/*.java" 2>/dev/null; } \
    | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
    | sort
)

C_SOURCES=$(
    find "$ROOT/src/user" \( -name "*.c" -o -name "*.h" \) 2>/dev/null \
    | sort
)

{
    printf 'JAVA_COMP_DIRS :='
    for d in $JAVA_COMP_DIRS; do printf ' \\\n  %s' "$d"; done
    printf '\n\n'

    printf 'JAVA_SOURCES :='
    for f in $JAVA_SOURCES; do printf ' \\\n  %s' "$f"; done
    printf '\n\n'

    printf 'C_SOURCES :='
    for f in $C_SOURCES; do printf ' \\\n  %s' "$f"; done
    printf '\n'
} > "$TMP" && mv "$TMP" "$OUT"
