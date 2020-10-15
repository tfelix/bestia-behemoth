package net.bestia.zoneserver.battle

import net.bestia.model.battle.AttackType
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.Defense
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.entity.StatusBasedValues
import net.bestia.zoneserver.battle.attack.BattleAttack
import net.bestia.zoneserver.battle.damage.DamageVariables
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent

/**
 * Data transfer object to carry all needed data during a damage calculation.
 *
 * @author Thomas Felix
 */
sealed class BattleContext {
  abstract val usedAttack: BattleAttack
  abstract val attacker: Entity
  abstract val damageVariables: DamageVariables
  abstract val attackElement: Element
  abstract val weaponAtk: Float // possibly include this in DamageVariables

  val attackerStatusPoints get() = attacker.getComponent(StatusComponent::class.java).statusValues
  val attackerDefense get() = attacker.getComponent(StatusComponent::class.java).defense
  val attackerStatusBased get() = attacker.getComponent(StatusComponent::class.java).statusBasedValues
  val attackerCondition get() = attacker.getComponent(ConditionComponent::class.java).conditionValues
  val attackerLevel get() = attacker.getComponent(LevelComponent::class.java).level
}

/**
 * Context used when damage is calculated between entities.
 */
data class EntityBattleContext(
    override val usedAttack: BattleAttack,
    override val attacker: Entity,
    override val damageVariables: DamageVariables,
    val defender: Entity,
    val defenderElement: Element,
    override val attackElement: Element,
    override val weaponAtk: Float
) : BattleContext() {

  val defenderStatusPoints get() = defender.getComponent(StatusComponent::class.java).statusValues
  val defenderDefense get() = defender.getComponent(StatusComponent::class.java).defense
  val defenderStatusBased get() = defender.getComponent(StatusComponent::class.java).statusBasedValues
  val defenderCondition get() = defender.getComponent(ConditionComponent::class.java).conditionValues
  val defenderLevel get() = defender.getComponent(LevelComponent::class.java).level

  companion object {
    fun test(): EntityBattleContext {
      return EntityBattleContext(
          usedAttack = BattleAttack(
              strength = 10,
              manaCost = 5,
              range = 10,
              attackType = AttackType.MELEE_PHYSICAL,
              needsLineOfSight = false
          ),
          attackElement = Element.NORMAL,
          defenderElement = Element.NORMAL,
          weaponAtk = 10f,
          attacker = Entity(1).apply {
            addComponent(LevelComponent(entityId = id))

            val statusValues = BasicStatusValues()
            addComponent(StatusComponent(
                entityId = id,
                statusValues = statusValues,
                statusBasedValues = BasicStatusBasedValues(statusValues = statusValues, level = 1),
                defense = Defense(),
                element = Element.NORMAL
            ))
          },
          defender = Entity(2).apply {
            addComponent(LevelComponent(entityId = id))

            val statusValues = BasicStatusValues()
            addComponent(StatusComponent(
                entityId = id,
                statusValues = statusValues,
                statusBasedValues = BasicStatusBasedValues(statusValues = statusValues, level = 1),
                defense = Defense(),
                element = Element.NORMAL
            ))
          },
          damageVariables = DamageVariables()
      )
    }
  }
}

/**
 * Context used if we choose to attack the ground.
 */
data class GroundBattleContext(
    override val usedAttack: BattleAttack,
    override val attacker: Entity,
    override val damageVariables: DamageVariables,
    override val attackElement: Element,
    override val weaponAtk: Float
) : BattleContext()