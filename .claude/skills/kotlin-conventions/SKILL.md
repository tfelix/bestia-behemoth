---
name: kotlin-conventions
description: Kotlin code conventions - file/class organization and function bodies (comment style lives in the code-comments skill). Read this BEFORE creating a new Kotlin class, exception, or DTO, before adding a second top-level type to an existing file, or before writing a function. Triggers on: new .kt file, top-level class, nested class, inner class, sealed class subtypes, exception hierarchy, DTO class, one class per file, multiple classes in one file, expression body, single-expression function, one-liner.
---

# Kotlin file & class organization

Kotlin doesn't force one-public-class-per-file the way Java does, so it's easy for a file to
quietly accumulate unrelated types over time. Default to the Java convention anyway: **one
top-level class/interface/object per file, named after that type.**
Before adding a second type as an inner class, ask what its relationship to the primary type is:

1. **Only ever constructed/used by the primary type, with no identity outside it** - a sealed
   hierarchy's own subtypes, a private helper type, a DTO that exists solely to hydrate one
   class → nest it *inside* the primary type instead of leaving it as a top-level sibling.
   - Default to a plain nested class (e.g. `private class Entry(...)`).
   - Reach for `inner class` only when it actually needs an implicit reference back to the
     outer instance (walking a parent pointer, calling back into the outer type's state) -
     don't reach for `inner` out of habit.
2. **Independent** - used elsewhere, unit-testable/reusable on its own, or just conceptually its
   own thing (e.g. a CMSG and the handler that processes it) → its own file, named after the
   type.


# Function bodies

Expression-body one-liners are forbidden, even for trivial getters/delegations. Always use a
block body with an explicit `return`.

NO:
```kotlin
fun accountFor(ticket: String): Long? = byTicket[ticket]
```

YES:
```kotlin
fun accountFor(ticket: String): Long? {
    return byTicket[ticket]
}
```

# Comments

Comment style lives in [code-comments](../code-comments/SKILL.md) - it applies to KDoc, GDScript
`##` and C# XML doc alike. The short version: explain **why**, not **how**; one or two lines; and if
the comment cannot be checked against the code, refactor the code instead of explaining it.

Kotlin-specific: don't restate a signature in KDoc, and don't KDoc a `private` member whose name
already says what it is.
