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
- `script` / `needsLineOfSight` / `manaCost` / `range` / `targetType` — behaviour. **`script`
  is also what decides whether the skill can be cast at all**; there is no `type` field. See
  "Castable, passive, unimplemented" below.
- `strength` — vestigial. Nothing reads it: a skill's damage comes out of its script, and a
  basic attack's comes from the weapon.
- `requiredLevel` — **not** used by the master skill tree; per-bestia-species skill
  unlocks are a third, separate mechanism.
- `description` (optional) — long-form BBCode flavor text, English only. When present,
  it's the authoritative source for the skill's English description and gets synced by
  `syncSkillDb` into `bestia-client/src/Localization/skills.csv` (see the translation
  pipeline below). Skills without a `description` here are still hand-authored directly
  in `skills.csv` until someone backfills one.

Convention in the file: master skills are grouped first under `# MASTER SKILLS`, bestia
skills after under `# BESTIA SKILLS`, by ascending id (**bestia-species/item skills are
reserved at id 1000+**; master skills use the open range below that, currently 1-44, with
id 9 a hole where `MASTER_RITUAL` used to be) — not enforced by code, just keep following
it. The 1000+ split was chosen when the master skill tree grew past the original
"masters 1-99" convention (see `docs/mechanics/master.md` for the full master skill tree
design).

**Two ids are load-bearing outside the content files, so renumbering is no longer free:**

