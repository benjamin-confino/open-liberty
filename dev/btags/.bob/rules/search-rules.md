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

Fall back to `grep -rn` only if the index returns no result or is absent.

## RULE 2 — Read only the lines the index gives you

The index returns an exact range, e.g. `MyClass.java:280-315`.
Use `read_file` with that range. **Never open a whole file when the index exists.**

## RULE 3 — Session start check (once only)

```sh
ls btags/ftags/l.tsv 2>/dev/null   # ftags present?
ls btags/stags/a.tsv 2>/dev/null   # stags present?
ls btags/xtags/l.tsv 2>/dev/null   # xtags present?
```

If any file is missing, warn the user **once** then fall back to `grep -rn` for
the rest of the session — do not repeat the warning:

> ⚠️ **btags indexes are not built.** Run `gradle tags` (~2 min) from inside
> `btags/`. Falling back to `grep -rn` for this session.

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
