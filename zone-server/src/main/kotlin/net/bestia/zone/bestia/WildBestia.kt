package net.bestia.zone.bestia

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId

/** A living bestia nobody owns: what a trap can catch and what a master can study. */
object WildBestia {

  /** What [isWild] reads, for a system's own `reads`. */
  val READS: ComponentClassSet = setOf(EntityVisual::class, Account::class, Dead::class)

  fun isWild(world: World, id: EntityId): Boolean {
    if (!world.isAlive(id)) return false
    if (world.get(id, EntityVisual::class)?.kind != VisualKind.BESTIA) return false

    return !world.has(id, Account::class) && !world.has(id, Dead::class)
  }
}
