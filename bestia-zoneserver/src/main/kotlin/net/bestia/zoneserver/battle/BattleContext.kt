package net.bestia.zoneserver.battle

import net.bestia.zoneserver.entity.Entity
import net.bestia.model.battle.Attack
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.battle.Element
import net.bestia.model.bestia.StatusValues
import net.bestia.model.entity.StatusBasedValues

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
    val attackerStatusPoints: StatusValues,
    val defenderStatusPoints: StatusValues,
    val attackerStatusBased: StatusBasedValues,
    val defenderStatusBased: StatusBasedValues,
    val attackerCondition: ConditionValues,
    val defenderCondition: ConditionValues,
    val defenderElement: Element,
    val attackElement: Element,
    val weaponAtk: Float
)
