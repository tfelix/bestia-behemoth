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
 * The skill applies a status effect rather than a health change - [net.bestia.zone.battle.skill.SkillExecutionService]
 * dispatches this to [net.bestia.zone.battle.StatusEffectService] instead of broadcasting a
 * [DamageEntitySMSG].
 */
data class Buff(
  val effectId: Long
) : Damage() {
  override val amount: Int = 0
}
