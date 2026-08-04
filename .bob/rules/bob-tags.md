# Search Rules - MANDATORY

**These rules are not optional. Violating them wastes context and defeats
the purpose of the indexes. There are no exceptions while the index files exist.**

---

## RULE 1 - Check index presence before any navigation

At the start of any task that involves finding, reading, or understanding code,
run this check exactly once:

```sh
ls dev/btags/ftags/l.tsv   2>/dev/null   # ftags present?
ls dev/btags/stags/a.tsv   2>/dev/null   # stags present?
ls dev/btags/mtags/mtags.tsv 2>/dev/null # mtags present?
ls dev/btags/xtags/l.tsv   2>/dev/null   # xtags present?
```

If a file exists -> **you must use that index**. No exceptions.
If any file is missing -> tell the user **once**:

**WARNING: btags indexes are not built** - falling back to `grep -rn`, which is
slower and uses more context. Run **`make tags`** (~90 sec) from the Lodestone
root to build the indexes and get faster, more accurate Bob assistance.

Then fall back to `grep -rn` for the rest of the session - **do not ask again**.

---

## RULE 2 - Use the correct index for each task

### Finding a method or function body -> ftags

**Do not open source files. Do not use `grep -rn` first. Do not use `FindSymbol`.**

Index location:
- Per-component (fastest): `dev/btags/ftags/parts/ftags_<comp>/<letter>.tsv`
- Flat cross-component: `dev/btags/ftags/<letter>.tsv`
- Component hint lookup: `dev/btags/ftags/hints.tsv`

Known components (from `dev/btags/ftags/parts/`):
`ad`, `bb`, `ca`, `cl`, `dm`, `dv_ut`, `dv_ut_hs`, `en`, `fc`, `fg`, `fw`,
`hl`, `hse`, `hsn`, `ic`, `ic_auth_csm`, `ic_feature_csm`, `inventory`, `lb`,
`ld`, `mm`, `mp`, `mr`, `nm`, `nos`, `orch_manager`, `pac`, `platform`, `plif`,
`qm`, `rc`, `rd_config`, `rep`, `se`, `sr`, `src`, `src2`, `st`, `stats`, `sv`,
`vg_java`

Format: `key_name TAB file:start-end TAB signature`

**Lookup sequence - stop at first hit:**

```sh
# Step 0 - unknown component: find it via hints
grep '^methodName' dev/btags/ftags/hints.tsv || true
# -> methodName    sv,hl
# Then use step 1 with the returned component name.

# Step 1 - known class + component
grep '^ClassName\..*methodName' dev/btags/ftags/parts/ftags_<comp>/<ClassLetter>.tsv || true

# Step 2 - known component, unknown class
grep -m5 '^methodName' dev/btags/ftags/parts/ftags_<comp>/<MethodLetter>.tsv || true

# Step 3 - unknown component (no hints result)
grep -m5 '^methodName' dev/btags/ftags/<MethodLetter>.tsv || true

# Step 4 - index absent only
grep -rn 'methodName' <source tree>
```

The letter is the **lowercase first character of the key name**.
Java -> skip `ftags_src/` and `ftags_src2/`.
C -> skip all `ftags_<java-comp>/` dirs.

**After finding the result**, call `read_file` with the exact range:
```
-> logicPrv   sv/com/ibm/svc/sv/CsmPartition.java:280-315   -
  read_file sv/com/ibm/svc/sv/CsmPartition.java range:280-315
```
**Never open the whole file.**

---

### Finding a class, enum, struct, typedef, macro, or field -> stags

**Do not use `GetSymbolsOverview`. Do not open source files to search.**

Index location:
- Per-component: `dev/btags/stags/parts/stags_<comp>/<letter>.tsv`
- Flat cross-component: `dev/btags/stags/<letter>.tsv`

Format: `qualified_name TAB file:line TAB kind`
Kinds: `class`, `enum`, `interface`, `struct`, `typedef`, `macro`, `union`, `field`

```sh
# Known component
grep '^TypeName' dev/btags/stags/parts/stags_<comp>/<Letter>.tsv || true

# Unknown component
grep '^TypeName' dev/btags/stags/<Letter>.tsv || true
```

