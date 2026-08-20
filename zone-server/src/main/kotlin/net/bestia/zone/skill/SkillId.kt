package net.bestia.zone.skill

/**
 * Every skill that *code* names, by identifier. Call sites use this -
 * `skills.findByIdentifier(SkillId.CARPENTRY)` - so naming a skill is a symbol lookup with
 * autocompletion rather than a string literal re-declared in whichever service happens to need it,
 * which is what a typo used to cost: a silently inert bonus or gate rather than a compiler error.
 *
 * ### No numeric id here, unlike [net.bestia.zone.dialog.DialogId] and
 * [net.bestia.zone.battle.status.StatusEffectId]
 *
 * Those two carry their id because the wire and their in-memory registries are keyed on it. A
 * skill's id is content owned by `skills.yml`, and every call site that wants one already goes
 * through [findByIdentifier] "because the id in `skills.yml` is content and this is code". Copying
 * the ids in here would give a renumbering a second place to be right - and renumbering an existing
 * skill already needs a database wipe, so it must not also become a code change. The constant's
 * *name* is the identifier; there is nothing else to carry.
 *
 * ### A subset of the catalogue, on purpose
 *
 * Deliberately **not** one constant per `skills.yml` entry. Most of the catalogue is content that no
 * code names, and adding a skill has to stay a content-only edit - which is why
 * [SkillCatalogBootValidator] checks this enum against the catalogue in one direction only, the
 * reverse of what the dialog and status-effect validators do.
 *
 * A constant belongs here when code has to name the skill. The groups below say what does.
 */
enum class SkillId {

  /**
   * The permission ladder - trading, chat and parties are gated on its rank rather than on any
   * effect ([net.bestia.zone.account.master.skill.BasicSkillGate]), and it is also the gate on every
   * skill tree but Novice. Pinned at catalogue id 1, which the client hardcodes.
   */
  BASIC_SKILL,

  // Crafting: each one's invested level buys a success chance, a craft time, an item tier or a rune
  // slot in net.bestia.zone.crafting.MasterCraftBonusService.
  CARPENTRY,

  /**
   * The docs' "Master Craftsman". Kept as `TINKERER` because that is the identifier `skills.yml`
   * shipped and `LearnedSkill` has a foreign key to it - renaming would try to delete a referenced
   * row.
   */
  TINKERER,
  ITEM_CUSTOMIZATION,
  ORE_REFINEMENT,
  FORGE_WEAPON,
  FORGE_ARMOR,
  WEAPON_REPAIR,
  WEAPONRY_RESEARCH,
  MASTER_SMITH,
  COOKING,
  UPGRADE_EQUIPMENT,

  // Weather: EnvironmentalExposureSystem widens the comfortable band with one, WeatherPublisher
  // buys forecast reach with the other.
  WEATHER_RESISTANCE,
  WEATHER_SENSE,

  // Passives with a stat effect, named by their PassiveSkillScript bean rather than from the yml.
  INNER_PEACE
}
