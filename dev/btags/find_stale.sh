#!/usr/bin/env bash
# find_stale.sh — Emit space-separated list of stale component identifiers.
#
# ⚠️  CAUTION: the timestamp comparisons, path manipulations, and find flags
#    here have been carefully tuned for performance and correctness.  Changes
#    to the comparison logic or argument handling can introduce subtle staleness
#    detection bugs that are hard to reproduce.
#
# Usage:
#   find_stale.sh <mode> <root> <stamps_dir> <comp_or_fns_list...>
#
# Modes:
#   ctags-java  <root> <stamps_dir> <comp_dir...>
#     Check each Java component dir — any .java newer than its ctags stamp?
#
#   ctags-c  <root> <stamps_dir> <subdir...>
#     Check each C subdir — any .c/.h newer than its ctags stamp?
#
#   ftags-java  <fns_dir> <comp_dir...>
#     Check each Java component dir — any .java newer than its .fns file?
#     Used so ftags can detect source changes independently of ctags stamps.
#
#   ftags-c  <fns_dir> <subdir...>
#     Check each C subdir — any .c/.h newer than its .fns file?
#
#   stags  <stamps_dir> <tags_file...>
#     Check each .tags partial — is it newer than its stags stamp?
#
#   fns  <stamps_dir_suffix> <fns_file...>
#     Check each .fns file — is it newer than its <suffix> stamp?
#     stamp path: <fns_dir>/<suffix>/<basename>.stamp
#
# Output: space-separated list of stale items (comp dirs, tags files, or fns files).
# Empty output means everything is up to date.

set -euo pipefail

MODE="$1"; shift

case "$MODE" in

ctags-java)
    ROOT="$1"; STAMPS_DIR="$2"; shift 2
    for comp in "$@"; do
        name="java_${comp##*/}"
        stamp="$STAMPS_DIR/${name}.stamp"
        if [ ! -f "$stamp" ]; then
            printf '%s ' "$comp"
            continue
        fi
        changed=$({ find "$comp/com" -name "*.java" -newer "$stamp" 2>/dev/null; \
                    find "$comp/src/main/java/com" -name "*.java" -newer "$stamp" 2>/dev/null; } \
            | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
            | head -1 || true)
        [ -n "$changed" ] && printf '%s ' "$comp"
    done
    ;;

ctags-c)
    ROOT="$1"; STAMPS_DIR="$2"; shift 2
    for subdir in "$@"; do
        name="c_src_${subdir##*/}"
        stamp="$STAMPS_DIR/${name}.stamp"
        if [ ! -f "$stamp" ]; then
            printf '%s ' "$subdir"
            continue
        fi
        changed=$(find "$subdir" \( -name "*.c" -o -name "*.h" \) -newer "$stamp" 2>/dev/null | head -1 || true)
        [ -n "$changed" ] && printf '%s ' "$subdir"
    done
    ;;

ftags-java)
    FNS_DIR="$1"; shift
    for comp in "$@"; do
        fns="$FNS_DIR/java_${comp##*/}.fns"
        if [ ! -f "$fns" ]; then
            printf '%s ' "$comp"
            continue
        fi
        changed=$({ find "$comp/com" -name "*.java" -newer "$fns" 2>/dev/null; \
                    find "$comp/src/main/java/com" -name "*.java" -newer "$fns" 2>/dev/null; } \
            | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/' \
            | head -1 || true)
        [ -n "$changed" ] && printf '%s ' "$comp"
    done
    ;;

ftags-c)
    FNS_DIR="$1"; shift
    for subdir in "$@"; do
        fns="$FNS_DIR/c_src_${subdir##*/}.fns"
        if [ ! -f "$fns" ]; then
            printf '%s ' "$subdir"
            continue
        fi
        changed=$(find "$subdir" \( -name "*.c" -o -name "*.h" \) -newer "$fns" 2>/dev/null | head -1 || true)
        [ -n "$changed" ] && printf '%s ' "$subdir"
    done
    ;;

stags)
    STAMPS_DIR="$1"; shift
    for tags_file in "$@"; do
        base="${tags_file##*/}"; base="${base%.tags}"
        stamp="$STAMPS_DIR/${base}.stamp"
        if [ ! -f "$stamp" ]; then
            printf '%s ' "$tags_file"
            continue
        fi
        [ "$tags_file" -nt "$stamp" ] && printf '%s ' "$tags_file"
    done
    ;;

fns)
    STAMPS_DIR="$1"; shift
    for fns_file in "$@"; do
        base="${fns_file##*/}"; base="${base%.fns}"
        stamp="$STAMPS_DIR/${base}.stamp"
        if [ ! -f "$stamp" ]; then
            printf '%s ' "$fns_file"
            continue
        fi
        [ "$fns_file" -nt "$stamp" ] && printf '%s ' "$fns_file"
    done
    ;;

*)
    echo "find_stale.sh: unknown mode '$MODE'" >&2
    exit 1
    ;;
esac
