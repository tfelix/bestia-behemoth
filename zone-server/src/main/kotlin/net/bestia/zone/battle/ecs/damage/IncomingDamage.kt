package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

class IncomingDamage() : Component {

  val amounts: MutableList<DamageAmount> = mutableListOf()

  data class DamageAmount(
    val amount: Int,
    val sourceEntityId: EntityId,
  )

  fun add(amount: Int, sourceEntity: EntityId) {
    amounts.add(DamageAmount(amount, sourceEntity))
  }

  fun total(): Int {
    return amounts.sumOf { it.amount }
  }
}
