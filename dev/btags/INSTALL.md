# INSTALL — Adding btags to a project

Step-by-step guide to add the btags index suite (ctags, ftags, stags, ltags,
xtags) to any Java/C project.

btags supports two installation modes — choose whichever suits your workflow:

| Mode | When to use |
|---|---|
| **Embedded** | You own the project repo and are happy to add a `btags/` subdirectory to it. |
| **Standalone** | The project is shared / read-only, or you prefer to keep btags entirely separate. btags lives anywhere and is pointed at the project via `BTAGS_SOURCE`. |

---

## Prerequisites

- **Universal Ctags ≥ 5.9** — must be the Universal Ctags fork (not Exuberant Ctags).
  The `end:` field used by btags was added in Universal Ctags.
  - macOS: `brew install universal-ctags`
  - Linux / WSL (Debian/Ubuntu): `sudo apt install universal-ctags`
  - RHEL / CentOS / Fedora: universal-ctags lives in the EPEL repository:
    ```sh
    sudo dnf install -y epel-release
    sudo dnf --enablerepo=epel install -y ctags
    ```
  - Verify: `ctags --version` must say "Universal Ctags"

- **bash ≥ 3.2**, **awk** (POSIX/BSD/GNU), **sort**, **GNU Make**

### Windows (WSL)

The build scripts require a POSIX shell environment. On Windows, use WSL:

```powershell
# PowerShell as Administrator — one-time
wsl --install
```

Inside WSL:

```sh
sudo apt install make universal-ctags
```

Clone and work inside the WSL filesystem (`~/myproject`), **not** `/mnt/c/...`.
Cross-filesystem I/O makes `make -j` very slow and risks CRLF corruption.

---

## Mode A — Embedded (btags inside the project)

Use this when you can add files to the project repository.  The indexes live
alongside the source so any team member can rebuild them with a single command.

### A1 — Copy btags into your project

```sh
cp -r path/to/btags-repo/  your-project/btags/
```

The resulting layout:

```
your-project/
└── btags/
    ├── GNUmakefile               ← main build orchestrator (edit this — see A2)
    ├── GNUmakefile.incremental   ← incremental logic (auto-activated, no edits needed)
    ├── find_stale.sh             ← stale-detection helper (serves all index stages)
    ├── .bob/
    │   └── rules/
    │       └── search-rules.md   ← Bob index rules (copy to ~/bob/rules/ — see A5)
    ├── ctags/
    │   ├── GNUmakefile
    │   ├── gen_srccache.sh       ← source-cache helper (no edits needed)
    │   ├── ctags_part.sh         ← per-component ctags runner
    │   └── merge_ctags.sh        ← merges per-component partials
    ├── ftags/
    │   ├── GNUmakefile
    │   ├── ftags.sh              ← per-component function-tag generator
    │   └── ftags_merge.sh        ← assembles component/letter TSVs + hints.tsv
    ├── stags/
    │   ├── GNUmakefile
    │   ├── gen_stags.sh          ← builds structure tags for one component
    │   └── gen_stags_all.sh      ← assembles the flat cross-component stags index
    ├── ltags/
    │   └── gen_ltags.sh          ← pivots ftags .fns into line-range files
    └── xtags/
        ├── GNUmakefile
        ├── gen_xtags.sh          ← per-file callers scanner
        ├── gen_xtags_comp.sh     ← per-component callers scanner
        └── xtags_merge.sh        ← assembles flat callers index
```

Make the shell scripts executable:

```sh
find your-project/btags -name '*.sh' | xargs chmod +x
```

### A2 — Adapt btags/GNUmakefile for your project

Open `btags/GNUmakefile`. Find the source-discovery blocks near the top and
edit them to match your source layout.

#### Java sources

The default discovers every top-level directory containing a `com/` subtree:

```makefile
JAVA_COMP_DIRS := $(shell \
  { find $(ROOT) -maxdepth 2 -name "com" -type d 2>/dev/null; \
    find $(ROOT) -maxdepth 5 -path "*/src/main/java/com" -type d 2>/dev/null; } \
  | grep -v '/be_decaf/\|/JavaParser/\|/cli_autogen/\|/ip_quorum/\|/bin/\|/btags/' \
  | sed 's|/com$$||' | sort)
```

Add exclusions to the `grep -v` patterns for generated code, vendor directories,
or test stubs that should not be indexed.

For Maven-layout projects (`src/main/java/com/...`), the second `find` arm
already handles it.  For non-Maven flat layouts, the first arm handles it.

#### C sources

The default scans all subdirectories of `src/user/`:

```makefile
C_SRC_SUBDIRS := $(shell find $(ROOT)/src/user -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort)
```

Adapt to your C source tree location.  For **Java-only projects**, delete the
`C_SRC_SUBDIRS` and `C_SOURCES` variables and remove them from `CTAGS_ALL_PARTS`
and `ALL_SOURCES`.

### A3 — Generated output is already gitignored

Each btags subdirectory ships with its own `.gitignore`.  **No changes to your
project's `.gitignore` are needed.**

