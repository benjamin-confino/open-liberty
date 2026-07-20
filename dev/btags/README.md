# btags — Bob Tags

Bob spends a lot of time and tokens navigating around a large codebase, often grepping
through files searching for definitions and callers. Bob tags 'btags' dramatically speeds
up these incredibly common internal sub-tasks and significantly reduces tokens used too.

Btags makes pre-built code-navigation indexes for any Java project, optimised for
Bob or similar AI coding assistants (and humans) who need to jump straight to a method body,
class definition, or call site without trawling through the entire source tree.

## Quickstart

1) Run `./gradlew -p btags tags` to build your tags
2) Use bob like normal

## Source structure

```
btags/
  build.gradle             Main build orchestrator (Gradle)
  settings.gradle
  buildSrc/                Java worker classes — compiled automatically by Gradle
    src/main/java/btags/
      CtagsPart.java       Per-component ctags runner
      FtagsComp.java       Per-component function-tag generator
      FtagsMerge.java      Assembles component/letter TSVs + hints.tsv
      LtagsComp.java       Pivots ftags .fns into line-range files
      MergeCtags.java      Merges per-component ctags partials
      SourceDiscovery.java Finds Java component source roots
      StagsComp.java       Builds structure tags for one component
      StagsAll.java        Assembles the flat cross-component stags index
      XtagsComp.java       Per-component callers scanner
      XtagsMerge.java      Assembles flat callers index
      Util.java            Shared utilities
  .bob/rules/
    search-rules.md        Rules for AI assistants using the indexes
  ctags/
    …                      Universal Ctags per-component partial files + merged tags
  ftags/                   Function Tags — methods with exact line ranges
  stags/                   Structure Tags — classes, enums, interfaces, fields
  ltags/                   Line-range index — pivoted from ftags for xtags lookups
  xtags/                   Callers index — which methods call a given method
  README.md                You are here
```

