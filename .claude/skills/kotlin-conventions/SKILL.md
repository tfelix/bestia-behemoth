---
name: kotlin-conventions
description: Kotlin code convention Read this BEFORE creating a new Kotlin class, exception, or DTO, or before adding a second top-level type to an existing file. Triggers on: new .kt file, top-level class, nested class, inner class, sealed class subtypes, exception hierarchy, DTO class, one class per file, multiple classes in one file.
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
