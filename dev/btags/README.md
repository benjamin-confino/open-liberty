# btags — Bob Tags

Bob spends a lot of time and tokens navigating around a large codebase, often grepping
through files searching for definitions and callers. Bob tags 'btags' dramatically speeds
up these incredibly common internal sub-tasks and significantly reduces tokens used too.

Just ```git clone git@github.ibm.com:spectrum-virtualize/btags.git```
ask Bob to follow INSTALL.md and it will do the rest. You can have btags as a folder in your
project or off to the side.

Btags makes pre-built code-navigation indexes for any Java/C project, optimised for
Bob or similar AI coding assistants (and humans) who need to jump straight to a method body,
class definition, or call site without trawling through the entire source tree.

```
btags/
  GNUmakefile              Main build orchestrator
  GNUmakefile.incremental  Incremental rebuild logic (auto-activated after first build)
  find_stale.sh            Stale-component detection helper (serves all index stages)
  .bob/rules/
    search-rules.md        Rules for AI assistants using the indexes
  ctags/
    gen_srccache.sh        Source-file cache helper
    …                      Universal Ctags scripts + per-component partial files
  ftags/                   Function Tags — methods and C functions with exact line ranges
  stags/                   Structure Tags — classes, enums, structs, typedefs, macros
  ltags/                   Line-range index — pivoted from ftags for xtags lookups
  xtags/                   Callers index — which methods call a given method
  README.md                You are here
```