- `BASIC_SKILL` is **pinned at id 1**. The Skills window has to name it to draw the tree
  gate (`skills.gd`'s `BASIC_SKILL_ID`), and an id is the only handle the client has —
  the wire carries ids and the Attack DB is keyed by them. Moving it means editing that
  constant too.
- `MultiPlayerJourneyScenario` hardcodes real ids from this file
  (`BLESSING_ID`, `HEAL_ID`, `DIVINE_PROTECTION_ID`) to drive an end-to-end investment.

Everywhere else, code resolves a skill by `identifier` through `SkillRepository`, which is
what `MasterSkillTreeService` and `BasicSkillGate` do. Prefer that. And note that
renumbering an **existing** id needs a database wipe: `YmlImporterBootRunner` matches rows
by identifier and `Skill.updateContentFrom` deliberately does not copy `id`, so an edit
here silently leaves the old id in the `skill` table while every other file says the new
one.

### Castable, passive, unimplemented — and why there is no `type`

`skills.yml` used to carry a `type` (`MELEE_PHYSICAL`, `NO_DAMAGE`, `PASSIVE`, ...). It is gone,
because it meant two unrelated things at once: how damage is calculated, and whether the entry may
be activated. The one question that survives is answered by `script` alone:

| `script` in `skills.yml` | meaning |
| --- | --- |
| names a `SkillStrategy` bean (`battle/skill/scripts/`) | **castable** — an active skill |
| absent | not castable: a passive, or a skill nobody has written yet |
| present but no such bean | not implemented; `SkillScriptBootValidator` warns at boot |

`SkillStrategyFactory.isCastable` is the single place that answers it.

A passive that *does* have a stat effect still leaves this column empty: a `PassiveSkillScript`
declares its own `skillIdentifier` on the bean rather than being named from the YAML, so `script`
stays one vocabulary — see that interface's KDoc for the full argument. This is what makes
`syncSkillDb`'s `is_passive = (script == null)` exact, since a Gradle task cannot see Spring beans.

A skill must never be castable *and* named by a `PassiveSkillScript` —
`PassiveSkillScriptRegistry.bind` fails the boot if it is, because the two would read the same
invested level with nothing deciding which one a point bought.

### A basic attack is not a skill

A sword swing, an arrow, a mob's bite has **no entry in this file at all**. It is a `BattleAttack`
(`battle/skill/BattleAttack.kt`) resolved by `AttackStrategyFactory` → `AttackStrategy` and run by
`AttackExecutionService` — no catalogue row, no script, no mana, no cast bar. That split is why
`AttackType` still exists (it selects the melee/ranged/magic formula) but no longer appears in
`skills.yml`.

The old `tackle` row (id 1001) was exactly this mistake and has been removed, along with the
`0`-valued `skillId` mobs used to be seeded with — an id `skills.yml` never had. An AI profile's
`attacks:` entry leaves `skill_id` unset for a basic attack, and names a real catalogue id only when
the creature should genuinely *cast* something (it must then know that skill via `KnownSkills`).

**How hard it hits** is `battle/damage/BaseDamageCalculator.kt`, whose KDoc carries the formula and
what is still missing from it. Don't restate it anywhere else — it is one template with three
subclasses (melee, ranged, magic) differing only in which attribute feeds the attack and how armour
bites, and it follows Ragnarok Online pre-renewal, as `DefenseValues`, `DerivedStatusValues` and
`ElementModifier` already did. A skill script that wants its *own* number computes it itself
(`Firebolt`, `Ember`, `Heal` all do); one that wants the shared formula can use these calculators.

### How a cast is resolved, and the execution budget

`SkillExecutionService.execute` **enqueues** and returns; the script runs on an `AsyncJobExecutor`
worker, keyed on the caster so two casts by one entity resolve in order. This is what lets a script
do its own world manipulation — spawn a patch, place a station, mint a chart — instead of returning
a spec for a service to enact.

It is safe because `World.tick` holds the world lock for its whole duration and every scope a script
opens takes the same lock, so a cast can never interleave with a tick. What it *can* do is make the
tick wait, so a script reaches the world only through `SkillContext.world` (a `SkillWorld`), whose
every operation is charged against a per-cast `SkillBudget`. Overrunning it fizzles the cast and logs
at ERROR. The ceilings are the `skill:` block in `application.yml`.

**A script must never inject `World` or `WorldView`.** Scripts are collected into
`SkillStrategyFactory`, which `CastingSystem` transitively depends on, and the `World` bean is
assembled from every system — injecting one closes a cycle Spring refuses to build and the context
fails at boot with nothing pointing at the script. A service whose methods take a world goes behind
`SkillWorld` (`offerRecipes`, `survey`); a service that needs none (a config, a calculator) a script
may inject itself.

### What a cast costs, and when

Four moments, and mixing them up is how a player ends up watching a five-second bar that was always
going to be refused — or losing a reagent to a cast they cancelled:

| moment | what is settled | where |
| --- | --- | --- |
| **activation** (before the cast bar) | is the reagent there — **checked only** | `SkillStrategy.checkCastStart`, from `ActivateSkillHandler` |
| **resolution** | range, line of sight, an eligible target | `SkillStrategy.isCastPossible` |
| **resolution**, after that check | mana | `SkillExecutionService` |
| **resolution**, in the script | the reagent — **checked again and taken** | the script, or the service it hands the work to |

`checkCastStart` defaults to checking nothing; `Cartography` is the one skill that overrides it, asking
`SurveyService.checkBlank` whether a sheet of blank vellum is in the live `Inventory`. It runs on the
message thread inside the caster's own `modify` scope, so it is handed the `World` directly (there is no
budget and no snapshot yet) and it may only read — it does no relational work and takes nothing. A
refusal comes back as an `OpError` and is sent as an `OperationErrorSMSG`, so the player reads it in
chat instead of watching a bar.

**The early check never replaces the late one.** It is there for the player, not for correctness: the
reagent is spent where it can be spent transactionally alongside the effect (`ChartService.mint` for a
survey), and the seconds in between are long enough to drop, trade or use up what was counted. Checking
at the start and taking at the end is exactly what `CraftingService.start`/`resolve` does — and it is
what makes an interrupted, cancelled or walked-out-of cast cost nothing.

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
- Two gates beyond `prerequisites` are enforced in `MasterSkillTreeService` and have **no
  representation in this file** — they are code, read off `tree`/`subTree`:
  - **Basic Skill 5 opens every tree but Novice** (`TREE_UNLOCK_BASIC_SKILL_LEVEL`). The
    Novice tree is always investable; anything else needs `BASIC_SKILL` maxed first. Checked
    per level, not once per batch, so one request can take Basic Skill to 5 and then spend
    into another tree.
  - **5+ points anywhere in a tree open its sub-trees** (`SUB_TREE_UNLOCK_THRESHOLD`), but
    only for a tree that has a trunk skill at all — Scholar and Warrior are nothing but a
    sub-tree today, so gating them would make Priest/Wizard unreachable rather than merely
    unfinished.

  `master.md`'s remaining "tree mastery" idea (a master capping out at 3 tree masteries) is
  still unimplemented, in this file and in code.
