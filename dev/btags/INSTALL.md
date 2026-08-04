# INSTALL — Adding btags to a project

Step-by-step guide to add the btags index suite (ctags, ftags, stags, ltags,
mtags, xtags) to any Java project.

btags supports two installation modes — choose whichever suits your workflow:

| Mode | When to use |
|---|---|
| **Embedded** | You own the project repo and are happy to add a `btags/` subdirectory to it. |
| **Standalone** | The project is shared / read-only, or you prefer to keep btags entirely separate. btags lives anywhere and is pointed at the project via the `btagsRoot` Gradle property. |

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

- **Java 11+** and the Gradle wrapper (`./gradlew`) included in the btags directory.
  No bash, awk, GNU make, Python, or other shell tools are required — the build
  is implemented entirely in Java worker classes under `buildSrc/`.

### Windows

The build is pure Java/Gradle and runs natively on Windows without WSL.
Universal Ctags for Windows can be installed via Scoop or Chocolatey:

```powershell
scoop install ctags
# or
choco install universal-ctags
```

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
    ├── build.gradle              ← main build orchestrator
    ├── settings.gradle
    ├── buildSrc/                 ← Java worker classes (compiled automatically by Gradle)
    │   └── src/main/java/btags/
    │       ├── CtagsPart.java    ← per-component ctags runner
    │       ├── FtagsComp.java    ← per-component function-tag generator
    │       ├── FtagsMerge.java   ← assembles component/letter TSVs + hints.tsv
    │       ├── LtagsComp.java    ← pivots ftags .fns into line-range files
    │       ├── MergeCtags.java   ← merges per-component ctags partials
    │       ├── MtagsComp.java    ← builds message-code index from .nlsprops + Tr call sites
    │       ├── SourceDiscovery.java ← finds Java component source roots
    │       ├── StagsComp.java    ← builds structure tags for one component
    │       ├── StagsAll.java     ← assembles the flat cross-component stags index
    │       ├── XtagsComp.java    ← per-component callers scanner
    │       ├── XtagsMerge.java   ← assembles flat callers index
    │       └── Util.java         ← shared utilities
    ├── .bob/
    │   └── rules/
    │       └── search-rules.md   ← Bob index rules (copy to ~/bob/rules/ — see A5)
    ├── ctags/                    ← Universal Ctags output
    ├── ftags/                    ← Function Tags output
    ├── mtags/                    ← Message-code index output
    ├── stags/                    ← Structure Tags output
    ├── ltags/                    ← Line-range index output
    └── xtags/                    ← Callers index output
```

### A2 — Source-discovery is automatic

btags automatically discovers every top-level directory under your project root
that contains Java source files (under `src/` or `src/main/java/`).  No
configuration edits are needed for standard project layouts.

To override the root being indexed, pass the `btagsRoot` property:

```sh
./gradlew -p btags tags -PbtagsRoot=/path/to/your-project
```

To tune parallelism:

```sh
./gradlew -p btags tags -PctagsJobs=8 -PftagsJobs=8   # default is 20 each
```

### A3 — Generated output is already gitignored

Each btags subdirectory ships with its own `.gitignore`.  **No changes to your
project's `.gitignore` are needed.**

| Directory | Ignored patterns |
|---|---|
| `btags/ctags/` | `.stamps/`, `parts/`, `tags`, `.srccache.mk` |
| `btags/ftags/` | `.fns/`, `parts/`, `hints.tsv`, `known_names.tsv`, `[_a-z].tsv` |
| `btags/mtags/` | `mtags.tsv` |
| `btags/stags/` | `.stamps/`, `parts/`, `[_a-z].tsv` |
| `btags/ltags/` | `.stamps/`, `*.range` |
| `btags/xtags/` | `.calls/`, `.stamps/`, `[_a-z].tsv` |

### A4 — Build and verify

```sh
./gradlew -p your-project/btags tags
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
where the project is via the `btagsRoot` Gradle property.  **Nothing
is added to or changed in the host project.**

### B1 — Clone or place btags anywhere

```sh
git clone <btags-repo-url> ~/tools/btags
# or any other location — ~/btags, /opt/btags, etc.
```

### B2 — Set btagsRoot

`btagsRoot` must be the absolute path to the root of the project you want
to index (the directory that contains your source components).

Pass it on the command line each time:

```sh
./gradlew -p ~/tools/btags tags -PbtagsRoot=/path/to/your-project
```

Or set it persistently in `~/tools/btags/gradle.properties`:

```properties
btagsRoot=/path/to/your-project
```

### B3 — Source discovery

Source discovery is automatic (see [A2](#a2--source-discovery-is-automatic)).
btags already excludes its own directory from indexing.

### B4 — Generated output stays inside btags

All index artifacts (`.tags`, `.tsv`, `.range`, `.calls`, stamps) are written
into the btags directory itself — `~/tools/btags/ctags/`, `~/tools/btags/ftags/`,
etc.  The host project's file tree is never touched.

Each subdirectory's `.gitignore` covers its own generated output.  If your
btags directory is tracked in git, no extra ignore rules are needed.

### B5 — Build and verify

```sh
./gradlew -p ~/tools/btags tags -PbtagsRoot=/path/to/your-project
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
[ctags] Scanning N Java components (jobs: 20) ...
[ctags] Merged N parallel tag files → btags/ctags/tags  (index count: NNNNNN)
[ftags] Scanning N components (jobs: 20) ...
[ftags] Wrote Bob-tuned function/method lookup index from N components  (NNN,NNN file:n..m ranges mapped)
[stags] Building structure index from N ctags partials (jobs: 20) ...
[stags] Created Bob-specific type/struct/enum lookup index  (NNN,NNN entries)
[ltags] Building line-range index from N .fns files ...
[ltags] Made Bob-friendly line-range index  (NNNN source files indexed)
[mtags] Scanning project for .nlsprops files and Tr call sites ...
[mtags] Indexed N,NNN message codes from NNN nlsprops files
[xtags] Scanning N components for call sites ...
[xtags] Mapped Bob-friendly call-site lookup index  (NNN,NNN call graph links)
```

Verify lookups work:

```sh
# Find a Java method by simple name
grep '^processOrder' btags/ftags/p.tsv

# Find a class
grep '^OrderService' btags/stags/o.tsv

# Find all callers of a method
grep '^processOrder' btags/xtags/p.tsv

# Look up a message code seen in a log
grep '^CWWKS0008I' btags/mtags/mtags.tsv
```

---

## Ongoing use

| Action | Command |
|---|---|
| Rebuild after code changes | `./gradlew -p btags tags` |
| Rebuild ctags only | `./gradlew -p btags ctags` |
| Rebuild ftags only | `./gradlew -p btags ftags` |
| Rebuild mtags only | `./gradlew -p btags mtags` |
| Rebuild stags only | `./gradlew -p btags stags` |
| Rebuild ltags only | `./gradlew -p btags ltags` |
| Rebuild xtags only | `./gradlew -p btags xtags` |
| Full clean rebuild | `./gradlew -p btags clean tags` |
| Clean ctags output only | `./gradlew -p btags cleanCtags` |
| Clean ftags output only | `./gradlew -p btags cleanFtags` |
| Clean mtags output only | `./gradlew -p btags cleanMtags` |
| Clean xtags output only | `./gradlew -p btags cleanXtags` |

All tasks run with `outputs.upToDateWhen { false }` so they always execute, but
the Java workers themselves perform incremental staleness checks internally —
only components with source files newer than their stamp are re-processed.
A typical one-file edit completes in under 60 seconds.
