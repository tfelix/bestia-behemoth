---
name: skill-system
description: Explains how zone-server's skill catalog (skills.yml), the master skill tree (master_skill_tree.yml), and the Godot client's Attack DB (bestia-client/src/Game/Attack/DB/*.tres) relate to and must stay in sync with each other, including the description -> skills.csv translation pipeline. Read this BEFORE adding/renumbering a skill, changing a master's learnable skill tree, adding/editing a skill description, or investigating why a skill shows up wrong (or not at all) in the client Skills window. Triggers on: skills.yml, master_skill_tree.yml, AttackResource, AttackDB, skill_id, description_key, skills.csv, MasterSkillTreeRegistry, MasterSkillTreeNode, GetSkillsHandler, SkillListSMSG, skill tree, learnable skill, max_level.
---

# Bestia skill system: catalog, master skill tree, and the client DB

Three files define one skill, and they are **not** loaded from each other at runtime —
each is parsed independently at boot (server) or at editor/runtime load (client), so
nothing fails loudly if they drift. Keeping them aligned is a manual discipline, not an
enforced invariant.

## 1. `zone-server/src/main/resources/skills.yml` — the skill catalog

The single source of truth for skill _identity_. Every skill that exists anywhere in the
game (master-castable, bestia-species, item-taught) gets one entry here, loaded by
`zone-server/.../boot/SkillImporterBootRunner.kt` into the `skill` table (`Skill.kt`):

- `id` (Long) — the numeric id used **everywhere downstream**, including the wire
  protocol (`SkillListSMSG.SkillListEntry.skillId`) and the client. Ids are
  hand-assigned in the YML and never reassigned by the DB.
- `identifier` — string name (e.g. `BLESSING`), used to cross-reference from
  `master_skill_tree.yml` (below) instead of hardcoding ids there.
- `type` / `script` / `strength` / `needsLineOfSight` / `manaCost` / `range` — combat
  behavior. `NO_DAMAGE` skills must have a `script` and no `strength`.
- `requiredLevel` — **not** used by the master skill tree; per-bestia-species skill
  unlocks are a third, separate mechanism.
- `description` (optional) — long-form BBCode flavor text, English only. When present,
  it's the authoritative source for the skill's English description and gets synced by
  `syncSkillDb` into `bestia-client/src/Localization/skills.csv` (see the translation
  pipeline below). Skills without a `description` here are still hand-authored directly
  in `skills.csv` until someone backfills one.

Convention in the file: master skills are grouped first under `# MASTER SKILLS`, bestia
skills after under `# BESTIA SKILLS`, by ascending id (**bestia-species/item skills are
reserved at id 1000+**; master skills use the open range below that, currently 1-43) —
not enforced by code, just keep following it. The 1000+ split was chosen when the master
skill tree grew past the original "masters 1-99" convention (see
`docs/mechanics/master.md` for the full master skill tree design) — if it ever needs
renumbering again, check that no other file hardcodes a raw skill id first (as of this
writing, nothing outside `skills.yml`/`master_skill_tree.yml`/the `.tres` files/
`skills.csv` does).

## 2. `zone-server/src/main/resources/master_skill_tree.yml` — the learnable subset

A **master** (the player, not a bestia) spends skill points to invest levels into a
subset of `skills.yml` entries. This file lists exactly that subset, referencing skills
by `identifier`.

```yaml
- skill: BLESSING
  maxLevel: 10
  prerequisites:
    - skill: DIVINE_PROTECTION
      level: 5
```

- `maxLevel` — highest level a master can invest in this node.
- `prerequisites` — a DAG edge list (`MasterSkillPrerequisite`): this node can't receive
  points until every listed prerequisite skill is invested to at least `level`.
  Enforced entirely server-side in `MasterSkillTreeService` — **the client never receives or
  evaluates the prerequisite graph**, it just follows its own cataloge generated from this file.
- A skill in `skills.yml` with no entry here (e.g. bestia skills 1000+, `ember`,
  `tackle`) is not master-investable at all — masters never see a level-up option for
  it.
- This file has **no representation of the "tree mastery" mechanics** described in
  `docs/mechanics/master.md` (a sub-tree unlocking once 5+ points are spent in its parent
  tree, or a master capping out at 3 tree masteries) — only explicit skill-to-skill
  `prerequisites` edges exist today. Enforcing the point-gated sub-tree unlock is a
  separate `MasterSkillTreeService` feature, not a YAML content change.

**Every** `master_skill_tree.yml` node must have a corresponding `skills.yml` entry, but not vice versa.

## 3. `bestia-client/src/Game/Attack/DB/*.tres` — the client mirror

One `AttackResource` (`bestia-client/src/Game/Attack/attack_resource.gd`) per file,
auto-loaded by `AttackDB` (`bestia-client/src/Game/Attack/attack_db.gd`) from every
`.tres` in that folder, keyed by `skill_id` — named to match `skills.yml`'s `id` and
`SkillListSMSG.SkillListEntry.skillId` on the wire, instead of the older, inconsistent
`attack_id`. `Skills.gd` / `skill_row.gd` look up an incoming `SkillListSMSG` entry's
`skillId` via `AttackDB.get_instance().get_attack(id)` and print an error to the console
(not a crash) if it's missing — so an unsynced skill silently shows as "Unknown Skill"
instead of failing loudly.

File naming convention: `<id>_<identifier lowercased>.tres` (e.g. `1000_ember.tres`),
matching `skills.yml`'s `id`/`identifier` — not required by any loader code (only
`skill_id` inside the file matters), but keep it for greppability.

Field provenance — **only `skill_id`, `max_level`, and `description_key` are meant to
mirror the server**; everything else (`icon`, `name`, `mana_cost`, `cooldown`) is
client-only presentation, hand-authored by whoever adds the skill.

## 4. `bestia-client/src/Localization/skills.csv` — the English description, and its translations

`description_key` on an `AttackResource` (e.g. `SKILL_1_DESC`, always `SKILL_<id>_DESC`)
is a lookup key into this Godot CSV translation source, resolved at display time via
`tr(description_key)` — the same mechanism `ItemResource.description_key`/`name_key`
already use against `Localization/items.csv`. The `.tres` file never holds description
text directly.

`skills.csv` has one row per key, one column per locale (`keys,en` to start; additional
locale columns like `de`/`fr` get added over time):

```csv
keys,en,de
SKILL_1_DESC,"Blesses the target with divine power.",Segnet das Ziel mit göttlicher Macht.
```

The intended workflow for a skill's flavor text:

1. A dev writes (or edits) the English `description` on the skill in `skills.yml`.
2. `./gradlew syncSkillDb` copies that English text into `skills.csv`'s `en` column
   for `SKILL_<id>_DESC`, creating the row if it doesn't exist yet. **This is the only
   column the sync task ever writes.**
3. An LLM (or a human translator) fills in/updates the other locale columns in
   `skills.csv` by hand, translating from the `en` column. This step is not automated by
   `syncSkillDb` — do it as a follow-up editing pass over the CSV whenever `en` changes.
4. Godot's `csv_translation` importer compiles `skills.csv` into `.translation`
   resources on next editor load (see `Localization/items.csv.import` for the analogous,
   already-generated example) — no manual step needed for that part.

A skill with no `description` in `skills.yml` yet still needs a `description_key` (so
the `.tres` schema is uniform), but its `skills.csv` row is hand-authored/placeholder
until someone backfills a `skills.yml` description for it.

## Adding a new skill end-to-end

1. Add the entry to `skills.yml` with a fresh unused `id`, plus an English `description`
   if you have flavor text ready.
2. If it's master-investable, add a node to `master_skill_tree.yml` referencing it by
   `identifier`, with `maxLevel` and any `prerequisites`. Skip this step for
   bestia-species/item-taught skills.
3. Add `bestia-client/src/Game/Attack/DB/<id>_<identifier>.tres`: `skill_id` = the new
   id, `max_level` = the `master_skill_tree.yml` value (or `1` if step 2 was skipped),
   `description_key` = `SKILL_<id>_DESC`, plus hand-authored `name`/`icon`/`mana_cost`/
   `cooldown`.
4. Restart zone-server once to confirm boot doesn't throw (duplicate id, unresolved
   `master_skill_tree.yml` identifier, or a `NO_DAMAGE` skill missing its `script`).
5. Cross check the skill consistence (see below).

## Cross-checking consistency: `checkSkillDb` / `syncSkillDb`

Two Gradle tasks, registered on the `zone-server` subproject (`zone-server/build.gradle`)
and backed by `buildSrc/src/main/kotlin/SkillDbSyncTask.kt`, automate exactly the checks
described above. They live on `zone-server` (not the root build) since this is
conceptually a zone-server concern, but `clientDbDir`/`skillsCsv` still point at
`bestia-client/` via `rootProject.layout.projectDirectory` — a sibling module, not
under `zone-server/`. `./gradlew checkSkillDb`/`syncSkillDb` from the repo root still
work unqualified (Gradle resolves task names project-wide); the task itself just runs
as `:zone-server:checkSkillDb`/`:zone-server:syncSkillDb`.

- `./gradlew checkSkillDb` — read-only. Fails the build with a listed diff if any
  `skills.yml` id is missing a `.tres`, or if `max_level`/`description_key`/the
  `skills.csv` English text disagree with `skills.yml`/`master_skill_tree.yml`.
- `./gradlew syncSkillDb` — same check, but patches `max_level`/`description_key` in
  place on existing `.tres` files, syncs the English text into `skills.csv` (creating
  the row/file if needed) whenever `skills.yml` declares a `description`, and creates a
  stub `.tres` + placeholder `skills.csv` row (`mana_cost: 0`, `cooldown: 0.0`, `"TODO:
  describe <identifier>"`) for any missing id, printing what it did.

Neither task ever touches a non-`en` locale column in `skills.csv` — those stay
hand/LLM-translated.
