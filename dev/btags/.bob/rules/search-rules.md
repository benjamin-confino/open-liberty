# Code Navigation — MANDATORY RULES (btags/)

> ⛔ **DO NOT use `grep -rn`, `find`, `GetSymbolsOverview`, `FindSymbol`, or
> `read_file` on a whole file to locate a symbol.**
> These are forbidden as a first action. The btags indexes exist to replace them.
>
> ⚠️ **xtags tracks call sites only — not enum references, field accesses, or
> type usages.** No xtags result for an enum constant is correct, not stale —
> use `grep -rn` for those.

## RULE 1 — Index first, always

| What you need | Index to use |
|---|---|
| Method or function body | `ftags` |
| Class / struct / enum / field declaration | `stags` |
| Callers of a method/function | `xtags` (call sites only) |
| All uses of an enum value or field | `grep -rn` directly — xtags does not cover these |
| Message code (e.g. `CWWKS0008I`) or message text | `mtags` |

Fall back to `grep -rn` only if the index returns no result or is absent.

## RULE 2 — Read only the lines the index gives you

The index returns an exact range, e.g. `MyClass.java:280-315`.
Use `read_file` with that range. **Never open a whole file when the index exists.**

## RULE 3 — Session start check (once only)

```sh
ls btags/ftags/l.tsv      2>/dev/null   # ftags present?
ls btags/stags/a.tsv      2>/dev/null   # stags present?
ls btags/xtags/l.tsv      2>/dev/null   # xtags present?
ls btags/mtags/mtags.tsv  2>/dev/null   # mtags present?
```

If any file is missing, warn the user **once** then fall back to `grep -rn` for
the rest of the session — do not repeat the warning:

> ⚠️ **btags indexes are not built.** Run `./gradlew -p btags tags` (~2 min).
> Falling back to `grep -rn` for this session.

## Lookup sequence — ftags (methods/functions)

Stop at the first hit:

0. Unknown component → `grep '^methodName' btags/ftags/hints.tsv || true`
1. Known class + component → `grep -m5 '^ClassName\..*methodName' btags/ftags/parts/ftags_<comp>/<ClassLetter>.tsv || true`
2. Known component → `grep -m5 '^methodName' btags/ftags/parts/ftags_<comp>/<MethodLetter>.tsv || true`
3. Unknown component → `grep -m5 '^methodName' btags/ftags/<MethodLetter>.tsv || true`
4. No index → `grep -rn` on source tree.

Component name = top-level directory verbatim (`my_module` → `ftags_my_module`).
Unsure? `ls btags/ftags/parts/` to list all components.

## Lookup sequence — stags (classes, enums, structs, fields)

1. Known component → `grep '^TypeName' btags/stags/parts/stags_<comp>/<Letter>.tsv || true`
2. Unknown component → `grep '^TypeName' btags/stags/<Letter>.tsv || true`
3. No index → `grep -rn`.

## Lookup sequence — xtags (callers)

```sh
grep '^methodName' btags/xtags/<Letter>.tsv || true
```

## Lookup sequence — mtags (message codes)

Index location: `btags/mtags/mtags.tsv`

**7-column TSV** (no header row):
```
msg_code  msg_key  msg_text  explanation  useraction  nlsprops_file  java_callers
```

- `msg_code` — the log/message code, e.g. `CWWKS0008I`
- `msg_key` — the NLS property key, e.g. `SECURITY_SERVICE_READY`
- `msg_text` — English message text (without the code prefix)
- `explanation` — user-facing explanation, or `-`
- `useraction` — user-facing action, or `-`
- `nlsprops_file` — relative path to the English `.nlsprops` source file
- `java_callers` — semicolon-separated `file:line` list of `Tr.info/warning/error/…` call sites, or `-`

**Lookup examples:**

```sh
# Look up a code you saw in a log
grep '^CWWKS0008I' btags/mtags/mtags.tsv || true

# Look up by partial code (prefix)
grep '^CWWKS' btags/mtags/mtags.tsv | head -20

# Look up by message key
grep $'\tSECURITY_SERVICE_READY\t' btags/mtags/mtags.tsv || true

# Full-text search in message text (column 3)
grep -i 'security service is ready' btags/mtags/mtags.tsv || true
```

**After finding the row**, the `nlsprops_file` column gives you the definition
source and `java_callers` gives you the exact file:line of each `Tr.*` call
site — use `read_file` with those ranges. Do not `grep -rn` the source tree first.
