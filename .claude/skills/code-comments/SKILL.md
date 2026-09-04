---
name: code-comments
description: Comment style for every language in this repo (Kotlin KDoc, GDScript `##`, C# XML doc) - what earns a comment, how long it is allowed to be, and when a comment that cannot be checked against the code means the *code* must be refactored instead. Read this BEFORE writing or reviewing a comment or doc block, before adding explanatory prose above a class/field/function, and when a file feels over-commented. Triggers on: comment, comments, commenting, KDoc, docstring, doc comment, XML doc, header comment, explain this code, document this function, too many comments, comment style, clean code, self-documenting, refactor for clarity.
---

# Comments

**Why, not how.** The code already states how; a comment that restates it is noise that goes stale
on the next edit. This applies in Kotlin, GDScript and C# alike - see also
[kotlin-conventions](../kotlin-conventions/SKILL.md) for Kotlin file/function layout.

## Two tests every comment must pass

1. **Non-obvious** - would a competent reader miss this by reading the code? If not, delete it.
2. **Checkable** - can that reader confirm the claim from the code in front of them? If not, the
   comment is papering over a code problem. **Fix the code, don't explain it.**

Test 2 is the one that gets skipped, and it bites twice in this repo: prose that cannot be checked
also cannot be *maintained*, so files here already carry KDoc describing machinery that was never
implemented. A comment nobody can verify is worse than no comment - it is trusted and wrong.

## Budget

- One or two lines is the default. Lead with the point; no filler openers ("Note that...",
  "This method simply...").
- Four lines is the most a genuine subsystem header needs. Ten-plus lines at the top of a file
  means the file is either explaining a design that should be visible in the code, or doing two
  jobs and wanting a split.
- A paragraph justifying one statement is a signal to reshape the statement.

## Smells, and what to do instead

| Smell | Instead |
|---|---|
| **Archaeology.** "It used to be sent four times a second, which broke X, so now..." | State only what is true now. The old behaviour and the bug it caused belong in the commit message. |
| **Litigating the alternative.** Three sentences on why the correction is not applied to the child node. | One clause naming the constraint: "applied to this node so the camera on its spring arm follows too". |
| **Repeated rationale.** Five sibling fields each carrying its own copy of "cached because the window may be closed when the push arrives". | One note above the group, then a single line per field for what it holds. |
| **Warning label on a bad name.** A doc block explaining that the flag does not mean what it says. | Rename the thing. See below. |
| **Restating the signature.** `## Returns the entity id.` over `func entity_id() -> int`. | Delete. |
| **Scaffolding chatter.** "added for the new feature", "changed from X". | Delete; git carries it. |

## When to refactor instead of commenting

If you cannot write a short, checkable comment, the code is the problem. Reach for the code when:

- **The comment warns about the name.** `is_moving()` in
  [entity.gd](../../../bestia-client/src/Game/Entity/entity.gd) needs a doc block saying it is
  prediction state and not a way to detect arrival - because the name promises truth. A name like
  `is_walking_predicted_path()` needs no warning.
- **The comment defines a term the code never names.** If "tile step", "logical position" or
  "posture" only exists in prose, make it a named constant, type, or function so the code says it.
- **The comment explains an ordering or invariant.** Prefer a guard, an assert, or a function whose
  name states the precondition, over a sentence asking the next reader to remember it.
- **The comment is a mini-tutorial on a flag.** Two booleans that must be read together usually
  want one enum or one small class.

Comment the thing you genuinely cannot express in code: an upstream bug you are working around, a
formula's source, a measured trade-off, a wire-protocol fact the code cannot show.

## Trimming a file that is already over-commented

Verify mechanically, because it is easy to delete a line of code along with the prose:

1. Change comment lines only - never touch a code line in the same pass.
2. Diff with code lines extracted from both versions and compare them; they must be byte-identical.
3. Preserve the file's line endings (most working-tree files here are CRLF - see the line-endings
   note in memory).
4. Report the before/after comment line and word counts, so the size of the cut is visible.

A worked example is `bestia-client/src/Game/Entity/entity.gd`: 244 comment lines / 3036 words down
to 86 / 1133, with the wire-protocol facts, the logical-vs-drawn position rule and the ground-offset
reasoning kept, and the protocol history, the rejected alternatives and the per-field repetition cut.
