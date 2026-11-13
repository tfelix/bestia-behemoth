package net.bestia.zone.skill

/**
 * Every skill that *code* names, by identifier. Call sites use this -
 * `skills.findByIdentifier(SkillId.M_CARPENTRY)` - so naming a skill is a symbol lookup with
 * autocompletion rather than a string literal re-declared in whichever service happens to need it,
 * which is what a typo used to cost: a silently inert bonus or gate rather than a compiler error.
 *
 * ### No numeric id here, unlike [net.bestia.zone.dialog.DialogId] and
 * [net.bestia.zone.battle.status.StatusEffectId]
 *
 * Those two carry their id because the wire and their in-memory registries are keyed on it. A
 * skill's id is content owned by `skills.yml`, and every call site that wants one already goes
 * through [net.bestia.zone.skill.persistence.findByIdentifier] "because the id in `skills.yml` is content and this is code". Copying
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
   * effect ([net.bestia.zone.skill.BasicSkillGate]), and it is also the gate on every
   * skill tree but Novice. Pinned at catalogue id 1, which the client hardcodes.
   */
  M_BASIC_SKILL,

  // Crafting: each one's invested level buys a success chance, a craft time, an item tier or a rune
  // slot in net.bestia.zone.crafting.CraftBonusService.
  M_CARPENTRY,

  /** The docs' "Master Craftsman"; the identifier predates that name. */
  M_MASTER_CRAFTSMAN,
  M_ITEM_CUSTOMIZATION,
  M_ORE_REFINEMENT,
  M_FORGE_WEAPON,
  M_FORGE_ARMOR,
  M_EQUIPMENT_REPAIR,
  M_WEAPONRY_RESEARCH,
  M_MASTER_SMITH,
  M_COOKING,
  M_UPGRADE_EQUIPMENT,

  // Weather: EnvironmentalExposureSystem widens the comfortable band with one, WeatherPublisher
  // buys forecast reach with the other.
  M_WEATHER_RESISTANCE,
  M_WEATHER_SENSE,

  // Player settlements and their auction houses: the town code checks who may found, own or link.
  M_FOUNDER,
  M_TRADE_POST_OWNER,
  M_TRADE_AGREEMENT,
  // FishingCatalogue: its rank raises the catch level cap.
  M_FISHING,

  // Treasure: Excavation digs a treasure cache faster and turns up its best item more often. See
  // net.bestia.zone.treasure.DigService.
  M_EXCAVATION,

  // Mining: its level gates the shaft kit, and sets how deep and how fast a shaft digs.
  M_MINING,

  // Passives with a stat effect, named by their PassiveSkillScript bean rather than from the yml.
  M_MEDITATION,
  M_QUICK_TRAVEL,
  M_MAXIMIZE_CARRY_CAPACITY,
  M_EXPERT_TAMING,
  M_FREE_CAST,
  M_DEMON_BANE,
  M_ENDURE,
  M_IRON_SKIN,
  M_ARCANE_MASTERY,
  M_HEAVY_WEAPON_MASTERY,
  M_HEAVY_ARMOR_MASTERY,
  M_AUTO_GUARD,
  M_TOUGH_GUY,
  M_UNBREAKABLE,
  M_IRON_FIST,
  M_CAMOUFLAGE,
  M_TRAILBLAZER,
  M_PACK_HUNT,
  M_OWLS_EYE,
  M_VULTURES_EYE,
  M_INSTRUMENT_MASTERY,
  M_NIMBLE_STEPS,
  // Its drain bonus is read by net.bestia.zone.battle.attack.PoisonedBlade.
  M_POISON_RESEARCH,
  // Its chance is read by net.bestia.zone.battle.attack.TripleBlow on every landed swing.
  M_TRIPLE_BLOW,

  // Read by the systems they change: felling and its yield, picking herbs.
  M_LUMBERJACK,
  M_HERBALISM,
  M_HAGGLING,

  // Capture: their levels add to every catch roll in net.bestia.zone.capture.BestiaTrapSystem.
  M_TRAPPING,
  M_BEASTFRIEND,

  // The companion app starts a survey without a cast, so it reads the level and the reagents itself.
  M_CARTOGRAPHY,

  // Breeding: net.bestia.zone.master.skill.BloodlineStudy reads its level for timers, dominant IVs and IV reading.
  M_BLOODLINE_STUDY,

  // The Spell Inscription picker shows the odds before the cast, so it reads the level.
  M_SPELL_INSCRIPTION,

  // A second weapon in the left hand needs it, see net.bestia.zone.item.net.EquipItemHandler. Its passive swings it.
  M_DUAL_WIELD,

  // net.bestia.zone.casting.plagiarism.Plagiarism reads its level when a spell strikes a master.
  M_PLAGIARISM,

  // A Bard's song reads them off the Bard while it plays, see net.bestia.zone.battle.status.scripts.SongScript.
  M_VOICE_TRAINING,
  M_GRAND_PERFORMANCE,

  // Guild skills a guild learns, not a master: net.bestia.zone.guild reads the guild's level of each.
  GUILD_EMBLEM,
  OFFICIAL_GUILD_APPROVAL,
  GUILD_EXTENSION,
  GREAT_LEADERSHIP,
  SHARP_GAZE,
  GLORIOUS_WOUNDS,
  COLD_HEART,
  ARCANE_ACCORD,
  STEADFAST_MIND,
  FORTUNES_FAVOR,
  EMERGENCY_MOVE,
  BATTLE_ORDERS,
  GUILD_REGENERATION,
  RESTORATION,
  EMERGENCY_RECALL,
  RALLY_TO_THE_BANNER,
}
