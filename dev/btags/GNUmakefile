# btags/GNUmakefile
#
# All tag indexes for a Java/C project.
# Two usage modes — see INSTALL.md for full details:
#
#   Embedded:    btags/ lives inside the project.  ROOT defaults to the
#                parent directory.  Run: cd your-project/btags && make tags
#
#   Standalone:  btags/ lives anywhere (e.g. ~/tools/btags).  Point it at a
#                project by setting BTAGS_SOURCE:
#                  export BTAGS_SOURCE=/path/to/your-project
#                  cd ~/tools/btags && make tags
#                Or as a one-shot: make tags BTAGS_SOURCE=/path/to/your-project
#
# ⚠️  CAUTION — hand-editing this file requires care.  Every recipe and
#    variable has been carefully tuned for build performance and to avoid
#    subtle make-evaluation bugs (parse-time vs recipe-time races, stamp
#    chain ordering, parallel job limits).  Do not simplify a recipe
#    without understanding its full dependency chain.
#
# Targets:
#   tags         - build all indexes: ctags + ftags + stags + ltags + xtags (default)
#   ctags        - Universal Ctags index  → btags/ctags/tags
#   ftags        - Function Tags          → btags/ftags/ftags_<comp>/<letter>.tsv
#   stags        - Structure Tags         → btags/stags/stags_<comp>/<letter>.tsv
#   clean        - remove all generated output
#
# Parallelism:
#   ctags  - per-component partial scans run with -j$(CTAGS_JOBS) (default 20).
#            Override: make ctags CTAGS_JOBS=8
#   ftags  - per-file stamp generation runs with -j$(FTAGS_JOBS) (default 20).
#            Override: make ftags FTAGS_JOBS=8
#
# Incrementality:
#   ctags  - per-component partial file in btags/ctags/parts/; only the
#            component(s) with changed sources are re-scanned, then re-merged.
#   ftags  - per-file stamps in btags/ftags/.stamps/; only changed files re-stamped.
#   stags  - reads btags/ctags/tags (no second ctags scan); rebuilt when the
#            ctags file is newer than the sentinel btags/stags/stags_all/a.tsv.
#
# Works on macOS (Homebrew universal-ctags) and Linux (system universal-ctags).

MAKEFLAGS += --no-builtin-rules
.SUFFIXES:

.DEFAULT_GOAL := tags

# ---------------------------------------------------------------------------
# Paths — resolved early (before any ifeq blocks).
# ---------------------------------------------------------------------------

_THIS_DIR   := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
_INC_MK     := $(_THIS_DIR)/GNUmakefile.incremental
_INC_SENT   := $(_THIS_DIR)/ctags/parts
_BTAGS_ABS  := $(realpath $(_THIS_DIR))
_ROOT_ABS   := $(realpath $(_THIS_DIR)/..)
_IS_INC     := $(if $(filter clean clean-%,$(MAKECMDGOALS)),,$(wildcard $(_INC_SENT)))

# Parallel jobs for ctags per-component scans and ftags stamp generation.
CTAGS_JOBS ?= 20
FTAGS_JOBS ?= 20

# ---------------------------------------------------------------------------
# Paths — this makefile lives at btags/GNUmakefile
# ---------------------------------------------------------------------------

BTAGS_DIR := $(realpath $(dir $(lastword $(MAKEFILE_LIST))))

# ROOT — the project root whose source tree is indexed.
#
# Standalone mode: set BTAGS_SOURCE (env var or make variable) to the
#   absolute path of the project you want to index.  btags can then live
#   anywhere without being copied into the host project.
#
# Embedded mode: btags/ is a subdirectory of the project.  ROOT resolves
#   automatically to the parent directory of btags/ — no configuration needed.
ifdef BTAGS_SOURCE
  ROOT ?= $(realpath $(BTAGS_SOURCE))
else
  ROOT ?= $(realpath $(BTAGS_DIR)/..)
endif

CTAGS_DIR := $(BTAGS_DIR)/ctags
FTAGS_DIR := $(BTAGS_DIR)/ftags
STAGS_DIR := $(BTAGS_DIR)/stags

