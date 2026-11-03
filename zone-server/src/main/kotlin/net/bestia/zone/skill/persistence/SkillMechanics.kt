package net.bestia.zone.skill.persistence

/**
 * What a skill does beyond its archetype's damage formula, read by the generic bestia scripts: how many blows,
 * whether it moves its caster or its target, what status it may leave behind. Defaults do nothing, so a row
 * names only what it adds. Stored as one JSON column, see [SkillMechanicsConverter].
 */
data class SkillMechanics(
  /** Damage rolls per cast, a random count between the two. Each roll carries the full coefficient. */
  val hitsMin: Int = 1,
  val hitsMax: Int = 1,

  /** Physical blows skip DEF, spells skip MDEF. */
  val ignoreDefense: Boolean = false,

  /** Percent more chance for a physical skill to crit. */
  val critBonus: Int = 0,

  /** Tiles the caster rushes towards its target before the blow, see `SkillWorld.forceMove`. */
  val dash: Int = 0,

  /** Extra coefficient per tile rushed, so a long run-up hits harder. */
  val dashBonusPerTile: Float = 0f,

  /** Tiles the caster jumps to its target, and the radius around the landing the rider rolls on. */
  val leap: Int = 0,
  val landingRadius: Int = 0,

  /** Tiles a hit target is pushed away: from the caster, or from the centre for an area. */
  val knockback: Int = 0,

  /** Further targets a bolt jumps to, each within [chainHop] tiles of the last one hit. */
  val chainTargets: Int = 0,
  val chainHop: Int = 0,

  /** Share of the damage dealt that heals the caster. */
  val lifesteal: Float = 0f,

  /** Seconds the caster keeps channelling after the cast, acting every [channelInterval] seconds. */
  val channelSeconds: Float = 0f,
  val channelInterval: Float = 0f,

  /** An item the cast uses up; only a player's caster pays it. */
  val reagent: SkillReagent? = null,

  /** Limits a skill to targets of these kinds or elements, by name. Empty means everyone. */
  val affectsKinds: List<String> = emptyList(),
  val affectsElements: List<String> = emptyList(),

  /** An area centred on the caster instead of the aimed-at point. */
  val selfCentred: Boolean = false,

  /** Seconds a ward or a lingering zone stays on the ground; 0 leaves it to the archetype. */
  val areaSeconds: Float = 0f,

  /** Max mana every entity in the area gains per tick, in percent. */
  val manaPercentPerTick: Int = 0,

  /** A status the area keeps refreshing on everyone inside, by `StatusEffectId` name. */
  val zoneStatus: String? = null,

  /** Tiles per second an area drifts away from its caster. */
  val drift: Float = 0f,

  /** Each channel tick strikes one random tile of the area instead of all of it. */
  val pillars: Boolean = false,

  /** Percent chance the impact sets the ground alight. */
  val igniteChance: Int = 0,

  /** Percent chance a hit breaks off the target's cast. */
  val interruptChance: Int = 0,

  /** The coefficient grows with the caster's body weight and what it carries. */
  val weightScaling: Boolean = false,

  /** Statuses a hit may leave on its target. */
  val onHit: List<SkillStatusRider> = emptyList(),
) {

  companion object {
    val NONE = SkillMechanics()
  }
}