Real examples:
```sh
grep '^CsmPartition' dev/btags/stags/c.tsv || true
# -> CsmPartition   sv/com/ibm/svc/sv/CsmPartition.java:42   class

grep '^se_io_test_t' dev/btags/stags/parts/stags_c_src_se/s.tsv || true
# -> se_io_test_t   src/user/se/se_io_test.c:102   struct
```

---

### Finding all callers of a method -> xtags

Index location: `dev/btags/xtags/<letter>.tsv`
Format: `callee_name TAB caller_qualified_name TAB file:line`

```sh
grep '^methodName' dev/btags/xtags/<MethodLetter>.tsv || true
```

**Limitation:** name-based heuristic. Cannot resolve overloads or dynamic
dispatch. Common names (`get`, `set`) return many results - cross-reference
with ftags to filter by class.

If `dev/btags/xtags/l.tsv` does not exist, suggest `make tags` and
fall back to `grep -rn`.

---

### Finding an error message / CMMVC code -> mtags

Index location: `dev/btags/mtags/mtags.tsv`

Format: 10-column TSV (row 0 is a `#` staleness header, row 1 is the column
header, rows 2+ are data):
`java_enum . c_name . error_id . msg_num . cmmvc . msg_text . java_loc . def_loc . strings_loc . msg_loc`

```sh
grep 'CMMVC1668E'              dev/btags/mtags/mtags.tsv || true
grep 'PARTITION_NOT_A_MAIN'    dev/btags/mtags/mtags.tsv || true
grep 'Ic_failed_hl_scsi'       dev/btags/mtags/mtags.tsv || true
grep -i 'partition not eligible' dev/btags/mtags/mtags.tsv || true
```

**The mtags row contains all related artifact paths - use them directly.**
The 10 columns include `java_loc`, `def_loc`, `strings_loc`, and `msg_loc`
pointing to the enum declaration, the C macro definition, the error table
entry, and the message string respectively. **Do not grep for these files -
read the paths out of the mtags result and call `read_file` with ranges.**

**mtags shows where an error is defined, not where it is thrown.**
To find callers, search xtags for both the Java enum name and the C name
(taken from the `java_enum` and `c_name` columns of the mtags result):
```sh
# Java callers (first letter of java_enum value)
grep '^JAVA_ENUM_NAME' dev/btags/xtags/<letter>.tsv || true

# C callers (first letter of c_name value)
grep '^c_name_value' dev/btags/xtags/<letter>.tsv || true
```
If both return nothing, fall back to `grep -rn` on the source tree for both
names. If grep finds hits, the indexes are stale - tell the user to run
`make tags`.

**Staleness check:** the first line of `mtags.tsv` starts with `#` and records
the build timestamp and source file mtimes. If source files in
`platform/` or `../lodestone-global-ids/` have changed since that timestamp,
warn the user and suggest `make tags` and fall back to `grep -rn`.

---

## RULE 3 - Never read a whole file when the index gives a range

The indexes return exact file:line or file:start-end locations.
Always pass the range to `read_file`. **Never omit the range.**

```
[ok]  read_file sv/com/ibm/svc/sv/CsmPartition.java range:280-315
[x]  read_file sv/com/ibm/svc/sv/CsmPartition.java
```

---

## RULE 4 - Forbidden first actions (when indexes are present)

The following are **forbidden as a first action** for any symbol lookup:

- `grep -rn` on the source tree
- `find` on the source tree
- `GetSymbolsOverview` on a file
- `FindSymbol` tool
- `read_file` without a line range on a file larger than ~100 lines

These may only be used **after** the relevant index has been consulted and
returned no result, or after confirming the index file does not exist.

---

## RULE 5 - Component name is verbatim

The component directory name is always used verbatim:
- `rd_config` -> `dev/btags/ftags/parts/ftags_rd_config/`  (not `ftags_rd/`)
- `ic_auth_csm` -> `dev/btags/ftags/parts/ftags_ic_auth_csm/`

If unsure of the component name: `ls dev/btags/ftags/parts/` to see all names,
or use `dev/btags/ftags/hints.tsv`  (step 0 above).
