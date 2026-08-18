---
name: kotlin-conventions
description: Kotlin code conventions - file/class organization and comment style. Read this BEFORE creating a new Kotlin class, exception, or DTO, before adding a second top-level type to an existing file, or before writing comments/KDoc. Triggers on: new .kt file, top-level class, nested class, inner class, sealed class subtypes, exception hierarchy, DTO class, one class per file, multiple classes in one file, comment, comments, KDoc, docstring, code documentation.
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


# Comments

Comments you write must be direct and concise, and explain **why**, not **how**. The code already
states how; a comment that restates it is noise that goes stale on the next edit.

- Write a comment when something is non-obvious: a non-obvious invariant, a deliberate trade-off,
  a workaround for an upstream bug, a formula's source, an ordering that matters.
- Don't narrate the mechanics (`// increment the counter`), don't restate a signature in KDoc, and
  don't leave scaffolding chatter (`// added for the new feature`, `// changed from X`) - the diff
  and git history already carry that.
- No filler openers ("Note that...", "This method simply..."). Lead with the point.
- One or two lines is usually enough. If a comment needs a paragraph to justify the code, that's
  a signal to reshape the code instead.
