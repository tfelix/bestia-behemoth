package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

class IncomingDamage() : Component {

  val amounts: MutableList<DamageAmount> = mutableListOf()

  /** One staged hit. [kind] lets a status that reacts to it tell a sword from a spell, see [HitKind]. */
  data class DamageAmount(
    val amount: Int,
    val sourceEntityId: EntityId,
    val kind: HitKind = HitKind.UNTYPED,
  )

  fun add(amount: Int, sourceEntity: EntityId, kind: HitKind = HitKind.UNTYPED) {
    amounts.add(DamageAmount(amount, sourceEntity, kind))
  }

  fun total(): Int {
    return amounts.sumOf { it.amount }
  }

  /** Hands over everything staged so far and keeps this instance for whatever is staged next. */
  fun drain(): List<DamageAmount> {
    val drained = amounts.toList()
    amounts.clear()
    return drained
  }
}