| Directory | Ignored patterns |
|---|---|
| `btags/ctags/` | `.stamps/`, `parts/`, `tags`, `.srccache.mk` |
| `btags/ftags/` | `.fns/`, `parts/`, `hints.tsv`, `known_names.tsv`, `[_a-z].tsv` |
| `btags/stags/` | `.stamps/`, `parts/`, `[_a-z].tsv` |
| `btags/ltags/` | `.stamps/`, `*.range` |
| `btags/xtags/` | `.calls/`, `.stamps/`, `[_a-z].tsv` |

### A4 — Build and verify

```sh
cd your-project/btags && make tags
```

### A5 — Tell Bob about the indexes

```sh
mkdir -p ~/bob/rules
cp your-project/btags/.bob/rules/search-rules.md ~/bob/rules/search-rules.md
```

Bob reads all Markdown files under `~/bob/rules/` at session start.  Re-copy
whenever you pull a new version of btags.

---

## Mode B — Standalone (btags outside the project)

Use this when the project is shared or read-only, or you simply prefer not to
add files to the project tree.  btags lives in its own directory and is told
where the project is via the `BTAGS_SOURCE` environment variable.  **Nothing
is added to or changed in the host project.**

### B1 — Clone or place btags anywhere

```sh
git clone <btags-repo-url> ~/tools/btags
# or any other location — ~/btags, /opt/btags, etc.
```

Make the shell scripts executable:

```sh
find ~/tools/btags -name '*.sh' | xargs chmod +x
```

### B2 — Set BTAGS_SOURCE

`BTAGS_SOURCE` must be the absolute path to the root of the project you want
to index (the directory that contains your source components).

Set it persistently in your shell profile:

```sh
# ~/.bashrc or ~/.zshrc
export BTAGS_SOURCE=/path/to/your-project
```

Or pass it as a one-shot make variable when you don't want a persistent
environment variable:

```sh
make -C ~/tools/btags tags BTAGS_SOURCE=/path/to/your-project
```

### B3 — Adapt the source-discovery filters (same as A2)

Open `~/tools/btags/GNUmakefile` and edit the `JAVA_COMP_DIRS` and
`C_SRC_SUBDIRS` discovery blocks (see [A2](#a2--adapt-btagsgnumakefile-for-your-project))
to match your project's source layout.

> **Note:** in standalone mode the source-discovery `grep -v` filter already
> excludes `/btags/` paths, so there is no risk of btags indexing itself.

### B4 — Generated output stays inside btags

All index artifacts (`.tags`, `.tsv`, `.range`, `.calls`, stamps) are written
into the btags directory itself — `~/tools/btags/ctags/`, `~/tools/btags/ftags/`,
etc.  The host project's file tree is never touched.

Each subdirectory's `.gitignore` covers its own generated output.  If your
btags directory is tracked in git, no extra ignore rules are needed.

### B5 — Build and verify

```sh
cd ~/tools/btags && make tags
# or with a one-shot source path:
make -C ~/tools/btags tags BTAGS_SOURCE=/path/to/your-project
```

### B6 — Tell Bob about the indexes

```sh
mkdir -p ~/bob/rules
cp ~/tools/btags/.bob/rules/search-rules.md ~/bob/rules/search-rules.md
```

Bob reads all Markdown files under `~/bob/rules/` at session start.  Re-copy
whenever you pull a new version of btags.

> **Important:** in standalone mode the index files live inside `~/tools/btags/`
> (or wherever btags is installed), **not** inside the project.  Update
> `~/bob/rules/search-rules.md` to reflect the correct index paths if you
> customised the btags location.

---

## Expected build output

```
[ctags] Using ctags to scan N Java components + M C subdirs (-j20) ...
[ctags] Merged N parallel tag files → btags/ctags/tags  (index count: NNNNNN)
[ftags] Scanning N ctags indexes for function and method ranges (-j20) ...
[ftags] Wrote Bob-tuned function/method lookup index from N components  (NNN,NNN ranges)
[stags] Building Bob-specific index from N ctags indexes ...
[stags] Created Bob-specific type/struct/enum lookup index  (NNN,NNN entries)
[ltags] Building line-range index from N .fns files ...
[ltags] Made Bob-friendly line-range index  (NNNN source files indexed)
[xtags] Scanning N ctags indexes for call sites ...
[xtags] Mapped Bob-friendly call-site lookup index  (NNN,NNN call graph links)
```

Verify lookups work:

```sh
# Find a Java method by simple name
grep '^processOrder' btags/ftags/p.tsv

# Find a class
grep '^OrderService' btags/stags/o.tsv

# Find a C struct
grep '^order_t' btags/stags/o.tsv

# Find all callers of a method
grep '^processOrder' btags/xtags/p.tsv
```

---

## Ongoing use

| Action | Command |
|---|---|
| Rebuild after code changes | `make tags` (incremental — only changed components re-processed) |
| Rebuild ctags only | `make ctags` |
| Rebuild ftags only | `make ftags` |
| Rebuild stags only | `make stags` |
| Rebuild ltags only | `make ltags` |
| Rebuild xtags only | `make xtags` |
| Full rebuild from scratch | `make clean && make tags` |
| Full ctags rebuild only | `make clean-ctags && make ctags` |
| Full ftags rebuild only | `make clean-ftags && make ftags` |
| Full xtags rebuild only | `make clean-xtags && make xtags` |

Incremental builds are fast: only components with source files newer than their
stamp are re-processed.  A typical one-file edit completes in under 60 seconds.
