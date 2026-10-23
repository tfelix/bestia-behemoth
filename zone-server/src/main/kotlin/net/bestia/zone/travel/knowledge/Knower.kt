package net.bestia.zone.travel.knowledge

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId

/** Who remembers what a traveller sees: a master and all its bestias share one memory, a creature keeps its own. */
sealed interface Knower {

  data class Player(val masterId: Long) : Knower

  data class Creature(val entityId: EntityId) : Knower

  companion object {
    val READS: ComponentClassSet = setOf(Master::class, OwnedBestia::class)

    fun of(world: World, entity: EntityId): Knower {
      val masterId = world.get(entity, OwnedBestia::class)?.masterId ?: world.get(entity, Master::class)?.masterId
      return if (masterId != null) Player(masterId) else Creature(entity)
    }
  }
}
