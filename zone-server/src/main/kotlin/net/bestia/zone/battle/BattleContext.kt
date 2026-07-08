package net.bestia.zone.battle

import net.bestia.zone.battle.attack.BattleSkill
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.geometry.Vec3L

/**
 * Data transfer object to carry all needed data during a damage calculation.
 *
 * @author Thomas Felix
 */
sealed class BattleContext {
  abstract val usedAttack: BattleSkill
  abstract val attacker: BattleEntity
  abstract val damageVariables: DamageVariables
  abstract val weapon: Weapon

  abstract fun targetPosition(): Vec3L
}

/**
 * Context used when damage is calculated between entities.
 */
data class EntityBattleContext(
  override val usedAttack: BattleSkill,
  override val attacker: BattleEntity,
  override val weapon: Weapon,
  override val damageVariables: DamageVariables,
  val defender: BattleEntity,
) : BattleContext() {

  companion object {
    /*
    fun test(): EntityBattleContext {
      return EntityBattleContext(
        usedAttack = BattleSkill(
          strength = 10,
          manaCost = 5,
          range = 10,
          skillType = SkillType.MELEE_PHYSICAL,
          needsLineOfSight = false
        ),
        attackElement = Element.NORMAL,
        defenderElement = Element.NORMAL,
        weaponAtk = 10f,
        attacker = 1L,
        defender = 2L,
        damageVariables = DamageVariables()
      )
    }*/
  }

  override fun targetPosition(): Vec3L {
    return defender.position
  }
}

/**
 * Context used if we choose to attack the ground.
 */
data class GroundBattleContext(
  override val usedAttack: BattleSkill,
  override val attacker: BattleEntity,
  override val weapon: Weapon,
  override val damageVariables: DamageVariables,
  val targetPosition: Vec3L
) : BattleContext() {
  override fun targetPosition(): Vec3L {
    return targetPosition
  }
}