package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs2.Component
import net.bestia.zone.util.EntityId

class Damage() : Component {

  val amounts: MutableList<DamageAmount> = mutableListOf()

  data class DamageAmount(
    val amount: Int,
    val sourceEntityId: EntityId
  )

  fun add(amount: Int, sourceEntity: EntityId) {
    amounts.add(DamageAmount(amount, sourceEntity))
  }

  fun total(): Int {
    return amounts.sumOf { it.amount }
  }
}
