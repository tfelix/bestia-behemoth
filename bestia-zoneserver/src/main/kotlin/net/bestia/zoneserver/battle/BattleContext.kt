package net.bestia.zoneserver.battle

import net.bestia.entity.Entity
import net.bestia.model.domain.Attack
import net.bestia.model.domain.ConditionValues
import net.bestia.model.domain.Element
import net.bestia.model.domain.StatusPoints
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
        val attackerStatusPoints: StatusPoints? = null,
        val defenderStatusPoints: StatusPoints? = null,
        var attackerStatusBased: StatusBasedValues? = null,
        var defenderStatusBased: StatusBasedValues? = null,
        var attackerCondition: ConditionValues? = null,
        var defenderCondition: ConditionValues? = null,
        val defenderElement: Element,
        val attackElement: Element,
        val attackerLevel: Int,
        val defenderLevel: Int
)