All index output is **generated** — git-ignored and built locally (see
[Building](#building)).  Only Java worker sources, Gradle build files, and docs are
tracked in git.

## Ongoing use

| Action | Command |
|---|---|
| Rebuild after code changes | `./gradlew -p btags tags` |
| Rebuild ctags only | `./gradlew -p btags ctags` |
| Rebuild ftags only | `./gradlew -p btags ftags` |
| Rebuild stags only | `./gradlew -p btags stags` |
| Rebuild ltags only | `./gradlew -p btags ltags` |
| Rebuild xtags only | `./gradlew -p btags xtags` |
| Full clean rebuild | `./gradlew -p btags clean tags` |
| Clean ctags output only | `./gradlew -p btags cleanCtags` |
| Clean ftags output only | `./gradlew -p btags cleanFtags` |
| Clean xtags output only | `./gradlew -p btags cleanXtags` |

All tasks run with `outputs.upToDateWhen { false }` so they always execute, but
the Java workers themselves perform incremental staleness checks internally —
only components with source files newer than their stamp are re-processed.
A typical one-file edit completes in under 60 seconds.

---

## The problem btags solves

Large Java repos make common navigation tasks expensive for Bob: finding a
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
that means saving hundreds to thousands of input tokens on repeated "find it,
open it, scroll it" workflows across large files.

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
class, method, field, enum, and interface.

The build splits work into **per-component parallel partitions** — one per Java
component directory.  All run concurrently across a configurable thread pool
(default: 20 threads).  Per-component partial files land in `ctags/parts/` and
are merged by `MergeCtags` using a priority-queue merge (O(N) since each partial
is already sorted).

Incremental: only the component(s) whose sources are newer than their stamp are
re-scanned; the merge re-runs on every build.  A component stamp in
`ctags/.stamps/` records the last scan time.

---

### ftags — Function Tags

**Location:**
- Per-component: `ftags/parts/ftags_<comp>/<letter>.tsv`
- Cross-component (flat): `ftags/<letter>.tsv`
- Hint index: `ftags/hints.tsv` — maps simple method name → component list

Every Java method is recorded with its **exact start and end line** so only the
method body needs to be loaded.

#### Format — 3-column TSV

```
key_name<TAB>file:start-end<TAB>signature
```

Each Java method is stored **twice**: once keyed by its fully-qualified name
(outer-class chain) and once by its simple (last-segment) name.

```
OrderService.processOrder    myapp/src/main/java/com/example/OrderService.java:120-145    -
processOrder                 myapp/src/main/java/com/example/OrderService.java:120-145    -
getOrderId                   myapp/src/main/java/com/example/OrderService.java:80-82      ()
```

- Qualified row: filed under the first letter of the outer class (`o.tsv` above).
- Simple-name row: filed under the first letter of the method name (`p.tsv` above).
- Signature column: `()`, `(int n)`, etc. for Java; `-` when absent.

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

`FtagsComp` runs ctags **per component** in parallel, writing a `.fns` file per
component to `ftags/.fns/`.  The incremental build detects which components have
source files newer than their `.fns` file and re-runs only those.
`FtagsMerge` merges all `.fns` files into the per-letter TSVs under
`parts/ftags_<comp>/` and the flat root.

---

### stags — Structure Tags

**Location:**
- Per-component: `stags/parts/stags_<comp>/<letter>.tsv`
- Cross-component (flat): `stags/<letter>.tsv`

Covers everything ftags does **not**: class definitions, interfaces, enums,
typedefs, and Java field declarations.

#### Format — 3-column TSV

```
qualified_name<TAB>file:line<TAB>kind
```

```
OrderService                myapp/src/main/java/com/example/OrderService.java:42      class
OrderService.Status         myapp/src/main/java/com/example/OrderService.java:88      enum
OrderService.id             myapp/src/main/java/com/example/OrderService.java:50      field
```

Kinds: `class`, `enum`, `interface`, `field`.

#### Lookup recipe

```sh
# Known component
grep '^OrderService' btags/stags/parts/stags_orders/o.tsv || true

# Unknown component
grep '^OrderService' btags/stags/o.tsv || true
```

#### How stags is built

`StagsComp` reads each `ctags/parts/*.tags` file and filters it to structural
kinds in a single pass — **no second ctags invocation**.
`StagsAll` merges all component parts into the flat root TSVs.

The incremental build uses source-level staleness checks (any `.java` newer than
the stags stamp) so stags always stays in sync with ctags.

---

### ltags — Line-range index

**Location:** `ltags/<stem>.range` — one file per source file

A pivot of the ftags `.fns` data into `(file, start, end, qualified_name)` order.
Used internally by `XtagsComp` to answer "which method contains line N?"
in a single sorted lookup — no brace-counting, no source re-reads.

Not normally queried directly by users; it exists to make xtags fast and accurate.

---

### xtags — Callers index

**Location:** `xtags/<letter>.tsv`

Name-based call-site index: for every method call in the codebase,
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

- **Call sites only** — xtags records method invocations.  It does
  **not** index enum value references, field accesses, type usages, annotation
  references, or constant reads.  Use `grep -rn` for those.
- Name-based heuristic — cannot resolve overloads or dynamic dispatch.
- Very common names (`get`, `set`) return many results; cross-reference with
  ftags to filter by class.

---

## Building

To install btags into another project, follow [`INSTALL.md`](INSTALL.md) or ask
Bob to run the [`INSTALL.md`](INSTALL.md) plan step by step. That gives you the
project-specific copy, `btagsRoot` configuration, build, and
`~/bob/rules/search-rules.md` wiring in the documented order.

Run from inside `btags/` (or from anywhere using `-p`):

```sh
./gradlew -p btags tags          # build all indexes  (~2 min first time, fast incremental after)
./gradlew -p btags ctags         # ctags only
./gradlew -p btags ftags         # ftags only  (incremental — only changed components re-processed)
./gradlew -p btags stags         # stags only  (reads ctags output — fast)
./gradlew -p btags ltags         # ltags only  (reads ftags .fns files)
./gradlew -p btags xtags         # xtags only  (reads ftags .fns + ltags .range files)
./gradlew -p btags clean         # wipe everything
./gradlew -p btags cleanCtags    # wipe ctags output for a full ctags rebuild
./gradlew -p btags cleanFtags    # wipe ftags stamps and output for a full ftags rebuild
./gradlew -p btags cleanStags    # wipe stags output folders
./gradlew -p btags cleanLtags    # remove all .range files
./gradlew -p btags cleanXtags    # remove all .calls files and merged TSVs
```

Parallelism can be tuned via Gradle properties:

```sh
./gradlew -p btags tags -PctagsJobs=8 -PftagsJobs=8    # defaults are 20
```

### Incremental builds

All tasks run with `outputs.upToDateWhen { false }` so Gradle always executes
them, but the Java workers perform their own incremental staleness checks:

1. `CtagsPart` — skips a component if no `.java` is newer than its stamp.
2. `FtagsComp` — skips a component if no `.java` is newer than its `.fns` file.
3. `StagsComp` — skips a component if no `.java` is newer than its stags stamp.
4. `LtagsComp` / `XtagsComp` — skip if the `.fns` file is not newer than the stamp.

A typical one-file edit triggers a rescan of one component in all five stages
and completes in **under 30 seconds** on a 10-core machine.

### Typical timings (10-core MacBook Pro)

| Operation | Time |
|---|---|
| Cold build (all indexes) | ~2 min |
| Incremental (nothing changed) | ~7 s |
| Single-component Java edit | ~23 s |

### Prerequisites

| Tool | Where |
|---|---|
| Java 11+ | Required to run Gradle and the worker classes |
| Universal Ctags ≥ 5.9 | `brew install universal-ctags` / `apt install universal-ctags` |

**No bash, no awk, no GNU make, no Python, no Perl, no Node** — the build is
pure Java/Gradle beyond ctags itself.

### Windows

The build is pure Java/Gradle and runs natively on Windows without WSL.
Install Universal Ctags via Scoop or Chocolatey (see [Prerequisites](#prerequisites)).

---

## Design notes

### Incremental build: stamp-based staleness

Each Java worker checks whether its input is newer than its stamp file before
doing any work.  `CtagsPart` and `FtagsComp` walk the source tree for `.java`
files newer than the stamp; `LtagsComp` and `XtagsComp` compare the `.fns`
modification time against the stamp.  This means the Gradle task graph always
runs to completion, but individual component work is skipped when nothing has
changed.

### Parallel ctags: per-component partitions + merge

`MergeCtags` uses a priority-queue merge — O(N) because each partial is already
sorted — and writes a canonical pseudo-tag header block.

`CtagsPart` performs an incremental staleness check before invoking ctags:
- **Up to date** (stamp present, no sources newer): exits immediately without
  touching the output file.
- **Stale or cold** (stamp absent or sources newer): runs a full recursive ctags
  scan on the component, then touches the stamp.

### The `+q` extra entries and deduplication

ctags `--extras=+q` emits a second tag for every nested Java type using the
fully-qualified dotted name (e.g. `OrderService.Status` in addition to
`Status` with a `class:OrderService` scope field).  Both entries resolve
to the same output row in stags.  `StagsAll` and `FtagsMerge` sort each letter
file after merging, which removes duplicates via `sort -u` semantics.

### XtagsComp: range tables rebuilt inline

`XtagsComp` rebuilds its per-file range tables directly from the `.fns` file
rather than reading the `.range` files written by `LtagsComp`.  This avoids
a cross-task file dependency and ensures correctness when both tasks run in
the same Gradle invocation.  The `ltagsDir` parameter is accepted but unused.