All index output is **generated** — git-ignored and built locally (see
[Building](#building)).  Only scripts, makefiles, and docs are tracked in git.

---

## The problem btags solves

Large Java/C repos make common navigation tasks expensive for Bob: finding a
method body, finding a class definition, finding an enum definition, finding a
field declaration, finding callers of a method, and narrowing the read to the
exact lines that matter. Bob performs these sub-tasks hundreds of times while
reasoning about the code it is operating on, so token use and time spent
compound quickly. In a subset benchmark on `~/side/Lodestone/btags`,
targeted index lookups completed in about **20–43 ms** while whole-repo
`grep` for the same kind of symbol took about **36–41 s** — roughly
**850×–1,700× faster** for these common tasks. Because `ftags` stores exact
method ranges, Bob can read tens of lines instead of entire files, which cuts
both token usage and wasted context during routine code navigation. In practice,
that means saving hundreds to thousands of input tokens on repeated “find it,
open it, scroll it” workflows across large files.

| Task | Without btags | With btags | Benchmark |
|---|---|---|---|
| Find a method body | Whole-repo text search, noisy matches, then manual scan for the method end | Look up `ftags/<letter>.tsv`, then read only the exact `start-end` lines | <span style="white-space: nowrap;"><code>validate</code>: <span style="color:#b42318; font-weight:600;">36.5 s</span> → <span style="color:#17603a; font-weight:600;">23.6 ms</span> (<strong>~1,546× faster, ~98% fewer tokens</strong>)</span> |
| Find a class / enum / field declaration | Whole-repo text search across source files | Look up `stags/<letter>.tsv` for the declaration line | <span style="white-space: nowrap;"><code>Anchor</code>: <span style="color:#b42318; font-weight:600;">36.5 s</span> → <span style="color:#17603a; font-weight:600;">40.7 ms</span> (<strong>~897× faster, ~99% fewer tokens</strong>)</span> |
| Find callers of a method | Whole-repo text search for call text, then inspect hits manually | Look up `xtags/<letter>.tsv` for direct call sites | <span style="white-space: nowrap;"><code>validate</code> callers: <span style="color:#b42318; font-weight:600;">36.5 s</span> → <span style="color:#17603a; font-weight:600;">43.0 ms</span> (<strong>~849× faster, ~99% fewer tokens</strong>)</span> |
| Reduce tokens and context | Open large files and scan manually | Read only the exact method range or declaration line returned by the index | <span style="color:#17603a; font-weight:600;">Read only the returned lines</span> instead of loading whole files (<strong>~98–99% fewer tokens</strong>) |

---

## Indexes

### ctags — Universal Ctags index

**Location:** `ctags/tags`

Standard Vi/Emacs/LSP-compatible Universal Ctags file.  Covers every Java
class, method, field, enum, and interface, and every C function, struct,
typedef, and macro.

The build splits work into **per-component parallel partitions** — one per Java
component directory plus one per `src/user/` subdirectory.  All run concurrently
under `make -j20`.  Per-component partial files land in `ctags/parts/` and are
merged by `merge_ctags.sh` using `sort --merge` (O(N) since each partial is
already sorted).

Incremental: only the component(s) whose sources are newer than their partial
`.tags` file are re-scanned; the merge re-runs only if any partial changed.
A component stamp in `ctags/.stamps/` records the last scan time.

---

### ftags — Function Tags

**Location:**
- Per-component: `ftags/parts/ftags_<comp>/<letter>.tsv`
- Cross-component (flat): `ftags/<letter>.tsv`
- Hint index: `ftags/hints.tsv` — maps simple method name → component list

Every Java method and every C function is recorded with its **exact start and
end line** so only the method body needs to be loaded.

#### Format — 3-column TSV

```
key_name<TAB>file:start-end<TAB>signature
```

Each Java method is stored **twice**: once keyed by its fully-qualified name
(outer-class chain) and once by its simple (last-segment) name.

```
OrderService.processOrder    myapp/src/main/java/com/example/OrderService.java:120-145    -
processOrder                 myapp/src/main/java/com/example/OrderService.java:120-145    -
parse_record                 src/util/parser.c:300-340                                    -
getOrderId                   myapp/src/main/java/com/example/OrderService.java:80-82      ()
```

- Qualified row: filed under the first letter of the outer class (`o.tsv` above).
- Simple-name row: filed under the first letter of the method name (`p.tsv` above).
- C functions: stored once (simple name == qualified name).
- Signature column: `()`, `(int n)`, etc. for Java; `-` for C or when absent.

#### Lookup recipe

```sh
# Step 0 — unknown component: find it via hints
grep '^processOrder' btags/ftags/hints.tsv || true
# → processOrder    orders,api

# Step 1 — known class + component
grep '^OrderService\..*processOrder' btags/ftags/parts/ftags_orders/o.tsv || true

# Step 2 — known component, unknown class
grep '^processOrder' btags/ftags/parts/ftags_orders/p.tsv || true

# Step 3 — unknown component (no hints result)
grep '^processOrder' btags/ftags/p.tsv || true
```

#### How ftags is built

`ftags.sh` runs ctags **per component** in parallel, writing a `.fns` file per
component to `ftags/.fns/`.  The incremental build detects which components have
source files newer than their `.fns` file and re-runs only those.
`ftags_merge.sh` merges all `.fns` files into the per-letter TSVs under
`parts/ftags_<comp>/` and the flat root.

---

### stags — Structure Tags

**Location:**
- Per-component: `stags/parts/stags_<comp>/<letter>.tsv`
- Cross-component (flat): `stags/<letter>.tsv`

Covers everything ftags does **not**: class definitions, interfaces, enums, C
structs, typedefs, macros, unions, and Java field declarations.

#### Format — 3-column TSV

```
qualified_name<TAB>file:line<TAB>kind
```

```
OrderService                myapp/src/main/java/com/example/OrderService.java:42      class
OrderService.Status         myapp/src/main/java/com/example/OrderService.java:88      enum
order_t                     src/util/order.h:55                                       struct
OrderService.id             myapp/src/main/java/com/example/OrderService.java:50      field
```

Kinds: `class`, `enum`, `interface`, `struct`, `typedef`, `macro`, `union`, `field`.

#### Lookup recipe

```sh
# Known component
grep '^order_t' btags/stags/parts/stags_src/o.tsv || true

# Unknown component
grep '^order_t' btags/stags/o.tsv || true
```

#### How stags is built

`gen_stags.sh` reads each `ctags/parts/*.tags` file and filters it to structural
kinds in a single awk pass — **no second ctags invocation**.
`gen_stags_all.sh` merges all component parts into the flat root TSVs.

The incremental build uses the same source-level staleness check as ctags
(any `.java`/`.c`/`.h` newer than the stags stamp) so stags always stays in sync
with ctags without any timestamp-race issues.

---

### ltags — Line-range index

**Location:** `ltags/<comp>.range` — one file per component

A pivot of the ftags `.fns` data into `(file, start, end, qualified_name)` order.
Used internally by `gen_xtags_comp.sh` to answer "which method contains line N?"
in a single sorted lookup — no brace-counting, no source re-reads.

Not normally queried directly by users; it exists to make xtags fast and accurate.

---

### xtags — Callers index

**Location:** `xtags/<letter>.tsv`

Name-based call-site index: for every method/function call in the codebase,
records the callee name, the qualified name of the enclosing caller, and the
file:line of the call site.

#### Format — 3-column TSV

```
callee_name<TAB>caller_qualified_name<TAB>file:line
```

```
processOrder    OrderController.handleRequest    myapp/src/main/java/com/example/OrderController.java:75
```

#### Lookup recipe

```sh
grep '^processOrder' btags/xtags/p.tsv || true
```

#### Limitations

- **Call sites only** — xtags records method/function invocations.  It does
  **not** index enum value references, field accesses, type usages, annotation
  references, or constant reads.  Use `grep -rn` for those.
- Name-based heuristic — cannot resolve overloads or dynamic dispatch.
- Very common names (`get`, `set`) return many results; cross-reference with
  ftags to filter by class.

---

## Building

To install btags into another project, follow [`INSTALL.md`](INSTALL.md) or ask
Bob to run the [`INSTALL.md`](INSTALL.md) plan step by step. That gives you the
project-specific copy, `GNUmakefile` edits, `.gitignore` updates, build, and
`~/bob/rules/search-rules.md` wiring in the documented order.

Run from the project root or from inside `btags/`:

```sh
make tags          # build all indexes  (~2 min first time, fast incremental after)
make ctags         # ctags only
make ftags         # ftags only  (incremental — only changed components re-processed)
make stags         # stags only  (reads ctags output — fast)
make ltags         # ltags only  (reads ftags .fns files)
make xtags         # xtags only  (reads ftags .fns + ltags .range files)
make clean         # wipe everything
make clean-ctags   # wipe ctags output for a full ctags rebuild
make clean-ftags   # wipe ftags stamps and output for a full ftags rebuild
make clean-stags   # wipe stags output folders
make clean-ltags   # remove all .range files
make clean-xtags   # remove all .calls files and merged TSVs
```

Parallelism can be tuned:

```sh
make tags CTAGS_JOBS=8 FTAGS_JOBS=8    # defaults are 20
```

### Incremental builds

After the first build, `make` automatically switches to
`GNUmakefile.incremental` for all subsequent runs.  The incremental make:

1. Detects which Java components / C subdirs have source files newer than their
   ctags stamp → re-runs ctags only for those.
2. Detects which components have source files newer than their ftags `.fns`
   file → re-runs ftags only for those.
3. Detects which components have source files newer than their stags stamp
   → re-runs stags only for those.
4. Detects which `.fns` files are newer than their ltags/xtags stamps
   → re-runs ltags/xtags only for those.

A typical one-file edit triggers a rescan of one component in all five stages
and completes in **under 30 seconds** on a 10-core machine.

### Typical timings (10-core MacBook Pro)

| Operation | Time |
|---|---|
| Cold build (all indexes) | ~2 min |
| Incremental (nothing changed) | ~7 s |
| Single-component Java edit | ~23 s |
| Single-component C edit | ~25 s |

### Prerequisites

| Tool | Where |
|---|---|
| `bash` ≥ 3.2 | macOS system bash is fine; Linux has bash 4+ |
| `awk` (POSIX) | macOS system awk works; so does gawk |
| `sort` | standard on all POSIX systems |
| `make` (GNU) | `brew install make` on macOS; `apt install make` on Linux |
| Universal Ctags ≥ 5.9 | `brew install universal-ctags` / `apt install universal-ctags` |

**No Python, no Perl, no Node, no compiled tools** beyond ctags itself.

### Windows (WSL)

The build scripts require a POSIX shell environment.  On Windows, use WSL:

**1. Install WSL** (PowerShell as Administrator):

```powershell
wsl --install
```

**2. Install prerequisites** (inside WSL):

```sh
sudo apt install make universal-ctags
```

**3. Clone inside the WSL filesystem** — do *not* work under `/mnt/c/`:

```sh
# Good — native WSL filesystem, fast I/O
cd ~
git clone <remote-url> myproject
```

Working under `/mnt/c/` causes slow `make -j` parallelism and can corrupt shell
scripts with Windows line endings (`\r\n`).  If you have already cloned there:

```sh
git config core.autocrlf false
git rm --cached -r btags/
git checkout btags/
```

---

## Why shell and awk, not Python?

1. **Zero dependency install.** Every developer machine has bash and awk.
   Python version conflicts are a constant source of friction on large teams.

2. **It is usually faster for this workload.** btags mostly orchestrates
   external tools and performs linear text processing. A Python rewrite would
   still spend most of its time spawning `ctags`, `sort`, and `grep`, then add
   interpreter startup, object allocation, and per-line Python parsing overhead.
   `awk` streams rows in tight native code with constant memory, which is a
   better fit for hundreds of thousands of TSV/text lines.

3. **Transparency.** A 150-line shell script that calls ctags and pipes through
   awk is auditable by any C or Java developer without knowing Python idioms.

4. **Parallelism is free.** Both ctags and ftags use `make -j` directly —
   the same mechanism the rest of the build already uses.

5. **Streaming is natural.** awk processes files one line at a time with
   constant memory.  The ctags output for 20,000 source files is ~900,000 lines;
   an awk pipeline processes it in seconds without loading the whole file.

6. **Portability.** POSIX shell + awk runs identically on RHEL, macOS, and WSL;
   Python 3 minor-version differences sometimes cause subtle breakage.

---

## Design notes

### Incremental build: parse-time vs execution-time staleness

The key insight behind `GNUmakefile.incremental` is that GNU make evaluates
`$(shell ...)` calls at **parse time**, before any recipe runs.  Stages that
depend on output from an upstream stage in the same run (stags depends on ctags
output; ltags/xtags depend on ftags output) must therefore compute their
staleness check **inside the recipe body**, not at the top of the makefile.

All four dependent stages (stags, ltags, xtags, and ftags-merge) call
`find_stale.sh` at shell-execution time — after upstream has completed —
avoiding false "Up to date" results when upstream updated its output in the
same make invocation.

stags uses the same source-level staleness check as ctags (`ctags-java` /
`ctags-c` modes of `find_stale.sh`) rather than comparing `.tags` partial
mtimes, because ctags always touches both the `.tags` file and its stamp at the
same second — making a `.tags`-vs-stamp comparison always tie.

### Parallel ctags: per-component partitions + merge

`merge_ctags.sh` uses `sort --merge` — O(N) because each partial is already
sorted — and writes a canonical pseudo-tag header block.

`ctags_part.sh` supports three modes automatically:
- **Cold** (output absent): full recursive scan.
- **Warm/clean** (no content changes detected): exits 0 without touching the output.
- **Warm/incremental** (changes detected): runs ctags on changed files only and
  splice-merges new entries into the existing sorted output in an O(N+M) awk
  pass — no full re-sort.

### Cold-build stamp initialisation

After a cold build, `_cold_ctags` bulk-touches a stamp in `ctags/.stamps/` for
every `.tags` partial just written.  This ensures the incremental makefile finds
everything up to date on the very next run, rather than triggering a full rescan
because the stamp directory is empty.

### The `+q` extra entries and deduplication

ctags `--extras=+q` emits a second tag for every nested Java type using the
fully-qualified dotted name (e.g. `OrderService.Status` in addition to
`Status` with a `class:OrderService` scope field).  Both entries resolve
to the same output row in stags.  The final `sort -u` pass in `gen_stags.sh`
removes duplicates without any stateful deduplication logic.

### ctags tab-in-ex-pattern edge case

A handful of C functions have a tab character inside the ctags ex-search pattern
(e.g. a function whose definition starts `int\tadd_ordered`).  This shifts
subsequent columns.  `gen_stags.sh` handles this with a two-pass filter: an
early `$4` kind-letter check and a post-loop `kindword` check that recognises
bare kind words in any shifted column position.