GEN_FTAGS_SH      := $(FTAGS_DIR)/ftags.sh
GEN_STAGS_SH      := $(STAGS_DIR)/gen_stags.sh
GEN_STAGS_ALL_SH  := $(STAGS_DIR)/gen_stags_all.sh
MERGE_CTAGS_SH    := $(CTAGS_DIR)/merge_ctags.sh
CTAGS_PART_SH     := $(CTAGS_DIR)/ctags_part.sh

# ---------------------------------------------------------------------------
# Locate Universal Ctags
# ---------------------------------------------------------------------------

CTAGS_BIN := $(shell \
  _c=$$(command -v ctags 2>/dev/null); \
  [ -n "$$_c" ] && "$$_c" --version 2>&1 | grep -qi "universal ctags" && echo "$$_c")

ifeq ($(CTAGS_BIN),)
  CTAGS_BIN := $(shell \
    for p in /opt/homebrew/bin/ctags /usr/local/bin/ctags; do \
      [ -x "$$p" ] && "$$p" --version 2>&1 | grep -qi "universal ctags" \
        && echo "$$p" && break; \
    done)
endif

ifeq ($(CTAGS_BIN),)
  $(error Universal Ctags not found. On macOS: brew install universal-ctags  On Debian/Ubuntu: sudo apt install universal-ctags  On RHEL/CentOS/Fedora: sudo dnf install -y epel-release && sudo dnf --enablerepo=epel install -y ctags)
endif

# ---------------------------------------------------------------------------
# Source file lists
#
# Java: *.java under every <comp>/com/ tree at the top level.
#       Excludes: be_decaf, JavaParser, cli_autogen, ip_quorum, bin/.
# C:    *.c and *.h under src/user/ only (src2/ is generated code, excluded by default).
#       Headers are included so that structs, enums, typedefs and macros
#       defined in .h files appear in the stags index.
# ---------------------------------------------------------------------------

# Skip all source-file discovery when the goal is clean-only — the $(shell find)
# calls dominate startup time and are not needed for deletion.
_CLEAN_ONLY := $(if $(filter-out clean clean-%,$(or $(MAKECMDGOALS),tags)),,1)

ifeq ($(_CLEAN_ONLY),1)
JAVA_COMP_DIRS :=
JAVA_SOURCES   :=
C_SOURCES      :=
ALL_SOURCES    :=
else

# Component dirs: open-liberty layout is <comp>/src/com/... (depth-3 com/).
# Also pick up the minority of Spring Boot fat-test apps with src/main/java/com/.
JAVA_COMP_DIRS := $(shell \
  { find $(ROOT) -maxdepth 3 -path "*/src/com" -type d 2>/dev/null; \
    find $(ROOT) -maxdepth 5 -path "*/src/main/java/com" -type d 2>/dev/null; } \
  | grep -v '/bin/\|/bin_test/\|/btags/' \
  | sed 's|/src/com$$||;s|/src/main/java/com$$||' | sort -u)

