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
    val defender: Entity? = null,
    val attackerStatusPoints: StatusValues? = null,
    val defenderStatusPoints: StatusValues? = null,
    var attackerStatusBased: StatusBasedValues? = null,
    var defenderStatusBased: StatusBasedValues? = null,
    var attackerCondition: ConditionValues? = null,
    var defenderCondition: ConditionValues? = null,
    val defenderElement: Element = Element.NORMAL,
    val attackElement: Element = Element.NORMAL,
    val attackerLevel: Int = 1,
    val defenderLevel: Int = 1
)