- The client mirrors both gates in `skills.gd` (`_is_tree_unlocked`), deriving them from the
  `SkillListSMSG` it already receives rather than from a flag of its own — so the tabs
  unlock on the list the server pushes right after an investment, not at the next login.
  The constants there are marked as mirrors; change one side and the other drifts silently.

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

Field provenance — the fields `SkillDbSyncTask` mirrors from the server are `skill_id`,
`max_level`, `description_key`, `target_type`, `aoe_radius`, `cast_time`, `tree`, `sub_tree`
and `is_passive`. `is_passive` is derived from whether `skills.yml` gives the skill a `script`,
which is the server's own definition of castable: a skill the client cannot activate is refused
by the Skills window as a hotbar drag (`skill_row.gd`) and by `ShortcutContainer` as a drop.
The build cannot see Spring beans, so a script name with no bean behind it still reads as
castable here while the server refuses the cast — that is the unimplemented case, and
`SkillScriptBootValidator` is what reports it. Everything else (`icon`, `name`, `mana_cost`, `cooldown`) is client-only
presentation, hand-authored by whoever adds the skill; the task never touches it.

A `.tres` is normally edited straight in Godot's inspector, which rewrites the whole file on
save and **drops every property still equal to its script default** — one save can remove
`max_level = 1`, `target_type = "GROUND"`, `mana_cost = 0` and `cooldown = 0.0` outright.
That is not drift: the sync task reads a missing line as the resource default, and `stubTres`
omits those same lines for the same reason. Don't put them back by hand.

### Icons

`icon` is a plain `Texture2D` export, so authoring one is drag-and-drop in the inspector, the
same as `ItemResource.icon`. The PNG lives in the DB folder beside its `.tres` and is named
after it (`40_mining.tres` ← `40_mining.png`); attribution goes in `bestia-client/ASSETS.md`.

Never read `icon` directly — `AttackResource.get_icon()` falls back to a shared placeholder so
a skill with no art yet draws a box instead of an empty hole (`ItemResource.get_icon()` does the
same for items). `checkSkillDb` reports how many skills still lack one: a to-do list, never a
build failure, since the field is deliberately hand-authored.

Source art can be any size; the importer scales it, so nothing needs pre-scaling externally.
Select the PNGs together in the FileSystem dock and set four options in the Import dock once:

| Option | Value |
| --- | --- |
| `process/size_limit` | `128` — 2x the 64x64 box the row draws. Downscale-only, longest dimension, aspect preserved. |
| `mipmaps/generate` | off |
| `compress/mode` | `Lossless` |
| `detect_3d/compress_to` | `Disabled` — left enabled, Godot silently switches the texture to VRAM-compressed + mipmaps if it ever shows up in a 3D scene. |

An atlas is deliberately not used. `ResourceImporterTextureAtlas` supports only `atlas_file`,
`import_mode`, `crop_to_region` and `trim_alpha_border_from_region` — no resize and no
compression — so it would force hand-scaling every source, and `AttackDB` loads every `.tres`
(and therefore every icon) on the first Skills-window open regardless, so it would save nothing.

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
   `description_key` = `SKILL_<id>_DESC`, plus hand-authored `name`/`mana_cost`/`cooldown`.
   `syncSkillDb` will create the file as a stub for you if you'd rather start from that.
   For `icon`, drop `<id>_<identifier>.png` in the same folder and assign it in the inspector
   (see Icons above) — leaving it unset is fine, the skill just shows the placeholder.
4. Restart zone-server once to confirm boot doesn't throw (duplicate id, unresolved
   `master_skill_tree.yml` identifier, or an `aoeRadius` that disagrees with `targetType`), and
   to see whether the new skill shows up in `SkillScriptBootValidator`'s "no skill script found"
   warning — if it does, it can be learned but will do nothing when cast.
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