JAVA_SOURCES := $(shell \
  { find $(ROOT)/*/src/com -name "*.java" 2>/dev/null; \
    find $(ROOT) -maxdepth 6 -path "*/src/main/java/com/**/*.java" 2>/dev/null; } \
  | grep -v '/bin/\|/bin_test/' \
  | sort)

# open-liberty is Java-only — no C sources.
C_SOURCES :=

ALL_SOURCES := $(JAVA_SOURCES)

endif

# ---------------------------------------------------------------------------
# Top-level phony targets
# ---------------------------------------------------------------------------

.PHONY: tags btags ctags ftags stags ltags xtags clean

# ---------------------------------------------------------------------------
# Incremental dispatch — when _IS_INC is set (ctags/parts/ exists from a
# previous cold build), tag targets delegate to GNUmakefile.incremental.
# 'clean' / 'clean-%' always stay in this makefile.
#
# GNU make merges recipes from multiple rules for the same target.  The
# cold-build recipes (ctags:, ftags:, …) each guard themselves with
# "exit 0 if _IS_INC" so they become no-ops on the incremental path.
# The tags/btags meta-target drives the incremental make directly.
# ---------------------------------------------------------------------------
_INC_MAKE = $(MAKE) --no-print-directory -f $(_INC_MK) \
	    BTAGS_DIR="$(_BTAGS_ABS)" ROOT="$(ROOT)" \
	    CTAGS_BIN="$(CTAGS_BIN)" \
	    CTAGS_JOBS="$(CTAGS_JOBS)" FTAGS_JOBS="$(FTAGS_JOBS)"

tags btags:
	@if [ -n "$(_IS_INC)" ]; then \
	    $(_INC_MAKE) $@; \
	else \
	    $(MAKE) --no-print-directory -f $(firstword $(MAKEFILE_LIST)) \
	        _cold_tags_btags; \
	fi

.PHONY: _cold_tags_btags _cold_ctags _cold_ftags _cold_stags _cold_ltags _cold_xtags
_cold_tags_btags: _cold_ctags _cold_ftags _cold_stags _cold_ltags _cold_xtags
	@echo ""
	@echo "🗂️  Bob tag indexes written. Incremental make enabled."
	@echo ""

# ===========================================================================
# ctags — parallel per-component scans → merge → btags/ctags/tags
#
# Each Java component and the C source tree (src/user) is scanned
# by its own ctags invocation.  All invocations run in parallel under a
# recursive make -j$(CTAGS_JOBS) call.  The resulting per-component .tags
# files land in btags/ctags/parts/ and are merged by merge_ctags.sh into the
# final btags/ctags/tags.
#
# Incrementality: only the component(s) whose sources are newer than their
# partial .tags file are re-scanned; the merge always runs when any partial
# changes.
# ===========================================================================

CTAGS_FILE      := $(CTAGS_DIR)/tags
CTAGS_PARTS_DIR := $(CTAGS_DIR)/parts

# Derive a safe filename stem from a component directory path.
# e.g. /path/myproject/orders  →  java_orders
comp_to_part = $(CTAGS_PARTS_DIR)/java_$(notdir $(1)).tags

# Per-Java-component partial tag files.
CTAGS_JAVA_PARTS := $(foreach d,$(JAVA_COMP_DIRS),$(call comp_to_part,$(d)))

# open-liberty is Java-only — no C subdirs.
C_SRC_SUBDIRS     :=
CTAGS_C_SRC_PARTS :=

CTAGS_ALL_PARTS := $(CTAGS_JAVA_PARTS)

.PHONY: ctags _ctags_parts

CTAGS_STAMPS_DIR := $(CTAGS_DIR)/.stamps

$(CTAGS_DIR):
	@mkdir -p $@

$(CTAGS_PARTS_DIR): | $(CTAGS_DIR)
	@mkdir -p $@

$(CTAGS_STAMPS_DIR): | $(CTAGS_DIR)
	@mkdir -p $@

# 'ctags' is a phony that runs both phases:
#   Phase 1 — parallel recursive make builds all stale per-component .tags files.
#   Phase 2 — merge_ctags.sh assembles them into the final tags file.
#
# $(CTAGS_FILE) is also declared as a real file target (with no recipe) so that
# stags — which depends on it — sees it as a prerequisite and rebuilds when the
# file's timestamp changes after a merge.
#
# The order-only prerequisite | $(CTAGS_PARTS_DIR) ensures the directory exists
# before either phase runs.

ctags: | $(CTAGS_PARTS_DIR)
	@[ -z "$(_IS_INC)" ] || $(_INC_MAKE) $@

_cold_ctags: | $(CTAGS_PARTS_DIR) $(CTAGS_STAMPS_DIR)
	@echo "[ctags] Using ctags to scan $(words $(JAVA_COMP_DIRS)) Java components + $(words $(C_SRC_SUBDIRS)) C subdirs (-j$(CTAGS_JOBS)) ..."
	@$(MAKE) --no-print-directory -s -j$(CTAGS_JOBS) -C $(BTAGS_DIR) \
	    CTAGS_BIN="$(CTAGS_BIN)" \
	    ROOT="$(ROOT)" \
	    CTAGS_PARTS_DIR="$(CTAGS_PARTS_DIR)" \
	    _ctags_parts; printf "\r\033[K" >&2
	@if [ -n "$$(find $(CTAGS_PARTS_DIR) -name '*.tags' \
	               -newer $(CTAGS_FILE) 2>/dev/null | head -1)" ] \
	      || [ ! -f $(CTAGS_FILE) ]; then \
	    bash $(MERGE_CTAGS_SH) $(CTAGS_PARTS_DIR) $(CTAGS_FILE); \
	else \
	    echo "[ctags] Up to date."; \
	fi
	@for _p in $(CTAGS_PARTS_DIR)/*.tags; do \
	    touch "$(CTAGS_STAMPS_DIR)/$$(basename $$_p .tags).stamp"; \
	done

_ctags_parts: $(CTAGS_ALL_PARTS)

# Per-Java-component rule — depends on the src/ dir (open-liberty layout:
# <comp>/src/com/...).  ctags_part.sh discovers com/, src/com/, and
# src/main/java/com/ internally, so we only need the component root as $4.
define CTAGS_JAVA_PART_RULE
$(call comp_to_part,$(1)): $(1)/src $(CTAGS_PART_SH) | $(CTAGS_PARTS_DIR)
	@printf "\r\033[K[ctags]   $(notdir $(1)) (Java)" >&2
	@bash $(CTAGS_PART_SH) $(CTAGS_BIN) Java cgimpf $(1) $$@
endef

$(foreach d,$(JAVA_COMP_DIRS),$(eval $(call CTAGS_JAVA_PART_RULE,$(d))))

# Per-C-subdir rules — one ctags job per src/user subdirectory.
define CTAGS_C_PART_RULE
$(call c_subdir_to_part,$(1)): $(1) $(CTAGS_PART_SH) | $(CTAGS_PARTS_DIR)
	@printf "\r\033[K[ctags]   src/$(notdir $(1)) (C+H)" >&2
	@bash $(CTAGS_PART_SH) $(CTAGS_BIN) C dfgstu $(1) $$@
endef

$(foreach d,$(C_SRC_SUBDIRS),$(eval $(call CTAGS_C_PART_RULE,$(d))))

# ===========================================================================
# ftags — Function Tags (methods + C functions, with line ranges)
#
# Per-file stamps produced by ftags.sh → merged by ftags_merge.sh into:
#   ftags/parts/ftags_<comp>/<letter>.tsv  — per-component
#   ftags/<letter>.tsv                     — flat cross-component
#
# Search priority for Bob:
#   1. ftags/parts/ftags_<comp>/<letter>.tsv  — when component is known
#   2. ftags/<letter>.tsv                     — flat cross-component fallback
# ===========================================================================

FTAGS_DIR_OUT  := $(FTAGS_DIR)
FTAGS_ALL_FILE := $(FTAGS_DIR)/l.tsv
FTAGS_FNS_DIR  := $(FTAGS_DIR)/.fns

# One .fns file per component dir — not per source file.
# Java: java_<comp>.fns  (ftags.sh receives <comp>/com, recurses with ctags -R)
# C:    c_src_<subdir>.fns  (ftags.sh receives src/user/<subdir>, recurses with ctags -R)
java_comp_to_fns = $(FTAGS_FNS_DIR)/java_$(notdir $(1)).fns
c_subdir_to_fns  = $(FTAGS_FNS_DIR)/c_src_$(notdir $(1)).fns

FTAGS_JAVA_FNS := $(foreach d,$(JAVA_COMP_DIRS),$(call java_comp_to_fns,$(d)))
FTAGS_C_FNS    := $(foreach d,$(C_SRC_SUBDIRS),$(call c_subdir_to_fns,$(d)))
FTAGS_ALL_FNS  := $(FTAGS_JAVA_FNS) $(FTAGS_C_FNS)

$(FTAGS_DIR):
	@mkdir -p $@

$(FTAGS_FNS_DIR): | $(FTAGS_DIR)
	@mkdir -p $@

.PHONY: ftags _ftags_fns

ftags: | $(FTAGS_FNS_DIR)
	@[ -z "$(_IS_INC)" ] || $(_INC_MAKE) $@

FTAGS_KNOWN_NAMES := $(FTAGS_DIR)/known_names.tsv

_cold_ftags: | $(FTAGS_FNS_DIR)
	@echo "[ftags] Scanning $(words $(FTAGS_ALL_FNS)) ctags indexes for function and method ranges (-j$(FTAGS_JOBS)) ..."
	@$(MAKE) --no-print-directory -s -j$(FTAGS_JOBS) -C $(BTAGS_DIR) \
	    CTAGS_BIN="$(CTAGS_BIN)" \
	    ROOT="$(ROOT)" \
	    FTAGS_DIR="$(FTAGS_DIR)" \
	    FTAGS_FNS_DIR="$(FTAGS_FNS_DIR)" \
	    GEN_FTAGS_SH="$(GEN_FTAGS_SH)" \
	    _ftags_fns; printf "\r\033[K" >&2
	@if [ -z "$$(find $(FTAGS_FNS_DIR) -name '*.fns' \
	               -newer $(FTAGS_ALL_FILE) 2>/dev/null | head -1)" ] \
	      && [ -f $(FTAGS_ALL_FILE) ]; then \
	    echo "[ftags] Up to date."; \
	else \
	    $(FTAGS_DIR)/ftags_merge.sh \
	        $(FTAGS_FNS_DIR) $(FTAGS_DIR_OUT); \
	    if ls $(FTAGS_DIR)/[a-z_].tsv >/dev/null 2>&1; then \
	        awk -F'\t' '{print $$1}' $(FTAGS_DIR)/[a-z_].tsv \
	            | sort -u > $(FTAGS_KNOWN_NAMES); \
	    else \
	        : > $(FTAGS_KNOWN_NAMES); \
	    fi; \
	fi

_ftags_fns: $(FTAGS_ALL_FNS)

# Per-Java-component rule — pass the com/ dir, ftags.sh recurses with ctags -R.
# The output .fns path is passed as arg 4 so ftags.sh can do cold/warm detection.
define FTAGS_JAVA_FNS_RULE
$(call java_comp_to_fns,$(1)): $(1)/src $(GEN_FTAGS_SH) | $(FTAGS_FNS_DIR)
	@printf "\r\033[K[ftags]   $(notdir $(1)) (Java)" >&2
	@$(GEN_FTAGS_SH) $(1)/src $(ROOT) $(CTAGS_BIN) $$@ > $$@.tmp \
	  && { [ -s $$@.tmp ] && mv $$@.tmp $$@ || { rm -f $$@.tmp; touch $$@; }; } \
	  || { rm -f $$@.tmp; echo "[ftags] WARNING: failed $(notdir $(1))" ; touch $$@ ; }
endef
$(foreach d,$(JAVA_COMP_DIRS),$(eval $(call FTAGS_JAVA_FNS_RULE,$(d))))

# Per-C-subdir rule — pass the subdir, ftags.sh recurses with ctags -R.
# The output .fns path is passed as arg 4 so ftags.sh can do cold/warm detection.
define FTAGS_C_FNS_RULE
$(call c_subdir_to_fns,$(1)): $(1) $(GEN_FTAGS_SH) | $(FTAGS_FNS_DIR)
	@printf "\r\033[K[ftags]   src/$(notdir $(1)) (C)" >&2
	@$(GEN_FTAGS_SH) $(1) $(ROOT) $(CTAGS_BIN) $$@ > $$@.tmp \
	  && { [ -s $$@.tmp ] && mv $$@.tmp $$@ || { rm -f $$@.tmp; touch $$@; }; } \
	  || { rm -f $$@.tmp; echo "[ftags] WARNING: failed src/$(notdir $(1))" ; touch $$@ ; }
endef
$(foreach d,$(C_SRC_SUBDIRS),$(eval $(call FTAGS_C_FNS_RULE,$(d))))

# ===========================================================================
# stags — Structure Tags (classes, enums, interfaces, structs, typedefs, macros)
#
# Reads the ctags/parts/ partial files directly — one awk process per
# component, all running in parallel under make -j$(CTAGS_JOBS).
# No need to wait for the merge into btags/ctags/tags.
#
# Phase 1: parallel — gen_stags.sh reads each ctags/parts/*.tags and writes
#          stags/parts/stags_<comp>/<letter>.tsv
# Phase 2: serial  — gen_stags_all.sh merges parts/stags_*/ into flat
#          stags/<letter>.tsv at the stags root (no stags_all/ subdirectory)
#
# Search priority for Bob:
#   1. stags/parts/stags_<comp>/<letter>.tsv  — when component is known
#   2. stags/<letter>.tsv                     — flat cross-component fallback
# ===========================================================================

# A per-part stamp file records that the parts/stags_<comp>/ directory is up to date.
# Naming: ctags/parts/java_sv.tags → stags/.stamps/java_sv.stamp
STAGS_STAMPS_DIR := $(STAGS_DIR)/.stamps
part_to_stag_stamp = $(STAGS_STAMPS_DIR)/$(basename $(notdir $(1))).stamp
STAGS_STAMPS := $(foreach p,$(CTAGS_ALL_PARTS),$(call part_to_stag_stamp,$(p)))

# Sentinel: flat index is up to date when this file exists and is newer than
# all stamps.
STAGS_SENTINEL := $(STAGS_DIR)/a.tsv

.PHONY: stags _stags_stamps

$(STAGS_DIR):
	@mkdir -p $@

$(STAGS_STAMPS_DIR): | $(STAGS_DIR)
	@mkdir -p $@

stags: | $(STAGS_STAMPS_DIR)
	@[ -z "$(_IS_INC)" ] || $(_INC_MAKE) $@

_cold_stags: | $(STAGS_STAMPS_DIR)
	@echo "[stags] Building Bob-specific index from $(words $(CTAGS_ALL_PARTS)) ctags indexes for types, structs and enums (-j$(CTAGS_JOBS)) ..."
	@$(MAKE) --no-print-directory -s -j$(CTAGS_JOBS) -C $(BTAGS_DIR) \
	    ROOT="$(ROOT)" \
	    STAGS_DIR="$(STAGS_DIR)" \
	    STAGS_STAMPS_DIR="$(STAGS_STAMPS_DIR)" \
	    GEN_STAGS_SH="$(GEN_STAGS_SH)" \
	    _stags_stamps; printf "\r\033[K" >&2
	@if [ -n "$$(find $(STAGS_STAMPS_DIR) -name '*.stamp' \
	               -newer $(STAGS_SENTINEL) 2>/dev/null | head -1)" ] \
	      || [ ! -f $(STAGS_SENTINEL) ]; then \
	    if ls $(STAGS_DIR)/parts/stags_*/[a-z_].tsv >/dev/null 2>&1; then \
	        bash $(GEN_STAGS_ALL_SH) $(STAGS_DIR); \
	    else \
	        : > $(STAGS_SENTINEL); \
	    fi; \
	else \
	    echo "[stags] Up to date."; \
	fi

define STAGS_STAMP_RULE
$(call part_to_stag_stamp,$(1)): $(1) $(GEN_STAGS_SH) | $(STAGS_STAMPS_DIR)
	@printf "\r\033[K[stags]   $(patsubst c_src_%,% (C+H),$(patsubst java_%,% (Java),$(basename $(notdir $(1)))))" >&2
	@bash $(GEN_STAGS_SH) $(ROOT) $(1) $(STAGS_DIR); touch $$@
endef

$(foreach p,$(CTAGS_ALL_PARTS),$(eval $(call STAGS_STAMP_RULE,$(p))))

_stags_stamps: $(foreach p,$(CTAGS_ALL_PARTS),$(call part_to_stag_stamp,$(p)))

# ===========================================================================
# ltags — Line-range index (one .range file per source file)
#
# Pivots the ftags .fns files into (file, start, end, qualified_name) order
# so that gen_xtags.sh can answer "which method contains line N?" with a
# simple range lookup rather than brace-counting.
#
# One gen_ltags.sh invocation per component .fns file splits all source files
# for that component in a single awk pass — ~50 invocations total instead of
# one per source file (~21,000).
#
# Build: depends on the component .fns files from ftags — no ctags, no source
# reads, just a column pivot + sort.  Runs after ftags, before xtags.
# ===========================================================================

LTAGS_DIR      := $(BTAGS_DIR)/ltags
LTAGS_STAMPS_DIR := $(LTAGS_DIR)/.stamps
GEN_LTAGS_SH   := $(LTAGS_DIR)/gen_ltags.sh

# Map a source file path to its .range file (runtime arg to gen_xtags.sh).
src_to_range = $(LTAGS_DIR)/$(subst /,_,$(subst $(ROOT)/,,$(1))).range

# Map a source file to its component .fns file (prerequisite for ltags/xtags).
# Java source under <comp>/com/... → java_<comp>.fns
# C source under src/user/<subdir>/... → c_src_<subdir>.fns
src_to_fns = $(FTAGS_FNS_DIR)/$(if $(filter $(ROOT)/src/%,$(1)),c_src_$(firstword $(subst /, ,$(subst $(ROOT)/src/user/,,$(1)))),java_$(firstword $(subst /, ,$(subst $(ROOT)/,,$(1))))).fns

# Map a .fns file to its ltags stamp file.
fns_to_ltags_stamp = $(LTAGS_STAMPS_DIR)/$(basename $(notdir $(1))).stamp

LTAGS_FNS_STAMPS := $(foreach f,$(FTAGS_ALL_FNS),$(call fns_to_ltags_stamp,$(f)))

$(LTAGS_DIR):
	@mkdir -p $@

$(LTAGS_STAMPS_DIR): | $(LTAGS_DIR)
	@mkdir -p $@

.PHONY: ltags _ltags_stamps

ltags: | $(LTAGS_STAMPS_DIR)
	@[ -z "$(_IS_INC)" ] || $(_INC_MAKE) $@

_cold_ltags: | $(LTAGS_STAMPS_DIR)
	@echo "[ltags] Building line-range index from $(words $(FTAGS_ALL_FNS)) .fns files (-j$(FTAGS_JOBS)) ..."
	@$(MAKE) --no-print-directory -s -j$(FTAGS_JOBS) -C $(BTAGS_DIR) \
	    LTAGS_DIR="$(LTAGS_DIR)" \
	    LTAGS_STAMPS_DIR="$(LTAGS_STAMPS_DIR)" \
	    GEN_LTAGS_SH="$(GEN_LTAGS_SH)" \
	    _ltags_stamps; \
	 _count=$$(ls $(LTAGS_DIR)/*.range 2>/dev/null | wc -l | tr -d ' '); \
	 printf "\r\033[K[ltags] Made Bob-friendly line-range index  ($$_count source files indexed)\n"

_ltags_stamps: $(LTAGS_FNS_STAMPS)

define LTAGS_FNS_RULE
$(call fns_to_ltags_stamp,$(1)): $(1) $(GEN_LTAGS_SH) | $(LTAGS_STAMPS_DIR)
	@printf "\r\033[K[ltags]   $(basename $(notdir $(1)))" >&2
	@bash $(GEN_LTAGS_SH) $(1) $(LTAGS_DIR) \
	  && touch $$@ \
	  || { echo "[ltags] WARNING: failed to split $(notdir $(1))" ; touch $$@ ; }
endef

$(foreach f,$(FTAGS_ALL_FNS),$(eval $(call LTAGS_FNS_RULE,$(f))))

# ===========================================================================
# xtags — Callers index (name-based, range-lookup driven)
#
# Per-file .calls fragments produced by gen_xtags.sh (which reads the
# corresponding ltags .range file for enclosing-method lookup).
# xtags_merge.sh aggregates into btags/xtags/<letter>.tsv.
#
# Lookup: grep '^methodName' btags/xtags/<letter>.tsv || true
# ===========================================================================

XTAGS_DIR          := $(BTAGS_DIR)/xtags
XTAGS_ALL_FILE     := $(XTAGS_DIR)/l.tsv
XTAGS_CALLS_DIR    := $(XTAGS_DIR)/.calls
XTAGS_STAMPS_DIR   := $(XTAGS_DIR)/.stamps
GEN_XTAGS_COMP_SH  := $(XTAGS_DIR)/gen_xtags_comp.sh
XTAGS_MERGE_SH     := $(XTAGS_DIR)/xtags_merge.sh

# One stamp per component .fns file (same set as ftags/ltags).
fns_to_xtags_stamp = $(XTAGS_STAMPS_DIR)/$(basename $(notdir $(1))).stamp
XTAGS_FNS_STAMPS := $(foreach f,$(FTAGS_ALL_FNS),$(call fns_to_xtags_stamp,$(f)))

$(XTAGS_DIR):
	@mkdir -p $@

$(XTAGS_CALLS_DIR): | $(XTAGS_DIR)
	@mkdir -p $@

$(XTAGS_STAMPS_DIR): | $(XTAGS_DIR)
	@mkdir -p $@

.PHONY: xtags _xtags_stamps

xtags: | $(XTAGS_CALLS_DIR) $(XTAGS_STAMPS_DIR)
	@[ -z "$(_IS_INC)" ] || $(_INC_MAKE) $@

_cold_xtags: | $(XTAGS_CALLS_DIR) $(XTAGS_STAMPS_DIR)
	@echo "[xtags] Scanning $(words $(FTAGS_ALL_FNS)) ctags indexes for call sites (-j$(FTAGS_JOBS)) ..."
	@$(MAKE) --no-print-directory -s -j$(FTAGS_JOBS) -C $(BTAGS_DIR) \
	    ROOT="$(ROOT)" \
	    XTAGS_DIR="$(XTAGS_DIR)" \
	    XTAGS_CALLS_DIR="$(XTAGS_CALLS_DIR)" \
	    XTAGS_STAMPS_DIR="$(XTAGS_STAMPS_DIR)" \
	    FTAGS_FNS_DIR="$(FTAGS_FNS_DIR)" \
	    GEN_XTAGS_COMP_SH="$(GEN_XTAGS_COMP_SH)" \
	    _xtags_stamps; printf "\r\033[K" >&2
	@if [ -z "$$(find $(XTAGS_CALLS_DIR) -name '*.calls' \
	               -newer $(XTAGS_ALL_FILE) 2>/dev/null | head -1)" ] \
	      && [ -f $(XTAGS_ALL_FILE) ]; then \
	    echo "[xtags] Up to date."; \
	else \
	    bash $(XTAGS_MERGE_SH) $(XTAGS_CALLS_DIR) $(XTAGS_DIR) $(FTAGS_DIR); \
	fi

_xtags_stamps: $(XTAGS_FNS_STAMPS)

# One rule per component .fns file.  The stamp depends on the .fns file so a
# component is re-processed whenever its ftags data changes.
define XTAGS_FNS_RULE
$(call fns_to_xtags_stamp,$(1)): $(1) $(GEN_XTAGS_COMP_SH) | $(XTAGS_CALLS_DIR) $(XTAGS_STAMPS_DIR)
	@printf "\r\033[K[xtags]   $(basename $(notdir $(1)))" >&2
	@bash $(GEN_XTAGS_COMP_SH) $(1) $(ROOT) $(ROOT) $(XTAGS_CALLS_DIR) \
	  && touch $$@ \
	  || { echo "[xtags] WARNING: failed $(notdir $(1))" ; touch $$@ ; }
endef

$(foreach f,$(FTAGS_ALL_FNS),$(eval $(call XTAGS_FNS_RULE,$(f))))

# ===========================================================================
# clean
# ===========================================================================

.PHONY: clean-ctags clean-ftags clean-stags clean-ltags clean-xtags

clean: clean-ctags clean-ftags clean-stags clean-ltags clean-xtags

clean-ctags:
	@rm -f $(CTAGS_FILE)
	@rm -rf $(CTAGS_PARTS_DIR)
	@rm -rf $(CTAGS_DIR)/.stamps

clean-ftags:
	@rm -rf $(FTAGS_DIR)/parts
	@find $(FTAGS_DIR) -maxdepth 1 -name '*.tsv' | xargs rm -f
	@rm -f $(FTAGS_KNOWN_NAMES)
	@rm -rf $(FTAGS_FNS_DIR)

clean-stags:
	@rm -rf $(STAGS_DIR)/parts
	@find $(STAGS_DIR) -maxdepth 1 -name '*.tsv' | xargs rm -f
	@rm -rf $(STAGS_STAMPS_DIR)

clean-ltags:
	@find $(LTAGS_DIR) -maxdepth 1 -name '*.range' -delete 2>/dev/null || true
	@rm -rf "$(LTAGS_STAMPS_DIR)"

clean-xtags:
	@find $(XTAGS_DIR) -maxdepth 1 -name '*.tsv' -delete 2>/dev/null || true
	@rm -rf "$(XTAGS_CALLS_DIR)"
	@rm -rf "$(XTAGS_STAMPS_DIR)"
