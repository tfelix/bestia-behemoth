package net.bestia.zone.battle

import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.geometry.Vec3L

/**
 * Data transfer object to carry all needed data during a damage calculation.
 *
 * @author Thomas Felix
 */
sealed class BattleContext {
  abstract val usedAttack: BattleAttack
  abstract val attacker: BattleEntity
  abstract val damageVariables: DamageVariables
  abstract val weapon: Weapon

  abstract fun targetPosition(): Vec3L

  companion object {
    fun verifyGroundBattleContext(ctx: BattleContext): GroundBattleContext {
      return ctx as? GroundBattleContext
        ?: throw IllegalArgumentException("Context is not a GroundBattleContext")
    }
  }
}

/**
 * Context used when damage is calculated between entities.
 */
data class EntityBattleContext(
  override val usedAttack: BattleAttack,
  override val attacker: BattleEntity,
  override val weapon: Weapon,
  override val damageVariables: DamageVariables,
  val defender: BattleEntity,
) : BattleContext() {

  override fun targetPosition(): Vec3L {
    return defender.position
  }
}

/**
 * Context used if we choose to attack the ground.
 */
data class GroundBattleContext(
  override val usedAttack: BattleAttack,
  override val attacker: BattleEntity,
  override val weapon: Weapon,
  override val damageVariables: DamageVariables,
  val targetPosition: Vec3L
) : BattleContext() {
  override fun targetPosition(): Vec3L {
    return targetPosition
  }
}