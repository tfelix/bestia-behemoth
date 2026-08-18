package net.bestia.zone.battle.damage

sealed class Damage {
  abstract val amount: Int

  init {
    require(amount >= 0) { "Amount can not be negative." }
  }
}

/**
 * Normal hit damage.
 */
data class HitDamage(
  override val amount: Int
) : Damage()

/**
 * Damage is heal.
 */
data class Heal(
  override val amount: Int
) : Damage()

/**
 * This was a critical damage and will be displayed differently.
 */
data class CriticalHit(
  override val amount: Int
) : Damage()

/**
 * True damage will (in most cases) hit the entity without modifications
 * of status effects or equipments.
 */
data class TrueDamage(
  override val amount: Int
) : Damage()

/**
 * No damage as the attack was a miss.
 */
data object Miss : Damage() {
  override val amount: Int
    get() = 0
}

/**
 * The skill leaves a patch of ground behind rather than resolving into a single number.
 * [net.bestia.zone.battle.skill.SkillExecutionService] turns this into an
 * [net.bestia.zone.ecs.battle.effects.AreaEffect] entity at the aimed-at position; from there
 * [net.bestia.zone.ecs.battle.effects.AreaEffectSystem] owns it.
 *
 * A script returns a spec rather than spawning the entity itself for the reason [Buff] does not apply
 * its own status effect: a strategy is a pure function of a
 * [net.bestia.zone.battle.BattleContext] and has no world to write to.
 */
data class AreaEffectResult(
  /** Half the affected cube's edge in tiles; 1 is a 3x3. */
  val radiusTiles: Long,
  val damagePerTick: Int,
  val tickIntervalSeconds: Float,
  val durationSeconds: Float,

  /** Id into the client's effect catalogue (`Game/Entity/Visual/EffectVisual/DB`). */
  val visualId: Long,

  val hitsCaster: Boolean = true
) : Damage() {
  override val amount: Int = 0
}

/**
 * The skill is a crafting skill, and activating it is a request to work rather than to hurt something.
 *
 * [net.bestia.zone.battle.skill.SkillExecutionService] resolves it against the world: with no station of
 * [station] in range and [placesStation] set, it puts one up; otherwise it sends back what the caster can make
 * here. That decision needs the world and belongs there, for the same reason [AreaEffectResult] exists - a
 * strategy is a pure function of a [net.bestia.zone.battle.BattleContext] and has nothing to write to.
 *
 * The "place one if there is none" rule is what makes a single activation discoverable: a player who has taken
 * Carpentry and aims it at bare ground gets a workbench, and aiming it at their workbench gets the menu.
 */
data class CraftingResult(
  /** The station this work happens at, or null for work that needs none - a meal, a ritual, an upgrade. */
  val station: net.bestia.zone.world.prop.StaticEntityKind?,

  /** Whether activating this skill with no station in range builds one. False for a skill that only uses one. */
  val placesStation: Boolean
) : Damage() {
  override val amount: Int = 0

  init {
    require(!placesStation || station != null) {
      "A skill that places a station has to say which one"
    }
  }
}

/**
 * The skill is a survey: it charts the ground around the aimed-at point rather than affecting anything on it.
 *
 * [net.bestia.zone.battle.skill.SkillExecutionService] hands this to
 * [net.bestia.zone.cartography.SurveyService], which mints the chart off the tick thread. A script returns a
 * radius rather than writing a chart itself for the reason [AreaEffectResult] and [CraftingResult] exist - a
 * strategy is a pure function of a [net.bestia.zone.battle.BattleContext], with no world and no database.
 *
 * The centre is not carried here: it is the skill's target position, which the executor already has, and
 * duplicating it would let the two disagree.
 */
data class SurveyResult(
  val radiusMetres: Double
) : Damage() {
  override val amount: Int = 0

  init {
    require(radiusMetres > 0.0) { "A survey has to reach somewhere, was $radiusMetres m" }
  }
}

/**
 * The skill applies a status effect rather than a health change - [net.bestia.zone.battle.skill.SkillExecutionService]
 * dispatches this to [net.bestia.zone.battle.StatusEffectService] instead of broadcasting a
 * [DamageEntitySMSG].
 */
data class Buff(
  val effectId: Long
) : Damage() {
  override val amount: Int = 0
}
