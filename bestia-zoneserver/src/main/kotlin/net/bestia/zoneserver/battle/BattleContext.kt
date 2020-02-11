package net.bestia.zoneserver.battle

import net.bestia.model.battle.Attack
import net.bestia.model.battle.Element
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent

/**
 * Data transfer object to carry all needed data during a damage calculation.
 *
 * @author Thomas Felix
 */
data class BattleContext(
    val usedAttack: Attack,
    val attacker: Entity,
    val damageVariables: DamageVariables,
    val defender: Entity,
    val defenderElement: Element,
    val attackElement: Element,
    val weaponAtk: Float
) {

  val attackerStatusPoints get() = attacker.getComponent(StatusComponent::class.java).statusValues
  val defenderStatusPoints get() = defender.getComponent(StatusComponent::class.java).statusValues

  val attackerStatusBased get() = attacker.getComponent(StatusComponent::class.java).statusBasedValues
  val defenderStatusBased get() = defender.getComponent(StatusComponent::class.java).statusBasedValues

  val attackerCondition get() = attacker.getComponent(ConditionComponent::class.java).conditionValues
  val defenderCondition get() = defender.getComponent(ConditionComponent::class.java).conditionValues

  val attackerLevel get() = attacker.getComponent(LevelComponent::class.java).level
  val defenderLevel get() = defender.getComponent(LevelComponent::class.java).level
}
