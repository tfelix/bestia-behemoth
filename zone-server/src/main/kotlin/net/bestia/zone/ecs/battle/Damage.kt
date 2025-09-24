package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import net.bestia.zone.util.EntityId

class Damage(amount: Int, sourceEntityId: EntityId) : Component<Damage> {

  private val amounts: MutableList<DamageAmount> = mutableListOf()

  init {
    add(amount, sourceEntityId)
  }

  data class DamageAmount(
    val value: Int,
    val sourceEntityId: EntityId
  )

  fun add(amount: Int, sourceEntity: EntityId) {
    amounts.add(DamageAmount(amount, sourceEntity))
  }

  fun total(): Int {
    return amounts.sumOf { it.value }
  }

  override fun type() = Damage

  companion object : ComponentType<Damage>()
}
